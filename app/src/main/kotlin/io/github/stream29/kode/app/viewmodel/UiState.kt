package io.github.stream29.kode.app.viewmodel

import io.github.stream29.kode.app.view.AppPage
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.McpServerConfig
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import io.github.stream29.kode.ui.bridge.auth.OAuthStatusUi
import io.github.stream29.kode.ui.bridge.mcp.McpHealthResult
import io.github.stream29.kode.ui.bridge.mcp.McpTestResult
import io.github.stream29.kode.ui.core.todo.TodoUiState
import kotlinx.serialization.Serializable

@Serializable
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
    val newSessionDirInput: String = "",
    val sessionDirDraft: String = "",
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val currentTask: String = "",
    val todoState: TodoUiState = TodoUiState(rootNodes = emptyList(), allExpanded = false),
    val isGeneratingSessionTitle: Boolean = false,
)

public data class MainChromeUiState(
    val currentPage: AppPage = AppPage.Chat,
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
    val mcpServers: Map<String, McpServerConfig> = emptyMap(),
    val mcpTestResults: Map<String, McpTestResult> = emptyMap(),
    val mcpTestsInFlight: Set<String> = emptySet(),
    val mcpHealthResults: Map<String, McpHealthResult> = emptyMap(),
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

public data class OpenAddModelDialogRequest(
    val preselectedAuthId: String?,
    val requestNonce: Long,
)

public data class OpenEditModelDialogRequest(
    val modelId: String,
    val requestNonce: Long,
)

public data class OpenAddAuthDialogRequest(
    val requestNonce: Long,
)

public data class OpenEditAuthDialogRequest(
    val authId: String,
    val requestNonce: Long,
)

public data class OpenDeleteAuthDialogRequest(
    val authId: String,
    val requestNonce: Long,
)

public data class OpenSessionManagerDialogRequest(
    val requestNonce: Long,
)

public data class CloseSessionManagerDialogRequest(
    val requestNonce: Long,
)

public data class OverlayDialogRequestsState(
    val openAddModelDialogRequest: OpenAddModelDialogRequest? = null,
    val openEditModelDialogRequest: OpenEditModelDialogRequest? = null,
    val openAddAuthDialogRequest: OpenAddAuthDialogRequest? = null,
    val openEditAuthDialogRequest: OpenEditAuthDialogRequest? = null,
    val openDeleteAuthDialogRequest: OpenDeleteAuthDialogRequest? = null,
    val openSessionManagerDialogRequest: OpenSessionManagerDialogRequest? = null,
    val closeSessionManagerDialogRequest: CloseSessionManagerDialogRequest? = null,
)

public data class AppUiState(
    val currentPage: AppPage = AppPage.Chat,
    val modelsPageSelectedTab: Int = 0,
    val overlayDialogRequests: OverlayDialogRequestsState = OverlayDialogRequestsState(),
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
    val mcpServers: Map<String, McpServerConfig> = emptyMap(),
    val mcpTestResults: Map<String, McpTestResult> = emptyMap(),
    val mcpTestsInFlight: Set<String> = emptySet(),
    val mcpHealthResults: Map<String, McpHealthResult> = emptyMap(),
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

public fun AppUiState.withPageNavigation(nextPage: AppPage): AppUiState {
    return this.copy(
        currentPage = nextPage,
        overlayDialogRequests = overlayDialogRequests.clearModelScopedRequests(),
    )
}

public fun OverlayDialogRequestsState.clearModelScopedRequests(): OverlayDialogRequestsState {
    return this.copy(
        openAddModelDialogRequest = null,
        openEditModelDialogRequest = null,
        openAddAuthDialogRequest = null,
        openEditAuthDialogRequest = null,
        openDeleteAuthDialogRequest = null,
    )
}

public data class UiToast(
    val id: String,
    val message: String,
)

public enum class StopMode {
    None,
    Stop,
    ForceStop,
    SafeRequested,
}

public data class SessionUiRunState(
    val isRunning: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val currentTask: String = "",
    val stopMode: StopMode = StopMode.None,
)

public data class SessionSearchHit(
    val summary: SessionSummary,
    val hits: Int,
)

public data class PreferencesSnapshot(
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

public data class McpSnapshot(
    val timeoutMs: Int,
    val servers: Map<String, McpServerConfig>,
)
