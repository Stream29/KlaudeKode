package io.github.stream29.kode.session.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents a single message in a conversation.
 * This is independent from Koog's message model.
 */
@Serializable
public data class SessionMessage(
    /**
     * Unique identifier for the message.
     */
    val id: String,
    
    /**
     * The role of the message sender.
     */
    val role: MessageRole,
    
    /**
     * The content of the message.
     * For tool calls/results, this contains structured data.
     */
    val content: String,
    
    /**
     * Additional structured data for tool calls and results.
     * This allows storing tool call information in a serializable format.
     */
    val structuredData: JsonElement?,
    
    /**
     * Type of the message content.
     */
    val contentType: ContentType,
    
    /**
     * Timestamp when the message was created.
     */
    val timestamp: Instant,
    
    /**
     * Optional metadata for extensions.
     */
    val metadata: Map<String, String>?
)

/**
 * Role of the message sender.
 */
@Serializable
public enum class MessageRole {
    /**
     * Human user.
     */
    USER,
    
    /**
     * AI assistant/agent.
     */
    ASSISTANT,
    
    /**
     * System prompt/instruction.
     */
    SYSTEM,
    
    /**
     * Tool call request from the agent.
     */
    TOOL_CALL,
    
    /**
     * Tool execution result.
     */
    TOOL_RESULT
}

/**
 * Type of content in the message.
 */
@Serializable
public enum class ContentType {
    TEXT,
    MARKDOWN,
    JSON,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR
}

/**
 * Structured data for tool calls.
 */
@Serializable
public data class ToolCallData(
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val displayName: String?
)

/**
 * Structured data for tool results.
 */
@Serializable
public data class ToolResultData(
    val toolCallId: String,
    val toolName: String,
    val result: JsonElement,
    val isError: Boolean,
    val errorMessage: String?
)
