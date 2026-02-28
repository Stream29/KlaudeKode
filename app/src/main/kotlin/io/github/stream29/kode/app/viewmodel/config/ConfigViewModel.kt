package io.github.stream29.kode.app.viewmodel.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

public class ConfigViewModel(
    private val configManager: ConfigManager,
    private val onSystemMessage: (String) -> Unit,
    private val onNotifyConfigChanged: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    public val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val config = configManager.load()
            _uiState.update { it.copy(
                theme = config.ui.theme,
                messageAlignment = config.ui.messageAlignment,
                messageMaxWidthRatio = config.ui.messageMaxWidthRatio,
                sendKeyMode = config.ui.sendKeyMode,
                activePresetName = config.preset.builtin ?: "build",
                loopControl = config.loopControl,
                storage = config.storage
            ) }
        }
    }

    public fun updateUiConfig(
        theme: String? = null,
        messageAlignment: String? = null,
        messageMaxWidthRatio: Float? = null,
        sendKeyMode: String? = null
    ) {
        _uiState.update { current ->
            current.copy(
                theme = theme ?: current.theme,
                messageAlignment = messageAlignment ?: current.messageAlignment,
                messageMaxWidthRatio = messageMaxWidthRatio ?: current.messageMaxWidthRatio,
                sendKeyMode = sendKeyMode ?: current.sendKeyMode
            )
        }
        saveConfig()
    }

    public fun updateLoopControl(loopControl: LoopControlConfig) {
        _uiState.update { it.copy(loopControl = loopControl) }
        saveConfig()
    }

    public fun updateActivePreset(name: String) {
        _uiState.update { it.copy(activePresetName = name) }
        saveConfig()
    }

    private fun saveConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(
                    ui = current.ui.copy(
                        theme = uiState.value.theme,
                        messageAlignment = uiState.value.messageAlignment,
                        messageMaxWidthRatio = uiState.value.messageMaxWidthRatio,
                        sendKeyMode = uiState.value.sendKeyMode
                    ),
                    preset = current.preset.copy(builtin = uiState.value.activePresetName),
                    loopControl = uiState.value.loopControl
                )
                configManager.save(updated)
                onNotifyConfigChanged()
            } catch (e: Exception) {
                onSystemMessage("Failed to save config: ${e.message}")
            }
        }
    }

    public fun buildDefaultSessionDirInput(): String {
        val storage = uiState.value.storage
        val base = if (storage.dataDir.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + storage.dataDir.removePrefix("~")
        } else {
            storage.dataDir
        }
        val sessionsDir = File(base, "sessions")
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return File(sessionsDir, "session-$timestamp").absolutePath
    }
}

public data class ConfigUiState(
    val theme: String = "dark",
    val messageAlignment: String = "left",
    val messageMaxWidthRatio: Float = 0.9f,
    val sendKeyMode: String = "ctrl_or_cmd_enter_send",
    val activePresetName: String = "build",
    val loopControl: LoopControlConfig = LoopControlConfig(),
    val storage: StorageConfig = StorageConfig()
)
