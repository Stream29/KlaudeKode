package io.github.stream29.kode.core.session

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.ContentType
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.ToolCallData
import io.github.stream29.kode.session.core.model.ToolResultData
import io.github.stream29.kode.session.core.model.parseJsonOrString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Bridges between Kode's session management and Koog's AIAgent.
 * Handles conversion and synchronization of message state.
 */
public class KoogSessionBridge(
    private val sessionManager: SessionManager,
    private val json: Json,
) {
    /**
     * Prepares the message history from a session for Koog Agent.
     */
    public suspend fun prepareMessagesForAgent(sessionId: String, agentId: String? = null): List<Message> {
        val messages = sessionManager.getAgentMessages(sessionId, agentId)
        return messages.map { item -> item.koogMessage }
    }

    /**
     * Adds a user message to the session and prepares it for the agent.
     */
    public suspend fun addUserInput(sessionId: String, userInput: String): List<Message> {
        sessionManager.addUserMessage(sessionId, userInput)
        return prepareMessagesForAgent(sessionId, null)
    }

    /**
     * Saves agent response to the session.
     */
    public suspend fun saveAgentResponse(sessionId: String, response: String, agentId: String? = null) {
        sessionManager.addAssistantMessage(sessionId, response, null, agentId)
    }

    /**
     * Saves tool call to the session.
     */
    public suspend fun saveToolCall(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        agentId: String? = null,
    ) {
        val toolCallData = ToolCallData(
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            displayName = null,
        )
        sessionManager.addToolCall(sessionId, toolCallData, agentId)
    }

    /**
     * Saves tool result to the session.
     */
    public suspend fun saveToolResult(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        agentId: String? = null,
    ) {
        val toolResultData = ToolResultData(
            toolCallId = toolCallId,
            toolName = toolName,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
        )
        sessionManager.addToolResult(sessionId, toolResultData, agentId)
    }

    /**
     * Creates a checkpoint after agent execution.
     */
    public suspend fun checkpoint(sessionId: String, label: String?): SessionCheckpoint {
        return sessionManager.createCheckpoint(sessionId, label)
    }

    /**
     * Converts Koog's Message to SessionMessage envelope.
     */
    public fun convertFromKoogMessage(
        koogMessage: Message,
        messageId: String,
    ): SessionMessage {
        return when (koogMessage) {
            is Message.Tool.Call -> convertToolCallMessage(messageId, koogMessage)
            is Message.Tool.Result -> convertToolResultMessage(messageId, koogMessage)
            else -> toSessionMessage(
                messageId = messageId,
                koogMessage = koogMessage,
                structuredData = null,
                contentType = ContentType.TEXT,
            )
        }
    }

    private fun convertToolCallMessage(messageId: String, koogMessage: Message.Tool.Call): SessionMessage {
        val toolCallData = ToolCallData(
            toolName = koogMessage.tool,
            toolCallId = koogMessage.id ?: messageId,
            arguments = parseJsonOrString(koogMessage.content),
            displayName = null,
        )
        return toSessionMessage(
            messageId = messageId,
            koogMessage = koogMessage,
            structuredData = json.encodeToJsonElement(ToolCallData.serializer(), toolCallData),
            contentType = ContentType.TOOL_CALL,
        )
    }

    private fun convertToolResultMessage(messageId: String, koogMessage: Message.Tool.Result): SessionMessage {
        val toolResultData = ToolResultData(
            toolCallId = koogMessage.id.orEmpty(),
            toolName = koogMessage.tool,
            result = parseJsonOrString(koogMessage.content),
            isError = false,
            errorMessage = null,
        )
        return toSessionMessage(
            messageId = messageId,
            koogMessage = koogMessage,
            structuredData = json.encodeToJsonElement(ToolResultData.serializer(), toolResultData),
            contentType = ContentType.TOOL_RESULT,
        )
    }

    private fun toSessionMessage(
        messageId: String,
        koogMessage: Message,
        structuredData: JsonElement?,
        contentType: ContentType,
    ): SessionMessage {
        return SessionMessage.fromKoogMessage(
            id = messageId,
            message = koogMessage,
            structuredData = structuredData,
            contentType = contentType,
            metadata = null,
        )
    }
}
