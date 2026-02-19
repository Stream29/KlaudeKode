package io.github.stream29.kode.core.session

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.toKoogMessages
import kotlinx.serialization.json.JsonElement

public class KoogSessionBridge(
    private val sessionManager: SessionManager,
) {
    public suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> {
        val messages = sessionManager.getAgentMessages(sessionId, agentId)
        return messages.flatMap { item -> item.toKoogMessages() }
    }

    public suspend fun addUserInput(sessionId: String, userInput: String): List<Message> {
        sessionManager.addUserMessage(sessionId, userInput, null)
        return prepareMessagesForAgent(sessionId, null)
    }

    public suspend fun saveToolExchange(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        agentId: String?,
    ) {
        sessionManager.addToolExchangeMessage(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            metadata = null,
            agentId = agentId,
        )
    }

    public suspend fun saveSuspend(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        agentId: String?,
    ) {
        sessionManager.addSuspendMessage(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            metadata = null,
            agentId = agentId,
        )
    }

    public suspend fun saveResume(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        agentId: String?,
    ) {
        sessionManager.addResumeMessage(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            metadata = null,
            agentId = agentId,
        )
    }
}
