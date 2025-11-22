package io.github.stream29.kode.app

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.app.viewmodel.MainViewModel

@Suppress("unused")
@LLMDescription("Tools to communicate with user")
public class CommunicationTools(private val appState: MainViewModel) : ToolSet {
    
    @Tool
    @LLMDescription("Wait for user input. This suspends execution until the user provides input via the UI.")
    public suspend fun waitForUserInput(): String {
        return appState.requestInput()
    }

    @Tool
    @LLMDescription("Say something to the user. Use this to communicate with the user.")
    public suspend fun sayToUser(
        @LLMDescription("The message to say to the user")
        message: String
    ): String {
        appState.addMessageToUser(message)
        return "Message sent to user successfully."
    }
}
