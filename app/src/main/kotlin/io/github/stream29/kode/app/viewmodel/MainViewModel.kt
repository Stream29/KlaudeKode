package io.github.stream29.kode.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.model.MessageItem
import io.github.stream29.kode.app.model.toMessageItem
import io.github.stream29.kode.config.ConfigLoader
import io.github.stream29.kode.config.FileLocations
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.core.agent.SessionAwareAgentFactory
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.AgentState
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

public class MainViewModel : ViewModel(), MessageHandler, AgentState {
    // UI State
    public var taskInput: String by mutableStateOf("")
    public var messages: List<MessageItem> by mutableStateOf(emptyList())
    
    // Dialog visibility
    public var showSessionManager: Boolean by mutableStateOf(false)
    public var showConfigEditor: Boolean by mutableStateOf(false)
    public var showSettings: Boolean by mutableStateOf(false)
    
    // Session management
    public var currentSessionId: String? by mutableStateOf(null)
    public var sessionSummaries: List<SessionSummary> by mutableStateOf(emptyList())
    
    // Config state for editor
    public var configText: String by mutableStateOf("")
    public var configError: String? by mutableStateOf(null)
    
    // Auth and model configurations
    public var auths: List<LlmAuthConfig> by mutableStateOf(emptyList())
    public var models: List<LlmModelConfig> by mutableStateOf(emptyList())
    public var activeModelId: String? by mutableStateOf(null)
    
    public var autoSaveSessions: Boolean by mutableStateOf(true)
    public var temperature: Float by mutableStateOf(0.3f)
    
    // AgentState implementation
    override var isRunning: Boolean by mutableStateOf(false)
    override var isWaitingForInput: Boolean by mutableStateOf(false)
    override var currentTask: String by mutableStateOf("")

    private var inputDeferred: CompletableDeferred<String>? = null
    private var eventListener: AgentEventListener? = null
    private var agentFactory: SessionAwareAgentFactory? = null

    public fun setEventListener(listener: AgentEventListener?) {
        this.eventListener = listener
    }
    
    init {
        viewModelScope.launch {
            initializeAgentFactory()
            loadSettings()
        }
    }
    
    private suspend fun initializeAgentFactory() {
        try {
            val config = ConfigLoader.load()
            loadConfigToState(config)
            
            if (models.isNotEmpty()) {
                agentFactory = SessionAwareAgentFactory(
                    auths = auths,
                    models = models,
                    messageHandler = this@MainViewModel,
                    workingDir = File("."),
                    eventListener = eventListener,
                    logger = { logMessage: String -> log(logMessage) }
                )
            }
        } catch (e: Exception) {
            addSystemMessage("Failed to initialize: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadConfigToState(config: AppConfig) {
        auths = config.auths
        models = config.models
        if (activeModelId == null && models.isNotEmpty()) {
            activeModelId = models.first().id
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = ConfigLoader.load()
                loadConfigToState(config)
            } catch (e: Exception) {
            }
        }
    }

    public fun runTask() {
        if (taskInput.isBlank()) return
        if (agentFactory == null) {
            addSystemMessage("Agent not initialized. Please check your configuration.")
            return
        }

        val task = taskInput
        val userMessage = io.github.stream29.kode.session.core.model.SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = io.github.stream29.kode.session.core.model.MessageRole.USER,
            content = task,
            structuredData = null,
            contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
            timestamp = kotlinx.datetime.Clock.System.now(),
            metadata = null
        )
        messages = messages + userMessage.toMessageItem()
        taskInput = ""
        isRunning = true
        currentTask = task

        val modelId = activeModelId
        if (modelId == null) {
            addSystemMessage("No model selected. Please configure at least one model.")
            isRunning = false
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionId = currentSessionId ?: agentFactory!!.createSession(
                    title = "Conversation ${System.currentTimeMillis()}",
                    systemPrompt = null,
                    modelId = modelId
                )
                currentSessionId = sessionId
                
                val result = agentFactory!!.runWithSession(sessionId, task, modelId)
                
                val assistantMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                    content = result,
                    structuredData = null,
                    contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    metadata = null
                )
                messages = messages + assistantMessage.toMessageItem()
            } catch (e: Exception) {
                val errorMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                    content = "Error: ${e.message}",
                    structuredData = null,
                    contentType = io.github.stream29.kode.session.core.model.ContentType.ERROR,
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    metadata = null
                )
                messages = messages + errorMessage.toMessageItem()
                eventListener?.onEvent(AgentEvent.Error(e.message ?: "Unknown error", e))
            } finally {
                isRunning = false
                currentTask = ""
            }
        }
    }
    
    public fun createNewSession() {
        currentSessionId = null
        messages = emptyList()
        addSystemMessage("New session created")
    }
    
    public fun continueCurrentSession() {
        val sessionId = currentSessionId
        if (sessionId == null) {
            addSystemMessage("No active session")
            return
        }
        
        if (agentFactory == null) {
            addSystemMessage("Agent not initialized")
            return
        }
        
        val modelId = activeModelId
        if (modelId == null) {
            addSystemMessage("No model selected")
            return
        }
        
        isRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = agentFactory!!.continueSession(sessionId, modelId)
                val assistantMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                    content = result,
                    structuredData = null,
                    contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    metadata = null
                )
                messages = messages + assistantMessage.toMessageItem()
            } catch (e: Exception) {
                addSystemMessage("Error: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }
    
    public fun forkFromMessage(messageIndex: Int) {
        val sessionId = currentSessionId
        if (sessionId == null || agentFactory == null) {
            addSystemMessage("No active session to fork from")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val forkMessages = messages.take(messageIndex + 1)
                val newSession = agentFactory!!.sessionManager.createSession(
                    title = "Fork at message ${messageIndex + 1}",
                    systemPrompt = null,
                    tags = emptyList(),
                    configuration = io.github.stream29.kode.session.core.model.SessionConfiguration(
                        preferredModel = null,
                        systemPrompt = null,
                        maxIterations = null,
                        temperature = null,
                        customValues = null
                    )
                )
                
                currentSessionId = newSession.id
                messages = forkMessages
                addSystemMessage("Forked from message ${messageIndex + 1}")
            } catch (e: Exception) {
                addSystemMessage("Failed to fork: ${e.message}")
            }
        }
    }
    
    // ==================== Session Management ====================
    
    public fun loadSessionList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summaries = agentFactory?.sessionManager?.listSessions(filter = null)
                sessionSummaries = summaries ?: emptyList()
            } catch (e: Exception) {
                addSystemMessage("Failed to load sessions: ${e.message}")
            }
        }
    }
    
    public fun switchToSession(sessionId: String) {
        currentSessionId = sessionId
        showSessionManager = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = agentFactory?.sessionManager?.getSession(sessionId)
                if (session != null) {
                    messages = session.messages.map { it.toMessageItem() }
                }
                addSystemMessage("Switched to session: ${sessionId.take(8)}...")
            } catch (e: Exception) {
                addSystemMessage("Failed to load session: ${e.message}")
            }
        }
    }
    
    public fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentFactory?.sessionManager?.deleteSession(sessionId, hardDelete = true)
                if (currentSessionId == sessionId) {
                    currentSessionId = null
                    messages = emptyList()
                }
                loadSessionList()
                addSystemMessage("Session deleted")
            } catch (e: Exception) {
                addSystemMessage("Failed to delete session: ${e.message}")
            }
        }
    }
    
    public fun forkSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newSession = agentFactory?.sessionManager?.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = null,
                    newTitle = "Fork of ${sessionId.take(8)}"
                )
                if (newSession != null) {
                    currentSessionId = newSession.id
                    messages = newSession.messages.map { it.toMessageItem() }
                    loadSessionList()
                    addSystemMessage("Session forked: ${newSession.id.take(8)}...")
                }
                showSessionManager = false
            } catch (e: Exception) {
                addSystemMessage("Failed to fork session: ${e.message}")
            }
        }
    }
    
    public fun archiveSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentFactory?.sessionManager?.archiveSession(sessionId)
                loadSessionList()
                addSystemMessage("Session archived")
            } catch (e: Exception) {
                addSystemMessage("Failed to archive session: ${e.message}")
            }
        }
    }
    
    // ==================== Config Management ====================
    
    public fun loadConfigForEditing() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configFile = FileLocations.configFile
                configText = if (configFile.exists()) {
                    configFile.readText()
                } else {
                    """auths:
  - type: Anthropic
    id: anthropic-main
    api_key: your-api-key-here
    base_url: null

models:
  - id: claude-sonnet
    auth_id: anthropic-main
    model: claude-sonnet-4-5-20250929
    display_name: Claude Sonnet 4.5"""
                }
                configError = null
                showConfigEditor = true
            } catch (e: Exception) {
                configError = "Failed to load config: ${e.message}"
            }
        }
    }
    
    public fun saveConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configFile = FileLocations.configFile
                configFile.writeText(configText)
                
                val config = ConfigLoader.load()
                if (config.models.isEmpty()) {
                    configError = "Config is valid but no models configured"
                } else {
                    configError = null
                    showConfigEditor = false
                    addSystemMessage("Config saved successfully")
                    initializeAgentFactory()
                }
            } catch (e: Exception) {
                configError = "Failed to save config: ${e.message}"
            }
        }
    }
    
    // ==================== Settings ====================
    
    public fun saveConfig(config: AppConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ConfigLoader.save(config)
                auths = config.auths
                models = config.models
                addSystemMessage("Configuration saved with ${config.models.size} models")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to save config: ${e.message}")
            }
        }
    }
    
    public fun testApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (models.isEmpty()) {
                    addSystemMessage("No models configured")
                    return@launch
                }
                
                val testFactory = SessionAwareAgentFactory(
                    auths = auths,
                    models = models,
                    messageHandler = object : MessageHandler {
                        override fun addMessageToUser(message: String) {}
                        override fun log(message: String) {}
                        override suspend fun requestInput(): String = ""
                    },
                    workingDir = File("."),
                    eventListener = null,
                    logger = { }
                )
                addSystemMessage("All configured models are valid")
            } catch (e: Exception) {
                addSystemMessage("API key test failed: ${e.message}")
            }
        }
    }
    
    public fun openDataDirectory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(FileLocations.dataDir)
                }
            } catch (e: Exception) {
                addSystemMessage("Failed to open directory: ${e.message}")
            }
        }
    }
    
    public fun confirmClearAllSessions() {
        // This would show a confirmation dialog in a real implementation
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentFactory?.sessionManager?.listSessions(filter = null)?.forEach { summary ->
                    agentFactory?.sessionManager?.deleteSession(summary.id, hardDelete = true)
                }
                currentSessionId = null
                messages = emptyList()
                loadSessionList()
                addSystemMessage("All sessions cleared")
            } catch (e: Exception) {
                addSystemMessage("Failed to clear sessions: ${e.message}")
            }
        }
    }

    public fun submitInput() {
        if (!isWaitingForInput) return

        val input = taskInput
        taskInput = ""
        
        val userMessage = io.github.stream29.kode.session.core.model.SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = io.github.stream29.kode.session.core.model.MessageRole.USER,
            content = input,
            structuredData = null,
            contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
            timestamp = kotlinx.datetime.Clock.System.now(),
            metadata = null
        )
        messages = messages + userMessage.toMessageItem()
        
        inputDeferred?.complete(input)
        isWaitingForInput = false
        inputDeferred = null
    }
    
    private fun addSystemMessage(content: String) {
        val systemMessage = io.github.stream29.kode.session.core.model.SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = io.github.stream29.kode.session.core.model.MessageRole.SYSTEM,
            content = content,
            structuredData = null,
            contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
            timestamp = kotlinx.datetime.Clock.System.now(),
            metadata = null
        )
        messages = messages + systemMessage.toMessageItem()
    }

    // MessageHandler implementation
    override suspend fun requestInput(): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        isWaitingForInput = true
        addSystemMessage("Waiting for user input...")
        return deferred.await()
    }

    override fun addMessageToUser(message: String) {
        val agentMessage = io.github.stream29.kode.session.core.model.SessionMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
            content = message,
            structuredData = null,
            contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
            timestamp = kotlinx.datetime.Clock.System.now(),
            metadata = null
        )
        messages = messages + agentMessage.toMessageItem()
    }

    override fun log(message: String) {
    }
    
    public fun switchModel(modelId: String) {
        val modelConfig = models.find { it.id == modelId }
        if (modelConfig != null) {
            activeModelId = modelId
            val displayName = modelConfig.displayName ?: modelConfig.model
            addSystemMessage("Switched to model: $displayName")
        }
    }

    public fun addAuth(auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuths = auths + auth
                val config = AppConfig(auths = newAuths, models = models)
                ConfigLoader.save(config)
                auths = newAuths
                addSystemMessage("Added auth provider: ${auth.provider} (${auth.id})")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to add auth: ${e.message}")
            }
        }
    }

    public fun updateAuth(id: String, auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuths = auths.map { if (it.id == id) auth else it }
                val config = AppConfig(auths = newAuths, models = models)
                ConfigLoader.save(config)
                auths = newAuths
                addSystemMessage("Updated auth provider: ${auth.provider} (${auth.id})")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to update auth: ${e.message}")
            }
        }
    }

    public fun deleteAuth(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuths = auths.filter { it.id != id }
                val config = AppConfig(auths = newAuths, models = models)
                ConfigLoader.save(config)
                auths = newAuths
                addSystemMessage("Deleted auth provider: $id")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to delete auth: ${e.message}")
            }
        }
    }

    public fun addModel(model: LlmModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newModels = models + model
                val config = AppConfig(auths = auths, models = newModels)
                ConfigLoader.save(config)
                models = newModels
                if (activeModelId == null) {
                    activeModelId = model.id
                }
                addSystemMessage("Added model: ${model.displayName ?: model.model}")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to add model: ${e.message}")
            }
        }
    }

    public fun updateModel(id: String, model: LlmModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newModels = models.map { if (it.id == id) model else it }
                val config = AppConfig(auths = auths, models = newModels)
                ConfigLoader.save(config)
                models = newModels
                addSystemMessage("Updated model: ${model.displayName ?: model.model}")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to update model: ${e.message}")
            }
        }
    }

    public fun deleteModel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newModels = models.filter { it.id != id }
                val config = AppConfig(auths = auths, models = newModels)
                ConfigLoader.save(config)
                models = newModels
                if (activeModelId == id) {
                    activeModelId = newModels.firstOrNull()?.id
                }
                addSystemMessage("Deleted model: $id")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to delete model: ${e.message}")
            }
        }
    }
}
