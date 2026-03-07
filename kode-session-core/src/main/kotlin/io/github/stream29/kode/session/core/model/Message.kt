package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

@Serializable(with = AgentMessageSerializer::class)
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
) : AgentMessage {
    public val awaitForUserInput: Boolean
        get() = status == AgentScriptStatus.PENDING_INPUT
}

public fun SessionMessage.pendingInputScriptOrNull(): AgentScript? {
    val script = this as? AgentScript ?: return null
    return if (script.status == AgentScriptStatus.PENDING_INPUT) {
        script
    } else {
        null
    }
}

public fun List<SessionMessage>.trailingPendingInputScriptOrNull(): AgentScript? {
    return lastOrNull()?.pendingInputScriptOrNull()
}

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

public object AgentMessageSerializer : kotlinx.serialization.KSerializer<AgentMessage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AgentMessage")

    override fun serialize(encoder: Encoder, value: AgentMessage) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("AgentMessage serialization requires JSON")
        val element = when (value) {
            is UserMessage -> jsonEncoder.json.encodeToJsonElement(UserMessage.serializer(), value)
            is AgentScript -> jsonEncoder.json.encodeToJsonElement(AgentScript.serializer(), value)
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): AgentMessage {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AgentMessage deserialization requires JSON")
        val element = jsonDecoder.decodeJsonElement()
        val payload = element as? JsonObject
            ?: throw SerializationException("AgentMessage payload must be a JSON object")
        val type = payload[TYPE_KEY]?.jsonPrimitive?.contentOrNull
        val json = jsonDecoder.json

        return when (type) {
            USER_TYPE -> json.decodeFromJsonElement(UserMessage.serializer(), element)
            SCRIPT_TYPE -> json.decodeFromJsonElement(AgentScript.serializer(), element)
            LEGACY_TOOL_EXCHANGE_TYPE -> json.decodeFromJsonElement(
                deserializer = LegacyToolExchangeMessage.serializer(),
                element = element,
            ).toAgentScript()

            LEGACY_SUSPEND_TYPE -> json.decodeFromJsonElement(
                deserializer = LegacySuspendMessage.serializer(),
                element = element,
            ).toAgentScript()

            LEGACY_RESUME_TYPE -> json.decodeFromJsonElement(
                deserializer = LegacyResumeMessage.serializer(),
                element = element,
            ).toAgentScript()

            null -> decodeCurrentShapeWithoutType(json = json, element = element)
            else -> throw SerializationException("Unsupported AgentMessage type '$type'")
        }
    }

    private fun decodeCurrentShapeWithoutType(json: Json, element: JsonElement): AgentMessage {
        val payload = element.jsonObject
        return when {
            "scriptId" in payload -> json.decodeFromJsonElement(AgentScript.serializer(), element)
            "content" in payload && "koogMessages" in payload ->
                json.decodeFromJsonElement(UserMessage.serializer(), element)

            else -> throw SerializationException("Unsupported AgentMessage payload without type discriminator")
        }
    }

    private const val TYPE_KEY: String = "type"
    private const val USER_TYPE: String = "user"
    private const val SCRIPT_TYPE: String = "script"
    private const val LEGACY_TOOL_EXCHANGE_TYPE: String = "tool_exchange"
    private const val LEGACY_SUSPEND_TYPE: String = "suspend"
    private const val LEGACY_RESUME_TYPE: String = "resume"
}

@Serializable
private data class LegacyToolExchangeMessage(
    val id: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val result: JsonElement,
    val isError: Boolean,
    val errorMessage: String?,
    val displayName: String? = null,
    val timestamp: Instant,
    val metadata: Map<String, String>? = null,
)

@Serializable
private data class LegacySuspendMessage(
    val id: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: JsonElement,
    val displayName: String? = null,
    val timestamp: Instant,
    val metadata: Map<String, String>? = null,
)

@Serializable
private data class LegacyResumeMessage(
    val id: String,
    val toolName: String,
    val toolCallId: String,
    val result: JsonElement,
    val isError: Boolean,
    val errorMessage: String?,
    val timestamp: Instant,
    val metadata: Map<String, String>? = null,
)

private fun LegacyToolExchangeMessage.toAgentScript(): AgentScript {
    val toolArgs = arguments.toString()
    val resultText = result.toMessageContent()
    return AgentScript(
        id = id,
        scriptId = toolCallId,
        status = if (isError) AgentScriptStatus.FAILED else AgentScriptStatus.COMPLETED,
        scriptReturnValue = resultText,
        scriptStdout = toolArgs,
        error = errorMessage,
        outputList = emptyList(),
        timestamp = timestamp,
        koogMessages = listOf(
            toolCallMessage(
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgs = toolArgs,
                timestamp = timestamp,
            ),
            toolResultMessage(
                toolCallId = toolCallId,
                toolName = toolName,
                result = resultText,
                timestamp = timestamp,
            ),
        ),
        metadata = metadata.withScriptMetadata(
            toolName = toolName,
            toolArgs = toolArgs,
            resultMode = SCRIPT_RESULT_MODE_CALL_RESULT,
        ),
    )
}

private fun LegacySuspendMessage.toAgentScript(): AgentScript {
    val toolArgs = arguments.toString()
    return AgentScript(
        id = id,
        scriptId = toolCallId,
        status = AgentScriptStatus.PENDING_INPUT,
        scriptReturnValue = null,
        scriptStdout = toolArgs,
        error = null,
        outputList = emptyList(),
        timestamp = timestamp,
        koogMessages = listOf(
            toolCallMessage(
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgs = toolArgs,
                timestamp = timestamp,
            )
        ),
        metadata = metadata.withScriptMetadata(
            toolName = toolName,
            toolArgs = toolArgs,
            resultMode = SCRIPT_RESULT_MODE_CALL_RESULT,
        ),
    )
}

private fun LegacyResumeMessage.toAgentScript(): AgentScript {
    val resultText = result.toMessageContent()
    return AgentScript(
        id = id,
        scriptId = toolCallId,
        status = if (isError) AgentScriptStatus.FAILED else AgentScriptStatus.COMPLETED,
        scriptReturnValue = resultText,
        scriptStdout = DEFAULT_SCRIPT_TOOL_ARGS,
        error = errorMessage,
        outputList = emptyList(),
        timestamp = timestamp,
        koogMessages = listOf(
            toolResultMessage(
                toolCallId = toolCallId,
                toolName = toolName,
                result = resultText,
                timestamp = timestamp,
            )
        ),
        metadata = metadata.withScriptMetadata(
            toolName = toolName,
            toolArgs = null,
            resultMode = SCRIPT_RESULT_MODE_RESULT_ONLY,
        ),
    )
}

private fun Map<String, String>?.withScriptMetadata(
    toolName: String,
    toolArgs: String?,
    resultMode: String,
): Map<String, String>? {
    val merged = this.orEmpty().toMutableMap()
    merged.putIfAbsent(SCRIPT_TOOL_NAME_METADATA_KEY, toolName)
    if (!toolArgs.isNullOrBlank()) {
        merged.putIfAbsent(SCRIPT_TOOL_ARGS_METADATA_KEY, toolArgs)
    }
    merged.putIfAbsent(SCRIPT_RESULT_MODE_METADATA_KEY, resultMode)
    return merged.takeIf { it.isNotEmpty() }
}

@Suppress("DEPRECATION")
private fun toolCallMessage(
    toolCallId: String,
    toolName: String,
    toolArgs: String,
    timestamp: Instant,
): Message.Tool.Call {
    return Message.Tool.Call(
        id = toolCallId,
        tool = toolName,
        content = toolArgs,
        metaInfo = ResponseMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
    )
}

@Suppress("DEPRECATION")
private fun toolResultMessage(
    toolCallId: String,
    toolName: String,
    result: String,
    timestamp: Instant,
): Message.Tool.Result {
    return Message.Tool.Result(
        id = toolCallId,
        tool = toolName,
        content = result,
        metaInfo = RequestMetaInfo(timestamp = timestamp.toDeprecatedInstant()),
    )
}

private fun JsonElement.toMessageContent(): String {
    return when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> toString()
    }
}
