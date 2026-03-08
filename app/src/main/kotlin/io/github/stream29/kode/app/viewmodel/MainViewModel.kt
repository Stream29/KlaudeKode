package io.github.stream29.kode.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.view.AppPage
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public class MainViewModel(
    private val configManager: ConfigManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _appUiState = MutableStateFlow(AppUiState())
    public val appUiState: StateFlow<AppUiState> = _appUiState.asStateFlow()

    public val currentSessionIdFlow: StateFlow<String?> = _appUiState.map { it.lastOpenedSessionId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    public val activeModelIdFlow: StateFlow<String?> = _appUiState.map { it.activeModelId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    private val _toastFlow = MutableSharedFlow<UiToast>(extraBufferCapacity = 10)
    public val toastFlow: SharedFlow<UiToast> = _toastFlow.asSharedFlow()

    private val _mainChromeUiState = MutableStateFlow(MainChromeUiState())
    public val mainChromeUiState: StateFlow<MainChromeUiState> = _mainChromeUiState.asStateFlow()

    init {
        loadInitialConfig()
    }

    private fun loadInitialConfig() {
        viewModelScope.launch {
            val config = configManager.load()
            _appUiState.update { it.copy(
                lastOpenedSessionId = config.ui.lastOpenedSessionId,
                activeModelId = config.defaults.modelId,
                models = config.models,
                auths = config.auths,
            ) }
            _mainChromeUiState.update { it.copy(uiTheme = config.ui.theme) }
        }
    }

    public fun navigateToPage(page: AppPage) {
        _mainChromeUiState.update { it.copy(currentPage = page) }
        _appUiState.update { it.copy(currentPage = page) }
    }

    public fun switchToSession(sessionId: String) {
        _appUiState.update { it.copy(lastOpenedSessionId = sessionId) }
        viewModelScope.launch(Dispatchers.IO) {
            val config = configManager.load()
            if (config.ui.lastOpenedSessionId != sessionId) {
                configManager.save(config.copy(ui = config.ui.copy(lastOpenedSessionId = sessionId)))
            }
        }
    }

    public fun createNewSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = configManager.load()
                val defaultModelId = config.defaults.modelId
                if (defaultModelId.isNullOrBlank()) {
                    showToast("No default model configured")
                    return@launch
                }
                val session = sessionManager.createConversationSession(
                    title = "session-${System.currentTimeMillis() / 1000}",
                    systemPrompt = "",
                    workDir = config.defaults.workDir,
                )
                switchToSession(session.id)
                navigateToPage(AppPage.Chat)
            } catch (e: Exception) {
                showToast("Failed to create session: ${e.message}")
            }
        }
    }

    public fun showToast(message: String) {
        viewModelScope.launch {
            val toast = UiToast(id = java.util.UUID.randomUUID().toString(), message = message)
            _toastFlow.emit(toast)
            _mainChromeUiState.update { it.copy(toasts = (it.toasts + toast).takeLast(10)) }
        }
    }

    public fun consumeToast(id: String) {
        _mainChromeUiState.update { state -> state.copy(toasts = state.toasts.filter { it.id != id }) }
    }

    public fun onAgentEvent(event: AgentEvent, sessionId: String?) {
        when (event) {
            is AgentEvent.Error -> showToast(event.message)
            is AgentEvent.MessageToUser -> {}
            else -> {}
        }
    }

    public fun onEvent(event: AgentEvent, sessionId: String?): Unit = onAgentEvent(event, sessionId)

    public fun onNotifyConfigChanged() {
        loadInitialConfig()
    }

    public fun updateActiveModelId(modelId: String) {
        _appUiState.update { it.copy(activeModelId = modelId) }
        viewModelScope.launch(Dispatchers.IO) {
            val config = configManager.load()
            configManager.save(config.copy(defaults = config.defaults.copy(modelId = modelId)))
        }
    }

    public fun switchModel(modelId: String) {
        updateActiveModelId(modelId)
    }
}
