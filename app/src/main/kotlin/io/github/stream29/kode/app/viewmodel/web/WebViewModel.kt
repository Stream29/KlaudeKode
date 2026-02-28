package io.github.stream29.kode.app.viewmodel.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.viewmodel.WebPageUiState
import io.github.stream29.kode.tools.WebTools
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

public class WebViewModel(
    private val onSystemMessage: (String) -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(WebPageUiState())
    public val uiState: StateFlow<WebPageUiState> = _uiState.asStateFlow()

    public fun updateWebUrl(url: String) {
        _uiState.update { it.copy(webUrl = url) }
    }

    public fun fetchWebContent() {
        val url = _uiState.value.webUrl.trim()
        if (url.isEmpty()) return

        _uiState.update { it.copy(webLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tool = WebTools(
                    messageHandler = object : MessageHandler {
                        override fun addMessageToUser(message: String) {}
                        override fun log(message: String) {}
                        override suspend fun requestInput(): String = ""
                    },
                    logger = {}
                )
                val result = tool.fetchURL(url)
                _uiState.update { it.copy(
                    webContent = result.toString(),
                    webLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    webContent = "Failed to fetch: ${e.message}",
                    webLoading = false
                ) }
            }
        }
    }

    public fun openWebInBrowser() {
        val url = _uiState.value.webUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url))
                }
            } catch (e: Exception) {
                onSystemMessage("Failed to open browser: ${e.message}")
            }
        }
    }
}