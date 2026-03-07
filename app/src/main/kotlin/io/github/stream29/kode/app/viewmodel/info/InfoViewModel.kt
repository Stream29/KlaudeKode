package io.github.stream29.kode.app.viewmodel.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.viewmodel.InfoPageUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

public class InfoViewModel(
    private val onSystemMessage: (String) -> Unit,
    private val appDataDirProvider: () -> File,
    private val toolLogsProvider: () -> List<String>,
    private val acpLogsProvider: () -> List<String>
) : ViewModel() {
    private val _uiState = MutableStateFlow(InfoPageUiState())
    public val uiState: StateFlow<InfoPageUiState> = _uiState.asStateFlow()

    public fun refreshSkillsPreview() {
        // Implement preview refresh if necessary
    }

    public fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = FileDialog(null as Frame?, "Export Logs", FileDialog.SAVE)
                dialog.isVisible = true
                val file = dialog.file ?: return@launch
                val dir = dialog.directory ?: return@launch
                
                val toastLogFile = File(appDataDirProvider(), "toast log.txt")
                val toastLogLines = if (toastLogFile.exists()) toastLogFile.readLines() else emptyList()
                val output = buildString {
                    appendLine("== Tool Logs ==")
                    toolLogsProvider().forEach { appendLine(it) }
                    appendLine()
                    appendLine("== ACP Logs ==")
                    acpLogsProvider().forEach { appendLine(it) }
                    appendLine()
                    appendLine("== Toast Logs ==")
                    toastLogLines.forEach { appendLine(it) }
                }
                File(dir, file).writeText(output)
                onSystemMessage("Logs exported")
            } catch (e: Exception) {
                onSystemMessage("Failed to export logs: ${e.message}")
            }
        }
    }
}
