package io.github.stream29.koogagent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppState(private val apiKey: String) {
    var taskInput by mutableStateOf("")
    var outputLog by mutableStateOf("")
    var isRunning by mutableStateOf(false)
    var isWaitingForInput by mutableStateOf(false)
    
    private var inputDeferred: CompletableDeferred<String>? = null

    fun runTask(scope: CoroutineScope) {
        if (taskInput.isBlank()) return
        
        val task = taskInput
        // Clear input for next interaction
        taskInput = ""
        isRunning = true
        outputLog += "🚀 Starting agent for task: $task\n\n"
        
        scope.launch(Dispatchers.IO) {
            try {
                // Pass this AppState to the agent so it can use CommunicationTools
                val agent = createCodingAgent(apiKey, this@AppState) { logMessage ->
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

    fun submitInput() {
        if (!isWaitingForInput) return
        
        val input = taskInput
        taskInput = "" // Clear after submission
        
        appendLog("[User]: $input")
        inputDeferred?.complete(input)
        isWaitingForInput = false
        inputDeferred = null
    }

    suspend fun requestInput(): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        isWaitingForInput = true
        appendLog("\n❓ Waiting for user input...")
        return deferred.await()
    }

    fun addMessageToUser(message: String) {
        appendLog("\n💬 [Agent]: $message")
    }

    private fun appendLog(text: String) {
        outputLog += "$text\n"
    }
}