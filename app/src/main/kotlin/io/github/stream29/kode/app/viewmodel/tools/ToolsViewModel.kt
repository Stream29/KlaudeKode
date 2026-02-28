package io.github.stream29.kode.app.viewmodel.tools

import androidx.lifecycle.ViewModel
import io.github.stream29.kode.app.viewmodel.ToolsPageUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

public class ToolsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsPageUiState())
    public val uiState: StateFlow<ToolsPageUiState> = _uiState.asStateFlow()

    public fun setToolEnabled(toolKey: String, enabled: Boolean) {
        _uiState.update { current ->
            val updated = if (enabled) {
                current.disabledTools - toolKey
            } else {
                current.disabledTools + toolKey
            }
            current.copy(disabledTools = updated)
        }
    }

    public fun clearToolLogs() {
        _uiState.update { it.copy(toolLogs = emptyList()) }
    }
}