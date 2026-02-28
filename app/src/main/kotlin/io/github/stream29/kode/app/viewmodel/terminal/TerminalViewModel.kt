package io.github.stream29.kode.app.viewmodel.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.viewmodel.TerminalPageUiState
import io.github.stream29.kode.core.agent.MainAgentScriptContext
import io.github.stream29.kode.tools.scripting.evalInThreadCancellable
import io.github.stream29.kode.tools.scripting.KotlinScriptResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

public class TerminalViewModel(
    private val defaultSessionDir: String,
    private val currentSessionWorkDirProvider: () -> String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalPageUiState())
    public val uiState: StateFlow<TerminalPageUiState> = _uiState.asStateFlow()

    public fun updateTerminalCommand(cmd: String) {
        _uiState.update { it.copy(terminalCommand = cmd) }
    }

    public fun updateScriptContent(script: String) {
        _uiState.update { it.copy(scriptContent = script) }
    }

    private fun resolveSessionWorkingDir(input: String?): File {
        val dir = input?.takeIf { it.isNotBlank() } ?: defaultSessionDir
        return File(dir.takeIf { it.isNotBlank() } ?: ".")
    }

    public fun runShellCommand() {
        val command = _uiState.value.terminalCommand.trim()
        if (command.isEmpty()) return

        _uiState.update { it.copy(terminalRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workDir = currentSessionWorkDirProvider()
                val process = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(resolveSessionWorkingDir(workDir))
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                _uiState.update { it.copy(
                    terminalOutput = "$output\n(exit code: $exitCode)",
                    terminalRunning = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    terminalOutput = "Failed to run command: ${e.message}",
                    terminalRunning = false
                ) }
            }
        }
    }

    public fun runScript() {
        val script = _uiState.value.scriptContent.trim()
        if (script.isEmpty()) return

        _uiState.update { it.copy(scriptRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = MainAgentScriptContext().evalInThreadCancellable(script = script)
                val out = when (result) {
                    is KotlinScriptResult.Success -> "Return: ${result.returnValue}\n\nStdout:\n${result.stdout}"
                    is KotlinScriptResult.Failure -> "Error: ${result.message}\n\nStdout:\n${result.stdout}"
                }
                _uiState.update { it.copy(
                    scriptOutput = out,
                    scriptRunning = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    scriptOutput = "Script failed: ${e.message}",
                    scriptRunning = false
                ) }
            }
        }
    }
}