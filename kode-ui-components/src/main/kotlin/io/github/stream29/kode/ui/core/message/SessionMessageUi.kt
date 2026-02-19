package io.github.stream29.kode.ui.core.message

import io.github.stream29.kode.session.core.model.ResumeMessage
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SuspendMessage
import io.github.stream29.kode.session.core.model.ToolExchangeMessage
import io.github.stream29.kode.session.core.model.UserMessage
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

private val prettyJson: Json = Json {
    prettyPrint = true
}

private val primaryToolCallTextKeys: List<String> = listOf("message", "text", "content", "prompt", "input")

private enum class UiRole {
    USER,
    ASSISTANT,
    TOOL,
}

public fun SessionMessage.isUiError(): Boolean {
    return when (this) {
        is ToolExchangeMessage -> isError
        is ResumeMessage -> isError
        else -> false
    }
}

public fun SessionMessage.isAssistantToolPlan(): Boolean = false

public fun SessionMessage.isUiToolCallLike(): Boolean = uiRole() == UiRole.TOOL

public fun SessionMessage.isToolRoleUi(): Boolean = uiRole() == UiRole.TOOL

public fun SessionMessage.isSystemRoleUi(): Boolean = false

public fun SessionMessage.isUserRoleUi(): Boolean = uiRole() == UiRole.USER

public fun SessionMessage.shouldExpandByDefaultUi(): Boolean {
    return uiRole() != UiRole.TOOL
}

public fun SessionMessage.collapsedTitleUi(): String {
    val toolSuffix = extractToolName()?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    return when {
        this is SuspendMessage -> "Suspend$toolSuffix"
        this is ResumeMessage -> "Resume$toolSuffix"
        isToolRoleUi() && isUiError() -> "Tool error$toolSuffix"
        isToolRoleUi() -> "Tool exchange$toolSuffix"
        isUserRoleUi() -> "User message"
        else -> "Assistant message"
    }
}

public fun SessionMessage.collapsedPreviewUi(maxLength: Int = 120): String {
    val source = when {
        isToolRoleUi() -> {
            val args = extractToolCallArgumentsText()?.takeIf { it.isNotBlank() }
            val result = extractToolResultText()?.takeIf { it.isNotBlank() }
            args ?: result ?: uiDisplayContent()
        }

        else -> uiDisplayContent()
    }
    val normalized = source.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) {
        return "(empty)"
    }
    return if (normalized.length > maxLength) {
        normalized.take(maxLength) + "..."
    } else {
        normalized
    }
}

public fun SessionMessage.projectedTextForSessionSummary(): String? {
    return when (this) {
        is UserMessage -> content.trim().takeIf { it.isNotBlank() }
        is SuspendMessage -> extractToolCallPrimaryTextArg()?.trim()?.takeIf { it.isNotBlank() }
        is ResumeMessage -> extractToolResultText()?.trim()?.takeIf { it.isNotBlank() }
        is ToolExchangeMessage -> {
            if (isSayToUserToolCall() || isUserInterruptTool()) {
                (extractToolCallPrimaryTextArg() ?: extractToolResultText())?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }
}

public fun SessionMessage.uiDisplayContent(): String {
    return when (this) {
        is UserMessage -> content
        is SuspendMessage -> extractToolCallPrimaryTextArg() ?: "Waiting for user input"
        is ResumeMessage -> extractToolResultText().orEmpty()
        is ToolExchangeMessage -> {
            if (isSayToUserToolCall()) {
                extractToolCallPrimaryTextArg() ?: extractToolResultText().orEmpty()
            } else if (isUserInterruptTool()) {
                extractToolCallPrimaryTextArg() ?: extractToolResultText().orEmpty()
            } else if (isError) {
                val errorLine = errorMessage?.takeIf { it.isNotBlank() } ?: "Tool execution failed"
                "$errorLine\n${extractToolResultText().orEmpty()}".trim()
            } else {
                ""
            }
        }
    }
}

public fun SessionMessage.extractToolName(): String? {
    return when (this) {
        is ToolExchangeMessage -> toolName
        is SuspendMessage -> toolName
        is ResumeMessage -> toolName
        else -> null
    }
}

public fun SessionMessage.extractToolCallId(): String? {
    return when (this) {
        is ToolExchangeMessage -> toolCallId
        is SuspendMessage -> toolCallId
        is ResumeMessage -> toolCallId
        else -> null
    }
}

public fun SessionMessage.isSayToUserToolCall(): Boolean {
    return extractToolName() == ToolNames.SAY_TO_USER
}

public fun SessionMessage.isSayToUserToolResult(): Boolean {
    return this is ToolExchangeMessage && toolName == ToolNames.SAY_TO_USER
}

public fun SessionMessage.isAwaitUserInputToolCall(): Boolean {
    return this is SuspendMessage && isAwaitUserInputToolName(toolName)
}

public fun SessionMessage.isAwaitUserInputToolResult(): Boolean {
    return this is ResumeMessage && isAwaitUserInputToolName(toolName)
}

public fun SessionMessage.isUserInterruptTool(): Boolean {
    return extractToolName() == ToolNames.USER_INTERRUPT
}

public fun SessionMessage.extractToolCallPrimaryTextArg(): String? {
    val args = when (this) {
        is ToolExchangeMessage -> arguments
        is SuspendMessage -> arguments
        else -> null
    } ?: return null

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
    val args = when (this) {
        is ToolExchangeMessage -> arguments
        is SuspendMessage -> arguments
        else -> null
    } ?: return null
    return args.toReadableText()
}

public fun SessionMessage.extractToolResultText(): String? {
    return when (this) {
        is ToolExchangeMessage -> {
            val resultText = result.toReadableText()
            if (!isError) {
                resultText
            } else {
                val errorLine = errorMessage?.takeIf { it.isNotBlank() }
                if (errorLine == null) {
                    "[error]\n$resultText"
                } else {
                    "[error] $errorLine\n$resultText"
                }
            }
        }

        is ResumeMessage -> {
            val resultText = result.toReadableText()
            if (!isError) {
                resultText
            } else {
                val errorLine = errorMessage?.takeIf { it.isNotBlank() }
                if (errorLine == null) {
                    "[error]\n$resultText"
                } else {
                    "[error] $errorLine\n$resultText"
                }
            }
        }

        else -> null
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

public fun SessionMessage.toolGroupPayloadOrNull(): ToolGroupPayload? {
    return when (this) {
        is SuspendMessage -> ToolGroupPayload.Call(
            toolName = toolName,
            callId = toolCallId,
            argumentsText = extractToolCallArgumentsText().orEmpty(),
            messageId = id,
        )

        is ResumeMessage -> ToolGroupPayload.Result(
            toolName = toolName,
            callId = toolCallId,
            resultText = extractToolResultText().orEmpty(),
            messageId = id,
        )

        else -> null
    }
}

public fun SessionMessage.messageTypeNameUi(): String {
    return messageTypeName(this::class)
}

private fun resolveUiRole(message: SessionMessage): UiRole {
    return when (message) {
        is UserMessage -> UiRole.USER
        is ResumeMessage -> UiRole.USER
        is SuspendMessage -> UiRole.ASSISTANT
        is ToolExchangeMessage -> {
            if (message.isSayToUserToolCall()) {
                UiRole.ASSISTANT
            } else if (message.isUserInterruptTool()) {
                UiRole.USER
            } else {
                UiRole.TOOL
            }
        }
    }
}

private fun SessionMessage.uiRole(): UiRole = resolveUiRole(this)

private fun JsonElement.toReadableText(): String {
    return when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> prettyJson.encodeToString(JsonElement.serializer(), this)
    }
}

private fun isAwaitUserInputToolName(toolName: String?): Boolean {
    return toolName == ToolNames.WAIT_FOR_USER_INPUT
}

private fun messageTypeName(type: KClass<out SessionMessage>): String {
    return type.simpleName ?: "AgentMessage"
}
