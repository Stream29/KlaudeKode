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
