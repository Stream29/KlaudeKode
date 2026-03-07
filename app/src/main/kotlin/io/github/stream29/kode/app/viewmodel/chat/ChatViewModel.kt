package io.github.stream29.kode.app.viewmodel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.viewmodel.StopMode
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.core.agent.SessionExecutionRuntime
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.agent.model.*
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.*
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.ui.core.todo.TodoUiNode
import io.github.stream29.kode.ui.core.todo.TodoUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

public class ChatViewModel(
    public val currentSessionIdFlow: StateFlow<String?>,
    public val activeModelIdFlow: StateFlow<String?>,
    private val sessionManager: SessionManager,
    private val configManager: ConfigManager,
    private val onEventCallback: (AgentEvent, String?) -> Unit,
    private val onNotifyConfigChanged: () -> Unit,
) : ViewModel(), AgentEventListener {

    private val _uiState = MutableStateFlow(ChatUiState())
    public val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionBindingJob: Job? = null
    private var boundSessionId: String? = null
    
    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val inputDeferreds = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val sessionTitleGeneratingIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            currentSessionIdFlow.collect { sessionId ->
                if (sessionId != null) {
                    bindSessionFlows(sessionId)
                } else {
                    unbindSessionFlows()
                }
            }
        }
        
        viewModelScope.launch {
            sessionTitleGeneratingIds.collect { generatingIds ->
                val currentId = currentSessionIdFlow.value
                if (currentId != null) {
                    updateUiState { it.copy(isGeneratingSessionTitle = currentId in generatingIds) }
                }
            }
        }
    }

    private fun updateUiState(transform: (ChatUiState) -> ChatUiState) {
        _uiState.update(transform)
    }

    private fun bindSessionFlows(sessionId: String) {
        if (boundSessionId == sessionId && sessionBindingJob?.isActive == true) {
            return
        }

        sessionBindingJob?.cancel()
        boundSessionId = sessionId

        sessionBindingJob = viewModelScope.launch(Dispatchers.IO) {
            val runtime = sessionManager.getSessionState(sessionId) ?: return@launch
            val agentState = runtime.agent.value
            val mainMessagesFlow = agentState.messages
            val mainTodoStateFlow = agentState.todoState

            launch {
                mainTodoStateFlow.collect { todoNodes ->
                    withContext(Dispatchers.Main) {
                        if (currentSessionIdFlow.value != sessionId) return@withContext
                        updateUiState { current ->
                            current.copy(todoState = toTodoUiState(todoNodes, current.todoState))
                        }
                    }
                }
            }

            combine(
                runtime.metadata,
                runtime.config,
                mainMessagesFlow,
            ) { metadata, config, mainMessages ->
                Triple(metadata, config, mainMessages)
            }.collect { (metadata, config, mainMessages) ->
                withContext(Dispatchers.Main) {
                    if (currentSessionIdFlow.value != sessionId) return@withContext

                    updateUiState { current ->
                        val isRunning = metadata.state == SessionRunState.Running
                        current.copy(
                            messages = mainMessages,
                            currentSessionWorkDir = config.workDir.orEmpty(),
                            isRunning = isRunning,
                            isWaitingForInput = deriveWaitingForInput(
                                messages = mainMessages,
                            ),
                            stopMode = deriveStopMode(
                                isRunning = isRunning,
                                currentStopMode = current.stopMode,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun unbindSessionFlows() {
        sessionBindingJob?.cancel()
        sessionBindingJob = null
        boundSessionId = null
        updateUiState { ChatUiState() }
    }

    private fun toTodoUiState(todoNodes: List<TodoItem>, oldUiState: TodoUiState? = null): TodoUiState {
        val expandedPaths = mutableSetOf<String>()
        
        fun collectExpanded(nodes: List<TodoUiNode>) {
            nodes.forEach {
                if (it.expanded) expandedPaths.add(it.path)
                collectExpanded(it.subItems)
            }
        }
        
        if (oldUiState != null) {
            collectExpanded(oldUiState.rootNodes)
        }

        fun mapNode(node: TodoItem, pathPrefix: String, level: Int): TodoUiNode {
            val currentPath = if (pathPrefix.isEmpty()) node.name else "$pathPrefix:${node.name}"
            return TodoUiNode(
                name = node.name,
                completed = node.completed,
                subItems = node.subItems.map { mapNode(it, currentPath, level + 1) },
                path = currentPath,
                expanded = expandedPaths.contains(currentPath),
                level = level
            )
        }

        return TodoUiState(
            rootNodes = todoNodes.map { mapNode(it, "", 0) },
            allExpanded = false
        )
    }

    public fun runTask() {
        val task = uiState.value.taskInput
        if (task.isBlank()) return
        
        updateUiState { it.copy(taskInput = "") }
        
        val sessionId = currentSessionIdFlow.value ?: return
        val modelId = activeModelIdFlow.value ?: run {
            onEvent(AgentEvent.Error("No model selected", null), sessionId)
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val runtime = createExecutionRuntime()
                runSessionLifecycle(
                    sessionId = sessionId,
                    taskLabel = task,
                    onError = { e -> 
                        onEvent(AgentEvent.Error(e.message ?: "Unknown error", e), sessionId)
                    },
                    onSuccess = {
                        ensureSessionAutoTitle(sessionId, modelId, runtime)
                    },
                    execution = {
                        runtime.runWithSession(sessionId, task, modelId)
                    }
                )
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Failed to start task: ${e.message}", e), sessionId)
            }
        }
        sessionJobs[sessionId] = job
    }

    public fun submitInput(input: String) {
        val sessionId = currentSessionIdFlow.value ?: return
        val deferred = inputDeferreds.remove(sessionId)
        if (deferred != null) {
            deferred.complete(input)
        } else {
            // 如果不是在等待输入，可能是一个新任务
            if (uiState.value.isRunning) {
                 onEvent(AgentEvent.Error("Session is already running", null), sessionId)
            } else {
                updateUiState { it.copy(taskInput = input) }
                runTask()
            }
        }
    }

    public fun stopRun(kill: Boolean = false) {
        val sessionId = currentSessionIdFlow.value ?: return
        updateUiState { current ->
            if (!current.isRunning) {
                return@updateUiState current
            }
            current.copy(
                stopMode = nextStopModeAfterClick(
                    currentStopMode = current.stopMode,
                    forceStop = kill,
                ),
            )
        }
        if (kill) {
            sessionJobs[sessionId]?.cancel("User requested kill")
            sessionJobs.remove(sessionId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.stopRun(sessionId)
        }
    }

    public fun continueCurrentSession() {
        val sessionId = currentSessionIdFlow.value ?: return
        val modelId = activeModelIdFlow.value ?: return
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val runtime = createExecutionRuntime()
                runSessionLifecycle(
                    sessionId = sessionId,
                    taskLabel = "Continue",
                    onError = { e -> 
                        onEvent(AgentEvent.Error(e.message ?: "Unknown error", e), sessionId)
                    },
                    onSuccess = {
                        ensureSessionAutoTitle(sessionId, modelId, runtime)
                    },
                    execution = {
                        runtime.continueSession(sessionId, modelId)
                    }
                )
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Failed to continue: ${e.message}", e), sessionId)
            }
        }
        sessionJobs[sessionId] = job
    }

    private suspend fun createExecutionRuntime(): SessionExecutionRuntime {
        val config = configManager.load()
        return SessionExecutionRuntime(
            auths = config.auths,
            models = config.models,
            messageHandler = createMessageHandler(),
            eventListener = this,
            logger = { msg -> onEvent(AgentEvent.MessageToUser(msg), boundSessionId ?: "") },
            sessionManager = sessionManager,
        )
    }

    private fun createMessageHandler() = object : MessageHandler {
        override fun addMessageToUser(message: String) {
            // 消息会通过 SessionManager 自动同步到 UI
        }
        override fun log(message: String) {
            onEvent(AgentEvent.MessageToUser(message), boundSessionId ?: "")
        }
        override suspend fun requestInput(): String {
            val sessionId = boundSessionId ?: throw IllegalStateException("No active session")
            val deferred = CompletableDeferred<String>()
            inputDeferreds[sessionId] = deferred
            try {
                sessionManager.suspendForUserInput(sessionId)
                return deferred.await()
            } finally {
                inputDeferreds.remove(sessionId)
            }
        }
    }

    private suspend fun runSessionLifecycle(
        sessionId: String,
        taskLabel: String,
        onError: (Throwable) -> Unit,
        onSuccess: suspend () -> Unit,
        execution: suspend () -> Unit
    ) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                execution()
                onSuccess()
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                onError(e)
            } finally {
                sessionJobs.remove(sessionId)
            }
        }
        sessionJobs[sessionId] = job
    }

    private fun ensureSessionAutoTitle(
        sessionId: String,
        modelId: String,
        runtime: SessionExecutionRuntime,
        force: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionManager.getSession(sessionId) ?: return@launch
            if (force || session.title.startsWith("session-") || session.title.isBlank()) {
                sessionTitleGeneratingIds.update { it + sessionId }
                try {
                    val newTitle = runtime.generateSessionTitleFromConversation(sessionId, modelId)
                    if (newTitle != null) {
                        sessionManager.updateTitle(sessionId, newTitle)
                        onNotifyConfigChanged() // 通知列表刷新
                    }
                } finally {
                    sessionTitleGeneratingIds.update { it - sessionId }
                }
            }
        }
    }

    public fun regenerateCurrentSessionTitle() {
        val sessionId = currentSessionIdFlow.value ?: return
        val modelId = activeModelIdFlow.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val runtime = createExecutionRuntime()
                ensureSessionAutoTitle(
                    sessionId = sessionId,
                    modelId = modelId,
                    runtime = runtime,
                    force = true,
                )
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Failed to regenerate title: ${e.message}", e), sessionId)
            }
        }
    }

    public fun updateCurrentSessionTitle(title: String) {
        val sessionId = currentSessionIdFlow.value ?: return
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.updateTitle(sessionId, normalizedTitle)
                onNotifyConfigChanged()
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Failed to update title: ${e.message}", e), sessionId)
            }
        }
    }

    public fun toggleTodoExpand(path: String) {
        updateUiState { current ->
            fun toggleInNodes(nodes: List<TodoUiNode>): List<TodoUiNode> {
                return nodes.map { node ->
                    if (node.path == path) {
                        node.copy(expanded = !node.expanded)
                    } else if (path.startsWith("${node.path}:")) {
                        node.copy(subItems = toggleInNodes(node.subItems))
                    } else {
                        node
                    }
                }
            }
            current.copy(todoState = current.todoState.copy(rootNodes = toggleInNodes(current.todoState.rootNodes)))
        }
    }

    public fun forkFromMessage(index: Int) {
        val sessionId = currentSessionIdFlow.value ?: return
        val messages = uiState.value.messages
        if (index !in messages.indices) return
        val messageId = messages[index].id
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newSession = sessionManager.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = messageId,
                    newTitle = "Fork of ${sessionId.take(8)}"
                )
                onEvent(AgentEvent.MessageToUser("Forked to session ${newSession.id}"), sessionId)
            } catch (e: Exception) {
                onEvent(AgentEvent.Error("Failed to fork: ${e.message}", e), sessionId)
            }
        }
    }

    public fun setTaskInput(input: String) {
        updateUiState { it.copy(taskInput = input) }
    }

    override fun onEvent(event: AgentEvent) {
        onEventCallback(event, boundSessionId)
    }

    override fun onEvent(event: AgentEvent, sessionId: String) {
        onEventCallback(event, sessionId)
    }
}

internal fun deriveWaitingForInput(
    messages: List<SessionMessage>,
): Boolean {
    return messages.trailingPendingInputScriptOrNull() != null
}

internal fun deriveStopMode(
    isRunning: Boolean,
    currentStopMode: StopMode,
): StopMode {
    if (!isRunning) {
        return StopMode.None
    }
    return when (currentStopMode) {
        StopMode.None -> StopMode.Stop
        StopMode.Stop,
        StopMode.ForceStop,
        StopMode.SafeRequested,
        -> currentStopMode
    }
}

internal fun nextStopModeAfterClick(
    currentStopMode: StopMode,
    forceStop: Boolean,
): StopMode {
    if (forceStop) {
        return StopMode.ForceStop
    }
    return when (currentStopMode) {
        StopMode.SafeRequested,
        StopMode.ForceStop,
        -> StopMode.ForceStop

        StopMode.None,
        StopMode.Stop,
        -> StopMode.SafeRequested
    }
}

public data class ChatUiState(
    val messages: List<SessionMessage> = emptyList(),
    val currentSessionWorkDir: String = "",
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val stopMode: StopMode = StopMode.None,
    val currentTask: String = "",
    val todoState: TodoUiState = TodoUiState(rootNodes = emptyList(), allExpanded = false),
    val isGeneratingSessionTitle: Boolean = false,
    val taskInput: String = ""
)
