package io.github.stream29.kode.agent.model

import ai.koog.prompt.message.Message
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
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
        val type = when (value) {
            is UserMessage -> USER_TYPE
            is AgentScript -> SCRIPT_TYPE
        }
        val element = when (value) {
            is UserMessage -> jsonEncoder.json.encodeToJsonElement(UserMessage.serializer(), value)
            is AgentScript -> jsonEncoder.json.encodeToJsonElement(AgentScript.serializer(), value)
        }
        val payload = element.jsonObject.toMutableMap()
        payload[TYPE_KEY] = JsonPrimitive(type)
        jsonEncoder.encodeJsonElement(JsonObject(payload))
    }

    override fun deserialize(decoder: Decoder): AgentMessage {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AgentMessage deserialization requires JSON")
        val element = jsonDecoder.decodeJsonElement()
        val payload = element as? JsonObject
            ?: throw SerializationException("AgentMessage payload must be a JSON object")
        val type = payload[TYPE_KEY]?.jsonPrimitive?.contentOrNull
        val json = jsonDecoder.json
        val payloadWithoutType = JsonObject(payload.filterKeys { key -> key != TYPE_KEY })

        return when (type) {
            USER_TYPE -> json.decodeFromJsonElement(UserMessage.serializer(), payloadWithoutType)
            SCRIPT_TYPE -> json.decodeFromJsonElement(AgentScript.serializer(), payloadWithoutType)
            else -> throw SerializationException("Unsupported AgentMessage type '${type ?: "<missing>"}'")
        }
    }

    private const val TYPE_KEY: String = "type"
    private const val USER_TYPE: String = "user"
    private const val SCRIPT_TYPE: String = "script"
}
