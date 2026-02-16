package io.github.stream29.kode.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.LlmModelParamsConfig
import io.github.stream29.kode.config.api.McpTransportType
import io.github.stream29.kode.config.api.OpenAiEndpoint
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ServiceConfig
import io.github.stream29.kode.config.api.transportType
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
import io.github.stream29.kode.app.util.parseKeyValueLines
import io.github.stream29.kode.app.model.MessageAlignmentPreference
import io.github.stream29.kode.app.model.SendKeyModePreference
import io.github.stream29.kode.app.model.isSystemRoleUi
import io.github.stream29.kode.app.model.projectedTextForSessionSummary
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.core.agent.SessionAwareAgentFactory
import io.github.stream29.kode.core.agent.SessionAwareAgentFactoryProvider
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderOAuthAuthCodePkcePreset
import io.github.stream29.kode.providers.api.ProviderOAuthDeviceFlowPreset
import io.github.stream29.kode.providers.api.ProviderPreset
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry
import io.github.stream29.kode.providers.builtin.BuiltinProviderPresetRegistry
import io.github.stream29.kode.oauth.core.OAuthCredentialStatus
import io.github.stream29.kode.oauth.core.OAuthCredentialManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import io.github.stream29.kode.session.core.storage.SortBy
import io.github.stream29.kode.session.core.storage.SortOrder
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.AgentState
import io.github.stream29.kode.ui.core.MessageHandler
import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
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
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val hookManager: HookManager,
    private val oauthCredentialManager: OAuthCredentialManager,
) : ViewModel(), MessageHandler, AgentState, AgentEventListener {
    private var autoSaveEnabled: Boolean = false

    private val defaultAgentPresets: List<AgentPreset> = listOf(
        AgentPreset(
            name = "build",
            description = "Full access agent for development",
            disabledTools = emptySet(),
        ),
        AgentPreset(
            name = "plan",
            description = "Read-only planning agent",
            disabledTools = setOf("shell", "task", "file-edit"),
        ),
        AgentPreset(
            name = "explore",
            description = "Exploration agent (search-heavy)",
            disabledTools = setOf("shell", "task", "file-edit"),
        ),
    )
    private val providerPresets: List<ProviderPreset> = BuiltinProviderPresetRegistry.listPresets()

    private val _sessionUiState: MutableStateFlow<SessionUiState> = MutableStateFlow(SessionUiState())
    public val sessionUiState: StateFlow<SessionUiState> = _sessionUiState.asStateFlow()

    private val _appUiState: MutableStateFlow<AppUiState> = MutableStateFlow(
        AppUiState(agentPresets = defaultAgentPresets)
    )
    public val appUiState: StateFlow<AppUiState> = _appUiState.asStateFlow()

    public val mainChromeUiState: StateFlow<MainChromeUiState> = _appUiState
        .map { state ->
            MainChromeUiState(
                currentPage = state.currentPage,
                showConfigEditor = state.showConfigEditor,
                uiTheme = state.uiTheme,
                toasts = state.toasts,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainChromeUiState(),
        )

    public val chatPageUiState: StateFlow<ChatPageUiState> = _appUiState
        .map { state ->
            ChatPageUiState(
                sessionSummaries = state.sessionSummaries,
                messageAlignment = state.messageAlignment,
                messageMaxWidthRatio = state.messageMaxWidthRatio,
                sendKeyMode = state.sendKeyMode,
                agentPresets = state.agentPresets,
                activePresetName = state.activePresetName,
                models = state.models,
                auths = state.auths,
                activeModelId = state.activeModelId,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ChatPageUiState(),
        )

    public val sessionsPageUiState: StateFlow<SessionsPageUiState> = _appUiState
        .map { state ->
            SessionsPageUiState(
                sessionSummaries = state.sessionSummaries,
                sessionSearchQuery = state.sessionSearchQuery,
                sessionStatusFilter = state.sessionStatusFilter,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionsPageUiState(),
        )

    public val toolsPageUiState: StateFlow<ToolsPageUiState> = _appUiState
        .map { state ->
            ToolsPageUiState(
                disabledTools = state.disabledTools,
                toolLogs = state.toolLogs,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ToolsPageUiState(),
        )

    public val mcpPageUiState: StateFlow<McpPageUiState> = _appUiState
        .map { state ->
            McpPageUiState(
                mcpToolTimeoutMs = state.mcpToolTimeoutMs,
                mcpServers = state.mcpServers,
                mcpTestResults = state.mcpTestResults,
                mcpTestsInFlight = state.mcpTestsInFlight,
                mcpHealthResults = state.mcpHealthResults,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = McpPageUiState(),
        )

    public val acpPageUiState: StateFlow<AcpPageUiState> = _appUiState
        .map { state ->
            AcpPageUiState(
                acpHost = state.acpHost,
                acpPort = state.acpPort,
                acpRunning = state.acpRunning,
                acpLogs = state.acpLogs,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AcpPageUiState(),
        )

    public val terminalPageUiState: StateFlow<TerminalPageUiState> = _appUiState
        .map { state ->
            TerminalPageUiState(
                terminalCommand = state.terminalCommand,
                terminalOutput = state.terminalOutput,
                terminalRunning = state.terminalRunning,
                scriptContent = state.scriptContent,
                scriptOutput = state.scriptOutput,
                scriptRunning = state.scriptRunning,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TerminalPageUiState(),
        )

    public val webPageUiState: StateFlow<WebPageUiState> = _appUiState
        .map { state ->
            WebPageUiState(
                webUrl = state.webUrl,
                webContent = state.webContent,
                webLoading = state.webLoading,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WebPageUiState(),
        )

    public val infoPageUiState: StateFlow<InfoPageUiState> = _appUiState
        .map { state ->
            InfoPageUiState(
                presetSpecPath = state.presetSpecPath,
                presetSpecPreview = state.presetSpecPreview,
                skillsPreview = state.skillsPreview,
                modelsCount = state.models.size,
                authCount = state.auths.size,
                mcpServerCount = state.mcpServers.size,
                disabledTools = state.disabledTools,
                acpRunning = state.acpRunning,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = InfoPageUiState(),
        )

    public val configEditorUiState: StateFlow<ConfigEditorUiState> = _appUiState
        .map { state ->
            ConfigEditorUiState(
                configText = state.configText,
                configError = state.configError,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConfigEditorUiState(),
        )

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
            val previousSessionId = _sessionUiState.value.currentSessionId
            updateSessionUiState { current ->
                applySessionRunState(
                    base = current.copy(
                        currentSessionId = value,
                        showContinueRecoveryDialog = false,
                        continueRecoveryToolName = "",
                        continueRecoveryToolCallId = "",
                    ),
                    sessionId = value,
                )
            }
            if (value != null) {
                updateAppUiState { current ->
                    if (current.lastOpenedSessionId == value) {
                        current
                    } else {
                        current.copy(lastOpenedSessionId = value)
                    }
                }
            }
            if (previousSessionId != null && previousSessionId != value) {
                hookManager.unbindSessionPreset(previousSessionId)
            }
            if (value != null) {
                bindPresetForSession(sessionId = value)
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

    public var oauthStatusByAuthId: Map<String, OAuthStatusUi>
        get() = _appUiState.value.oauthStatusByAuthId
        set(value) = updateAppUiState { it.copy(oauthStatusByAuthId = value) }

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

    public var appDataDir: String
        get() = _appUiState.value.appDataDir
        set(value) = updateAppUiState { it.copy(appDataDir = normalizeAppDataDirInput(value)) }

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

    public var presetBuiltin: String
        get() = _appUiState.value.presetBuiltin
        set(value) = updateAppUiState { it.copy(presetBuiltin = value) }

    public var presetFile: String
        get() = _appUiState.value.presetFile
        set(value) = updateAppUiState { it.copy(presetFile = value) }

    public var logLevel: String
        get() = _appUiState.value.logLevel
        set(value) = updateAppUiState { it.copy(logLevel = value) }

    public var logFile: String
        get() = _appUiState.value.logFile
        set(value) = updateAppUiState { it.copy(logFile = value) }

    public var uiTheme: String
        get() = _appUiState.value.uiTheme
        set(value) = updateAppUiState { it.copy(uiTheme = value) }

    public var lastOpenedSessionId: String?
        get() = _appUiState.value.lastOpenedSessionId
        set(value) = updateAppUiState { it.copy(lastOpenedSessionId = value?.trim()?.takeIf { id -> id.isNotBlank() }) }

    public var messageAlignment: String
        get() = _appUiState.value.messageAlignment
        set(value) = updateAppUiState { it.copy(messageAlignment = normalizeMessageAlignment(value)) }

    public var messageMaxWidthRatio: Float
        get() = _appUiState.value.messageMaxWidthRatio
        set(value) = updateAppUiState { it.copy(messageMaxWidthRatio = normalizeMessageWidthRatio(value)) }

    public var sendKeyMode: String
        get() = _appUiState.value.sendKeyMode
        set(value) = updateAppUiState { it.copy(sendKeyMode = normalizeSendKeyMode(value)) }

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

    public var presetSpecPath: String
        get() = _appUiState.value.presetSpecPath
        set(value) = updateAppUiState { it.copy(presetSpecPath = value) }

    public var presetSpecPreview: String
        get() = _appUiState.value.presetSpecPreview
        set(value) = updateAppUiState { it.copy(presetSpecPreview = value) }

    public var skillsPreview: List<String>
        get() = _appUiState.value.skillsPreview
        set(value) = updateAppUiState { it.copy(skillsPreview = value) }

    public var activePresetName: String
        get() = _appUiState.value.activePresetName
        set(value) = updateAppUiState { it.copy(activePresetName = value) }
    public val agentPresets: List<AgentPreset>
        get() = _appUiState.value.agentPresets
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
    private var sessionTitleGeneratingIds: Set<String> = emptySet()
    private val inputDeferreds: MutableMap<String, CompletableDeferred<String>> = mutableMapOf()
    private val sessionJobs: MutableMap<String, Job> = mutableMapOf()
    private val oauthJobs: MutableMap<String, Job> = mutableMapOf()
    private var sessionBindingJob: Job? = null
    private var boundSessionId: String? = null
    private var lastSessionRestoreAttempted: Boolean = false
    private val defaultAppDataDir: String = "~/.kode/"
    private val autoSessionTitlePlaceholder: String = "New Chat"
    private val legacySessionTitlePrefix: String = "Conversation "

    private enum class ContinueConflictResolution {
        Auto,
        Rollback,
        ContinueWithoutRollback,
    }

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
        _sessionUiState.update(transform)
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
        registerPresetHooks()
        viewModelScope.launch {
            initializeAgentFactory()
            startAutoSaveObservers()
        }
    }
    
    private suspend fun initializeAgentFactory() {
        val config = configManager.load()
        loadConfigToState(config)
        refreshAgentFactoryForConversation()
        refreshOAuthStatusSnapshot()
        restoreLastSessionIfNeeded()
    }

    private suspend fun refreshAgentFactoryForConversation(): Boolean {
        if (models.isEmpty()) {
            agentFactory = null
            return false
        }

        ensureOAuthCredentialsUpToDate()

        val mcpRegistry = buildMcpToolRegistry()
        buildAgentFactory(mcpRegistry)
        return true
    }

    private fun buildAgentFactory(mcpRegistry: ToolRegistry?) {
        agentFactory = agentFactoryProvider.create(
            auths = auths,
            models = models,
            messageHandler = this@MainViewModel,
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
        appDataDir = normalizeAppDataDirInput(config.storage.dataDir)
        defaultSessionDir = config.defaults.workDir ?: ""
        if (currentSessionId == null && currentSessionWorkDir.isBlank()) {
            currentSessionWorkDir = normalizeSessionDir(defaultSessionDir).orEmpty()
        }
        maxStepsPerTurn = config.loopControl.maxStepsPerTurn
        maxRetriesPerStep = config.loopControl.maxRetriesPerStep
        maxRalphIterations = config.loopControl.maxRalphIterations
        reservedContextSize = config.loopControl.reservedContextSize
        skillsDir = config.skills.dir ?: ""
        presetBuiltin = config.preset.builtin ?: ""
        presetFile = config.preset.file ?: ""
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
        lastOpenedSessionId = config.ui.lastOpenedSessionId
        messageAlignment = normalizeMessageAlignment(config.ui.messageAlignment)
        messageMaxWidthRatio = normalizeMessageWidthRatio(config.ui.messageMaxWidthRatio)
        sendKeyMode = normalizeSendKeyMode(config.ui.sendKeyMode)
        applyAgentPresetFromConfig()
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

        refreshPresetAndSkillsPreview()
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

                val sessionId = currentSessionId ?: run {
                    addSystemMessage("No active session. Please create a session first.")
                    return@launch
                }
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
                ensureSessionAutoTitle(
                    sessionId = sessionId,
                    modelId = modelId,
                    factory = factory,
                    force = false,
                )
            } catch (e: Exception) {
                e.printStackTrace()
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
        continueCurrentSession(resolution = ContinueConflictResolution.Auto)
    }

    public fun continueCurrentSessionAfterRollback() {
        dismissContinueRecoveryDialog()
        continueCurrentSession(resolution = ContinueConflictResolution.Rollback)
    }

    public fun continueCurrentSessionWithoutRollback() {
        dismissContinueRecoveryDialog()
        continueCurrentSession(resolution = ContinueConflictResolution.ContinueWithoutRollback)
    }

    public fun dismissContinueRecoveryDialog() {
        updateSessionUiState { current ->
            current.copy(
                showContinueRecoveryDialog = false,
                continueRecoveryToolName = "",
                continueRecoveryToolCallId = "",
            )
        }
    }

    private fun continueCurrentSession(resolution: ContinueConflictResolution) {
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
            var runStarted = false
            try {
                if (sessionRunStates[sessionId]?.isRunning == true) {
                    addSystemMessage("Session is already running", sessionId)
                    return@launch
                }

                when (resolution) {
                    ContinueConflictResolution.Auto -> {
                        val pendingCall = sessionManager.getTrailingPendingToolCall(sessionId)
                        if (pendingCall != null) {
                            if (isAwaitUserInputToolNameForContinue(pendingCall.toolName)) {
                                val normalized = sessionManager.normalizeTrailingAwaitUserInputToolCall(sessionId)
                                if (normalized) {
                                    addSystemMessage(
                                        "Detected pending awaitUserInput call and normalized it to sayToUser before continue.",
                                        sessionId,
                                    )
                                }
                            } else {
                                showContinueRecoveryDialog(
                                    toolName = pendingCall.toolName,
                                    toolCallId = pendingCall.toolCallId,
                                )
                                return@launch
                            }
                        }
                    }

                    ContinueConflictResolution.Rollback -> {
                        sessionManager.rollbackTrailingPendingToolCall(sessionId)
                    }

                    ContinueConflictResolution.ContinueWithoutRollback -> Unit
                }

                if (!refreshAgentFactoryForConversation()) {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                val factory = agentFactory ?: run {
                    addSystemMessage("Agent not initialized. Please check your configuration.")
                    return@launch
                }

                runStarted = true
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
                e.printStackTrace()
                addSystemMessage("Error: ${e.message}", sessionId)
                log("continueCurrentSession failed: ${e.stackTraceToString()}", sessionId)
            } finally {
                if (runStarted) {
                    updateSessionRunState(sessionId) { state ->
                        state.copy(isRunning = false, currentTask = "", isWaitingForInput = false)
                    }
                    sessionJobs.remove(sessionId)
                }
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

    public fun regenerateCurrentSessionTitle() {
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

                ensureSessionAutoTitle(
                    sessionId = sessionId,
                    modelId = modelId,
                    factory = factory,
                    force = true,
                )
            } catch (e: Exception) {
                addSystemMessage("Failed to regenerate title: ${e.message}", sessionId)
            }
        }
    }

    public fun updateCurrentSessionTitle(newTitle: String) {
        val sessionId = currentSessionId
        if (sessionId == null) {
            addSystemMessage("No active session")
            return
        }
        val normalizedTitle = normalizeSessionTitleInput(newTitle)
        if (normalizedTitle.isBlank()) {
            addSystemMessage("Title cannot be empty", sessionId)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                sessionManager.updateTitle(sessionId, normalizedTitle)
            }.onSuccess {
                loadSessionList()
            }.onFailure { error ->
                addSystemMessage("Failed to update title: ${error.message}", sessionId)
            }
        }
    }

    private fun normalizeSessionTitleInput(raw: String): String {
        val compact = raw
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")
        return if (compact.length > 80) {
            compact.take(80).trimEnd()
        } else {
            compact
        }
    }

    private fun showContinueRecoveryDialog(toolName: String, toolCallId: String) {
        updateSessionUiState { current ->
            current.copy(
                showContinueRecoveryDialog = true,
                continueRecoveryToolName = toolName,
                continueRecoveryToolCallId = toolCallId,
            )
        }
    }

    private fun isAwaitUserInputToolNameForContinue(toolName: String): Boolean {
        val normalized = toolName.trim().replace("_", "").lowercase()
        return normalized == "awaituserinput" || normalized == "waitforuserinput"
    }

    private suspend fun ensureSessionAutoTitle(
        sessionId: String,
        modelId: String,
        factory: SessionAwareAgentFactory,
        force: Boolean,
    ) {
        val session = sessionManager.getSession(sessionId) ?: return
        val currentTitle = session.title.trim()
        if (!force && !shouldAutoGenerateSessionTitle(currentTitle)) {
            return
        }

        setSessionTitleGenerating(sessionId = sessionId, isGenerating = true)
        try {
            val generatedTitle = runCatching {
                factory.generateSessionTitleFromConversation(sessionId = sessionId, modelId = modelId)
            }.onFailure { error ->
                log("Failed to auto-generate title for session $sessionId: ${error.message}")
            }.getOrNull()
            val fallbackTitle = buildFallbackSessionTitleFromSession(session)
            val resolvedTitle = generatedTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle

            if (resolvedTitle.isBlank() || resolvedTitle == currentTitle) {
                return
            }

            runCatching {
                sessionManager.updateTitle(sessionId, resolvedTitle)
            }.onSuccess {
                loadSessionList()
            }.onFailure { error ->
                addSystemMessage("Failed to update title: ${error.message}", sessionId)
            }
        } finally {
            setSessionTitleGenerating(sessionId = sessionId, isGenerating = false)
        }
    }

    private fun setSessionTitleGenerating(sessionId: String, isGenerating: Boolean) {
        val updatedIds = if (isGenerating) {
            sessionTitleGeneratingIds + sessionId
        } else {
            sessionTitleGeneratingIds - sessionId
        }
        if (updatedIds == sessionTitleGeneratingIds) {
            return
        }

        sessionTitleGeneratingIds = updatedIds
        updateSessionUiState { currentUi ->
            applySessionRunState(
                base = currentUi,
                sessionId = currentUi.currentSessionId,
            )
        }
    }

    private fun shouldAutoGenerateSessionTitle(currentTitle: String): Boolean {
        if (currentTitle.isBlank()) {
            return true
        }
        if (currentTitle.equals(autoSessionTitlePlaceholder, ignoreCase = true)) {
            return true
        }
        return currentTitle.startsWith(legacySessionTitlePrefix)
    }

    private fun buildFallbackSessionTitleFromSession(session: ConversationSession): String {
        val projected = session.messages.firstNotNullOfOrNull { message -> message.projectedTextForSessionSummary() }
        val normalized = projected.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) {
            return autoSessionTitlePlaceholder
        }
        return if (normalized.length > 50) {
            normalized.take(50).trimEnd() + "..."
        } else {
            normalized
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
        if (selected == null || selected.isSystemRoleUi()) {
            addSystemMessage("Cannot fork from system message")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionManager.getSession(sessionId)
                    ?: throw IllegalArgumentException("Session not found: $sessionId")

                val nonSystemMessages = messages.filter {
                    !it.isSystemRoleUi()
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

    private fun buildSessionFilter(): SessionFilter {
        return SessionFilter(
            status = sessionStatusFilter,
            sortBy = SortBy.CREATED_AT,
            sortOrder = SortOrder.DESCENDING
        )
    }

    public fun loadSessionList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filter = buildSessionFilter()
                val summaries = sessionManager.listSessions(filter = filter)
                sessionSummaries = applySessionTextSearch(
                    summaries = summaries,
                    query = sessionSearchQuery,
                )
            } catch (e: Exception) {
                addSystemMessage("Failed to load sessions: ${e.message}")
            }
        }
    }

    private suspend fun applySessionTextSearch(
        summaries: List<SessionSummary>,
        query: String,
    ): List<SessionSummary> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return summaries
        }

        val ranked = summaries.mapNotNull { summary ->
            val corpus = buildSessionSearchCorpus(summary)
            val hits = countOccurrencesIgnoreCase(corpus, normalizedQuery)
            if (hits <= 0) {
                null
            } else {
                SessionSearchHit(summary = summary, hits = hits)
            }
        }

        return ranked.sortedWith(
            compareByDescending<SessionSearchHit> { hit -> hit.hits }
                .thenByDescending { hit -> hit.summary.createdAt }
                .thenBy { hit -> hit.summary.id }
        ).map { hit -> hit.summary }
    }

    private suspend fun buildSessionSearchCorpus(summary: SessionSummary): String {
        val session = sessionManager.getSession(summary.id) ?: return summary.title

        val projectedTexts = session.messages.mapNotNull { message -> message.projectedTextForSessionSummary() }

        return buildString {
            append(summary.title)
            projectedTexts.forEach { text ->
                append('\n')
                append(text)
            }
        }
    }

    private fun countOccurrencesIgnoreCase(text: String, query: String): Int {
        if (text.isBlank() || query.isBlank()) {
            return 0
        }
        var count = 0
        var cursor = 0
        while (cursor < text.length) {
            val found = text.indexOf(query, startIndex = cursor, ignoreCase = true)
            if (found < 0) {
                break
            }
            count += 1
            cursor = found + query.length
        }
        return count
    }

    public fun updateSessionSearchQuery(query: String) {
        sessionSearchQuery = query
        loadSessionList()
    }

    public fun updateSessionStatusFilter(status: SessionStatusFilter) {
        sessionStatusFilter = status
        loadSessionList()
    }

    public fun switchToSession(sessionId: String) {
        switchToSessionInternal(sessionId = sessionId, announce = true)
    }

    public fun restoreLastSessionIfNeeded() {
        if (currentSessionId != null || lastSessionRestoreAttempted) {
            return
        }
        lastSessionRestoreAttempted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filter = buildSessionFilter()
                val summaries = sessionManager.listSessions(filter = filter)
                sessionSummaries = summaries
                val preferredSessionId = lastOpenedSessionId
                if (!preferredSessionId.isNullOrBlank()) {
                    val preferredSession = sessionManager.getSession(preferredSessionId)
                    if (preferredSession != null) {
                        switchToSessionInternal(sessionId = preferredSessionId, announce = false)
                        return@launch
                    }
                    lastOpenedSessionId = null
                }

                val latest = summaries.firstOrNull() ?: return@launch
                switchToSessionInternal(sessionId = latest.id, announce = false)
            } catch (e: Exception) {
                addSystemMessage("Failed to restore session: ${e.message}")
            }
        }
    }

    private fun switchToSessionInternal(sessionId: String, announce: Boolean) {
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
                if (announce) {
                    addSystemMessage("Switched to session: ${sessionId.take(8)}...")
                }
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
                if (lastOpenedSessionId == sessionId) {
                    lastOpenedSessionId = null
                }
                sessionJobs.remove(sessionId)?.cancel("Session deleted")
                clearSessionRunState(sessionId)
                setSessionTitleGenerating(sessionId = sessionId, isGenerating = false)
                inputDeferreds.remove(sessionId)
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
        if (!canEditSessionWorkDir()) {
            addSystemMessage("Session work directory can only be changed while suspended")
            return
        }
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
                    title = autoSessionTitlePlaceholder,
                    systemPrompt = buildSystemPrompt(),
                    modelId = modelId,
                    workDir = normalizedWorkDir
                )
                createdSessionId = sessionId
                currentSessionId = sessionId
                bindSessionFlows(sessionId)
                loadSessionList()

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
                    ensureSessionAutoTitle(
                        sessionId = sessionId,
                        modelId = modelId,
                        factory = factory,
                        force = false,
                    )
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
        if (!canEditSessionWorkDir()) {
            addSystemMessage("Session work directory can only be changed while suspended")
            return
        }
        sessionDirDraft = currentSessionWorkDir
        showSessionDirDialog = true
    }

    public fun confirmSessionDirDialog() {
        if (!canEditSessionWorkDir()) {
            showSessionDirDialog = false
            addSystemMessage("Session work directory can only be changed while suspended")
            return
        }
        updateCurrentSessionWorkDir(sessionDirDraft)
        showSessionDirDialog = false
    }

    public fun cancelSessionDirDialog() {
        showSessionDirDialog = false
    }

    private fun buildDefaultSessionDirInput(): String {
        return defaultSessionDir.trim().ifBlank { "." }
    }

    private fun canEditSessionWorkDir(): Boolean {
        val session = _sessionUiState.value
        return session.currentSessionId != null && !session.isRunning && !session.isWaitingForInput
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

    private fun normalizeAppDataDirInput(input: String): String {
        val trimmed = input.trim()
        return trimmed.ifBlank { defaultAppDataDir }
    }

    private suspend fun persistAppDataDirSetting(dataDir: String) {
        val current = configManager.load()
        val updated = current.copy(
            storage = current.storage.copy(
                dataDir = normalizeAppDataDirInput(dataDir)
            )
        )
        configManager.save(updated)
    }

    private fun migrateAppDataDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists()) {
            targetDir.mkdirs()
            return
        }

        val sourceCanonical = sourceDir.canonicalFile
        val targetCanonical = targetDir.canonicalFile
        if (targetCanonical.path.startsWith(sourceCanonical.path + File.separator)) {
            throw IllegalArgumentException("Target directory cannot be nested inside current data directory")
        }

        val entries = sourceDir.listFiles().orEmpty()
        targetDir.mkdirs()
        val conflicts = entries.map { entry -> File(targetDir, entry.name) }
            .filter { candidate -> candidate.exists() }
        if (conflicts.isNotEmpty()) {
            throw IllegalStateException(
                "Target directory already contains ${conflicts.first().name}; please choose an empty directory"
            )
        }

        entries.forEach { entry ->
            entry.copyRecursively(target = File(targetDir, entry.name), overwrite = false)
        }
    }

    private fun resolveAppDataDir(): File {
        return FileSystemLocations.resolveDataDir(path = appDataDir)
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
        val titleGenerating = sessionId?.let { id -> id in sessionTitleGeneratingIds } ?: false
        return base.copy(
            isRunning = runState?.isRunning ?: false,
            isWaitingForInput = runState?.isWaitingForInput ?: false,
            currentTask = runState?.currentTask.orEmpty(),
            isGeneratingSessionTitle = titleGenerating,
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
  - id: anthropic-main
    provider_id: anthropic
    auth:
      type: api_key
      api_key: your-api-key-here
      env_keys: []
      base_url: null
      custom_headers: {}
  - id: openai-subscription
    provider_id: openai-subscription-browser
    auth:
      type: oauth
      oauth:
        storage: file
        key: ~/.kode/oauth/openai-subscription.oauth.json
        authorization_endpoint: https://auth.openai.com/oauth/authorize
        token_endpoint: https://auth.openai.com/oauth/token
        client_id: app_EMoamEEZ73f0CkXaXp7hrann
        scopes:
          - openid
          - profile
          - email
          - offline_access
        callback_uri: http://localhost:1455/auth/callback
        authorization_additional_params:
          id_token_add_organizations: "true"
          codex_cli_simplified_flow: "true"
          originator: opencode
        token_additional_params: {}
      base_url: https://api.openai.com/v1
      custom_headers: {}

models:
  - id: claude-sonnet
    auth_id: anthropic-main
    model: claude-sonnet-4-5-20250929
    display_name: Claude Sonnet 4.5

defaults:
  model_id: claude-sonnet
  thinking: false
  work_dir: "."

storage:
  data_dir: "~/.kode/"

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

preset:
  builtin: default
  file: null

ui:
  theme: dark
  message_alignment: left
  message_max_width_ratio: 0.9
  send_key_mode: ctrl_or_cmd_enter_send

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
                val parsedConfig = configManager.parse(configText)
                validateAuthClientProviderScope(parsedConfig.auths)
                validateModelProviderSupport(authConfigs = parsedConfig.auths, modelConfigs = parsedConfig.models)
                val configFile = FileSystemLocations.configFile
                configFile.parentFile?.mkdirs()
                configFile.writeText(configText)

                loadConfigToState(parsedConfig)
                refreshAgentFactoryForConversation()
                refreshOAuthStatusSnapshot()

                if (parsedConfig.models.isEmpty()) {
                    configError = "Config is valid but no models configured"
                } else {
                    configError = null
                    showConfigEditor = false
                    addSystemMessage("Config saved successfully")
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
                validateAuthClientProviderScope(config.auths)
                validateModelProviderSupport(authConfigs = config.auths, modelConfigs = config.models)
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
                val dataDir = resolveAppDataDir()
                dataDir.mkdirs()
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(dataDir)
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
                sessionTitleGeneratingIds = emptySet()
                inputDeferreds.clear()
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
        viewModelScope.launch(Dispatchers.Main) {
            addSystemMessage(message, sessionId)
        }
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
                log("Agent error: ${event.message}", sessionId)
                event.exception?.let { throwable ->
                    log("Agent error stacktrace: ${throwable.stackTraceToString()}", sessionId)
                    throwable.printStackTrace()
                }
            }
        }
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

    public fun resolveAppDataDirPath(input: String): String {
        val normalized = normalizeAppDataDirInput(input)
        return FileSystemLocations.resolveDataDir(path = normalized).absolutePath
    }

    public fun applyAppDataDirChange(newInput: String, migrateExistingData: Boolean) {
        val normalized = normalizeAppDataDirInput(newInput)
        val currentDir = resolveAppDataDir()
        val targetDir = FileSystemLocations.resolveDataDir(path = normalized)
        if (currentDir.absolutePath == targetDir.absolutePath) {
            appDataDir = normalized
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (migrateExistingData) {
                    migrateAppDataDirectory(sourceDir = currentDir, targetDir = targetDir)
                } else {
                    targetDir.mkdirs()
                }
                appDataDir = normalized
                persistAppDataDirSetting(dataDir = normalized)
                val action = if (migrateExistingData) {
                    "updated and migrated"
                } else {
                    "updated"
                }
                addSystemMessage(
                    "App data directory $action: ${targetDir.absolutePath}. Restart Kode to apply session storage switching."
                )
                enqueueToast("App data directory updated. Please restart Kode.")
            } catch (e: Exception) {
                addSystemMessage("Failed to update app data directory: ${e.message}")
            }
        }
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
                storage = current.storage.copy(
                    dataDir = normalizeAppDataDirInput(appDataDir)
                ),
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
                preset = current.preset.copy(
                    builtin = presetBuiltin.takeIf { it.isNotBlank() },
                    file = presetFile.takeIf { it.isNotBlank() }
                ),
                logging = current.logging.copy(
                    level = logLevel,
                    file = logFile.takeIf { it.isNotBlank() }
                ),
                ui = current.ui.copy(
                    theme = uiTheme,
                    messageAlignment = messageAlignment,
                    messageMaxWidthRatio = messageMaxWidthRatio,
                    sendKeyMode = sendKeyMode,
                    lastOpenedSessionId = lastOpenedSessionId,
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

    public fun selectPreset(presetName: String, persist: Boolean) {
        val preset = agentPresets.firstOrNull { it.name == presetName }
        if (preset == null) {
            activePresetName = presetName
            presetBuiltin = presetName
            currentSessionId?.let { sessionId ->
                bindPresetForSession(sessionId = sessionId)
            }
            if (persist) {
                savePreferences()
            }
            return
        }
        applyPreset(preset)
        if (persist) {
            savePreferences()
        }
    }

    private fun applyPreset(preset: AgentPreset) {
        activePresetName = preset.name
        presetBuiltin = preset.name
        disabledTools = preset.disabledTools
        currentSessionId?.let { sessionId ->
            bindPresetForSession(sessionId = sessionId)
        }
    }

    private fun applyAgentPresetFromConfig() {
        val presetName = presetBuiltin.trim()
        if (presetName.isBlank()) {
            activePresetName = "build"
            currentSessionId?.let { sessionId ->
                bindPresetForSession(sessionId = sessionId)
            }
            return
        }
        val preset = agentPresets.firstOrNull { it.name == presetName }
        if (preset != null) {
            applyPreset(preset)
        } else {
            activePresetName = presetName
            currentSessionId?.let { sessionId ->
                bindPresetForSession(sessionId = sessionId)
            }
        }
    }

    private fun registerPresetHooks() {
        agentPresets.forEach { preset ->
            hookManager.registerPresetHooks(presetName = preset.name)
        }
    }

    private fun bindPresetForSession(sessionId: String) {
        val presetName = activePresetName.trim().ifBlank {
            presetBuiltin.trim().ifBlank { "build" }
        }
        hookManager.bindSessionPreset(
            sessionId = sessionId,
            presetName = presetName,
        )
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
                if (server.transportType() == McpTransportType.Http && !url.isNullOrBlank()) {
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
        return when (server.transportType()) {
            McpTransportType.Stdio -> {
                val command = server.command
                if (command.isNullOrBlank()) {
                    buildMcpTestError("MCP test (${name}): missing command")
                } else {
                    var process: Process? = null
                    try {
                        process = startMcpTestProcess(server = server, command = command)
                        val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                        val registry = McpToolRegistryProvider.fromTransport(
                            transport = transport,
                            name = name,
                            version = "1.0.0",
                        )
                        buildMcpTestSuccess(registry = registry)
                    } finally {
                        process?.destroy()
                    }
                }
            }

            McpTransportType.Http,
            McpTransportType.Sse,
            -> {
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

            McpTransportType.Unsupported -> {
                buildMcpTestError("MCP test (${name}): unsupported transport ${server.transport}")
            }
        }
    }

    private fun startMcpTestProcess(
        server: io.github.stream29.kode.config.api.McpServerConfig,
        command: String,
    ): Process {
        val args = server.args
        val processBuilder = ProcessBuilder(listOf(command) + args)
            .directory(resolveSessionWorkingDir(currentSessionWorkDir))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        server.env?.forEach { (key, value) ->
            processBuilder.environment()[key] = value
        }
        return processBuilder.start()
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
        return result.status.toHealthResult(message = result.message)
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
                val result = io.github.stream29.kode.scripting.eval(
                    script = script,
                    workingDir = resolveSessionWorkingDir(currentSessionWorkDir).absolutePath,
                )
                scriptOutput = result.toUiScriptOutput()
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
                val registry = when (server.transportType()) {
                    McpTransportType.Stdio -> {
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

                    McpTransportType.Http,
                    McpTransportType.Sse,
                    -> {
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

                    McpTransportType.Unsupported -> {
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

    private fun mapToLines(map: Map<String, String>?, separator: String): String {
        if (map.isNullOrEmpty()) {
            return ""
        }
        return map.entries.joinToString("\n") { (key, value) -> "$key$separator$value" }
    }

    private fun normalizeMessageAlignment(value: String): String {
        return MessageAlignmentPreference.fromValue(value).value
    }

    private fun normalizeSendKeyMode(value: String): String {
        return SendKeyModePreference.fromValue(value).value
    }

    private fun normalizeMessageWidthRatio(value: Float): Float {
        if (value.isNaN()) {
            return 0.9f
        }
        return value.coerceIn(0.5f, 1f)
    }

    private fun buildPreferencesSnapshot(): PreferencesSnapshot {
        return PreferencesSnapshot(
            defaultModelId = defaultModelId,
            defaultThinking = defaultThinking,
            appDataDir = appDataDir,
            defaultSessionDir = defaultSessionDir,
            skillsDir = skillsDir,
            presetBuiltin = presetBuiltin,
            presetFile = presetFile,
            logLevel = logLevel,
            logFile = logFile,
            uiTheme = uiTheme,
            lastOpenedSessionId = lastOpenedSessionId,
            messageAlignment = messageAlignment,
            messageMaxWidthRatio = messageMaxWidthRatio,
            sendKeyMode = sendKeyMode,
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
        val presetSpec = readPresetSpec()
        val basePrompt = presetSpec?.trim().takeUnless { it.isNullOrBlank() }
            ?: SessionAwareAgentFactory.SYSTEM_PROMPT
        val skillsSummary = buildSkillsSummary()
        return if (skillsSummary.isBlank()) {
            basePrompt
        } else {
            "$basePrompt\n\n## Available Skills\n$skillsSummary\n\nUse skills when appropriate."
        }
    }

    private fun readPresetSpec(): String? {
        val explicit = presetFile.trim()
        if (explicit.isNotBlank()) {
            val file = File(expandHome(explicit))
            if (file.isFile) {
                presetSpecPath = file.absolutePath
                return file.readText()
            }
        }

        val workDir = resolveSessionWorkingDir(currentSessionWorkDir)
        val projectAgents = File(workDir, "AGENTS.md")
        if (projectAgents.isFile) {
            presetSpecPath = projectAgents.absolutePath
            return projectAgents.readText()
        }

        val userAgents = File(resolveAppDataDir(), "AGENTS.md")
        if (userAgents.isFile) {
            presetSpecPath = userAgents.absolutePath
            return userAgents.readText()
        }

        presetSpecPath = ""
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

    public fun refreshPresetAndSkillsPreview() {
        val presetSpec = readPresetSpec()
        presetSpecPreview = presetSpec?.trim().orEmpty()
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

    public fun getProviderPresets(): List<ProviderPreset> {
        return providerPresets
    }

    public enum class McpTestStatus {
        Success,
        Error,
    }

    public enum class McpHealthStatus(
        public val label: String,
    ) {
        Unknown(label = "Unknown"),
        Checking(label = "Checking"),
        Healthy(label = "Healthy"),
        Unhealthy(label = "Unhealthy"),
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

    private fun McpTestStatus.toHealthResult(message: String): McpHealthResult {
        val healthStatus = when (this) {
            McpTestStatus.Success -> McpHealthStatus.Healthy
            McpTestStatus.Error -> McpHealthStatus.Unhealthy
        }
        return McpHealthResult(
            status = healthStatus,
            message = message,
        )
    }

    private fun io.github.stream29.kode.scripting.EvalResult.toUiScriptOutput(): String {
        return when (this) {
            is io.github.stream29.kode.scripting.EvalResult.Success -> {
                "Return: ${returnValue}\n\nStdout:\n${stdout}"
            }

            is io.github.stream29.kode.scripting.EvalResult.Failure -> {
                "Error: ${message}\n\nStdout:\n${stdout}"
            }
        }
    }


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
            roots.add(File(resolveAppDataDir(), "skills"))
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

        override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
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
            sessionParameters: SessionCreationParameters
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

    public fun quickSetupProvider(
        providerId: String,
        authMode: String,
        apiKey: String,
        baseUrlInput: String,
        addRecommendedModels: Boolean,
        connectOAuthNow: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preset = providerPresets.firstOrNull { it.id == providerId }
                    ?: throw IllegalArgumentException("Provider preset not found: $providerId")

                val normalizedAuthMode = normalizeAuthMode(authMode)
                if (!isAuthModeSupported(preset = preset, authMode = normalizedAuthMode)) {
                    throw IllegalArgumentException(
                        "Provider ${preset.displayName} does not support auth mode: $normalizedAuthMode"
                    )
                }

                val normalizedApiKey = apiKey.trim()
                if (normalizedAuthMode == AUTH_MODE_API_KEY && normalizedApiKey.isBlank()) {
                    throw IllegalArgumentException("API key is required for ${preset.displayName}")
                }

                val authId = generateUniqueId(preset.id, auths.map { it.id }.toSet())
                if (authId.isBlank()) {
                    throw IllegalArgumentException("Failed to generate auth id for ${preset.displayName}")
                }

                val authConfig = buildAuthFromPreset(
                    preset = preset,
                    authId = authId,
                    authMode = normalizedAuthMode,
                    apiKey = normalizedApiKey,
                    baseUrlInput = baseUrlInput,
                )

                val modelsToAdd = buildModelsFromPreset(
                    preset = preset,
                    authId = authId,
                    addRecommendedModels = addRecommendedModels,
                )
                val newAuths = auths + authConfig
                val newModels = models + modelsToAdd
                saveAuthAndModelConfig(newAuths = newAuths, newModels = newModels)

                auths = newAuths
                models = newModels
                if (activeModelId == null) {
                    activeModelId = modelsToAdd.firstOrNull()?.id
                }
                val addedModelCount = modelsToAdd.size
                addSystemMessage(
                    "Quick setup completed: ${preset.displayName} ($authId), added $addedModelCount model(s)"
                )
                if (connectOAuthNow && authConfig.auth is io.github.stream29.kode.config.api.LlmAuth.OAuth) {
                    startOAuthConnectJob(authId = authId, authOverride = authConfig)
                } else {
                    initializeAgentFactory()
                }
            } catch (e: Exception) {
                addSystemMessage("Quick setup failed: ${e.message}")
            }
        }
    }

    private fun isAuthModeSupported(preset: ProviderPreset, authMode: String): Boolean {
        return preset.authModes.any { mode -> providerAuthModeToConfigValue(mode) == authMode }
    }

    private fun buildAuthFromPreset(
        preset: ProviderPreset,
        authId: String,
        authMode: String,
        apiKey: String,
        baseUrlInput: String,
    ): LlmAuthConfig {
        val resolvedBaseUrl = normalizeBaseUrlInput(baseUrlInput, preset.defaultBaseUrl)
        val oauthAuthCodePreset = resolveOAuthAuthCodePresetForAuthMode(
            preset = preset,
            authMode = authMode,
        )
        val oauthDevicePreset = resolveOAuthDevicePresetForAuthMode(
            preset = preset,
            authMode = authMode,
        )
        val oauthConfig = if (authMode == AUTH_MODE_API_KEY) {
            null
        } else {
            if (oauthAuthCodePreset == null && oauthDevicePreset == null) {
                throw IllegalArgumentException(
                    "Provider ${preset.displayName} does not define OAuth preset for mode: $authMode"
                )
            }
            if (oauthDevicePreset != null) {
                io.github.stream29.kode.config.api.OAuthConfig.DeviceFlow(
                    storage = "file",
                    key = buildOAuthTokenStorageKey(authId = authId),
                    tokenEndpoint = oauthDevicePreset.tokenEndpoint,
                    clientId = oauthDevicePreset.clientId,
                    scopes = oauthDevicePreset.scopes,
                    tokenAdditionalParams = emptyMap(),
                    deviceFlowStrategy = oauthDevicePreset.strategy,
                    deviceAuthorizationEndpoint = oauthDevicePreset.deviceAuthorizationEndpoint,
                    deviceTokenEndpoint = oauthDevicePreset.deviceTokenEndpoint,
                    deviceVerificationUri = oauthDevicePreset.verificationUri,
                    deviceRedirectUri = oauthDevicePreset.redirectUri,
                )
            } else {
                requireNotNull(oauthAuthCodePreset) {
                    "Provider ${preset.displayName} does not define auth-code OAuth preset for mode: $authMode"
                }
                io.github.stream29.kode.config.api.OAuthConfig.AuthCodePkce(
                    storage = "file",
                    key = buildOAuthTokenStorageKey(authId = authId),
                    authorizationEndpoint = oauthAuthCodePreset.authorizationEndpoint,
                    tokenEndpoint = oauthAuthCodePreset.tokenEndpoint,
                    clientId = oauthAuthCodePreset.clientId,
                    scopes = oauthAuthCodePreset.scopes,
                    callbackUri = oauthAuthCodePreset.callbackUri,
                    authorizationAdditionalParams = oauthAuthCodePreset.authorizationAdditionalParams,
                    tokenAdditionalParams = oauthAuthCodePreset.tokenAdditionalParams,
                )
            }
        }

        val providerId = resolveProviderIdForPreset(presetId = preset.id)
        val auth = if (authMode == AUTH_MODE_API_KEY) {
            io.github.stream29.kode.config.api.LlmAuth.ApiKey(
                apiKey = apiKey,
                envKeys = preset.envKeys,
                baseUrl = resolvedBaseUrl,
                customHeaders = emptyMap(),
            )
        } else {
            io.github.stream29.kode.config.api.LlmAuth.OAuth(
                oauth = requireNotNull(oauthConfig) { "oauthConfig is null for authMode=$authMode" },
                baseUrl = resolvedBaseUrl,
                customHeaders = emptyMap(),
            )
        }

        return LlmAuthConfig(
            id = authId,
            providerId = providerId,
            name = preset.displayName.takeIf {
                shouldKeepPresetDisplayName(
                    providerId = providerId,
                    presetId = preset.id,
                )
            },
            auth = auth,
        )
    }

    private fun shouldKeepPresetDisplayName(providerId: String, presetId: String): Boolean {
        val normalizedProviderId = providerId.trim().lowercase()
        val normalizedPresetId = presetId.trim().lowercase()
        if (normalizedProviderId !in CUSTOM_NAMED_PROVIDER_IDS) {
            return false
        }
        return normalizedPresetId !in CUSTOM_NAMED_PROVIDER_IDS
    }

    private fun resolveProviderIdForPreset(presetId: String): String {
        val normalizedPresetId = presetId.trim()
        require(normalizedPresetId.isNotBlank()) { "Provider preset id is blank" }
        return normalizedPresetId
    }

    private fun resolveOAuthAuthCodePresetForAuthMode(
        preset: ProviderPreset,
        authMode: String,
    ): ProviderOAuthAuthCodePkcePreset? {
        return preset.oauthAuthCodePkceByMode.entries
            .firstOrNull { entry -> providerAuthModeToConfigValue(entry.key) == authMode }
            ?.value
    }

    private fun resolveOAuthDevicePresetForAuthMode(
        preset: ProviderPreset,
        authMode: String,
    ): ProviderOAuthDeviceFlowPreset? {
        return preset.oauthDeviceFlowByMode.entries
            .firstOrNull { entry -> providerAuthModeToConfigValue(entry.key) == authMode }
            ?.value
    }

    private fun buildOAuthTokenStorageKey(authId: String): String {
        return resolveAppDataDir()
            .resolve("oauth")
            .resolve("${authId.trim()}.oauth.json")
            .absolutePath
    }

    public fun generateDefaultOAuthTokenStorageKey(authId: String): String {
        val normalized = authId.trim().ifBlank { "auth" }
        return buildOAuthTokenStorageKey(authId = normalized)
    }

    private fun buildModelsFromPreset(
        preset: ProviderPreset,
        authId: String,
        addRecommendedModels: Boolean,
    ): List<LlmModelConfig> {
        val selectedModels = selectPresetModels(preset = preset, addRecommendedModels = addRecommendedModels)
        val existingIds = models.map { model -> model.id }.toMutableSet()
        return selectedModels.map { presetModel ->
            val suggestedId = generateUniqueId("$authId-${presetModel.id}", existingIds)
            val resolvedId = suggestedId.ifBlank {
                generateUniqueId("${preset.id}-${presetModel.id}", existingIds)
            }
            if (resolvedId.isBlank()) {
                throw IllegalArgumentException("Failed to generate model id for ${presetModel.id}")
            }
            existingIds += resolvedId
            LlmModelConfig(
                id = resolvedId,
                authId = authId,
                model = presetModel.id,
                displayName = null,
                maxContextSize = null,
                capabilities = null,
            )
        }
    }

    private fun selectPresetModels(
        preset: ProviderPreset,
        addRecommendedModels: Boolean,
    ): List<LLModel> {
        if (preset.models.isEmpty()) {
            return emptyList()
        }
        if (!addRecommendedModels) {
            return listOf(preset.models.first())
        }
        return preset.models.take(2)
    }

    private fun normalizeBaseUrlInput(input: String, defaultValue: String?): String? {
        return input.trim().ifBlank { defaultValue.orEmpty() }.takeIf { value -> value.isNotBlank() }
    }

    private fun normalizeAuthMode(mode: String): String {
        val normalized = mode.trim().lowercase()
        return if (normalized.isBlank()) {
            AUTH_MODE_API_KEY
        } else {
            normalized
        }
    }

    private fun providerAuthModeToConfigValue(mode: ProviderAuthMode): String {
        return PROVIDER_AUTH_MODE_TO_CONFIG_VALUE[mode] ?: AUTH_MODE_API_KEY
    }

    private suspend fun saveAuthAndModelConfig(newAuths: List<LlmAuthConfig>, newModels: List<LlmModelConfig>) {
        validateAuthClientProviderScope(newAuths)
        validateModelProviderSupport(authConfigs = newAuths, modelConfigs = newModels)
        val current = configManager.load()
        val updated = current.copy(
            auths = newAuths,
            models = newModels,
        )
        configManager.save(updated)
    }

    private fun validateModelProviderSupport(authConfigs: List<LlmAuthConfig>, modelConfigs: List<LlmModelConfig>) {
        val authById = authConfigs.associateBy { auth -> auth.id }
        modelConfigs.forEach { modelConfig ->
            val auth = authById[modelConfig.authId]
                ?: throw IllegalArgumentException("Model '${modelConfig.id}' references missing auth '${modelConfig.authId}'")
            val providerId = auth.providerId.trim().lowercase()
            val params = modelConfig.params ?: return@forEach

            if (!params.supportsProvider(providerId)) {
                throw IllegalArgumentException(
                    "Model '${modelConfig.id}' params '${params.summaryText()}' do not match provider '$providerId'"
                )
            }

            if (params is LlmModelParamsConfig.OpenAiFamily) {
                val endpointSupport = resolveOpenAiEndpointSupport(
                    providerId = providerId,
                    modelConfig = modelConfig,
                )
                if (endpointSupport.constrained && !endpointSupport.supports(params.endpoint)) {
                    throw IllegalArgumentException(
                        "Model '${modelConfig.id}' (${modelConfig.model}) does not support ${params.endpoint.asConfigValue()} endpoint"
                    )
                }
            }
        }
    }

    private fun resolveOpenAiEndpointSupport(
        providerId: String,
        modelConfig: LlmModelConfig,
    ): OpenAiEndpointSupport {
        val fromConfiguredCapabilities = resolveOpenAiEndpointSupportFromConfiguredCapabilities(
            capabilities = modelConfig.capabilities,
        )
        if (fromConfiguredCapabilities != null) {
            return fromConfiguredCapabilities
        }

        val preset = providerPresets.firstOrNull { it.id == providerId }
            ?: return OpenAiEndpointSupport.unspecified()
        val presetModel = preset.models.firstOrNull { model -> model.id == modelConfig.model }
            ?: return OpenAiEndpointSupport.unspecified()

        val supportsChat = presetModel.capabilities.contains(ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Completions)
        val supportsResponses = presetModel.capabilities.contains(ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Responses)
        val hasEndpointCapability = supportsChat || supportsResponses
        if (!hasEndpointCapability) {
            return OpenAiEndpointSupport.unspecified()
        }

        return OpenAiEndpointSupport(
            supportsChat = supportsChat,
            supportsResponses = supportsResponses,
            constrained = true,
        )
    }

    private fun resolveOpenAiEndpointSupportFromConfiguredCapabilities(capabilities: List<String>?): OpenAiEndpointSupport? {
        val normalized = capabilities
            ?.map { capability -> capability.trim().lowercase() }
            ?.filter { capability -> capability.isNotBlank() }
            ?.toSet()
            ?: return null
        if (normalized.isEmpty()) {
            return null
        }

        val supportsChat = normalized.any { capability -> capability in OPENAI_CHAT_CAPABILITY_ALIASES }
        val supportsResponses = normalized.any { capability -> capability in OPENAI_RESPONSES_CAPABILITY_ALIASES }
        if (!supportsChat && !supportsResponses) {
            return null
        }

        return OpenAiEndpointSupport(
            supportsChat = supportsChat,
            supportsResponses = supportsResponses,
            constrained = true,
        )
    }

    private data class OpenAiEndpointSupport(
        val supportsChat: Boolean,
        val supportsResponses: Boolean,
        val constrained: Boolean,
    ) {
        fun supports(endpoint: OpenAiEndpoint): Boolean {
            return when (endpoint) {
                OpenAiEndpoint.Chat -> supportsChat
                OpenAiEndpoint.Responses -> supportsResponses
            }
        }

        companion object {
            fun unspecified(): OpenAiEndpointSupport {
                return OpenAiEndpointSupport(
                    supportsChat = true,
                    supportsResponses = true,
                    constrained = false,
                )
            }
        }
    }

    private fun OpenAiEndpoint.asConfigValue(): String {
        return when (this) {
            OpenAiEndpoint.Chat -> "chat"
            OpenAiEndpoint.Responses -> "responses"
        }
    }

    private fun validateAuthClientProviderScope(authConfigs: List<LlmAuthConfig>) {
        val authByProviderKey = linkedMapOf<ai.koog.prompt.llm.LLMProvider, LlmAuthConfig>()
        authConfigs.forEach { auth ->
            val providerId = auth.providerId.trim()
            require(providerId.isNotBlank()) { "providerId is blank for auth '${auth.id}'" }
            val provider = BuiltinLlmProviderRegistry.findProvider(providerId)
                ?: throw IllegalArgumentException("Provider not found: $providerId (authId=${auth.id})")

            val existing = authByProviderKey[provider.llmProvider]
            if (existing != null && existing.id != auth.id) {
                throw IllegalArgumentException(
                    "Auth '${auth.id}' conflicts with '${existing.id}': " +
                            "${providerId} and ${existing.providerId} share the same runtime client scope (${provider.llmProvider.id}). " +
                            "Use only one auth per provider scope."
                )
            }
            authByProviderKey[provider.llmProvider] = auth
        }
    }

    private suspend fun ensureOAuthCredentialsUpToDate() {
        auths.forEach { auth ->
            val oauth = auth.auth.oauthConfigOrNull()
                ?: return@forEach
            oauthCredentialManager.ensureValidAccessToken(authId = auth.id, oauth = oauth)
        }
    }

    private suspend fun refreshOAuthStatusSnapshot() {
        oauthStatusByAuthId = auths
            .mapNotNull { auth ->
                val oauth = auth.auth.oauthConfigOrNull()
                    ?: return@mapNotNull null
                val status = runCatching {
                    oauthCredentialManager.inspectStatus(
                        authId = auth.id,
                        oauth = oauth,
                    )
                }.onFailure { error ->
                    log("Failed to inspect OAuth status for ${auth.id}: ${error.message}")
                }.getOrNull()
                auth.id to mapOAuthStatusUi(status)
            }
            .toMap()
    }

    private fun mapOAuthStatusUi(status: OAuthCredentialStatus?): OAuthStatusUi {
        if (status == null) {
            return OAuthStatusUi(
                connected = false,
                expired = false,
                hasRefreshToken = false,
                expiresAtEpochSecond = null,
                summary = "unknown",
                inProgress = false,
            )
        }

        val summary = when {
            !status.connected -> "not connected"
            status.expired && status.hasRefreshToken -> "expired (refresh available)"
            status.expired -> "expired"
            status.expiresAtEpochSecond == null -> "connected"
            else -> "connected (exp=${status.expiresAtEpochSecond})"
        }
        return OAuthStatusUi(
            connected = status.connected,
            expired = status.expired,
            hasRefreshToken = status.hasRefreshToken,
            expiresAtEpochSecond = status.expiresAtEpochSecond,
            summary = summary,
            inProgress = false,
        )
    }

    private fun markOAuthProgress(authId: String, summary: String) {
        val normalized = summary.trim().ifBlank { "processing" }
        val current = oauthStatusByAuthId[authId]
        val next = if (current == null) {
            OAuthStatusUi(
                connected = false,
                expired = false,
                hasRefreshToken = false,
                expiresAtEpochSecond = null,
                summary = normalized,
                inProgress = true,
            )
        } else {
            current.copy(
                summary = normalized,
                inProgress = true,
            )
        }
        oauthStatusByAuthId = oauthStatusByAuthId + (authId to next)
    }

    private fun markOAuthFailure(authId: String, summary: String) {
        val normalized = summary.trim().ifBlank { "failed" }
        val current = oauthStatusByAuthId[authId]
        val next = if (current == null) {
            OAuthStatusUi(
                connected = false,
                expired = false,
                hasRefreshToken = false,
                expiresAtEpochSecond = null,
                summary = normalized,
                inProgress = false,
            )
        } else {
            current.copy(
                summary = normalized,
                inProgress = false,
            )
        }
        oauthStatusByAuthId = oauthStatusByAuthId + (authId to next)
    }

    public fun connectOAuth(authId: String) {
        startOAuthConnectJob(authId = authId, authOverride = null)
    }

    private fun startOAuthConnectJob(authId: String, authOverride: LlmAuthConfig?) {
        val existing = oauthJobs[authId]
        if (existing?.isActive == true) {
            addSystemMessage("OAuth is already running for $authId")
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO) {
            markOAuthProgress(authId = authId, summary = "connecting")
            try {
                val auth = authOverride ?: auths.firstOrNull { config -> config.id == authId }
                    ?: throw IllegalArgumentException("Auth not found: $authId")
                connectOAuthInternal(auth = auth)
                initializeAgentFactory()
            } catch (e: CancellationException) {
                addSystemMessage("OAuth connect cancelled for $authId")
                markOAuthFailure(authId = authId, summary = "cancelled")
            } catch (e: Exception) {
                addSystemMessage("OAuth connect failed: ${e.message}")
                markOAuthFailure(authId = authId, summary = "connect failed: ${e.message}")
            } finally {
                oauthJobs.remove(authId)
            }
        }
        oauthJobs[authId] = job
    }

    public fun cancelOAuth(authId: String) {
        val job = oauthJobs[authId]
        if (job == null || !job.isActive) {
            addSystemMessage("No running OAuth operation for $authId")
            return
        }
        markOAuthProgress(authId = authId, summary = "cancelling")
        job.cancel(cause = CancellationException("Cancelled by user"))
    }

    private suspend fun connectOAuthInternal(auth: LlmAuthConfig) {
        val rawOauth = auth.auth.oauthConfigOrNull()
            ?: throw IllegalArgumentException("Auth ${auth.id} does not have OAuth config")
        val oauth = normalizeOAuthConfigForConnect(auth = auth, oauth = rawOauth)
        if (!oauth.canInteractiveConnect(providerId = auth.providerId)) {
            throw IllegalArgumentException("OAuth config for ${auth.id} is incomplete")
        }
        val authMode = resolveOAuthConnectAuthMode(auth)
        val token = oauthCredentialManager.connect(
            authId = auth.id,
            authMode = authMode,
            oauth = oauth,
            openBrowser = { url ->
                if (Desktop.isDesktopSupported()) {
                    runCatching {
                        Desktop.getDesktop().browse(java.net.URI.create(url))
                    }.onFailure {
                        addSystemMessage("Failed to open browser automatically: ${it.message}")
                        addSystemMessage("Open this URL in your browser to continue OAuth: $url")
                    }
                } else {
                    addSystemMessage("Open this URL in your browser to continue OAuth: $url")
                }
            },
            onProgress = { message ->
                addSystemMessage("OAuth ${auth.id}: $message")
                markOAuthProgress(authId = auth.id, summary = message)
            },
        )
        val expiresAt = token.expiresAtEpochSecond
        val expiresHint = if (expiresAt == null) {
            "no expiry"
        } else {
            "expires at epoch=$expiresAt"
        }
        addSystemMessage("OAuth connected for ${resolveProviderDisplayName(auth.providerId)} (${auth.id}), $expiresHint")
        refreshOAuthStatusSnapshot()
    }

    private fun resolveOAuthConnectAuthMode(auth: LlmAuthConfig): String {
        val providerId = auth.providerId.trim()
        val oauth = auth.auth.oauthConfigOrNull()
        if (oauth?.isDeviceFlow(providerId = providerId) == true) {
            return "oauth_device"
        }
        return OAUTH_CONNECT_AUTH_MODE_BY_PROVIDER_ID[providerId] ?: "oauth_subscription"
    }

    private fun resolveProviderDisplayName(providerId: String): String {
        val normalized = providerId.trim()
        return providerPresets.firstOrNull { preset -> preset.id == normalized }?.displayName ?: normalized
    }

    private fun normalizeOAuthConfigForConnect(
        auth: LlmAuthConfig,
        oauth: io.github.stream29.kode.config.api.OAuthConfig,
    ): io.github.stream29.kode.config.api.OAuthConfig {
        if (!OPENAI_SUBSCRIPTION_BROWSER_PROVIDER_IDS.contains(auth.providerId.trim())) {
            return oauth
        }

        if (oauth !is io.github.stream29.kode.config.api.OAuthConfig.AuthCodePkce) {
            return oauth
        }

        val callbackUri = normalizeOpenAiSubscriptionCallbackUri(oauth.callbackUri)
        val authorizationAdditionalParams = if (oauth.authorizationAdditionalParams.isNotEmpty()) {
            oauth.authorizationAdditionalParams
        } else {
            OPENAI_SUBSCRIPTION_AUTHORIZATION_ADDITIONAL_PARAMS
        }
        return oauth.copy(
            callbackUri = callbackUri,
            authorizationAdditionalParams = authorizationAdditionalParams,
        )
    }

    private fun normalizeOpenAiSubscriptionCallbackUri(current: String?): String {
        val normalized = current?.trim().orEmpty()
        if (normalized.isBlank()) {
            return OPENAI_SUBSCRIPTION_CALLBACK_URI
        }
        if (normalized.contains("{port}") || normalized.contains("/oauth/callback")) {
            return OPENAI_SUBSCRIPTION_CALLBACK_URI
        }
        if (normalized.startsWith("http://127.0.0.1:1455/auth/callback")) {
            return normalized.replace("http://127.0.0.1", "http://localhost")
        }
        return normalized
    }

    public fun disconnectOAuth(authId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            markOAuthProgress(authId = authId, summary = "disconnecting")
            try {
                val auth = auths.firstOrNull { config -> config.id == authId }
                    ?: throw IllegalArgumentException("Auth not found: $authId")
                val running = oauthJobs[authId]
                if (running?.isActive == true) {
                    running.cancel(cause = CancellationException("Cancelled due to disconnect"))
                    oauthJobs.remove(authId)
                }
                val oauth = auth.auth.oauthConfigOrNull()
                    ?: throw IllegalArgumentException("Auth $authId does not have OAuth config")
                oauthCredentialManager.disconnect(oauth = oauth)
                addSystemMessage("OAuth token removed for ${resolveProviderDisplayName(auth.providerId)} (${auth.id})")
                refreshOAuthStatusSnapshot()
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("OAuth disconnect failed: ${e.message}")
                markOAuthFailure(authId = authId, summary = "disconnect failed: ${e.message}")
            }
        }
    }

    public fun refreshOAuth(authId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            markOAuthProgress(authId = authId, summary = "refreshing")
            try {
                val auth = auths.firstOrNull { config -> config.id == authId }
                    ?: throw IllegalArgumentException("Auth not found: $authId")
                val oauth = auth.auth.oauthConfigOrNull()
                    ?: throw IllegalArgumentException("Auth $authId does not have OAuth config")
                val token = oauthCredentialManager.ensureValidAccessToken(
                    authId = auth.id,
                    oauth = oauth,
                )
                if (token.isNullOrBlank()) {
                    addSystemMessage("OAuth token unavailable for ${resolveProviderDisplayName(auth.providerId)} (${auth.id})")
                } else {
                    addSystemMessage("OAuth token ready for ${resolveProviderDisplayName(auth.providerId)} (${auth.id})")
                }
                refreshOAuthStatusSnapshot()
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("OAuth refresh failed: ${e.message}")
                markOAuthFailure(authId = authId, summary = "refresh failed: ${e.message}")
            }
        }
    }

    public fun addAuth(auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuths = auths + auth
                saveAuthAndModelConfig(newAuths = newAuths, newModels = models)
                auths = newAuths
                addSystemMessage("Added auth provider: ${resolveProviderDisplayName(auth.providerId)} (${auth.id})")
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
                saveAuthAndModelConfig(newAuths = newAuths, newModels = models)
                auths = newAuths
                addSystemMessage("Updated auth provider: ${resolveProviderDisplayName(auth.providerId)} (${auth.id})")
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
                saveAuthAndModelConfig(newAuths = newAuths, newModels = models)
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
                saveAuthAndModelConfig(newAuths = auths, newModels = newModels)
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
                saveAuthAndModelConfig(newAuths = auths, newModels = newModels)
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
                saveAuthAndModelConfig(newAuths = auths, newModels = newModels)
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

    private companion object {
        private const val AUTH_MODE_API_KEY: String = "api_key"
        private val PROVIDER_AUTH_MODE_TO_CONFIG_VALUE: Map<ProviderAuthMode, String> = mapOf(
            ProviderAuthMode.ApiKey to AUTH_MODE_API_KEY,
            ProviderAuthMode.OAuthSubscription to "oauth_subscription",
            ProviderAuthMode.OAuthDevice to "oauth_device",
            ProviderAuthMode.CloudCredentialChain to "cloud_credential_chain",
            ProviderAuthMode.WellKnown to "well_known",
        )
        private val OAUTH_CONNECT_AUTH_MODE_BY_PROVIDER_ID: Map<String, String> = mapOf(
            "openai-subscription-device" to "oauth_device",
            "openai-subscription-browser" to "oauth_subscription",
        )
        private val OPENAI_SUBSCRIPTION_BROWSER_PROVIDER_IDS: Set<String> = setOf(
            "openai-subscription-browser",
        )
        private val CUSTOM_NAMED_PROVIDER_IDS: Set<String> = setOf(
            "openai-compatible",
        )
        private const val OPENAI_SUBSCRIPTION_CALLBACK_URI: String = "http://localhost:1455/auth/callback"
        private val OPENAI_SUBSCRIPTION_AUTHORIZATION_ADDITIONAL_PARAMS: Map<String, String> = mapOf(
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "originator" to "opencode",
        )
        private val OPENAI_CHAT_CAPABILITY_ALIASES: Set<String> = setOf(
            "openai_completions",
            "openai_endpoint_completions",
        )
        private val OPENAI_RESPONSES_CAPABILITY_ALIASES: Set<String> = setOf(
            "openai_responses",
            "openai_endpoint_responses",
        )
    }

    override fun onCleared() {
        super.onCleared()
        unbindSessionFlows()
    }
}

public data class AgentPreset(
    val name: String,
    val description: String,
    val disabledTools: Set<String>,
)

public data class SessionUiState(
    val taskInput: String = "",
    val messages: List<SessionMessage> = emptyList(),
    val currentSessionId: String? = null,
    val currentSessionWorkDir: String = "",
    val showNewSessionDialog: Boolean = false,
    val showSessionDirDialog: Boolean = false,
    val showContinueRecoveryDialog: Boolean = false,
    val continueRecoveryToolName: String = "",
    val continueRecoveryToolCallId: String = "",
    val newSessionDirInput: String = "",
    val sessionDirDraft: String = "",
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val currentTask: String = "",
    val isGeneratingSessionTitle: Boolean = false,
)

public data class MainChromeUiState(
    val currentPage: io.github.stream29.kode.app.view.AppPage = io.github.stream29.kode.app.view.AppPage.Chat,
    val showConfigEditor: Boolean = false,
    val uiTheme: String = "dark",
    val toasts: List<UiToast> = emptyList(),
)

public data class ChatPageUiState(
    val sessionSummaries: List<SessionSummary> = emptyList(),
    val messageAlignment: String = "left",
    val messageMaxWidthRatio: Float = 0.9f,
    val sendKeyMode: String = "ctrl_or_cmd_enter_send",
    val agentPresets: List<AgentPreset> = emptyList(),
    val activePresetName: String = "build",
    val models: List<LlmModelConfig> = emptyList(),
    val auths: List<LlmAuthConfig> = emptyList(),
    val activeModelId: String? = null,
)

public data class SessionsPageUiState(
    val sessionSummaries: List<SessionSummary> = emptyList(),
    val sessionSearchQuery: String = "",
    val sessionStatusFilter: SessionStatusFilter = SessionStatusFilter.ALL,
)

public data class ToolsPageUiState(
    val disabledTools: Set<String> = emptySet(),
    val toolLogs: List<String> = emptyList(),
)

public data class McpPageUiState(
    val mcpToolTimeoutMs: Int = 60000,
    val mcpServers: Map<String, io.github.stream29.kode.config.api.McpServerConfig> = emptyMap(),
    val mcpTestResults: Map<String, MainViewModel.McpTestResult> = emptyMap(),
    val mcpTestsInFlight: Set<String> = emptySet(),
    val mcpHealthResults: Map<String, MainViewModel.McpHealthResult> = emptyMap(),
)

public data class AcpPageUiState(
    val acpHost: String = "127.0.0.1",
    val acpPort: Int = 5494,
    val acpRunning: Boolean = false,
    val acpLogs: List<String> = emptyList(),
)

public data class TerminalPageUiState(
    val terminalCommand: String = "",
    val terminalOutput: String = "",
    val terminalRunning: Boolean = false,
    val scriptContent: String = "",
    val scriptOutput: String = "",
    val scriptRunning: Boolean = false,
)

public data class WebPageUiState(
    val webUrl: String = "",
    val webContent: String = "",
    val webLoading: Boolean = false,
)

public data class InfoPageUiState(
    val presetSpecPath: String = "",
    val presetSpecPreview: String = "",
    val skillsPreview: List<String> = emptyList(),
    val modelsCount: Int = 0,
    val authCount: Int = 0,
    val mcpServerCount: Int = 0,
    val disabledTools: Set<String> = emptySet(),
    val acpRunning: Boolean = false,
)

public data class ConfigEditorUiState(
    val configText: String = "",
    val configError: String? = null,
)

public data class AppUiState(
    val currentPage: io.github.stream29.kode.app.view.AppPage = io.github.stream29.kode.app.view.AppPage.Chat,
    val showSessionManager: Boolean = false,
    val showConfigEditor: Boolean = false,
    val showSettings: Boolean = false,
    val sessionSummaries: List<SessionSummary> = emptyList(),
    val sessionSearchQuery: String = "",
    val sessionStatusFilter: SessionStatusFilter = SessionStatusFilter.ALL,
    val configText: String = "",
    val configError: String? = null,
    val auths: List<LlmAuthConfig> = emptyList(),
    val oauthStatusByAuthId: Map<String, OAuthStatusUi> = emptyMap(),
    val models: List<LlmModelConfig> = emptyList(),
    val activeModelId: String? = null,
    val defaultModelId: String? = null,
    val defaultThinking: Boolean = false,
    val appDataDir: String = "~/.kode/",
    val defaultSessionDir: String = "",
    val maxStepsPerTurn: Int = 100,
    val maxRetriesPerStep: Int = 3,
    val maxRalphIterations: Int = 0,
    val reservedContextSize: Int = 50000,
    val skillsDir: String = "",
    val presetBuiltin: String = "",
    val presetFile: String = "",
    val logLevel: String = "info",
    val logFile: String = "",
    val uiTheme: String = "dark",
    val lastOpenedSessionId: String? = null,
    val messageAlignment: String = "left",
    val messageMaxWidthRatio: Float = 0.9f,
    val sendKeyMode: String = "ctrl_or_cmd_enter_send",
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
    val presetSpecPath: String = "",
    val presetSpecPreview: String = "",
    val skillsPreview: List<String> = emptyList(),
    val activePresetName: String = "build",
    val agentPresets: List<AgentPreset> = emptyList(),
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
    val toasts: List<UiToast> = emptyList(),
    val disabledTools: Set<String> = emptySet(),
    val toolLogs: List<String> = emptyList(),
    val autoSaveSessions: Boolean = true,
    val temperature: Float = 0.3f,
)

public data class OAuthStatusUi(
    val connected: Boolean,
    val expired: Boolean,
    val hasRefreshToken: Boolean,
    val expiresAtEpochSecond: Long?,
    val summary: String,
    val inProgress: Boolean,
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

private data class SessionSearchHit(
    val summary: SessionSummary,
    val hits: Int,
)

private data class PreferencesSnapshot(
    val defaultModelId: String?,
    val defaultThinking: Boolean,
    val appDataDir: String,
    val defaultSessionDir: String,
    val skillsDir: String,
    val presetBuiltin: String,
    val presetFile: String,
    val logLevel: String,
    val logFile: String,
    val uiTheme: String,
    val lastOpenedSessionId: String?,
    val messageAlignment: String,
    val messageMaxWidthRatio: Float,
    val sendKeyMode: String,
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
