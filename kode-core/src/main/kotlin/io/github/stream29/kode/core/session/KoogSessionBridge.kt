package io.github.stream29.kode.core.session

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Bridges between Kode's session management and Koog's AIAgent.
 * Handles conversion of messages and synchronization of state.
 */
public class KoogSessionBridge(
    private val sessionManager: SessionManager,
    private val clock: Clock,
    private val json: Json
) {
    /**
     * Prepares the message history from a session for Koog Agent.
     * This converts our SessionMessage to Koog's Message format.
     */
    public suspend fun prepareMessagesForAgent(sessionId: String): List<Message> {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        return session.messages.mapNotNull { convertToKoogMessage(it) }
    }
    
    /**
     * Adds a user message to the session and prepares it for the agent.
     */
    public suspend fun addUserInput(sessionId: String, userInput: String): List<Message> {
        // Add user message to session
        sessionManager.addUserMessage(sessionId, userInput)
        
        // Return all messages for the agent
        return prepareMessagesForAgent(sessionId)
    }
    
    /**
     * Saves agent response to the session.
     */
    public suspend fun saveAgentResponse(sessionId: String, response: String) {
        sessionManager.addAssistantMessage(sessionId, response, null)
    }
    
    /**
     * Saves tool call to the session.
     */
    public suspend fun saveToolCall(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement
    ) {
        val toolCallData = ToolCallData(
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            displayName = null
        )
        sessionManager.addToolCall(sessionId, toolCallData)
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
        errorMessage: String?
    ) {
        val toolResultData = ToolResultData(
            toolCallId = toolCallId,
            toolName = toolName,
            result = result,
            isError = isError,
            errorMessage = errorMessage
        )
        sessionManager.addToolResult(sessionId, toolResultData)
    }
    
    /**
     * Creates a checkpoint after agent execution.
     */
    public suspend fun checkpoint(sessionId: String, label: String?): SessionCheckpoint {
        return sessionManager.createCheckpoint(sessionId, label)
    }
    
    /**
     * Converts a SessionMessage to Koog's Message format.
     */
    private fun convertToKoogMessage(sessionMessage: SessionMessage): Message? {
        return when (sessionMessage.role) {
            MessageRole.USER -> {
                Message.User(
                    content = sessionMessage.content,
                    metaInfo = RequestMetaInfo.create(clock)
                )
            }
            MessageRole.ASSISTANT -> {
                Message.Assistant(
                    content = sessionMessage.content,
                    metaInfo = ResponseMetaInfo.create(clock)
                )
            }
            MessageRole.SYSTEM -> {
                Message.System(
                    content = sessionMessage.content,
                    metaInfo = RequestMetaInfo.create(clock)
                )
            }
            MessageRole.TOOL_CALL -> {
                // Parse tool call data from structuredData
                val toolCallData = sessionMessage.structuredData?.let {
                    try {
                        json.decodeFromJsonElement(ToolCallData.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (toolCallData != null) {
                    Message.Tool.Call(
                        id = toolCallData.toolCallId,
                        tool = toolCallData.toolName,
                        content = toolCallData.arguments.toString(),
                        metaInfo = ResponseMetaInfo.create(clock)
                    )
                } else {
                    // Fallback: treat as regular assistant message
                    Message.Assistant(
                        content = sessionMessage.content,
                        metaInfo = ResponseMetaInfo.create(clock)
                    )
                }
            }
            MessageRole.TOOL_RESULT -> {
                // Parse tool result data from structuredData
                val toolResultData = sessionMessage.structuredData?.let {
                    try {
                        json.decodeFromJsonElement(ToolResultData.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (toolResultData != null) {
                    Message.Tool.Result(
                        id = toolResultData.toolCallId,
                        tool = toolResultData.toolName,
                        content = toolResultData.result.toString(),
                        metaInfo = RequestMetaInfo.create(clock)
                    )
                } else {
                    // Fallback: treat as regular message
                    Message.Tool.Result(
                        id = null,
                        tool = "",
                        content = sessionMessage.content,
                        metaInfo = RequestMetaInfo.create(clock)
                    )
                }
            }
        }
    }
    
    /**
     * Converts Koog's Message to our SessionMessage format.
     */
    public fun convertFromKoogMessage(
        koogMessage: Message,
        messageId: String
    ): SessionMessage {
        val role = when (koogMessage.role) {
            Message.Role.User -> MessageRole.USER
            Message.Role.Assistant -> MessageRole.ASSISTANT
            Message.Role.System -> MessageRole.SYSTEM
            Message.Role.Tool -> MessageRole.TOOL_RESULT
            Message.Role.Reasoning -> MessageRole.ASSISTANT
        }
        
        // Handle tool calls
        if (koogMessage is Message.Tool.Call) {
            return SessionMessage(
                id = messageId,
                role = MessageRole.TOOL_CALL,
                content = "Calling tool: ${koogMessage.tool}",
                structuredData = json.encodeToJsonElement(
                    ToolCallData.serializer(),
                    ToolCallData(
                        toolName = koogMessage.tool,
                        toolCallId = koogMessage.id ?: messageId,
                        arguments = Json.parseToJsonElement(koogMessage.content),
                        displayName = null
                    )
                ),
                contentType = ContentType.TOOL_CALL,
                timestamp = Clock.System.now(),
                metadata = null
            )
        }

        // Handle tool results
        if (koogMessage is Message.Tool.Result) {
            return SessionMessage(
                id = messageId,
                role = MessageRole.TOOL_RESULT,
                content = koogMessage.content,
                structuredData = json.encodeToJsonElement(
                    ToolResultData.serializer(),
                    ToolResultData(
                        toolCallId = koogMessage.id ?: "",
                        toolName = koogMessage.tool,
                        result = Json.parseToJsonElement("\"${koogMessage.content}\""),
                        isError = false,
                        errorMessage = null
                    )
                ),
                contentType = ContentType.TOOL_RESULT,
                timestamp = Clock.System.now(),
                metadata = null
            )
        }

        // Regular message
        return SessionMessage(
            id = messageId,
            role = role,
            content = koogMessage.content,
            structuredData = null,
            contentType = ContentType.TEXT,
            timestamp = Clock.System.now(),
            metadata = null
        )
    }
}
