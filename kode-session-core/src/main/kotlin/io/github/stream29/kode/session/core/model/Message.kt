package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

@Serializable
public sealed interface AgentMessage {
    public val id: String
    public val timestamp: Instant
    public val metadata: Map<String, String>?
}

public typealias SessionMessage = AgentMessage

@Serializable
@SerialName("user")
public data class UserMessage(
    override val id: String,
    val content: String,
    override val timestamp: Instant,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

@Serializable
@SerialName("tool_exchange")
public data class ToolExchangeMessage(
    override val id: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val result: JsonElement,
    val isError: Boolean,
    val errorMessage: String?,
    val displayName: String? = null,
    override val timestamp: Instant,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

@Serializable
@SerialName("suspend")
public data class SuspendMessage(
    override val id: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val displayName: String? = null,
    override val timestamp: Instant,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

@Serializable
@SerialName("resume")
public data class ResumeMessage(
    override val id: String,
    val toolName: String,
    val toolCallId: String,
    val result: JsonElement,
    val isError: Boolean,
    val errorMessage: String?,
    override val timestamp: Instant,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

@Suppress("DEPRECATION")
public fun AgentMessage.toKoogMessages(): List<Message> {
    return when (this) {
        is UserMessage -> {
            listOf(
                Message.User(
                    content = content,
                    metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
                )
            )
        }

        is ToolExchangeMessage -> {
            listOf(
                Message.Tool.Call(
                    id = toolCallId,
                    tool = toolName,
                    content = arguments.toString(),
                    metaInfo = ResponseMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
                ),
                Message.Tool.Result(
                    id = toolCallId,
                    tool = toolName,
                    content = result.toMessageContent(),
                    metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
                )
            )
        }

        is SuspendMessage -> {
            listOf(
                Message.Tool.Call(
                    id = toolCallId,
                    tool = toolName,
                    content = arguments.toString(),
                    metaInfo = ResponseMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
                )
            )
        }

        is ResumeMessage -> {
            listOf(
                Message.Tool.Result(
                    id = toolCallId,
                    tool = toolName,
                    content = result.toMessageContent(),
                    metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
                )
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
