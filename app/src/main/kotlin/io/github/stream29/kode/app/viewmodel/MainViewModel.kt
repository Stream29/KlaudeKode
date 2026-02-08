@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ServiceConfig
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.app.service.WebToolsProvider
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.core.agent.SessionAwareAgentFactory
import io.github.stream29.kode.core.agent.SessionAwareAgentFactoryProvider
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import io.github.stream29.kode.session.core.storage.SortBy
import io.github.stream29.kode.session.core.storage.SortOrder
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.AgentState
import io.github.stream29.kode.ui.core.ApprovalHandler
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.ui.core.ToolApprovalDecision
import io.github.stream29.kode.ui.core.ToolApprovalRequest
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.LATEST_PROTOCOL_VERSION
import com.agentclientprotocol.model.PromptCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import java.awt.Desktop
import java.io.File
import java.nio.channels.Channels
import java.nio.channels.Pipe

public class MainViewModel(
    private val configManager: ConfigManager,
    private val sessionManager: SessionManager,
    private val agentFactoryProvider: SessionAwareAgentFactoryProvider,
    private val webToolsProvider: WebToolsProvider,
) : ViewModel(), MessageHandler, AgentState, AgentEventListener, ApprovalHandler {
    private var autoSaveEnabled: Boolean = false

    private val defaultAgentProfiles: List<AgentProfile> = listOf(
        AgentProfile(
            name = "build",
            description = "Full access agent for development",
            disabledTools = emptySet(),
            defaultYolo = false,
        ),
        AgentProfile(
            name = "plan",
            description = "Read-only planning agent",
            disabledTools = setOf("shell", "task", "file-edit"),
            defaultYolo = false,
        ),
        AgentProfile(
            name = "explore",
            description = "Exploration agent (search-heavy)",
            disabledTools = setOf("shell", "task", "file-edit"),
            defaultYolo = false,
        ),
    )

    private val _sessionUiState: MutableStateFlow<SessionUiState> = MutableStateFlow(SessionUiState())
    public val sessionUiState: StateFlow<SessionUiState> = _sessionUiState.asStateFlow()

    private val _appUiState: MutableStateFlow<AppUiState> = MutableStateFlow(
        AppUiState(agentProfiles = defaultAgentProfiles)
    )
    public val appUiState: StateFlow<AppUiState> = _appUiState.asStateFlow()

    // UI State
    public var taskInput: String
        get() = _sessionUiState.value.taskInput
        set(value) {
            updateSessionUiState { current ->
                current.copy(taskInput = value)
            }
        }

    public var messages: List<SessionMessage>
        get() = _sessionUiState.value.messages
        set(value) {
            updateSessionUiState { current ->
                current.copy(messages = value)
            }
        }
    
    // Navigation
    public var currentPage: io.github.stream29.kode.app.view.AppPage
        get() = _appUiState.value.currentPage
        set(value) {
            updateAppUiState { current ->
                current.copy(currentPage = value)
            }
        }

    // Dialog visibility
    public var showSessionManager: Boolean
        get() = _appUiState.value.showSessionManager
        set(value) {
            updateAppUiState { current ->
                current.copy(showSessionManager = value)
            }
        }

    public var showConfigEditor: Boolean
        get() = _appUiState.value.showConfigEditor
        set(value) {
            updateAppUiState { current ->
                current.copy(showConfigEditor = value)
            }
        }

    public var showSettings: Boolean
        get() = _appUiState.value.showSettings
        set(value) {
            updateAppUiState { current ->
                current.copy(showSettings = value)
            }
        }
    
    // Session management
    public var currentSessionId: String?
        get() = _sessionUiState.value.currentSessionId
        set(value) {
            updateSessionUiState { current ->
                applySessionRunState(
                    base = current.copy(currentSessionId = value),
                    sessionId = value,
                )
            }
        }

    public var sessionSummaries: List<SessionSummary>
        get() = _appUiState.value.sessionSummaries
        set(value) {
            updateAppUiState { current ->
                current.copy(sessionSummaries = value)
            }
        }

    public var sessionSearchQuery: String
        get() = _appUiState.value.sessionSearchQuery
        set(value) {
            updateAppUiState { current ->
                current.copy(sessionSearchQuery = value)
            }
        }

    public var sessionTagFilter: String
        get() = _appUiState.value.sessionTagFilter
        set(value) {
            updateAppUiState { current ->
                current.copy(sessionTagFilter = value)
            }
        }

    public var sessionStatusFilter: SessionStatusFilter
        get() = _appUiState.value.sessionStatusFilter
        set(value) {
            updateAppUiState { current ->
                current.copy(sessionStatusFilter = value)
            }
        }

    public var currentSessionWorkDir: String
        get() = _sessionUiState.value.currentSessionWorkDir
        set(value) {
            updateSessionUiState { current ->
                current.copy(currentSessionWorkDir = value)
            }
        }

    public var showNewSessionDialog: Boolean
        get() = _sessionUiState.value.showNewSessionDialog
        set(value) {
            updateSessionUiState { current ->
                current.copy(showNewSessionDialog = value)
            }
        }

    public var showSessionDirDialog: Boolean
        get() = _sessionUiState.value.showSessionDirDialog
        set(value) {
            updateSessionUiState { current ->
                current.copy(showSessionDirDialog = value)
            }
        }

    public var newSessionDirInput: String
        get() = _sessionUiState.value.newSessionDirInput
        set(value) {
            updateSessionUiState { current ->
                current.copy(newSessionDirInput = value)
            }
        }

    public var sessionDirDraft: String
        get() = _sessionUiState.value.sessionDirDraft
        set(value) {
            updateSessionUiState { current ->
                current.copy(sessionDirDraft = value)
            }
        }
    
    // Config state for editor
    public var configText: String
        get() = _appUiState.value.configText
        set(value) {
            updateAppUiState { current ->
                current.copy(configText = value)
            }
        }

    public var configError: String?
        get() = _appUiState.value.configError
        set(value) {
            updateAppUiState { current ->
                current.copy(configError = value)
            }
        }
    
    // Auth and model configurations
    public var auths: List<LlmAuthConfig>
        get() = _appUiState.value.auths
        set(value) = updateAppUiState { it.copy(auths = value) }

    public var models: List<LlmModelConfig>
        get() = _appUiState.value.models
        set(value) = updateAppUiState { it.copy(models = value) }

    public var activeModelId: String?
        get() = _appUiState.value.activeModelId
        set(value) = updateAppUiState { it.copy(activeModelId = value) }

    public var defaultModelId: String?
        get() = _appUiState.value.defaultModelId
        set(value) = updateAppUiState { it.copy(defaultModelId = value) }

    public var defaultThinking: Boolean
        get() = _appUiState.value.defaultThinking
        set(value) = updateAppUiState { it.copy(defaultThinking = value) }

    public var defaultSessionDir: String
        get() = _appUiState.value.defaultSessionDir
        set(value) = updateAppUiState { it.copy(defaultSessionDir = value) }

    public var maxStepsPerTurn: Int
        get() = _appUiState.value.maxStepsPerTurn
        set(value) = updateAppUiState { it.copy(maxStepsPerTurn = value) }

    public var maxRetriesPerStep: Int
        get() = _appUiState.value.maxRetriesPerStep
        set(value) = updateAppUiState { it.copy(maxRetriesPerStep = value) }

    public var maxRalphIterations: Int
        get() = _appUiState.value.maxRalphIterations
        set(value) = updateAppUiState { it.copy(maxRalphIterations = value) }

    public var reservedContextSize: Int
        get() = _appUiState.value.reservedContextSize
        set(value) = updateAppUiState { it.copy(reservedContextSize = value) }

    public var skillsDir: String
        get() = _appUiState.value.skillsDir
        set(value) = updateAppUiState { it.copy(skillsDir = value) }

    public var agentBuiltin: String
        get() = _appUiState.value.agentBuiltin
        set(value) = updateAppUiState { it.copy(agentBuiltin = value) }

    public var agentFile: String
        get() = _appUiState.value.agentFile
        set(value) = updateAppUiState { it.copy(agentFile = value) }

    public var logLevel: String
        get() = _appUiState.value.logLevel
        set(value) = updateAppUiState { it.copy(logLevel = value) }

    public var logFile: String
        get() = _appUiState.value.logFile
        set(value) = updateAppUiState { it.copy(logFile = value) }

    public var uiTheme: String
        get() = _appUiState.value.uiTheme
        set(value) = updateAppUiState { it.copy(uiTheme = value) }

    public var mcpToolTimeoutMs: Int
        get() = _appUiState.value.mcpToolTimeoutMs
        set(value) = updateAppUiState { it.copy(mcpToolTimeoutMs = value) }

    public var mcpServers: Map<String, io.github.stream29.kode.config.api.McpServerConfig>
        get() = _appUiState.value.mcpServers
        set(value) = updateAppUiState { it.copy(mcpServers = value) }

    public var mcpTestResults: Map<String, McpTestResult>
        get() = _appUiState.value.mcpTestResults
        set(value) = updateAppUiState { it.copy(mcpTestResults = value) }

    public var mcpTestsInFlight: Set<String>
        get() = _appUiState.value.mcpTestsInFlight
        set(value) = updateAppUiState { it.copy(mcpTestsInFlight = value) }

    public var mcpHealthResults: Map<String, McpHealthResult>
        get() = _appUiState.value.mcpHealthResults
        set(value) = updateAppUiState { it.copy(mcpHealthResults = value) }

    public var webSearchProvider: String
        get() = _appUiState.value.webSearchProvider
        set(value) = updateAppUiState { it.copy(webSearchProvider = value) }

    public var webSearchApiKey: String
        get() = _appUiState.value.webSearchApiKey
        set(value) = updateAppUiState { it.copy(webSearchApiKey = value) }

    public var webSearchBaseUrl: String
        get() = _appUiState.value.webSearchBaseUrl
        set(value) = updateAppUiState { it.copy(webSearchBaseUrl = value) }

    public var webSearchHeaders: String
        get() = _appUiState.value.webSearchHeaders
        set(value) = updateAppUiState { it.copy(webSearchHeaders = value) }

    public var webSearchEnv: String
        get() = _appUiState.value.webSearchEnv
        set(value) = updateAppUiState { it.copy(webSearchEnv = value) }

    public var webFetchProvider: String
        get() = _appUiState.value.webFetchProvider
        set(value) = updateAppUiState { it.copy(webFetchProvider = value) }

    public var webFetchApiKey: String
        get() = _appUiState.value.webFetchApiKey
        set(value) = updateAppUiState { it.copy(webFetchApiKey = value) }

    public var webFetchBaseUrl: String
        get() = _appUiState.value.webFetchBaseUrl
        set(value) = updateAppUiState { it.copy(webFetchBaseUrl = value) }

    public var webFetchHeaders: String
        get() = _appUiState.value.webFetchHeaders
        set(value) = updateAppUiState { it.copy(webFetchHeaders = value) }

    public var webFetchEnv: String
        get() = _appUiState.value.webFetchEnv
        set(value) = updateAppUiState { it.copy(webFetchEnv = value) }

    public var agentSpecPath: String
        get() = _appUiState.value.agentSpecPath
        set(value) = updateAppUiState { it.copy(agentSpecPath = value) }

    public var agentSpecPreview: String
        get() = _appUiState.value.agentSpecPreview
        set(value) = updateAppUiState { it.copy(agentSpecPreview = value) }

    public var skillsPreview: List<String>
        get() = _appUiState.value.skillsPreview
        set(value) = updateAppUiState { it.copy(skillsPreview = value) }

    public var activeAgentProfileName: String
        get() = _appUiState.value.activeAgentProfileName
        set(value) = updateAppUiState { it.copy(activeAgentProfileName = value) }
    public val agentProfiles: List<AgentProfile>
        get() = _appUiState.value.agentProfiles
    public var acpHost: String
        get() = _appUiState.value.acpHost
        set(value) = updateAppUiState { it.copy(acpHost = value) }

    public var acpPort: Int
        get() = _appUiState.value.acpPort
        set(value) = updateAppUiState { it.copy(acpPort = value) }

    public var acpRunning: Boolean
        get() = _appUiState.value.acpRunning
        set(value) = updateAppUiState { it.copy(acpRunning = value) }

    public var acpLogs: List<String>
        get() = _appUiState.value.acpLogs
        set(value) = updateAppUiState { it.copy(acpLogs = value) }

    public var terminalCommand: String
        get() = _appUiState.value.terminalCommand
        set(value) = updateAppUiState { it.copy(terminalCommand = value) }

    public var terminalOutput: String
        get() = _appUiState.value.terminalOutput
        set(value) = updateAppUiState { it.copy(terminalOutput = value) }

    public var terminalRunning: Boolean
        get() = _appUiState.value.terminalRunning
        set(value) = updateAppUiState { it.copy(terminalRunning = value) }

    public var scriptContent: String
        get() = _appUiState.value.scriptContent
        set(value) = updateAppUiState { it.copy(scriptContent = value) }

    public var scriptOutput: String
        get() = _appUiState.value.scriptOutput
        set(value) = updateAppUiState { it.copy(scriptOutput = value) }

    public var scriptRunning: Boolean
        get() = _appUiState.value.scriptRunning
        set(value) = updateAppUiState { it.copy(scriptRunning = value) }

    public var webUrl: String
        get() = _appUiState.value.webUrl
        set(value) = updateAppUiState { it.copy(webUrl = value) }

    public var webContent: String
        get() = _appUiState.value.webContent
        set(value) = updateAppUiState { it.copy(webContent = value) }

    public var webLoading: Boolean
        get() = _appUiState.value.webLoading
        set(value) = updateAppUiState { it.copy(webLoading = value) }

    public var yoloEnabled: Boolean
        get() = _appUiState.value.yoloEnabled
        set(value) = updateAppUiState { it.copy(yoloEnabled = value) }

    public var approvalDefaultYolo: Boolean
        get() = _appUiState.value.approvalDefaultYolo
        set(value) = updateAppUiState { it.copy(approvalDefaultYolo = value) }

    public var approvalAutoApproveActions: List<String>
        get() = _appUiState.value.approvalAutoApproveActions
        set(value) = updateAppUiState { it.copy(approvalAutoApproveActions = value) }

    public var pendingApprovals: List<PendingApproval>
        get() = _appUiState.value.pendingApprovals
        set(value) = updateAppUiState { it.copy(pendingApprovals = value) }

    public var disabledTools: Set<String>
        get() = _appUiState.value.disabledTools
        set(value) = updateAppUiState { it.copy(disabledTools = value) }

    public var toolLogs: List<String>
        get() = _appUiState.value.toolLogs
        set(value) = updateAppUiState { it.copy(toolLogs = value) }

    public var autoSaveSessions: Boolean
        get() = _appUiState.value.autoSaveSessions
        set(value) = updateAppUiState { it.copy(autoSaveSessions = value) }

    public var temperature: Float
        get() = _appUiState.value.temperature
        set(value) = updateAppUiState { it.copy(temperature = value) }

    private var sessionRunStates: Map<String, SessionRunState> = emptyMap()
    private val inputDeferreds: MutableMap<String, CompletableDeferred<String>> = mutableMapOf()
    private val sessionJobs: MutableMap<String, Job> = mutableMapOf()
    private var sessionBindingJob: Job? = null
    private var boundSessionId: String? = null

    // AgentState implementation
    override val isRunning: Boolean
        get() = _sessionUiState.value.isRunning
    override val isWaitingForInput: Boolean
        get() = _sessionUiState.value.isWaitingForInput
    override val currentTask: String
        get() = _sessionUiState.value.currentTask

    private var eventListener: AgentEventListener? = null
    private var agentFactory: SessionAwareAgentFactory? = null
    private var pendingTaskAfterSessionCreate: String? = null
    private val approvalDeferreds: MutableMap<String, CompletableDeferred<ToolApprovalDecision>> = mutableMapOf()
    private val autoApproveActions: MutableSet<String> = mutableSetOf()
    private val autoApproveActionsBySession: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private var acpProtocol: Protocol? = null
    private var acpTransport: StdioTransport? = null
    private var acpClientTransport: StdioTransport? = null
    private var acpProtocolScope: CoroutineScope? = null
    private var acpClientToAgent: Pipe? = null
    private var acpAgentToClient: Pipe? = null
    private val mcpProcesses: MutableMap<String, Process> = mutableMapOf()

    public fun setEventListener(listener: AgentEventListener?) {
        this.eventListener = listener
    }

    private fun updateAppUiState(transform: (AppUiState) -> AppUiState) {
        _appUiState.update(transform)
    }

    private fun updateSessionUiState(transform: (SessionUiState) -> SessionUiState) {
        _sessionUiState.update { current ->
            val updated = transform(current)
            updateAppUiState { app ->
                app.copy(session = updated)
            }
            updated
        }
    }

    private fun enqueueToast(message: String) {
        val normalized = message.trim()
        if (normalized.isBlank()) {
            return
        }
        val toast = UiToast(
            id = java.util.UUID.randomUUID().toString(),
            message = normalized,
        )
        updateAppUiState { current ->
            current.copy(toasts = (current.toasts + toast).takeLast(5))
        }
    }

    public fun consumeToast(toastId: String) {
        updateAppUiState { current ->
            current.copy(toasts = current.toasts.filterNot { it.id == toastId })
        }
    }
    
    init {
        eventListener = this
        viewModelScope.launch {
            initializeAgentFactory()
            startAutoSaveObservers()
        }
    }
    
    private suspend fun initializeAgentFactory() {
        try {
            val config = configManager.load()
            loadConfigToState(config)
            refreshAgentFactoryForConversation()
        } catch (e: Exception) {
            addSystemMessage("Failed to initialize: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun refreshAgentFactoryForConversation(): Boolean {
        if (models.isEmpty()) {
            agentFactory = null
            return false
        }

        val mcpRegistry = buildMcpToolRegistry()
        buildAgentFactory(mcpRegistry)
        return true
    }

    private fun buildAgentFactory(mcpRegistry: ToolRegistry?) {
        agentFactory = agentFactoryProvider.create(
            auths = auths,
            models = models,
            messageHandler = this@MainViewModel,
            approvalHandler = this@MainViewModel,
            disabledTools = disabledTools,
            mcpToolRegistry = mcpRegistry,
            eventListener = this@MainViewModel,
            logger = { logMessage: String -> log(logMessage) },
        )
    }
    
    private fun loadConfigToState(config: AppConfig) {
        auths = config.auths
        models = config.models
        if (activeModelId == null && models.isNotEmpty()) {
            activeModelId = models.first().id
        }
        defaultModelId = config.defaults.modelId
        defaultThinking = config.defaults.thinking
        defaultSessionDir = config.defaults.workDir ?: ""
        if (currentSessionId == null && currentSessionWorkDir.isBlank()) {
            currentSessionWorkDir = normalizeSessionDir(defaultSessionDir).orEmpty()
        }
        maxStepsPerTurn = config.loopControl.maxStepsPerTurn
        maxRetriesPerStep = config.loopControl.maxRetriesPerStep
        maxRalphIterations = config.loopControl.maxRalphIterations
        reservedContextSize = config.loopControl.reservedContextSize
        skillsDir = config.skills.dir ?: ""
        agentBuiltin = config.agent.builtin ?: ""
        agentFile = config.agent.file ?: ""
        logLevel = config.logging.level
        logFile = config.logging.file ?: ""
        disabledTools = config.tools.disabled.toSet()
        mcpToolTimeoutMs = config.mcp.client.toolCallTimeoutMs
        mcpServers = config.mcp.servers
        val knownServers = mcpServers.keys
        mcpHealthResults = mcpHealthResults.filterKeys { it in knownServers }
        mcpTestResults = mcpTestResults.filterKeys { it in knownServers }
        mcpTestsInFlight = mcpTestsInFlight.filter { it in knownServers }.toSet()
        val missingServers = knownServers - mcpHealthResults.keys
        if (missingServers.isNotEmpty()) {
            mcpHealthResults = mcpHealthResults + missingServers.associateWith {
                McpHealthResult(McpHealthStatus.Unknown, "")
            }
        }
        uiTheme = config.ui.theme
        approvalDefaultYolo = config.approvals.yoloDefault
        approvalAutoApproveActions = config.approvals.autoApproveActions
        yoloEnabled = approvalDefaultYolo
        autoApproveActions.clear()
        autoApproveActions.addAll(approvalAutoApproveActions)
        applyAgentProfileFromConfig()
        val webSearch = config.services.webSearch
        webSearchProvider = webSearch?.provider ?: "none"
        webSearchApiKey = webSearch?.apiKey ?: ""
        webSearchBaseUrl = webSearch?.baseUrl ?: ""
        webSearchHeaders = mapToLines(webSearch?.customHeaders, separator = ":")
        webSearchEnv = mapToLines(webSearch?.env, separator = "=")

        val webFetch = config.services.webFetch
        webFetchProvider = webFetch?.provider ?: "builtin"
        webFetchApiKey = webFetch?.apiKey ?: ""
        webFetchBaseUrl = webFetch?.baseUrl ?: ""
        webFetchHeaders = mapToLines(webFetch?.customHeaders, separator = ":")
        webFetchEnv = mapToLines(webFetch?.env, separator = "=")

        refreshAgentAndSkillsPreview()
    }
    
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startAutoSaveObservers() {
        autoSaveEnabled = true

        viewModelScope.launch(Dispatchers.IO) {
            snapshotFlow { buildPreferencesSnapshot() }
                .distinctUntilChanged()
                .debounce(500)
                .collect {
                    if (autoSaveEnabled) {
                        persistPreferences()
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            snapshotFlow { McpSnapshot(mcpToolTimeoutMs, mcpServers) }
                .distinctUntilChanged()
                .debounce(500)
                .collect {
                    if (autoSaveEnabled) {
                        persistMcpSettings()
                    }
                }
        }
    }

    public fun runTask() {
        if (taskInput.isBlank()) return

        val task = taskInput
        taskInput = ""

        val modelId = activeModelId
        if (modelId == null) {
            addSystemMessage("No model selected. Please configure at least one model.")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            var runningSessionId: String? = null
            try {
                if (!refreshAgentFactoryForConversation()) {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                val factory = agentFactory ?: run {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                if (currentSessionId == null) {
                    pendingTaskAfterSessionCreate = task
                    addSystemMessage("No active session. Please create a session first.")
                    withContext(Dispatchers.Main) {
                        newSessionDirInput = buildDefaultSessionDirInput()
                        showNewSessionDialog = true
                    }
                    return@launch
                }

                val sessionId = currentSessionId!!
                if (sessionRunStates[sessionId]?.isRunning == true) {
                    addSystemMessage("Session is already running", sessionId)
                    return@launch
                }
                runningSessionId = sessionId
                sessionJobs[sessionId] = requireNotNull(coroutineContext[Job])
                bindSessionFlows(sessionId)
                ensureSessionWorkDir(sessionId)
                updateSessionRunState(sessionId) {
                    it.copy(
                        isRunning = true,
                        currentTask = task,
                        isWaitingForInput = false
                    )
                }

                factory.runWithSession(sessionId, task, modelId)
            } catch (e: Exception) {
                val message = e.message ?: "Unknown error"
                val targetSessionId = runningSessionId ?: currentSessionId
                if (targetSessionId != null) {
                    onEvent(AgentEvent.Error(message, e), targetSessionId)
                    log("runTask failed: ${e.stackTraceToString()}", targetSessionId)
                } else {
                    addSystemMessage("Error: $message")
                    log("runTask failed: ${e.stackTraceToString()}")
                }
            } finally {
                runningSessionId?.let { sessionId ->
                    updateSessionRunState(sessionId) { state ->
                        state.copy(isRunning = false, currentTask = "", isWaitingForInput = false)
                    }
                    sessionJobs.remove(sessionId)
                }
            }
        }
    }
    
    public fun createNewSession() {
        unbindSessionFlows(currentSessionId)
        currentSessionId = null
        messages = emptyList()
        currentSessionWorkDir = ""
        newSessionDirInput = buildDefaultSessionDirInput()
        showNewSessionDialog = true
    }
    
    public fun continueCurrentSession() {
        val sessionId = currentSessionId
        if (sessionId == null) {
            addSystemMessage("No active session")
            return
        }
        
        val modelId = activeModelId
        if (modelId == null) {
            addSystemMessage("No model selected")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!refreshAgentFactoryForConversation()) {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                val factory = agentFactory ?: run {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                if (sessionRunStates[sessionId]?.isRunning == true) {
                    addSystemMessage("Session is already running", sessionId)
                    return@launch
                }

                sessionJobs[sessionId] = requireNotNull(coroutineContext[Job])
                bindSessionFlows(sessionId)
                ensureSessionWorkDir(sessionId)
                updateSessionRunState(sessionId) {
                    it.copy(
                        isRunning = true,
                        currentTask = "Continue",
                        isWaitingForInput = false
                    )
                }

                factory.continueSession(sessionId, modelId)
            } catch (e: Exception) {
                addSystemMessage("Error: ${e.message}", sessionId)
                log("continueCurrentSession failed: ${e.stackTraceToString()}", sessionId)
            } finally {
                updateSessionRunState(sessionId) { state ->
                    state.copy(isRunning = false, currentTask = "", isWaitingForInput = false)
                }
                sessionJobs.remove(sessionId)
            }
        }
    }

    public fun stopCurrentSession() {
        val sessionId = currentSessionId ?: return
        sessionJobs[sessionId]?.cancel("Stopped by user")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.stopRun(sessionId)
            } catch (e: Exception) {
                addSystemMessage("Stop session failed: ${e.message}", sessionId)
            } finally {
                sessionJobs.remove(sessionId)
                updateSessionRunState(sessionId) { state ->
                    state.copy(isRunning = false, isWaitingForInput = false, currentTask = "")
                }
            }
        }
    }
    
    public fun forkFromMessage(messageIndex: Int) {
        val sessionId = currentSessionId
        if (sessionId == null) {
            addSystemMessage("No active session to fork from")
            return
        }

        if (messageIndex >= messages.size) {
            addSystemMessage("Cannot fork from invalid message")
            return
        }

        val selected = messages.getOrNull(messageIndex)
        if (selected == null || selected.role == io.github.stream29.kode.session.core.model.MessageRole.SYSTEM) {
            addSystemMessage("Cannot fork from system message")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionManager.getSession(sessionId)
                    ?: throw IllegalArgumentException("Session not found: $sessionId")

                val nonSystemMessages = messages.filter {
                    it.role != io.github.stream29.kode.session.core.model.MessageRole.SYSTEM
                }
                val nonSystemIndex = nonSystemMessages.indexOfFirst { it.id == selected.id }
                if (nonSystemIndex == -1) {
                    throw IllegalArgumentException("Message not found in session")
                }
                val sessionMessage = session.messages.getOrNull(nonSystemIndex)
                    ?: throw IllegalArgumentException("Session message not found")

                val newSession = sessionManager.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = sessionMessage.id,
                    newTitle = "Fork at message ${nonSystemIndex + 1}"
                )

                currentSessionId = newSession.id
                messages = newSession.messages
                currentSessionWorkDir = newSession.configuration.workDir.orEmpty()
                addSystemMessage("Forked from message ${nonSystemIndex + 1}")
            } catch (e: Exception) {
                addSystemMessage("Failed to fork: ${e.message}")
            }
        }
    }
    
    // ==================== Session Management ====================
    
    public fun loadSessionList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filter = SessionFilter(
                    status = sessionStatusFilter,
                    tags = sessionTagFilter.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
                    searchQuery = sessionSearchQuery.takeIf { it.isNotBlank() },
                    sortBy = SortBy.UPDATED_AT,
                    sortOrder = SortOrder.DESCENDING
                )
                sessionSummaries = sessionManager.listSessions(filter = filter)
            } catch (e: Exception) {
                addSystemMessage("Failed to load sessions: ${e.message}")
            }
        }
    }

    public fun updateSessionSearchQuery(query: String) {
        sessionSearchQuery = query
        loadSessionList()
    }

    public fun updateSessionTagFilter(tags: String) {
        sessionTagFilter = tags
        loadSessionList()
    }

    public fun updateSessionStatusFilter(status: SessionStatusFilter) {
        sessionStatusFilter = status
        loadSessionList()
    }
    
    public fun switchToSession(sessionId: String) {
        currentSessionId = sessionId
        showSessionManager = false
        bindSessionFlows(sessionId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionManager.getSession(sessionId)
                if (session != null) {
                    messages = session.messages
                    ensureSessionWorkDir(sessionId)
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
                sessionManager.deleteSession(sessionId, hardDelete = true)
                if (currentSessionId == sessionId) {
                    currentSessionId = null
                    messages = emptyList()
                    currentSessionWorkDir = ""
                    unbindSessionFlows(sessionId)
                }
                sessionJobs.remove(sessionId)?.cancel("Session deleted")
                clearSessionRunState(sessionId)
                inputDeferreds.remove(sessionId)
                pendingApprovals = pendingApprovals.filterNot { it.sessionId == sessionId }
                autoApproveActionsBySession.remove(sessionId)
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
                val newSession = sessionManager.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = null,
                    newTitle = "Fork of ${sessionId.take(8)}"
                )
                currentSessionId = newSession.id
                messages = newSession.messages
                currentSessionWorkDir = newSession.configuration.workDir.orEmpty()
                loadSessionList()
                addSystemMessage("Session forked: ${newSession.id.take(8)}...")
                showSessionManager = false
            } catch (e: Exception) {
                addSystemMessage("Failed to fork session: ${e.message}")
            }
        }
    }
    
    public fun archiveSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.archiveSession(sessionId)
                loadSessionList()
                addSystemMessage("Session archived")
            } catch (e: Exception) {
                addSystemMessage("Failed to archive session: ${e.message}")
            }
        }
    }

    public fun restoreSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.restoreSession(sessionId)
                loadSessionList()
                addSystemMessage("Session restored")
            } catch (e: Exception) {
                addSystemMessage("Failed to restore session: ${e.message}")
            }
        }
    }

    public fun addSessionTags(sessionId: String, tags: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.addTags(sessionId, tags)
                loadSessionList()
                addSystemMessage("Tags updated")
            } catch (e: Exception) {
                addSystemMessage("Failed to update tags: ${e.message}")
            }
        }
    }

    public fun removeSessionTag(sessionId: String, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.removeTags(sessionId, listOf(tag))
                loadSessionList()
                addSystemMessage("Tag removed")
            } catch (e: Exception) {
                addSystemMessage("Failed to remove tag: ${e.message}")
            }
        }
    }

    public fun exportSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Export Session", java.awt.FileDialog.SAVE)
                dialog.isVisible = true
                val file = dialog.file ?: return@launch
                val dir = dialog.directory ?: return@launch
                sessionManager.exportSession(sessionId, File(dir, file))
                addSystemMessage("Session exported")
            } catch (e: Exception) {
                addSystemMessage("Failed to export session: ${e.message}")
            }
        }
    }

    public fun updateCurrentSessionWorkDir(input: String) {
        val normalized = normalizeSessionDir(input)
        currentSessionWorkDir = normalized.orEmpty()
        val sessionId = currentSessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionManager.getSession(sessionId)
                    ?: return@launch
                val updatedConfig = session.configuration.copy(workDir = normalized)
                sessionManager.updateConfiguration(sessionId, updatedConfig)
            } catch (e: Exception) {
                addSystemMessage("Failed to update session dir: ${e.message}")
            }
        }
    }

    private suspend fun ensureSessionWorkDir(sessionId: String) {
        val session = sessionManager.getSession(sessionId) ?: return
        val normalized = normalizeSessionDir(session.configuration.workDir.orEmpty())
            ?: normalizeSessionDir(defaultSessionDir)
            ?: "."
        if (session.configuration.workDir != normalized) {
            val updated = session.configuration.copy(workDir = normalized)
            sessionManager.updateConfiguration(sessionId, updated)
        }
        withContext(Dispatchers.Main) {
            currentSessionWorkDir = normalized
        }
    }

    public fun confirmNewSessionDir() {
        val modelId = activeModelId
        if (modelId == null) {
            addSystemMessage("No model selected")
            showNewSessionDialog = false
            pendingTaskAfterSessionCreate = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            var createdSessionId: String? = null
            try {
                if (!refreshAgentFactoryForConversation()) {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                val factory = agentFactory ?: run {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                val normalizedWorkDir = normalizeSessionDir(newSessionDirInput)
                currentSessionWorkDir = normalizedWorkDir.orEmpty()
                val sessionId = factory.createSession(
                    title = "Conversation ${System.currentTimeMillis()}",
                    systemPrompt = buildSystemPrompt(),
                    modelId = modelId,
                    workDir = normalizedWorkDir
                )
                createdSessionId = sessionId
                currentSessionId = sessionId
                bindSessionFlows(sessionId)

                val task = pendingTaskAfterSessionCreate
                pendingTaskAfterSessionCreate = null

                withContext(Dispatchers.Main) {
                    showNewSessionDialog = false
                    messages = emptyList()
                }

                if (!task.isNullOrBlank()) {
                    updateSessionRunState(sessionId) {
                        it.copy(
                            isRunning = true,
                            currentTask = task,
                            isWaitingForInput = false
                        )
                    }
                    sessionJobs[sessionId] = requireNotNull(coroutineContext[Job])
                    factory.runWithSession(sessionId, task, modelId)
                } else {
                    addSystemMessage("New session created")
                }
            } catch (e: Exception) {
                addSystemMessage("Failed to create session: ${e.message}")
            } finally {
                createdSessionId?.let { sessionId ->
                    updateSessionRunState(sessionId) { state ->
                        state.copy(isRunning = false, currentTask = "", isWaitingForInput = false)
                    }
                    sessionJobs.remove(sessionId)
                }
            }
        }
    }

    public fun cancelNewSessionDir() {
        pendingTaskAfterSessionCreate = null
        showNewSessionDialog = false
    }

    public fun openSessionDirDialog() {
        sessionDirDraft = currentSessionWorkDir
        showSessionDirDialog = true
    }

    public fun confirmSessionDirDialog() {
        updateCurrentSessionWorkDir(sessionDirDraft)
        showSessionDirDialog = false
    }

    public fun cancelSessionDirDialog() {
        showSessionDirDialog = false
    }

    private fun buildDefaultSessionDirInput(): String {
        return defaultSessionDir.trim().ifBlank { "." }
    }

    private fun normalizeSessionDir(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return null
        }
        val expanded = if (trimmed.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        return File(expanded).absolutePath
    }

    private fun updateSessionRunState(
        sessionId: String,
        transform: (SessionRunState) -> SessionRunState
    ) {
        val current = sessionRunStates[sessionId] ?: SessionRunState()
        sessionRunStates = sessionRunStates + (sessionId to transform(current))
        updateSessionUiState { currentUi ->
            applySessionRunState(
                base = currentUi,
                sessionId = currentUi.currentSessionId,
            )
        }
    }

    private fun clearSessionRunState(sessionId: String) {
        sessionRunStates = sessionRunStates - sessionId
        updateSessionUiState { currentUi ->
            applySessionRunState(
                base = currentUi,
                sessionId = currentUi.currentSessionId,
            )
        }
    }

    private fun applySessionRunState(base: SessionUiState, sessionId: String?): SessionUiState {
        val runState = sessionId?.let { id -> sessionRunStates[id] }
        return base.copy(
            isRunning = runState?.isRunning ?: false,
            isWaitingForInput = runState?.isWaitingForInput ?: false,
            currentTask = runState?.currentTask.orEmpty(),
        )
    }

    private fun bindSessionFlows(sessionId: String) {
        if (boundSessionId == sessionId && sessionBindingJob?.isActive == true) {
            return
        }

        sessionBindingJob?.cancel()
        boundSessionId = sessionId

        sessionBindingJob = viewModelScope.launch(Dispatchers.IO) {
            val runtime = sessionManager.getRuntimeSession(sessionId) ?: return@launch
            val mainMessagesFlow = runtime.agent.value.messages

            combine(
                runtime.metadata,
                runtime.config,
                mainMessagesFlow,
            ) { metadata, config, mainMessages ->
                Triple(metadata, config, mainMessages)
            }.collect { (metadata, config, mainMessages) ->
                withContext(Dispatchers.Main) {
                    if (currentSessionId != sessionId) {
                        return@withContext
                    }

                    messages = mainMessages
                    currentSessionWorkDir = config.workDir.orEmpty()
                    updateSessionRunState(sessionId) { state ->
                        state.copy(
                            isRunning = metadata.state == io.github.stream29.kode.session.core.model.SessionState.Running,
                            currentTask = when {
                                metadata.state == io.github.stream29.kode.session.core.model.SessionState.Running -> {
                                    state.currentTask.ifBlank { "Running" }
                                }
                                state.isWaitingForInput -> state.currentTask.ifBlank { "Waiting for input" }
                                else -> ""
                            }
                        )
                    }
                }
            }
        }
    }

    private fun unbindSessionFlows(sessionId: String? = null) {
        if (sessionId == null || boundSessionId == sessionId) {
            sessionBindingJob?.cancel()
            sessionBindingJob = null
            boundSessionId = null
        }
    }

    public fun importSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Import Session", java.awt.FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file ?: return@launch
                val dir = dialog.directory ?: return@launch
                val imported = sessionManager.importSession(File(dir, file), newTitle = null)
                currentSessionId = imported.id
                messages = imported.messages
                currentSessionWorkDir = imported.configuration.workDir.orEmpty()
                loadSessionList()
                addSystemMessage("Session imported")
            } catch (e: Exception) {
                addSystemMessage("Failed to import session: ${e.message}")
            }
        }
    }
    
    // ==================== Config Management ====================
    
    public fun loadConfigForEditing() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configFile = FileSystemLocations.configFile
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
    display_name: Claude Sonnet 4.5

defaults:
  model_id: claude-sonnet
  thinking: false

loop_control:
  max_steps_per_turn: 100
  max_retries_per_step: 3
  max_ralph_iterations: 0
  reserved_context_size: 50000

services:
  web_search:
    provider: none
    api_key: ""
    base_url: null
  web_fetch:
    provider: builtin
    api_key: ""
    base_url: null

mcp:
  client:
    tool_call_timeout_ms: 60000
  servers: {}

skills:
  dir: "~/.kode/skills"

agent:
  builtin: default
  file: null

ui:
  theme: dark

approvals:
  yolo_default: false
  auto_approve_actions: []

logging:
  level: info
  file: null"""
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
                val configFile = FileSystemLocations.configFile
                configFile.writeText(configText)
                
                val config = configManager.load()
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
                configManager.save(config)
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
                
                val testFactory = agentFactoryProvider.create(
                    auths = auths,
                    models = models,
                    messageHandler = object : MessageHandler {
                        override fun addMessageToUser(message: String) {}
                        override fun log(message: String) {}
                        override suspend fun requestInput(): String = ""
                    },
                    approvalHandler = null,
                    disabledTools = emptySet(),
                    mcpToolRegistry = null,
                    eventListener = null,
                    logger = { },
                )
                models.forEach { model ->
                    testFactory.createLLModel(model.id)
                }
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
                    Desktop.getDesktop().open(FileSystemLocations.dataDir)
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
                sessionManager.listSessions(filter = null).forEach { summary ->
                    sessionManager.deleteSession(summary.id, hardDelete = true)
                }
                unbindSessionFlows(currentSessionId)
                currentSessionId = null
                currentSessionWorkDir = ""
                messages = emptyList()
                sessionRunStates = emptyMap()
                inputDeferreds.clear()
                pendingApprovals = emptyList()
                autoApproveActionsBySession.clear()
                loadSessionList()
                addSystemMessage("All sessions cleared")
            } catch (e: Exception) {
                addSystemMessage("Failed to clear sessions: ${e.message}")
            }
        }
    }

    public fun submitInput() {
        if (!isWaitingForInput) return
        val sessionId = currentSessionId ?: return

        val input = taskInput
        taskInput = ""

        val deferred = inputDeferreds.remove(sessionId) ?: return
        updateSessionRunState(sessionId) { state ->
            state.copy(isWaitingForInput = false)
        }
        deferred.complete(input)
    }
    
    private fun addSystemMessage(content: String, sessionId: String? = currentSessionId) {
        if (sessionId != null && currentSessionId != sessionId) {
            return
        }
        enqueueToast(content)
    }

    // MessageHandler implementation
    override suspend fun requestInput(): String {
        val sessionId = currentSessionId ?: ""
        if (sessionId.isBlank()) {
            return ""
        }
        return requestInput(sessionId)
    }

    override suspend fun requestInput(sessionId: String): String {
        val deferred = CompletableDeferred<String>()
        inputDeferreds[sessionId] = deferred
        updateSessionRunState(sessionId) { state ->
            state.copy(isWaitingForInput = true)
        }
        addSystemMessage("Waiting for user input...", sessionId)
        return deferred.await()
    }

    override fun addMessageToUser(message: String) {
        val sessionId = currentSessionId ?: return
        addMessageToUser(message, sessionId)
    }

    override fun addMessageToUser(message: String, sessionId: String) {
        addSystemMessage(message, sessionId)
    }

    override fun log(message: String) {
        val sessionId = currentSessionId ?: ""
        if (sessionId.isBlank()) {
            viewModelScope.launch(Dispatchers.Main) {
                toolLogs = (toolLogs + message).takeLast(200)
            }
            return
        }
        log(message, sessionId)
    }

    override fun log(message: String, sessionId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val prefix = sessionId.take(8)
            toolLogs = (toolLogs + "[$prefix] $message").takeLast(200)
        }
    }

    override fun onEvent(event: AgentEvent) {
        val sessionId = currentSessionId ?: return
        onEvent(event, sessionId)
    }

    override fun onEvent(event: AgentEvent, sessionId: String) {
        when (event) {
            is AgentEvent.ToolCallStarting -> {
                addSystemMessage("Tool call: ${event.toolName}", sessionId)
            }
            is AgentEvent.ToolCallCompleted -> {
                addSystemMessage("Tool completed: ${event.toolName}", sessionId)
            }
            is AgentEvent.MessageToUser -> {
                addSystemMessage(event.message, sessionId)
            }
            is AgentEvent.Error -> {
                addSystemMessage("Error: ${event.message}", sessionId)
            }
        }
    }

    override suspend fun requestApproval(request: ToolApprovalRequest): ToolApprovalDecision {
        val sessionId = currentSessionId ?: return ToolApprovalDecision.Reject
        return requestApproval(request, sessionId)
    }

    override suspend fun requestApproval(
        request: ToolApprovalRequest,
        sessionId: String
    ): ToolApprovalDecision {
        if (yoloEnabled) {
            return ToolApprovalDecision.Approve
        }
        if (autoApproveActions.contains(request.toolName)) {
            return ToolApprovalDecision.Approve
        }
        if (autoApproveActionsBySession[sessionId]?.contains(request.toolName) == true) {
            return ToolApprovalDecision.Approve
        }

        val requestId = if (request.id.isBlank()) {
            "${sessionId}_${java.util.UUID.randomUUID()}"
        } else {
            "${sessionId}_${request.id}"
        }
        val normalizedRequest = request.copy(id = requestId)
        val deferred = CompletableDeferred<ToolApprovalDecision>()
        approvalDeferreds[requestId] = deferred

        withContext(Dispatchers.Main) {
            pendingApprovals = pendingApprovals + PendingApproval(
                sessionId = sessionId,
                request = normalizedRequest
            )
        }

        return deferred.await()
    }

    public fun approvePendingRequest(requestId: String, decision: ToolApprovalDecision) {
        val deferred = approvalDeferreds.remove(requestId) ?: return
        if (decision == ToolApprovalDecision.ApproveForSession) {
            pendingApprovals.find { it.request.id == requestId }?.let { pending ->
                val sessionSet = autoApproveActionsBySession
                    .getOrPut(pending.sessionId) { mutableSetOf() }
                sessionSet.add(pending.request.toolName)
            }
        }
        pendingApprovals = pendingApprovals.filterNot { it.request.id == requestId }
        deferred.complete(
            if (decision == ToolApprovalDecision.ApproveForSession) {
                ToolApprovalDecision.Approve
            } else {
                decision
            }
        )
    }

    public fun addApprovalAction(action: String) {
        val trimmed = action.trim()
        if (trimmed.isBlank()) {
            return
        }
        autoApproveActions.add(trimmed)
        approvalAutoApproveActions = autoApproveActions.toList().sorted()
    }

    public fun removeApprovalAction(action: String) {
        autoApproveActions.remove(action)
        approvalAutoApproveActions = autoApproveActions.toList().sorted()
    }

    public fun switchModel(modelId: String) {
        val modelConfig = models.find { it.id == modelId }
        if (modelConfig != null) {
            activeModelId = modelId
            val displayName = modelConfig.displayName ?: modelConfig.model
            addSystemMessage("Switched to model: $displayName")
        }
    }

    public fun setDefaultModel(modelId: String) {
        defaultModelId = modelId
    }

    public fun savePreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            persistPreferences()
        }
    }

    private suspend fun persistPreferences() {
        try {
            val current = configManager.load()
            val updated = current.copy(
                defaults = current.defaults.copy(
                    modelId = defaultModelId,
                    thinking = defaultThinking,
                    workDir = defaultSessionDir.takeIf { it.isNotBlank() }
                ),
                loopControl = current.loopControl.copy(
                    maxStepsPerTurn = maxStepsPerTurn,
                    maxRetriesPerStep = maxRetriesPerStep,
                    maxRalphIterations = maxRalphIterations,
                    reservedContextSize = reservedContextSize
                ),
                skills = current.skills.copy(
                    dir = skillsDir.takeIf { it.isNotBlank() }
                ),
                agent = current.agent.copy(
                    builtin = agentBuiltin.takeIf { it.isNotBlank() },
                    file = agentFile.takeIf { it.isNotBlank() }
                ),
                logging = current.logging.copy(
                    level = logLevel,
                    file = logFile.takeIf { it.isNotBlank() }
                ),
                ui = current.ui.copy(
                    theme = uiTheme
                ),
                approvals = current.approvals.copy(
                    yoloDefault = approvalDefaultYolo,
                    autoApproveActions = approvalAutoApproveActions
                ),
                tools = current.tools.copy(
                    disabled = disabledTools.toList()
                ),
                services = current.services.copy(
                    webSearch = buildServiceConfig(
                        provider = webSearchProvider,
                        apiKey = webSearchApiKey,
                        baseUrl = webSearchBaseUrl,
                        headersText = webSearchHeaders,
                        envText = webSearchEnv
                    ),
                    webFetch = buildServiceConfig(
                        provider = webFetchProvider,
                        apiKey = webFetchApiKey,
                        baseUrl = webFetchBaseUrl,
                        headersText = webFetchHeaders,
                        envText = webFetchEnv
                    )
                )
            )
            configManager.save(updated)
            initializeAgentFactory()
        } catch (e: Exception) {
            addSystemMessage("Failed to save preferences: ${e.message}")
        }
    }

    public fun selectAgentProfile(profileName: String, persist: Boolean) {
        val profile = agentProfiles.firstOrNull { it.name == profileName }
        if (profile == null) {
            activeAgentProfileName = profileName
            agentBuiltin = profileName
            if (persist) {
                savePreferences()
            }
            return
        }
        applyAgentProfile(profile)
        if (persist) {
            savePreferences()
        }
    }

    private fun applyAgentProfile(profile: AgentProfile) {
        activeAgentProfileName = profile.name
        agentBuiltin = profile.name
        disabledTools = profile.disabledTools
        approvalDefaultYolo = profile.defaultYolo
        yoloEnabled = profile.defaultYolo
    }

    private fun applyAgentProfileFromConfig() {
        val profileName = agentBuiltin.trim()
        if (profileName.isBlank()) {
            activeAgentProfileName = "build"
            return
        }
        val profile = agentProfiles.firstOrNull { it.name == profileName }
        if (profile != null) {
            applyAgentProfile(profile)
        } else {
            activeAgentProfileName = profileName
        }
    }

    public fun saveMcpSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            persistMcpSettings()
        }
    }

    private suspend fun persistMcpSettings() {
        try {
            val current = configManager.load()
            val updated = current.copy(
                mcp = current.mcp.copy(
                    client = current.mcp.client.copy(toolCallTimeoutMs = mcpToolTimeoutMs),
                    servers = mcpServers
                )
            )
            configManager.save(updated)
            initializeAgentFactory()
        } catch (e: Exception) {
            addSystemMessage("Failed to save MCP settings: ${e.message}")
        }
    }

    public fun addMcpServer(name: String, config: io.github.stream29.kode.config.api.McpServerConfig) {
        mcpServers = mcpServers + (name to config)
        mcpHealthResults = mcpHealthResults + (name to McpHealthResult(McpHealthStatus.Unknown, ""))
        mcpTestResults = mcpTestResults - name
        mcpTestsInFlight = mcpTestsInFlight - name
        saveMcpSettings()
    }

    public fun removeMcpServer(name: String) {
        mcpServers = mcpServers - name
        mcpHealthResults = mcpHealthResults - name
        mcpTestResults = mcpTestResults - name
        mcpTestsInFlight = mcpTestsInFlight - name
        saveMcpSettings()
    }

    public fun clearMcpTestResult(name: String) {
        mcpTestResults = mcpTestResults - name
    }

    public fun testMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = mcpServers[name] ?: return@launch
            withContext(Dispatchers.Main) {
                mcpTestsInFlight = mcpTestsInFlight + name
                mcpTestResults = mcpTestResults - name
                mcpHealthResults = mcpHealthResults + (
                    name to McpHealthResult(McpHealthStatus.Checking, "Checking...")
                )
            }
            val result = try {
                runMcpTest(name = name, server = server)
            } catch (e: Exception) {
                buildMcpTestError("MCP test failed (${name}): ${e.message}")
            }
            withContext(Dispatchers.Main) {
                mcpTestsInFlight = mcpTestsInFlight - name
                mcpTestResults = mcpTestResults + (name to result)
                mcpHealthResults = mcpHealthResults + (name to buildMcpHealthFromTest(result))
            }
        }
    }

    public fun authMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = mcpServers[name] ?: return@launch
            try {
                val url = server.url
                if (server.transport == "http" && !url.isNullOrBlank()) {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(java.net.URI.create(url))
                        addSystemMessage("Opened browser for MCP auth: $name")
                    } else {
                        addSystemMessage("Desktop not supported for MCP auth: $name")
                    }
                } else {
                    addSystemMessage("MCP auth not supported for stdio server: $name")
                }
            } catch (e: Exception) {
                addSystemMessage("MCP auth failed (${name}): ${e.message}")
            }
        }
    }

    private suspend fun runMcpTest(
        name: String,
        server: io.github.stream29.kode.config.api.McpServerConfig,
    ): McpTestResult {
        return when (server.transport.lowercase()) {
            "stdio" -> {
                val command = server.command
                if (command.isNullOrBlank()) {
                    buildMcpTestError("MCP test (${name}): missing command")
                } else {
                    var process: Process? = null
                    try {
                        process = startMcpTestProcess(server = server, command = command)
                        if (process == null) {
                            buildMcpTestError("MCP test (${name}): failed to start process")
                        } else {
                            val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                            val registry = McpToolRegistryProvider.fromTransport(
                                transport = transport,
                                name = name,
                                version = "1.0.0",
                            )
                            buildMcpTestSuccess(registry = registry)
                        }
                    } finally {
                        process?.destroy()
                    }
                }
            }
            "http", "sse" -> {
                val url = server.url
                if (url.isNullOrBlank()) {
                    buildMcpTestError("MCP test (${name}): missing url")
                } else {
                    val transport = McpToolRegistryProvider.defaultSseTransport(url)
                    val registry = McpToolRegistryProvider.fromTransport(
                        transport = transport,
                        name = name,
                        version = "1.0.0",
                    )
                    buildMcpTestSuccess(registry = registry)
                }
            }
            else -> buildMcpTestError("MCP test (${name}): unsupported transport ${server.transport}")
        }
    }

    private fun startMcpTestProcess(
        server: io.github.stream29.kode.config.api.McpServerConfig,
        command: String,
    ): Process? {
        return try {
            val args = server.args
            val processBuilder = ProcessBuilder(listOf(command) + args)
                .directory(resolveSessionWorkingDir(currentSessionWorkDir))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            server.env?.forEach { (key, value) ->
                processBuilder.environment()[key] = value
            }
            processBuilder.start()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMcpTestSuccess(registry: ToolRegistry): McpTestResult {
        val tools = registry.tools
            .map { tool ->
                val descriptor = tool.descriptor
                McpToolSummary(
                    name = tool.name,
                    description = descriptor.description,
                    requiredParameters = descriptor.requiredParameters.map { param ->
                        buildToolParameterSummary(param)
                    },
                    optionalParameters = descriptor.optionalParameters.map { param ->
                        buildToolParameterSummary(param)
                    },
                )
            }
            .sortedBy { tool -> tool.name.lowercase() }
        val message = if (tools.isEmpty()) {
            "No tools returned"
        } else {
            "Found ${tools.size} tools"
        }
        return McpTestResult(
            status = McpTestStatus.Success,
            message = message,
            tools = tools,
        )
    }

    private fun buildMcpTestError(message: String): McpTestResult {
        return McpTestResult(
            status = McpTestStatus.Error,
            message = message,
            tools = emptyList(),
        )
    }

    private fun buildMcpHealthFromTest(result: McpTestResult): McpHealthResult {
        return when (result.status) {
            McpTestStatus.Success -> McpHealthResult(
                status = McpHealthStatus.Healthy,
                message = result.message,
            )
            McpTestStatus.Error -> McpHealthResult(
                status = McpHealthStatus.Unhealthy,
                message = result.message,
            )
        }
    }

    private suspend fun updateMcpHealth(name: String, result: McpHealthResult) {
        withContext(Dispatchers.Main) {
            mcpHealthResults = mcpHealthResults + (name to result)
        }
    }

    private fun buildMcpHealthSuccess(registry: ToolRegistry): McpHealthResult {
        val count = registry.tools.size
        val message = if (count == 0) {
            "Found 0 tools"
        } else {
            "Found $count tools"
        }
        return McpHealthResult(
            status = McpHealthStatus.Healthy,
            message = message,
        )
    }

    private fun buildMcpHealthError(message: String): McpHealthResult {
        return McpHealthResult(
            status = McpHealthStatus.Unhealthy,
            message = message,
        )
    }

    private fun buildToolParameterSummary(param: ToolParameterDescriptor): McpToolParameterSummary {
        return McpToolParameterSummary(
            name = param.name,
            type = formatToolParameterType(param.type),
            description = param.description,
        )
    }

    private fun formatToolParameterType(type: ToolParameterType): String {
        return when (type) {
            ToolParameterType.String -> "string"
            ToolParameterType.Null -> "null"
            ToolParameterType.Integer -> "int"
            ToolParameterType.Float -> "float"
            ToolParameterType.Boolean -> "boolean"
            is ToolParameterType.Enum -> "enum(${type.entries.joinToString(", ")})"
            is ToolParameterType.List -> "list<${formatToolParameterType(type.itemsType)}>"
            is ToolParameterType.AnyOf -> {
                val entries = type.types.joinToString(", ") { entry ->
                    "${entry.name}:${formatToolParameterType(entry.type)}"
                }
                "anyOf($entries)"
            }
            is ToolParameterType.Object -> {
                val props = type.properties.joinToString(", ") { entry -> entry.name }
                val required = if (type.requiredProperties.isNotEmpty()) {
                    " required=${type.requiredProperties.joinToString(", ")}"
                } else {
                    ""
                }
                val additional = when (type.additionalProperties) {
                    null -> ""
                    true -> " additional=true"
                    false -> " additional=false"
                }
                "object{$props}$required$additional"
            }
        }
    }

    public fun runShellCommand() {
        val command = terminalCommand.trim()
        if (command.isEmpty()) {
            return
        }
        terminalRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(resolveSessionWorkingDir(currentSessionWorkDir))
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                terminalOutput = "$output\n(exit code: $exitCode)"
            } catch (e: Exception) {
                terminalOutput = "Failed to run command: ${e.message}"
            } finally {
                terminalRunning = false
            }
        }
    }

    public fun runScript() {
        val script = scriptContent.trim()
        if (script.isEmpty()) {
            return
        }
        scriptRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = io.github.stream29.kode.scripting.eval(script)
                scriptOutput = when (result) {
                    is io.github.stream29.kode.scripting.EvalResult.Success -> {
                        "Return: ${result.returnValue}\n\nStdout:\n${result.stdout}"
                    }
                    is io.github.stream29.kode.scripting.EvalResult.Failure -> {
                        "Error: ${result.message}\n\nStdout:\n${result.stdout}"
                    }
                }
            } catch (e: Exception) {
                scriptOutput = "Script failed: ${e.message}"
            } finally {
                scriptRunning = false
            }
        }
    }

    public fun fetchWebContent() {
        val url = webUrl.trim()
        if (url.isEmpty()) {
            return
        }
        webLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tool = webToolsProvider.create(
                    messageHandler = this@MainViewModel,
                    logger = { log(it) },
                )
                val result = tool.fetchURL(url)
                webContent = result.toString()
            } catch (e: Exception) {
                webContent = "Failed to fetch: ${e.message}"
            } finally {
                webLoading = false
            }
        }
    }

    public fun openWebInBrowser() {
        val url = webUrl.trim()
        if (url.isEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(java.net.URI.create(url))
                }
            } catch (e: Exception) {
                addSystemMessage("Failed to open browser: ${e.message}")
            }
        }
    }

    public fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Export Logs", java.awt.FileDialog.SAVE)
                dialog.isVisible = true
                val file = dialog.file ?: return@launch
                val dir = dialog.directory ?: return@launch
                val output = buildString {
                    appendLine("== Tool Logs ==")
                    toolLogs.forEach { appendLine(it) }
                    appendLine()
                    appendLine("== ACP Logs ==")
                    acpLogs.forEach { appendLine(it) }
                }
                File(dir, file).writeText(output)
                addSystemMessage("Logs exported")
            } catch (e: Exception) {
                addSystemMessage("Failed to export logs: ${e.message}")
            }
        }
    }

    private suspend fun buildMcpToolRegistry(): ToolRegistry? {
        stopMcpProcesses()

        if (mcpServers.isEmpty()) {
            withContext(Dispatchers.Main) {
                mcpHealthResults = emptyMap()
            }
            return null
        }

        val knownServers = mcpServers.keys
        withContext(Dispatchers.Main) {
            mcpHealthResults = mcpHealthResults.filterKeys { it in knownServers }
            mcpHealthResults = mcpHealthResults + knownServers.associateWith {
                McpHealthResult(McpHealthStatus.Checking, "Checking...")
            }
        }

        var combined: ToolRegistry? = null
        mcpServers.forEach { (name, server) ->
            try {
                val registry = when (server.transport.lowercase()) {
                    "stdio" -> {
                        val command = server.command
                        if (command.isNullOrBlank()) {
                            addSystemMessage("MCP server $name missing command")
                            updateMcpHealth(name, buildMcpHealthError("Missing command"))
                            null
                        } else {
                            val process = startMcpProcess(name, server, command)
                            if (process == null) {
                                updateMcpHealth(name, buildMcpHealthError("Failed to start process"))
                                null
                            } else {
                                val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                                val result = McpToolRegistryProvider.fromTransport(
                                    transport = transport,
                                    name = name,
                                    version = "1.0.0"
                                )
                                updateMcpHealth(name, buildMcpHealthSuccess(result))
                                result
                            }
                        }
                    }
                    "http", "sse" -> {
                        val url = server.url
                        if (url.isNullOrBlank()) {
                            addSystemMessage("MCP server $name missing url")
                            updateMcpHealth(name, buildMcpHealthError("Missing url"))
                            null
                        } else {
                            val transport = McpToolRegistryProvider.defaultSseTransport(url)
                            val result = McpToolRegistryProvider.fromTransport(
                                transport = transport,
                                name = name,
                                version = "1.0.0"
                            )
                            updateMcpHealth(name, buildMcpHealthSuccess(result))
                            result
                        }
                    }
                    else -> {
                        addSystemMessage("MCP server $name has unsupported transport ${server.transport}")
                        updateMcpHealth(
                            name,
                            buildMcpHealthError("Unsupported transport ${server.transport}")
                        )
                        null
                    }
                }

                if (registry != null) {
                    combined = if (combined == null) {
                        registry
                    } else {
                        combined + registry
                    }
                }
            } catch (e: Exception) {
                addSystemMessage("Failed to load MCP server $name: ${e.message}")
                updateMcpHealth(name, buildMcpHealthError(e.message ?: "Failed to load"))
            }
        }

        return combined
    }

    private fun startMcpProcess(
        name: String,
        server: io.github.stream29.kode.config.api.McpServerConfig,
        command: String
    ): Process? {
        return try {
            val args = server.args
            val processBuilder = ProcessBuilder(listOf(command) + args)
                .directory(resolveSessionWorkingDir(currentSessionWorkDir))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            server.env?.forEach { (key, value) ->
                processBuilder.environment()[key] = value
            }
            val process = processBuilder.start()
            mcpProcesses[name] = process
            process
        } catch (e: Exception) {
            addSystemMessage("Failed to start MCP process $name: ${e.message}")
            null
        }
    }

    private fun stopMcpProcesses() {
        mcpProcesses.values.forEach { process ->
            process.destroy()
        }
        mcpProcesses.clear()
    }

    private fun buildServiceConfig(
        provider: String,
        apiKey: String,
        baseUrl: String,
        headersText: String,
        envText: String
    ): ServiceConfig? {
        val normalizedProvider = provider.trim()
        if (normalizedProvider.isBlank()) {
            return null
        }
        val headers = parseKeyValueLines(headersText, separator = ":")
        val env = parseKeyValueLines(envText, separator = "=")
        return ServiceConfig(
            provider = normalizedProvider,
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim().ifBlank { null },
            customHeaders = headers.ifEmpty { null },
            env = env.ifEmpty { null },
            oauth = null
        )
    }

    private fun parseKeyValueLines(input: String, separator: String): Map<String, String> {
        if (input.isBlank()) {
            return emptyMap()
        }
        return input.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(separator) }
            .associate {
                val parts = it.split(separator, limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    private fun mapToLines(map: Map<String, String>?, separator: String): String {
        if (map.isNullOrEmpty()) {
            return ""
        }
        return map.entries.joinToString("\n") { (key, value) -> "$key$separator$value" }
    }

    private fun buildPreferencesSnapshot(): PreferencesSnapshot {
        return PreferencesSnapshot(
            defaultModelId = defaultModelId,
            defaultThinking = defaultThinking,
            defaultSessionDir = defaultSessionDir,
            skillsDir = skillsDir,
            agentBuiltin = agentBuiltin,
            agentFile = agentFile,
            logLevel = logLevel,
            logFile = logFile,
            uiTheme = uiTheme,
            approvalDefaultYolo = approvalDefaultYolo,
            approvalAutoApproveActions = approvalAutoApproveActions,
            disabledTools = disabledTools,
            webSearchProvider = webSearchProvider,
            webSearchApiKey = webSearchApiKey,
            webSearchBaseUrl = webSearchBaseUrl,
            webSearchHeaders = webSearchHeaders,
            webSearchEnv = webSearchEnv,
            webFetchProvider = webFetchProvider,
            webFetchApiKey = webFetchApiKey,
            webFetchBaseUrl = webFetchBaseUrl,
            webFetchHeaders = webFetchHeaders,
            webFetchEnv = webFetchEnv,
        )
    }

    private fun buildSystemPrompt(): String {
        val agentSpec = readAgentSpec()
        val basePrompt = agentSpec?.trim().takeUnless { it.isNullOrBlank() }
            ?: SessionAwareAgentFactory.SYSTEM_PROMPT
        val skillsSummary = buildSkillsSummary()
        return if (skillsSummary.isBlank()) {
            basePrompt
        } else {
            "$basePrompt\n\n## Available Skills\n$skillsSummary\n\nUse skills when appropriate."
        }
    }

    private fun readAgentSpec(): String? {
        val explicit = agentFile.trim()
        if (explicit.isNotBlank()) {
            val file = File(expandHome(explicit))
            if (file.isFile) {
                agentSpecPath = file.absolutePath
                return file.readText()
            }
        }

        val workDir = resolveSessionWorkingDir(currentSessionWorkDir)
        val projectAgents = File(workDir, "AGENTS.md")
        if (projectAgents.isFile) {
            agentSpecPath = projectAgents.absolutePath
            return projectAgents.readText()
        }

        val userAgents = File(FileSystemLocations.dataDir, "AGENTS.md")
        if (userAgents.isFile) {
            agentSpecPath = userAgents.absolutePath
            return userAgents.readText()
        }

        agentSpecPath = ""
        return null
    }

    private fun buildSkillsSummary(): String {
        val skills = discoverSkills()
        if (skills.isEmpty()) {
            return ""
        }
        return skills.joinToString("\n") { skill ->
            if (skill.description.isBlank()) {
                "- ${skill.name}"
            } else {
                "- ${skill.name}: ${skill.description}"
            }
        }
    }

    private data class SkillSummary(
        val name: String,
        val description: String,
        val content: String
    )

    private fun discoverSkills(): List<SkillSummary> {
        val roots = resolveSkillsRoots()
        val skillMap = linkedMapOf<String, SkillSummary>()
        roots.forEach { root ->
            if (!root.isDirectory) {
                return@forEach
            }
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) {
                    return@forEach
                }
                val skillFile = File(dir, "SKILL.md")
                if (!skillFile.isFile) {
                    return@forEach
                }
                try {
                    val content = skillFile.readText()
                    val summary = parseSkillSummary(content, dir.name)
                    val key = summary.name.lowercase()
                    skillMap[key] = summary
                } catch (e: Exception) {
                    addSystemMessage("Failed to read skill ${dir.name}: ${e.message}")
                }
            }
        }
        return skillMap.values.sortedBy { it.name.lowercase() }
    }

    public fun refreshAgentAndSkillsPreview() {
        val agentSpec = readAgentSpec()
        agentSpecPreview = agentSpec?.trim().orEmpty()
        skillsPreview = discoverSkills().map { skill ->
            if (skill.description.isBlank()) {
                skill.name
            } else {
                "${skill.name}: ${skill.description}"
            }
        }
    }

    public fun generateDefaultAuthId(provider: String, customName: String): String {
        val base = customName.ifBlank { provider }
        return generateUniqueId(base, auths.map { it.id }.toSet())
    }

    public fun generateDefaultModelId(modelName: String, authId: String): String {
        val base = listOf(authId, modelName).filter { it.isNotBlank() }.joinToString("-")
        return generateUniqueId(base, models.map { it.id }.toSet())
    }

    public enum class McpTestStatus {
        Success,
        Error,
    }

    public enum class McpHealthStatus {
        Unknown,
        Checking,
        Healthy,
        Unhealthy,
    }

    public data class McpToolSummary(
        val name: String,
        val description: String,
        val requiredParameters: List<McpToolParameterSummary>,
        val optionalParameters: List<McpToolParameterSummary>,
    )

    public data class McpToolParameterSummary(
        val name: String,
        val type: String,
        val description: String,
    )

    public data class McpTestResult(
        val status: McpTestStatus,
        val message: String,
        val tools: List<McpToolSummary>,
    )

    public data class McpHealthResult(
        val status: McpHealthStatus,
        val message: String,
    )


    private fun generateUniqueId(base: String, existing: Set<String>): String {
        val normalized = normalizeId(base)
        if (normalized.isBlank()) {
            return ""
        }
        return ensureUniqueId(normalized, existing)
    }

    private fun ensureUniqueId(base: String, existing: Set<String>): String {
        if (!existing.contains(base)) {
            return base
        }
        var index = 1
        var candidate = "${base}_$index"
        while (existing.contains(candidate)) {
            index += 1
            candidate = "${base}_$index"
        }
        return candidate
    }

    private fun normalizeId(value: String): String {
        val normalized = value.trim()
        val cleaned = normalized.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-', '_')
        return cleaned.take(40)
    }

    private fun parseSkillSummary(content: String, fallbackName: String): SkillSummary {
        val lines = content.lines()
        val name = lines.firstOrNull { it.trim().startsWith("# ") }
            ?.removePrefix("#")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
        val description = lines
            .dropWhile { it.isBlank() || it.trim().startsWith("#") }
            .firstOrNull()
            ?.trim()
            .orEmpty()
        return SkillSummary(name = name, description = description, content = content)
    }

    private fun resolveSkillsRoots(): List<File> {
        val roots = mutableListOf<File>()
        val workDir = resolveSessionWorkingDir(currentSessionWorkDir)
        val homeDir = File(System.getProperty("user.home"))

        val overrideDir = skillsDir.trim()
        if (overrideDir.isNotBlank()) {
            roots.add(File(expandHome(overrideDir)))
        } else {
            roots.add(File(homeDir, ".kode/skills"))
        }

        listOf(
            File(homeDir, ".agents/skills"),
            File(homeDir, ".kimi/skills"),
            File(homeDir, ".claude/skills"),
            File(homeDir, ".codex/skills"),
        ).forEach { roots.add(it) }

        listOf(
            File(workDir, ".agents/skills"),
            File(workDir, ".kimi/skills"),
            File(workDir, ".claude/skills"),
            File(workDir, ".codex/skills"),
        ).forEach { roots.add(it) }

        return roots.distinctBy { it.absolutePath }
    }

    private fun expandHome(path: String): String {
        if (!path.startsWith("~")) {
            return path
        }
        val home = System.getProperty("user.home")
        return home + path.removePrefix("~")
    }

    public fun startAcpServer() {
        if (acpRunning) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val factory = agentFactory
                if (factory == null) {
                    appendAcpLog("ACP start failed: agent not initialized")
                    return@launch
                }
                val modelId = activeModelId
                if (modelId == null) {
                    appendAcpLog("ACP start failed: no model selected")
                    return@launch
                }
                val model = factory.createLLModel(modelId)
                val sessionId = currentSessionId ?: java.util.UUID.randomUUID().toString()
                val toolRegistry = factory.buildToolRegistry(
                    workingDir = resolveSessionWorkingDir(currentSessionWorkDir),
                    sessionId = sessionId
                )

                val clientToAgent = Pipe.open()
                val agentToClient = Pipe.open()
                val protocolScope = CoroutineScope(Dispatchers.IO + Job())

                val agentTransport = StdioTransport(
                    protocolScope,
                    Dispatchers.IO,
                    input = Channels.newInputStream(clientToAgent.source()).asSource().buffered(),
                    output = Channels.newOutputStream(agentToClient.sink()).asSink().buffered(),
                    name = "agent"
                )

                val clientTransport = StdioTransport(
                    protocolScope,
                    Dispatchers.IO,
                    input = Channels.newInputStream(agentToClient.source()).asSource().buffered(),
                    output = Channels.newOutputStream(clientToAgent.sink()).asSink().buffered(),
                    name = "client"
                )

                val protocol = Protocol(protocolScope, agentTransport)
                val support = KodeAcpAgentSupport(
                    promptExecutor = factory.promptExecutor,
                    toolRegistry = toolRegistry,
                    model = model,
                    systemPrompt = SessionAwareAgentFactory.SYSTEM_PROMPT,
                    maxIterations = maxStepsPerTurn,
                    protocol = protocol
                )

                Agent(protocol, support)
                protocol.start()

                acpProtocolScope = protocolScope
                acpProtocol = protocol
                acpTransport = agentTransport
                acpClientTransport = clientTransport
                acpClientToAgent = clientToAgent
                acpAgentToClient = agentToClient
                acpRunning = true
                appendAcpLog("ACP agent started (stdio). Host/port: $acpHost:$acpPort")
            } catch (e: Exception) {
                appendAcpLog("ACP start failed: ${e.message}")
            }
        }
    }

    public fun stopAcpServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acpTransport?.close()
                acpClientTransport?.close()
                acpClientToAgent?.sink()?.close()
                acpClientToAgent?.source()?.close()
                acpAgentToClient?.sink()?.close()
                acpAgentToClient?.source()?.close()
                acpProtocolScope?.cancel()
                acpProtocol = null
                acpTransport = null
                acpClientTransport = null
                acpProtocolScope = null
                acpClientToAgent = null
                acpAgentToClient = null
                acpRunning = false
                appendAcpLog("ACP server stopped")
            } catch (e: Exception) {
                appendAcpLog("ACP stop failed: ${e.message}")
            }
        }
    }

    private fun appendAcpLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            acpLogs = (acpLogs + message).takeLast(200)
        }
    }

    private class KodeAcpAgentSupport(
        private val promptExecutor: PromptExecutor,
        private val toolRegistry: ToolRegistry,
        private val model: LLModel,
        private val systemPrompt: String,
        private val maxIterations: Int,
        private val protocol: Protocol
    ) : AgentSupport {
        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
            return AgentInfo(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = AgentCapabilities(
                    loadSession = false,
                    promptCapabilities = PromptCapabilities(
                        audio = false,
                        image = false,
                        embeddedContext = true
                    )
                ),
                authMethods = emptyList()
            )
        }

        override suspend fun createSession(sessionParameters: SessionParameters): AgentSession {
            return KodeAcpAgentSession(
                sessionId = SessionId(java.util.UUID.randomUUID().toString()),
                promptExecutor = promptExecutor,
                toolRegistry = toolRegistry,
                model = model,
                systemPrompt = systemPrompt,
                maxIterations = maxIterations,
                protocol = protocol
            )
        }

        override suspend fun loadSession(
            sessionId: SessionId,
            sessionParameters: SessionParameters
        ): AgentSession {
            throw UnsupportedOperationException("ACP loadSession is not supported")
        }
    }

    private class KodeAcpAgentSession(
        override val sessionId: SessionId,
        private val promptExecutor: PromptExecutor,
        private val toolRegistry: ToolRegistry,
        private val model: LLModel,
        private val systemPrompt: String,
        private val maxIterations: Int,
        private val protocol: Protocol
    ) : AgentSession {
        private var agentJob: kotlinx.coroutines.Deferred<Any?>? = null
        private val agentMutex = kotlinx.coroutines.sync.Mutex()

        @Suppress("UNUSED_PARAMETER", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override suspend fun prompt(
            content: List<ContentBlock>,
            meta: kotlinx.serialization.json.JsonElement?
        ): kotlinx.coroutines.flow.Flow<Event> = kotlinx.coroutines.flow.channelFlow {
            val inputText = content.joinToString("\n") { it.toString() }

            agentMutex.withLock {
                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    llmModel = model,
                    toolRegistry = toolRegistry,
                    systemPrompt = systemPrompt,
                    strategy = singleRunStrategy(),
                    maxIterations = maxIterations
                ) {
                    install(AcpAgent) {
                        this.sessionId = this@KodeAcpAgentSession.sessionId.value
                        this.protocol = this@KodeAcpAgentSession.protocol
                        this.eventsProducer = this@channelFlow
                        this.setDefaultNotifications = true
                    }
                }

                agentJob = async { agent.run(inputText) }
                agentJob?.await()
            }
        }

        override suspend fun cancel() {
            agentJob?.cancelAndJoin()
        }
    }

    public fun setToolEnabled(toolKey: String, enabled: Boolean) {
        val updated = when (toolKey) {
            "file-edit" -> {
                if (enabled) {
                    disabledTools - toolKey
                } else {
                    disabledTools + toolKey
                }
            }
            "file" -> {
                if (enabled) {
                    disabledTools - toolKey
                } else {
                    disabledTools + toolKey
                }
            }
            else -> {
                if (enabled) {
                    disabledTools - toolKey
                } else {
                    disabledTools + toolKey
                }
            }
        }
        disabledTools = updated
    }

    public fun clearToolLogs() {
        toolLogs = emptyList()
    }

    

    private fun resolveSessionWorkingDir(input: String): File {
        val normalized = normalizeSessionDir(input)
            ?: normalizeSessionDir(defaultSessionDir)
            ?: "."
        return File(normalized)
    }

    public fun addAuth(auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuths = auths + auth
                val config = AppConfig(auths = newAuths, models = models)
                configManager.save(config)
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
                configManager.save(config)
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
                configManager.save(config)
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
                configManager.save(config)
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
                configManager.save(config)
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
                configManager.save(config)
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

    override fun onCleared() {
        super.onCleared()
        unbindSessionFlows()
    }
}

public data class AgentProfile(
    val name: String,
    val description: String,
    val disabledTools: Set<String>,
    val defaultYolo: Boolean
)

public data class SessionUiState(
    val taskInput: String = "",
    val messages: List<SessionMessage> = emptyList(),
    val currentSessionId: String? = null,
    val currentSessionWorkDir: String = "",
    val showNewSessionDialog: Boolean = false,
    val showSessionDirDialog: Boolean = false,
    val newSessionDirInput: String = "",
    val sessionDirDraft: String = "",
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val currentTask: String = "",
)

public data class AppUiState(
    val session: SessionUiState = SessionUiState(),
    val currentPage: io.github.stream29.kode.app.view.AppPage = io.github.stream29.kode.app.view.AppPage.Chat,
    val showSessionManager: Boolean = false,
    val showConfigEditor: Boolean = false,
    val showSettings: Boolean = false,
    val sessionSummaries: List<SessionSummary> = emptyList(),
    val sessionSearchQuery: String = "",
    val sessionTagFilter: String = "",
    val sessionStatusFilter: SessionStatusFilter = SessionStatusFilter.ALL,
    val configText: String = "",
    val configError: String? = null,
    val auths: List<LlmAuthConfig> = emptyList(),
    val models: List<LlmModelConfig> = emptyList(),
    val activeModelId: String? = null,
    val defaultModelId: String? = null,
    val defaultThinking: Boolean = false,
    val defaultSessionDir: String = "",
    val maxStepsPerTurn: Int = 100,
    val maxRetriesPerStep: Int = 3,
    val maxRalphIterations: Int = 0,
    val reservedContextSize: Int = 50000,
    val skillsDir: String = "",
    val agentBuiltin: String = "",
    val agentFile: String = "",
    val logLevel: String = "info",
    val logFile: String = "",
    val uiTheme: String = "dark",
    val mcpToolTimeoutMs: Int = 60000,
    val mcpServers: Map<String, io.github.stream29.kode.config.api.McpServerConfig> = emptyMap(),
    val mcpTestResults: Map<String, MainViewModel.McpTestResult> = emptyMap(),
    val mcpTestsInFlight: Set<String> = emptySet(),
    val mcpHealthResults: Map<String, MainViewModel.McpHealthResult> = emptyMap(),
    val webSearchProvider: String = "none",
    val webSearchApiKey: String = "",
    val webSearchBaseUrl: String = "",
    val webSearchHeaders: String = "",
    val webSearchEnv: String = "",
    val webFetchProvider: String = "builtin",
    val webFetchApiKey: String = "",
    val webFetchBaseUrl: String = "",
    val webFetchHeaders: String = "",
    val webFetchEnv: String = "",
    val agentSpecPath: String = "",
    val agentSpecPreview: String = "",
    val skillsPreview: List<String> = emptyList(),
    val activeAgentProfileName: String = "build",
    val agentProfiles: List<AgentProfile> = emptyList(),
    val acpHost: String = "127.0.0.1",
    val acpPort: Int = 5494,
    val acpRunning: Boolean = false,
    val acpLogs: List<String> = emptyList(),
    val terminalCommand: String = "",
    val terminalOutput: String = "",
    val terminalRunning: Boolean = false,
    val scriptContent: String = "",
    val scriptOutput: String = "",
    val scriptRunning: Boolean = false,
    val webUrl: String = "",
    val webContent: String = "",
    val webLoading: Boolean = false,
    val yoloEnabled: Boolean = false,
    val approvalDefaultYolo: Boolean = false,
    val approvalAutoApproveActions: List<String> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val toasts: List<UiToast> = emptyList(),
    val disabledTools: Set<String> = emptySet(),
    val toolLogs: List<String> = emptyList(),
    val autoSaveSessions: Boolean = true,
    val temperature: Float = 0.3f,
)

public data class UiToast(
    val id: String,
    val message: String,
)

private data class SessionRunState(
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val currentTask: String = "",
)

public data class PendingApproval(
    val sessionId: String,
    val request: ToolApprovalRequest
)

private data class PreferencesSnapshot(
    val defaultModelId: String?,
    val defaultThinking: Boolean,
    val defaultSessionDir: String,
    val skillsDir: String,
    val agentBuiltin: String,
    val agentFile: String,
    val logLevel: String,
    val logFile: String,
    val uiTheme: String,
    val approvalDefaultYolo: Boolean,
    val approvalAutoApproveActions: List<String>,
    val disabledTools: Set<String>,
    val webSearchProvider: String,
    val webSearchApiKey: String,
    val webSearchBaseUrl: String,
    val webSearchHeaders: String,
    val webSearchEnv: String,
    val webFetchProvider: String,
    val webFetchApiKey: String,
    val webFetchBaseUrl: String,
    val webFetchHeaders: String,
    val webFetchEnv: String,
)

private data class McpSnapshot(
    val timeoutMs: Int,
    val servers: Map<String, io.github.stream29.kode.config.api.McpServerConfig>
)
