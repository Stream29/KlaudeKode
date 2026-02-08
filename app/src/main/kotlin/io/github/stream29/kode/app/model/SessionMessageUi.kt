@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.model

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.model.ContentType
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.ToolCallData
import io.github.stream29.kode.session.core.model.ToolResultData
import kotlinx.serialization.json.Json

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
    return when {
        role == MessageRole.TOOL_CALL -> {
            structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolCallData.serializer(), element).toolName
                }.getOrNull()
            } ?: (koogMessage as? Message.Tool.Call)?.tool
        }

        role == MessageRole.TOOL_RESULT -> {
            structuredData?.let { element ->
                runCatching {
                    Json.decodeFromJsonElement(ToolResultData.serializer(), element).toolName
                }.getOrNull()
            } ?: (koogMessage as? Message.Tool.Result)?.tool
        }

        else -> null
    }
}
