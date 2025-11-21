package io.github.stream29.koogagent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@Suppress("unused")
@LLMDescription("Tools to communicate with user")
class CommunicationTools(private val appState: AppState) : ToolSet {
    
    @Tool
    @LLMDescription("Wait for user input. This suspends execution until the user provides input via the UI.")
    suspend fun waitForUserInput(): String {
        return appState.requestInput()
    }

    @Tool
    @LLMDescription("Say something to the user. Use this to communicate with the user.")
    suspend fun sayToUser(
        @LLMDescription("The message to say to the user")
        message: String
    ): String {
        appState.addMessageToUser(message)
        return "Message sent to user successfully."
    }
}
