@file:Suppress("DEPRECATION")

package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.datetime.toStdlibInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Represents a single message in a conversation.
 * SessionMessage keeps session-domain fields while composing Koog's Message model.
 */
@Serializable
public data class SessionMessage(
    /**
     * Unique identifier for the message.
     */
    val id: String,

    /**
     * Session-level role used by session operations and UI rendering.
     */
    val role: MessageRole,

    /**
     * Session-level display content.
     */
    val content: String,

    /**
     * Additional structured data for tool calls and tool results.
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
    val metadata: Map<String, String>?,

    /**
     * Koog-native message payload for agent runtime.
     */
    val koogMessage: Message = role.toKoogMessage(
        content = content,
        timestamp = timestamp,
        structuredData = structuredData,
    ),
) {
    public companion object {
        public fun fromKoogMessage(
            id: String,
            message: Message,
            structuredData: JsonElement?,
            contentType: ContentType,
            metadata: Map<String, String>?,
        ): SessionMessage {
            return SessionMessage(
                id = id,
                role = message.toSessionRole(),
                content = message.toSessionDisplayContent(),
                structuredData = structuredData,
                contentType = contentType,
                timestamp = message.metaInfo.timestamp.toStdlibInstant(),
                metadata = metadata,
                koogMessage = message,
            )
        }
    }
}

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
    TOOL_RESULT,
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
    ERROR,
}

/**
 * Structured data for tool calls.
 */
@Serializable
public data class ToolCallData(
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val displayName: String?,
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
    val errorMessage: String?,
)

public fun Message.toSessionRole(): MessageRole {
    return when (this) {
        is Message.User -> MessageRole.USER
        is Message.Assistant -> MessageRole.ASSISTANT
        is Message.System -> MessageRole.SYSTEM
        is Message.Tool.Call -> MessageRole.TOOL_CALL
        is Message.Tool.Result -> MessageRole.TOOL_RESULT
        is Message.Reasoning -> MessageRole.ASSISTANT
    }
}

public fun Message.toSessionDisplayContent(): String {
    return when (this) {
        is Message.Tool.Call -> "Calling tool: ${this.tool}"
        else -> this.content
    }
}

public fun MessageRole.toKoogMessage(
    content: String,
    timestamp: Instant,
    structuredData: JsonElement?,
): Message {
    return when (this) {
        MessageRole.USER -> Message.User(
            content = content,
            metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
        )

        MessageRole.ASSISTANT -> Message.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
        )

        MessageRole.SYSTEM -> Message.System(
            content = content,
            metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
        )

        MessageRole.TOOL_CALL -> {
            val data = structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolCallData.serializer(), element)
                }.getOrNull()
            }
            Message.Tool.Call(
                id = data?.toolCallId,
                tool = data?.toolName ?: "",
                content = data?.arguments?.toString() ?: content,
                metaInfo = ResponseMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
            )
        }

        MessageRole.TOOL_RESULT -> {
            val data = structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolResultData.serializer(), element)
                }.getOrNull()
            }
            Message.Tool.Result(
                id = data?.toolCallId,
                tool = data?.toolName ?: "",
                content = data?.result?.toMessageContent() ?: content,
                metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
            )
        }
    }
}

private fun JsonElement.toMessageContent(): String {
    return when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> toString()
    }
}

public fun parseJsonOrString(content: String): JsonElement {
    return runCatching {
        Json.parseToJsonElement(content)
    }.getOrElse {
        JsonPrimitive(content)
    }
}
