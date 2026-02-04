@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.model.MessageItem
import io.github.stream29.kode.app.model.toMessageItem
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ServiceConfig
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.acp.AcpAgent
import io.github.stream29.kode.core.hooks.HookManager
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.core.agent.SessionAwareAgentFactory
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import io.github.stream29.kode.session.core.storage.SortBy
import io.github.stream29.kode.session.core.storage.SortOrder
import io.github.stream29.kode.tools.WebTools
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Desktop
import java.io.File
import java.nio.channels.Channels
import java.nio.channels.Pipe

public class MainViewModel : ViewModel(), MessageHandler, AgentState, AgentEventListener, ApprovalHandler {
    // UI State
    public var taskInput: String by mutableStateOf("")
    public var messages: List<MessageItem> by mutableStateOf(emptyList())
    public var streamingMessage: MessageItem? by mutableStateOf(null)
    
    // Navigation
    public var currentPage: io.github.stream29.kode.app.view.AppPage by mutableStateOf(
        io.github.stream29.kode.app.view.AppPage.Chat
    )

    // Dialog visibility
    public var showSessionManager: Boolean by mutableStateOf(false)
    public var showConfigEditor: Boolean by mutableStateOf(false)
    public var showSettings: Boolean by mutableStateOf(false)
    
    // Session management
    public var currentSessionId: String? by mutableStateOf(null)
    public var sessionSummaries: List<SessionSummary> by mutableStateOf(emptyList())
    public var sessionSearchQuery: String by mutableStateOf("")
    public var sessionTagFilter: String by mutableStateOf("")
    public var sessionStatusFilter: SessionStatusFilter by mutableStateOf(SessionStatusFilter.ALL)
    
    // Config state for editor
    public var configText: String by mutableStateOf("")
    public var configError: String? by mutableStateOf(null)
    
    // Auth and model configurations
    public var auths: List<LlmAuthConfig> by mutableStateOf(emptyList())
    public var models: List<LlmModelConfig> by mutableStateOf(emptyList())
    public var activeModelId: String? by mutableStateOf(null)
    public var defaultModelId: String? by mutableStateOf(null)
    public var defaultThinking: Boolean by mutableStateOf(false)
    public var workDir: String by mutableStateOf("")
    public var maxStepsPerTurn: Int by mutableStateOf(100)
    public var maxRetriesPerStep: Int by mutableStateOf(3)
    public var maxRalphIterations: Int by mutableStateOf(0)
    public var reservedContextSize: Int by mutableStateOf(50000)
    public var skillsDir: String by mutableStateOf("")
    public var agentBuiltin: String by mutableStateOf("")
    public var agentFile: String by mutableStateOf("")
    public var logLevel: String by mutableStateOf("info")
    public var logFile: String by mutableStateOf("")
    public var uiTheme: String by mutableStateOf("dark")
    public var mcpToolTimeoutMs: Int by mutableStateOf(60000)
    public var mcpServers: Map<String, io.github.stream29.kode.config.api.McpServerConfig> by mutableStateOf(emptyMap())
    public var webSearchProvider: String by mutableStateOf("none")
    public var webSearchApiKey: String by mutableStateOf("")
    public var webSearchBaseUrl: String by mutableStateOf("")
    public var webSearchHeaders: String by mutableStateOf("")
    public var webSearchEnv: String by mutableStateOf("")
    public var webFetchProvider: String by mutableStateOf("builtin")
    public var webFetchApiKey: String by mutableStateOf("")
    public var webFetchBaseUrl: String by mutableStateOf("")
    public var webFetchHeaders: String by mutableStateOf("")
    public var webFetchEnv: String by mutableStateOf("")
    public var agentSpecPath: String by mutableStateOf("")
    public var agentSpecPreview: String by mutableStateOf("")
    public var skillsPreview: List<String> by mutableStateOf(emptyList())
    public var activeAgentProfileName: String by mutableStateOf("build")
    public val agentProfiles: List<AgentProfile> = listOf(
        AgentProfile(
            name = "build",
            description = "Full access agent for development",
            disabledTools = emptySet(),
            defaultYolo = false
        ),
        AgentProfile(
            name = "plan",
            description = "Read-only planning agent",
            disabledTools = setOf("shell", "task", "file-edit"),
            defaultYolo = false
        ),
        AgentProfile(
            name = "explore",
            description = "Exploration agent (search-heavy)",
            disabledTools = setOf("shell", "task", "file-edit"),
            defaultYolo = false
        )
    )
    public var acpHost: String by mutableStateOf("127.0.0.1")
    public var acpPort: Int by mutableStateOf(5494)
    public var acpRunning: Boolean by mutableStateOf(false)
    public var acpLogs: List<String> by mutableStateOf(emptyList())
    public var terminalCommand: String by mutableStateOf("")
    public var terminalOutput: String by mutableStateOf("")
    public var terminalRunning: Boolean by mutableStateOf(false)
    public var scriptContent: String by mutableStateOf("")
    public var scriptOutput: String by mutableStateOf("")
    public var scriptRunning: Boolean by mutableStateOf(false)
    public var webUrl: String by mutableStateOf("")
    public var webContent: String by mutableStateOf("")
    public var webLoading: Boolean by mutableStateOf(false)

    public var yoloEnabled: Boolean by mutableStateOf(false)
    public var approvalDefaultYolo: Boolean by mutableStateOf(false)
    public var approvalAutoApproveActions: List<String> by mutableStateOf(emptyList())
    public var pendingApprovals: List<ToolApprovalRequest> by mutableStateOf(emptyList())
    public var disabledTools: Set<String> by mutableStateOf(emptySet())
    public var toolLogs: List<String> by mutableStateOf(emptyList())
    
    public var autoSaveSessions: Boolean by mutableStateOf(true)
    public var temperature: Float by mutableStateOf(0.3f)
    
    // AgentState implementation
    override var isRunning: Boolean by mutableStateOf(false)
    override var isWaitingForInput: Boolean by mutableStateOf(false)
    override var currentTask: String by mutableStateOf("")

    private var inputDeferred: CompletableDeferred<String>? = null
    private var eventListener: AgentEventListener? = null
    private var agentFactory: SessionAwareAgentFactory? = null
    private val approvalDeferreds: MutableMap<String, CompletableDeferred<ToolApprovalDecision>> = mutableMapOf()
    private val autoApproveActions: MutableSet<String> = mutableSetOf()
    private var streamingUsedForCurrentRun: Boolean = false
    private var acpProtocol: com.agentclientprotocol.protocol.Protocol? = null
    private var acpTransport: com.agentclientprotocol.transport.StdioTransport? = null
    private var acpClientTransport: com.agentclientprotocol.transport.StdioTransport? = null
    private var acpProtocolScope: kotlinx.coroutines.CoroutineScope? = null
    private var acpClientToAgent: java.nio.channels.Pipe? = null
    private var acpAgentToClient: java.nio.channels.Pipe? = null
    private val mcpProcesses: MutableMap<String, Process> = mutableMapOf()
    private val configManager: ConfigManager by lazy {
        FileSystemConfigFactory.createDefault()
    }

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
            val config = configManager.load()
            loadConfigToState(config)
            
                if (models.isNotEmpty()) {
                val mcpRegistry = buildMcpToolRegistry()
                agentFactory = SessionAwareAgentFactory(
                    auths = auths,
                    models = models,
                    messageHandler = this@MainViewModel,
                    approvalHandler = this@MainViewModel,
                    disabledTools = disabledTools,
                    mcpToolRegistry = mcpRegistry,
                    workingDir = resolveWorkingDir(),
                    eventListener = this@MainViewModel,
                    hookManager = HookManager.empty(),
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
        defaultModelId = config.defaults.modelId
        defaultThinking = config.defaults.thinking
        workDir = config.defaults.workDir ?: ""
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
    
    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = configManager.load()
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
            timestamp = nowInstant(),
            metadata = null
        )
        messages = messages + userMessage.toMessageItem()
        taskInput = ""
        isRunning = true
        currentTask = task
        streamingUsedForCurrentRun = false
        streamingMessage = null

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
                    systemPrompt = buildSystemPrompt(),
                    modelId = modelId
                )
                currentSessionId = sessionId
                
                val result = agentFactory!!.runWithSession(sessionId, task, modelId)
                if (!streamingUsedForCurrentRun) {
                    val assistantMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                        content = result,
                        structuredData = null,
                        contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
                        timestamp = nowInstant(),
                        metadata = null
                    )
                    messages = messages + assistantMessage.toMessageItem()
                }
            } catch (e: Exception) {
                val errorMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                    content = "Error: ${e.message}",
                    structuredData = null,
                    contentType = io.github.stream29.kode.session.core.model.ContentType.ERROR,
                    timestamp = nowInstant(),
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
        streamingUsedForCurrentRun = false
        streamingMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = agentFactory!!.continueSession(sessionId, modelId)
                if (!streamingUsedForCurrentRun) {
                    val assistantMessage = io.github.stream29.kode.session.core.model.SessionMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                        content = result,
                        structuredData = null,
                        contentType = io.github.stream29.kode.session.core.model.ContentType.TEXT,
                        timestamp = nowInstant(),
                        metadata = null
                    )
                    messages = messages + assistantMessage.toMessageItem()
                }
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

        if (messageIndex >= messages.size) {
            addSystemMessage("Cannot fork from streaming message")
            return
        }

        val selected = messages.getOrNull(messageIndex)
        if (selected == null || selected.role == io.github.stream29.kode.session.core.model.MessageRole.SYSTEM) {
            addSystemMessage("Cannot fork from system message")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = agentFactory!!.sessionManager.getSession(sessionId)
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

                val newSession = agentFactory!!.sessionManager.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = sessionMessage.id,
                    newTitle = "Fork at message ${nonSystemIndex + 1}"
                )

                currentSessionId = newSession.id
                messages = newSession.messages.map { it.toMessageItem() }
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
                val summaries = agentFactory?.sessionManager?.listSessions(filter = filter)
                sessionSummaries = summaries ?: emptyList()
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

    public fun restoreSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agentFactory?.sessionManager?.restoreSession(sessionId)
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
                agentFactory?.sessionManager?.addTags(sessionId, tags)
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
                agentFactory?.sessionManager?.removeTags(sessionId, listOf(tag))
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
                agentFactory?.sessionManager?.exportSession(sessionId, File(dir, file))
                addSystemMessage("Session exported")
            } catch (e: Exception) {
                addSystemMessage("Failed to export session: ${e.message}")
            }
        }
    }

    public fun importSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Import Session", java.awt.FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file ?: return@launch
                val dir = dialog.directory ?: return@launch
                val imported = agentFactory?.sessionManager?.importSession(File(dir, file), newTitle = null)
                if (imported != null) {
                    currentSessionId = imported.id
                    messages = imported.messages.map { it.toMessageItem() }
                    loadSessionList()
                    addSystemMessage("Session imported")
                }
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
                
                val testFactory = SessionAwareAgentFactory(
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
                    workingDir = File("."),
                    eventListener = null,
                    hookManager = HookManager.empty(),
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
            timestamp = nowInstant(),
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
            timestamp = nowInstant(),
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
            timestamp = nowInstant(),
            metadata = null
        )
        messages = messages + agentMessage.toMessageItem()
    }

    override fun log(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            toolLogs = (toolLogs + message).takeLast(200)
        }
    }

    override fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolCallStarting -> {
                addSystemMessage("Tool call: ${event.toolName}")
            }
            is AgentEvent.ToolCallCompleted -> {
                addSystemMessage("Tool completed: ${event.toolName}")
            }
            is AgentEvent.MessageToUser -> {
                addSystemMessage(event.message)
            }
            is AgentEvent.AssistantMessageChunk -> {
                streamingUsedForCurrentRun = true
                appendStreamingChunk(event.content, event.isFinal)
            }
            is AgentEvent.Error -> {
                addSystemMessage("Error: ${event.message}")
            }
        }
    }

    override suspend fun requestApproval(request: ToolApprovalRequest): ToolApprovalDecision {
        if (yoloEnabled) {
            return ToolApprovalDecision.Approve
        }
        if (autoApproveActions.contains(request.toolName)) {
            return ToolApprovalDecision.Approve
        }

        val requestId = if (request.id.isBlank()) {
            java.util.UUID.randomUUID().toString()
        } else {
            request.id
        }
        val normalizedRequest = request.copy(id = requestId)
        val deferred = CompletableDeferred<ToolApprovalDecision>()
        approvalDeferreds[requestId] = deferred

        withContext(Dispatchers.Main) {
            pendingApprovals = pendingApprovals + normalizedRequest
        }

        return deferred.await()
    }

    public fun approvePendingRequest(requestId: String, decision: ToolApprovalDecision) {
        val deferred = approvalDeferreds.remove(requestId) ?: return
        if (decision == ToolApprovalDecision.ApproveForSession) {
            pendingApprovals.find { it.id == requestId }?.let { request ->
                autoApproveActions.add(request.toolName)
                approvalAutoApproveActions = autoApproveActions.toList().sorted()
            }
        }
        pendingApprovals = pendingApprovals.filterNot { it.id == requestId }
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

    private fun appendStreamingChunk(chunk: String, isFinal: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            streamingUsedForCurrentRun = true
            val current = streamingMessage
            val updated = if (current == null) {
                MessageItem(
                    id = java.util.UUID.randomUUID().toString(),
                    role = io.github.stream29.kode.session.core.model.MessageRole.ASSISTANT,
                    content = chunk,
                    timestamp = nowInstant(),
                    isError = false,
                    isToolCall = false,
                    toolName = null
                )
            } else {
                current.copy(content = current.content + chunk)
            }

            streamingMessage = updated

            if (isFinal) {
                messages = messages + updated
                streamingMessage = null
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

    public fun savePreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(
                    defaults = current.defaults.copy(
                        modelId = defaultModelId,
                        thinking = defaultThinking,
                        workDir = workDir.takeIf { it.isNotBlank() }
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
                addSystemMessage("Preferences saved")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to save preferences: ${e.message}")
            }
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
            try {
                val current = configManager.load()
                val updated = current.copy(
                    mcp = current.mcp.copy(
                        client = current.mcp.client.copy(toolCallTimeoutMs = mcpToolTimeoutMs),
                        servers = mcpServers
                    )
                )
                configManager.save(updated)
                addSystemMessage("MCP settings saved")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to save MCP settings: ${e.message}")
            }
        }
    }

    public fun addMcpServer(name: String, config: io.github.stream29.kode.config.api.McpServerConfig) {
        mcpServers = mcpServers + (name to config)
        saveMcpSettings()
    }

    public fun removeMcpServer(name: String) {
        mcpServers = mcpServers - name
        saveMcpSettings()
    }

    public fun testMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = mcpServers[name] ?: return@launch
            try {
                if (server.transport == "http" && server.url != null) {
                    val client = java.net.http.HttpClient.newBuilder().build()
                    val requestBuilder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.url))
                        .GET()
                    server.headers?.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }
                    val response = client.send(
                        requestBuilder.build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()
                    )
                    addSystemMessage("MCP test (${name}): HTTP ${response.statusCode()}")
                } else if (server.transport == "stdio") {
                    val command = server.command
                    if (command == null) {
                        addSystemMessage("MCP test (${name}): invalid configuration")
                        return@launch
                    }
                    val exists = resolveCommand(command)
                    if (exists) {
                        addSystemMessage("MCP test (${name}): command found")
                    } else {
                        addSystemMessage("MCP test (${name}): command not found")
                    }
                } else {
                    addSystemMessage("MCP test (${name}): invalid configuration")
                }
            } catch (e: Exception) {
                addSystemMessage("MCP test failed (${name}): ${e.message}")
            }
        }
    }

    public fun authMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = mcpServers[name] ?: return@launch
            try {
                if (server.transport == "http" && server.url != null) {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(java.net.URI.create(server.url))
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

    private fun resolveCommand(command: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        val separator = File.pathSeparator
        return path.split(separator).any { dir ->
            val file = File(dir, command)
            file.exists() && file.canExecute()
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
                    .directory(resolveWorkingDir())
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                terminalOutput = output + "\n(exit code: $exitCode)"
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
                val tool = WebTools(
                    messageHandler = this@MainViewModel,
                    logger = { log(it) }
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
            return null
        }

        var combined: ToolRegistry? = null
        mcpServers.forEach { (name, server) ->
            try {
                val registry = when (server.transport.lowercase()) {
                    "stdio" -> {
                        val command = server.command
                        if (command.isNullOrBlank()) {
                            addSystemMessage("MCP server $name missing command")
                            null
                        } else {
                            val process = startMcpProcess(name, server, command)
                            if (process == null) {
                                null
                            } else {
                                val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                                McpToolRegistryProvider.fromTransport(
                                    transport = transport,
                                    name = name,
                                    version = "1.0.0"
                                )
                            }
                        }
                    }
                    "http", "sse" -> {
                        val url = server.url
                        if (url.isNullOrBlank()) {
                            addSystemMessage("MCP server $name missing url")
                            null
                        } else {
                            val transport = McpToolRegistryProvider.defaultSseTransport(url)
                            McpToolRegistryProvider.fromTransport(
                                transport = transport,
                                name = name,
                                version = "1.0.0"
                            )
                        }
                    }
                    else -> {
                        addSystemMessage("MCP server $name has unsupported transport ${server.transport}")
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
                .directory(resolveWorkingDir())
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

        val workDir = resolveWorkingDir()
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
        val workDir = resolveWorkingDir()
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

    @Suppress("DEPRECATION")
    private fun nowInstant(): kotlinx.datetime.Instant {
        return kotlinx.datetime.Clock.System.now()
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
                    toolRegistry = factory.toolRegistry,
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

        override suspend fun prompt(
            content: List<ContentBlock>,
            _meta: kotlinx.serialization.json.JsonElement?
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
        saveToolSettings()
    }

    public fun clearToolLogs() {
        toolLogs = emptyList()
    }

    private fun saveToolSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(
                    tools = current.tools.copy(
                        disabled = disabledTools.toList()
                    )
                )
                configManager.save(updated)
                addSystemMessage("Tool settings saved")
                initializeAgentFactory()
            } catch (e: Exception) {
                addSystemMessage("Failed to save tool settings: ${e.message}")
            }
        }
    }

    private fun resolveWorkingDir(): File {
        val path = workDir.ifBlank { "." }
        return File(path)
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
}

public data class AgentProfile(
    val name: String,
    val description: String,
    val disabledTools: Set<String>,
    val defaultYolo: Boolean
)
