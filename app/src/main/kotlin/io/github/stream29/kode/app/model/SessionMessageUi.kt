@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.model

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.model.ContentType
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.ToolCallData
import io.github.stream29.kode.session.core.model.ToolResultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val prettyJson: Json = Json {
    prettyPrint = true
}

public fun SessionMessage.isUiError(): Boolean {
    return contentType == ContentType.ERROR
}

public fun SessionMessage.isAssistantToolPlan(): Boolean {
    return role == MessageRole.ASSISTANT && metadata?.get("source") == "assistant_tool_plan"
}

public fun SessionMessage.isUiToolCallLike(): Boolean {
    return role == MessageRole.TOOL_CALL || role == MessageRole.TOOL_RESULT || isAssistantToolPlan()
}

public fun SessionMessage.extractToolName(): String? {
    return when (role) {
        MessageRole.TOOL_CALL -> {
            structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolCallData.serializer(), element).toolName
                }.getOrNull()
            } ?: (koogMessage as? Message.Tool.Call)?.tool
        }

        MessageRole.TOOL_RESULT -> {
            structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolResultData.serializer(), element).toolName
                }.getOrNull()
            } ?: (koogMessage as? Message.Tool.Result)?.tool
        }

        else -> null
    }
}

public fun SessionMessage.isSayToUserToolCall(): Boolean {
    return role == MessageRole.TOOL_CALL && normalizeToolName(extractToolName()) == SAY_TO_USER_KEY
}

public fun SessionMessage.isSayToUserToolResult(): Boolean {
    return role == MessageRole.TOOL_RESULT && normalizeToolName(extractToolName()) == SAY_TO_USER_KEY
}

public fun SessionMessage.isAwaitUserInputToolCall(): Boolean {
    if (role != MessageRole.TOOL_CALL) {
        return false
    }
    val normalized = normalizeToolName(extractToolName())
    return normalized == AWAIT_USER_INPUT_KEY || normalized == WAIT_FOR_USER_INPUT_KEY
}

public fun SessionMessage.isAwaitUserInputToolResult(): Boolean {
    if (role != MessageRole.TOOL_RESULT) {
        return false
    }
    val normalized = normalizeToolName(extractToolName())
    return normalized == AWAIT_USER_INPUT_KEY || normalized == WAIT_FOR_USER_INPUT_KEY
}

public fun SessionMessage.extractToolCallPrimaryTextArg(): String? {
    if (role != MessageRole.TOOL_CALL) {
        return null
    }
    val data = structuredData?.let { element ->
        runCatching {
            Json.decodeFromJsonElement(ToolCallData.serializer(), element)
        }.getOrNull()
    } ?: return null

    val args = data.arguments
    if (args is JsonPrimitive && args.isString) {
        return args.content
    }

    val objectValue = runCatching { args.jsonObject }.getOrNull() ?: return null
    val candidateKeys = listOf("message", "text", "content", "prompt", "input")
    for (key in candidateKeys) {
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
    val data = structuredData?.let { element ->
        runCatching {
            Json.decodeFromJsonElement(ToolCallData.serializer(), element)
        }.getOrNull()
    }
    return data?.arguments?.toReadableText()
}

public fun SessionMessage.extractToolResultText(): String? {
    if (role != MessageRole.TOOL_RESULT) {
        return null
    }
    val data = structuredData?.let { element ->
        runCatching {
            Json.decodeFromJsonElement(ToolResultData.serializer(), element)
        }.getOrNull()
    } ?: return null

    val resultText = data.result.toReadableText()
    return if (data.isError) {
        val errorLine = data.errorMessage?.takeIf { it.isNotBlank() }
        if (errorLine == null) {
            "[error]\n$resultText"
        } else {
            "[error] $errorLine\n$resultText"
        }
    } else {
        resultText
    }
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

private const val SAY_TO_USER_KEY: String = "saytouser"
private const val AWAIT_USER_INPUT_KEY: String = "awaituserinput"
private const val WAIT_FOR_USER_INPUT_KEY: String = "waitforuserinput"
