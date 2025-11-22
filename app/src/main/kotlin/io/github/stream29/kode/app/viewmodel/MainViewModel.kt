package io.github.stream29.kode.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.app.createCodingAgent
import io.github.stream29.kode.app.file.Config
import io.github.stream29.kode.app.file.LlmConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

public class MainViewModel: ViewModel() {
    public var taskInput: String by mutableStateOf("")
    public var outputLog: String by mutableStateOf("")
    public var isRunning: Boolean by mutableStateOf(false)
    public var isWaitingForInput: Boolean by mutableStateOf(false)

    private var inputDeferred: CompletableDeferred<String>? = null

    public fun runTask() {
        if (taskInput.isBlank()) return

        val task = taskInput
        // Clear input for next interaction
        taskInput = ""
        isRunning = true
        outputLog += "🚀 Starting agent for task: $task\n\n"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pass this AppState to the agent so it can use CommunicationTools
                val agent = createCodingAgent(
                    Config.load().llm.first().let { it as LlmConfig.Anthropic }.apiKey,
                    this@MainViewModel
                ) { logMessage ->
                    appendLog(logMessage)
                }

                val result = agent.run(task)

                appendLog("\n✅ Result:\n$result")
            } catch (e: Exception) {
                appendLog("\n❌ Error:\n${e.message}")
                e.printStackTrace()
            } finally {
                isRunning = false
            }
        }
    }

    public fun submitInput() {
        if (!isWaitingForInput) return

        val input = taskInput
        taskInput = "" // Clear after submission

        appendLog("[User]: $input")
        inputDeferred?.complete(input)
        isWaitingForInput = false
        inputDeferred = null
    }

    public suspend fun requestInput(): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        isWaitingForInput = true
        appendLog("\n❓ Waiting for user input...")
        return deferred.await()
    }

    public fun addMessageToUser(message: String) {
        appendLog("\n💬 [Agent]: $message")
    }

    private fun appendLog(text: String) {
        outputLog += "$text\n"
    }
}