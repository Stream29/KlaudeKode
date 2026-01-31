package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler

/**
 * Communication tools for interacting with the user.
 */
@Suppress("unused")
@LLMDescription("Tools to communicate with user")
public class CommunicationTools public constructor(
    private val messageHandler: MessageHandler
) : ToolSet {
    
    @Tool
    @LLMDescription("Wait for user input. This suspends execution until the user provides input via the UI.")
    public suspend fun waitForUserInput(): String {
        return messageHandler.requestInput()
    }

    @Tool
    @LLMDescription("Say something to the user. Use this to communicate with the user.")
    public fun sayToUser(
        @LLMDescription("The message to say to the user")
        message: String
    ): String {
        messageHandler.addMessageToUser(message)
        return "Message sent to user successfully."
    }
}
