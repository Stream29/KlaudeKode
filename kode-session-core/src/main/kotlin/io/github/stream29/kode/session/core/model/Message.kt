package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val koogMessages: List<Message>,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

@Serializable
public enum class AgentScriptStatus {
    PENDING_INPUT,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
@SerialName("script")
public data class AgentScript(
    override val id: String,
    val scriptId: String,
    val status: AgentScriptStatus,
    val scriptReturnValue: String?,
    val scriptStdout: String,
    val error: String?,
    val outputList: List<String>,
    override val timestamp: Instant,
    val koogMessages: List<Message>,
    override val metadata: Map<String, String>? = null,
) : AgentMessage

public fun AgentMessage.toKoogMessages(): List<Message> {
    val rawMessages = when (this) {
        is UserMessage -> koogMessages
        is AgentScript -> koogMessages
    }
    if (rawMessages.isEmpty()) {
        throw IllegalStateException(
            "AgentMessage(id=$id, type=${this::class.simpleName}) has no raw koogMessages"
        )
    }
    return rawMessages
}

public const val SCRIPT_TOOL_NAME_METADATA_KEY: String = "toolName"
public const val SCRIPT_TOOL_ARGS_METADATA_KEY: String = "toolArgs"
public const val SCRIPT_RESULT_MODE_METADATA_KEY: String = "resultMode"
public const val SCRIPT_RESULT_MODE_CALL_RESULT: String = "call_result"
public const val SCRIPT_RESULT_MODE_RESULT_ONLY: String = "result_only"
public const val DEFAULT_SCRIPT_TOOL_NAME: String = ToolNames.EXECUTE_KOTLIN_SCRIPT
public const val DEFAULT_SCRIPT_TOOL_ARGS: String = "{}"
