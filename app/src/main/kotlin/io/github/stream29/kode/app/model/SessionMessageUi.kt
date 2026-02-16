package io.github.stream29.kode.app.model

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.model.ContentType
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.ToolCallData
import io.github.stream29.kode.session.core.model.ToolResultData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val prettyJson: Json = Json {
    prettyPrint = true
}

private val primaryToolCallTextKeys: List<String> = listOf("message", "text", "content", "prompt", "input")

public fun SessionMessage.isUiError(): Boolean = contentType == ContentType.ERROR

public fun SessionMessage.isAssistantToolPlan(): Boolean =
    role == MessageRole.ASSISTANT && metadata?.get("source") == "assistant_tool_plan"

public fun SessionMessage.isUiToolCallLike(): Boolean =
    role == MessageRole.TOOL_CALL || role == MessageRole.TOOL_RESULT || isAssistantToolPlan()

public fun SessionMessage.isToolRoleUi(): Boolean {
    return role == MessageRole.TOOL_CALL || role == MessageRole.TOOL_RESULT
}

public fun SessionMessage.isSystemRoleUi(): Boolean {
    return role == MessageRole.SYSTEM
}

public fun SessionMessage.isUserRoleUi(): Boolean {
    return role == MessageRole.USER
}

public fun SessionMessage.shouldExpandByDefaultUi(): Boolean {
    return when (role) {
        MessageRole.USER -> true
        MessageRole.ASSISTANT -> !isUiToolCallLike() && !isUiError()
        MessageRole.SYSTEM,
        MessageRole.TOOL_CALL,
        MessageRole.TOOL_RESULT,
        -> false
    }
}

public fun SessionMessage.collapsedTitleUi(): String {
    val toolSuffix = extractToolName()?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    return when (role) {
        MessageRole.SYSTEM -> "System message"
        MessageRole.TOOL_CALL -> "Tool call$toolSuffix"
        MessageRole.TOOL_RESULT -> if (isUiError()) {
            "Tool error$toolSuffix"
        } else {
            "Tool result$toolSuffix"
        }

        MessageRole.ASSISTANT -> if (isAssistantToolPlan()) {
            "Assistant tool plan"
        } else {
            "Assistant message"
        }

        MessageRole.USER -> "User message"
    }
}

public fun SessionMessage.collapsedPreviewUi(maxLength: Int = 120): String {
    val content = when (role) {
        MessageRole.TOOL_CALL -> extractToolCallArgumentsText() ?: this.content
        MessageRole.TOOL_RESULT -> extractToolResultText() ?: this.content
        else -> this.content
    }
    val normalized = content.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) {
        return "(empty)"
    }
    return if (normalized.length > maxLength) {
        normalized.take(maxLength) + "..."
    } else {
        normalized
    }
}

public fun SessionMessage.toolGroupPayloadOrNull(): ToolGroupPayload? {
    return when (role) {
        MessageRole.TOOL_CALL -> ToolGroupPayload.Call(
            toolName = extractToolName(),
            callId = extractToolCallId(),
            argumentsText = extractToolCallArgumentsText()?.takeIf { it.isNotBlank() } ?: content,
            messageId = id,
        )

        MessageRole.TOOL_RESULT -> ToolGroupPayload.Result(
            toolName = extractToolName(),
            callId = extractToolCallId(),
            resultText = extractToolResultText()?.takeIf { it.isNotBlank() } ?: content,
            messageId = id,
        )

        else -> null
    }
}

public fun SessionMessage.projectedTextForSessionSummary(): String? {
    return when (role) {
        MessageRole.USER,
        MessageRole.ASSISTANT,
        -> content.trim().takeIf { it.isNotBlank() }

        MessageRole.TOOL_CALL -> {
            if (isSayToUserToolCall() || isAwaitUserInputToolCall()) {
                extractToolCallPrimaryTextArg()?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        MessageRole.TOOL_RESULT -> {
            if (isAwaitUserInputToolResult()) {
                extractToolResultText()?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        MessageRole.SYSTEM -> null
    }
}

public sealed interface ToolGroupPayload {
    public val toolName: String?
    public val callId: String?
    public val messageId: String

    public data class Call(
        override val toolName: String?,
        override val callId: String?,
        val argumentsText: String,
        override val messageId: String,
    ) : ToolGroupPayload

    public data class Result(
        override val toolName: String?,
        override val callId: String?,
        val resultText: String,
        override val messageId: String,
    ) : ToolGroupPayload
}

public fun SessionMessage.extractToolName(): String? {
    return when (role) {
        MessageRole.TOOL_CALL -> decodeToolCallData()?.toolName ?: (koogMessage as? Message.Tool.Call)?.tool
        MessageRole.TOOL_RESULT -> decodeToolResultData()?.toolName ?: (koogMessage as? Message.Tool.Result)?.tool
        else -> null
    }
}

public fun SessionMessage.extractToolCallId(): String? {
    return when (role) {
        MessageRole.TOOL_CALL -> decodeToolCallData()?.toolCallId ?: (koogMessage as? Message.Tool.Call)?.id
        MessageRole.TOOL_RESULT -> decodeToolResultData()?.toolCallId ?: (koogMessage as? Message.Tool.Result)?.id
        else -> null
    }
}

public fun SessionMessage.isSayToUserToolCall(): Boolean =
    role == MessageRole.TOOL_CALL && normalizeToolName(extractToolName()) == SAY_TO_USER_KEY

public fun SessionMessage.isSayToUserToolResult(): Boolean =
    role == MessageRole.TOOL_RESULT && normalizeToolName(extractToolName()) == SAY_TO_USER_KEY

public fun SessionMessage.isAwaitUserInputToolCall(): Boolean =
    role == MessageRole.TOOL_CALL && isAwaitUserInputToolName(extractToolName())

public fun SessionMessage.isAwaitUserInputToolResult(): Boolean =
    role == MessageRole.TOOL_RESULT && isAwaitUserInputToolName(extractToolName())

public fun SessionMessage.extractToolCallPrimaryTextArg(): String? {
    if (role != MessageRole.TOOL_CALL) {
        return null
    }

    val args = decodeToolCallData()?.arguments ?: return null
    if (args is JsonPrimitive && args.isString) {
        return args.content
    }

    val objectValue = runCatching { args.jsonObject }.getOrNull() ?: return null
    for (key in primaryToolCallTextKeys) {
        val rawValue = objectValue[key]?.jsonPrimitive?.contentOrNull?.trim()
        if (!rawValue.isNullOrBlank()) {
            return rawValue
        }
    }
    return null
}

public fun SessionMessage.extractToolCallArgumentsText(): String? {
    if (role != MessageRole.TOOL_CALL) {
        return null
    }
    return decodeToolCallData()?.arguments?.toReadableText()
}

public fun SessionMessage.extractToolResultText(): String? {
    if (role != MessageRole.TOOL_RESULT) {
        return null
    }

    val data = decodeToolResultData() ?: return null
    val resultText = data.result.toReadableText()
    if (!data.isError) {
        return resultText
    }

    val errorLine = data.errorMessage?.takeIf { it.isNotBlank() }
    return if (errorLine == null) {
        "[error]\n$resultText"
    } else {
        "[error] $errorLine\n$resultText"
    }
}

private fun SessionMessage.decodeToolCallData(): ToolCallData? {
    return structuredData?.decode(ToolCallData.serializer())
}

private fun SessionMessage.decodeToolResultData(): ToolResultData? {
    return structuredData?.decode(ToolResultData.serializer())
}

private fun <T> JsonElement.decode(deserializer: KSerializer<T>): T? {
    return runCatching {
        Json.decodeFromJsonElement(deserializer, this)
    }.getOrNull()
}

private fun JsonElement.toReadableText(): String {
    return when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> prettyJson.encodeToString(JsonElement.serializer(), this)
    }
}

private fun normalizeToolName(name: String?): String? {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    return trimmed.replace("_", "").lowercase()
}

private fun isAwaitUserInputToolName(toolName: String?): Boolean {
    val normalized = normalizeToolName(toolName)
    return normalized == AWAIT_USER_INPUT_KEY || normalized == WAIT_FOR_USER_INPUT_KEY
}

private const val SAY_TO_USER_KEY: String = "saytouser"
private const val AWAIT_USER_INPUT_KEY: String = "awaituserinput"
private const val WAIT_FOR_USER_INPUT_KEY: String = "waitforuserinput"
