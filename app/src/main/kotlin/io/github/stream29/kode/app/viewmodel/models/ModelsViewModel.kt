package io.github.stream29.kode.app.viewmodel.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public class ModelsViewModel(
    private val configManager: ConfigManager,
    private val onSystemMessage: (String) -> Unit,
    private val onNotifyConfigChanged: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    public val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            val config = configManager.load()
            _uiState.update { it.copy(
                models = config.models,
                auths = config.auths
            ) }
        }
    }

    public fun addModel(model: LlmModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(models = current.models + model)
                configManager.save(updated)
                onNotifyConfigChanged()
                loadModels()
            } catch (e: Exception) {
                onSystemMessage("Failed to add model: ${e.message}")
            }
        }
    }

    public fun updateModel(id: String, model: LlmModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updatedModels = current.models.map { if (it.id == id) model else it }
                val updated = current.copy(models = updatedModels)
                configManager.save(updated)
                onNotifyConfigChanged()
                loadModels()
            } catch (e: Exception) {
                onSystemMessage("Failed to update model: ${e.message}")
            }
        }
    }

    public fun deleteModel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updatedModels = current.models.filter { it.id != id }
                val updated = current.copy(models = updatedModels)
                configManager.save(updated)
                onNotifyConfigChanged()
                loadModels()
            } catch (e: Exception) {
                onSystemMessage("Failed to delete model: ${e.message}")
            }
        }
    }

    public fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}

public data class ModelsUiState(
    val models: List<LlmModelConfig> = emptyList(),
    val auths: List<LlmAuthConfig> = emptyList(),
    val selectedTab: Int = 0,
)
