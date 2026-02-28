package io.github.stream29.kode.app.viewmodel.acp

import androidx.lifecycle.ViewModel
import io.github.stream29.kode.app.viewmodel.AcpPageUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

public class AcpViewModel(
    private val onSystemMessage: (String) -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(AcpPageUiState())
    public val uiState: StateFlow<AcpPageUiState> = _uiState.asStateFlow()

    public fun updateHost(host: String) {
        _uiState.update { it.copy(acpHost = host) }
    }

    public fun updatePort(port: Int) {
        _uiState.update { it.copy(acpPort = port) }
    }

    public fun startAcpServer() {
        val msg = "ACP is disabled in script-only runtime"
        _uiState.update { it.copy(acpLogs = (it.acpLogs + msg).takeLast(200)) }
        onSystemMessage(msg)
    }

    public fun stopAcpServer() {
        val msg = "ACP is disabled in script-only runtime"
        _uiState.update { it.copy(acpLogs = (it.acpLogs + msg).takeLast(200)) }
        onSystemMessage(msg)
    }
}