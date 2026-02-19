package io.github.stream29.kode.ui.core.message

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.model.AgentScript
import io.github.stream29.kode.session.core.model.AgentScriptStatus
import io.github.stream29.kode.session.core.model.SCRIPT_TOOL_ARGS_METADATA_KEY
import io.github.stream29.kode.session.core.model.SCRIPT_TOOL_NAME_METADATA_KEY
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.UserMessage
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlin.reflect.KClass

private enum class UiRole {
    USER,
    ASSISTANT,
    TOOL,
}

public fun SessionMessage.isUiError(): Boolean {
    return this is AgentScript && (status == AgentScriptStatus.FAILED || error?.isNotBlank() == true)
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
    return when (this) {
        is UserMessage -> "User message"
        is AgentScript -> when (status) {
            AgentScriptStatus.PENDING_INPUT -> "Script pending$toolSuffix"
            AgentScriptStatus.COMPLETED -> "Script result$toolSuffix"
            AgentScriptStatus.FAILED -> "Script failed$toolSuffix"
            AgentScriptStatus.CANCELLED -> "Script cancelled$toolSuffix"
        }
    }
}

public fun SessionMessage.collapsedPreviewUi(maxLength: Int = 120): String {
    val source = when {
        isToolRoleUi() && isUiError() -> {
            extractToolResultText()?.takeIf { it.isNotBlank() } ?: uiDisplayContent()
        }

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
        is AgentScript -> {
            val projectedOutput = outputList
                .asSequence()
                .map { item -> item.trim() }
                .firstOrNull { item -> item.isNotBlank() }
            when {
                projectedOutput != null -> {
                    projectedOutput
                }

                status == AgentScriptStatus.PENDING_INPUT -> {
                    extractToolCallPrimaryTextArg()?.trim()?.takeIf { it.isNotBlank() }
                }

                else -> null
            }
        }
    }
}

public fun SessionMessage.uiDisplayContent(): String {
    return when (this) {
        is UserMessage -> content
        is AgentScript -> {
            val outputText = outputList.joinToString(separator = "\n")
            when (status) {
                AgentScriptStatus.PENDING_INPUT -> extractToolCallPrimaryTextArg() ?: "Waiting for user input"
                AgentScriptStatus.COMPLETED -> {
                    when {
                        outputList.isNotEmpty() -> outputText
                        isSayToUserToolCall() || isUserInterruptTool() -> {
                            (scriptReturnValue ?: scriptStdout).orEmpty()
                        }

                        else -> (scriptReturnValue ?: scriptStdout).orEmpty()
                    }
                }

                AgentScriptStatus.FAILED,
                AgentScriptStatus.CANCELLED,
                    -> {
                    val errorLine = error?.takeIf { it.isNotBlank() } ?: "Script execution failed"
                    val body = outputText.takeIf { it.isNotBlank() } ?: (scriptReturnValue ?: scriptStdout).orEmpty()
                    "$errorLine\n$body".trim()
                }
            }
        }
    }
}

public fun SessionMessage.extractToolName(): String? {
    return when (this) {
        is AgentScript -> {
            metadata?.get(SCRIPT_TOOL_NAME_METADATA_KEY)
                ?: koogMessages.filterIsInstance<Message.Tool>().firstOrNull()?.tool
        }

        else -> null
    }
}

public fun SessionMessage.extractToolCallId(): String? {
    return when (this) {
        is AgentScript -> scriptId
        else -> null
    }
}

public fun SessionMessage.isSayToUserToolCall(): Boolean {
    return extractToolName() == ToolNames.SAY_TO_USER
}

public fun SessionMessage.isSayToUserToolResult(): Boolean {
    return this is AgentScript && extractToolName() == ToolNames.SAY_TO_USER
}

public fun SessionMessage.isAwaitUserInputToolCall(): Boolean {
    return this is AgentScript &&
            status == AgentScriptStatus.PENDING_INPUT &&
            extractToolName() == ToolNames.WAIT_FOR_USER_INPUT
}

public fun SessionMessage.isAwaitUserInputToolResult(): Boolean {
    return this is AgentScript &&
            status != AgentScriptStatus.PENDING_INPUT &&
            extractToolName() == ToolNames.WAIT_FOR_USER_INPUT
}

public fun SessionMessage.isUserInterruptTool(): Boolean {
    return extractToolName() == ToolNames.USER_INTERRUPT
}

public fun SessionMessage.extractToolCallPrimaryTextArg(): String? {
    return when (this) {
        is AgentScript -> {
            metadata?.get(SCRIPT_TOOL_ARGS_METADATA_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: koogMessages.filterIsInstance<Message.Tool.Call>().lastOrNull()?.content?.takeIf { it.isNotBlank() }
        }

        else -> null
    }
}

public fun SessionMessage.extractToolCallArgumentsText(): String? {
    return when (this) {
        is AgentScript -> {
            metadata?.get(SCRIPT_TOOL_ARGS_METADATA_KEY)
                ?: koogMessages.filterIsInstance<Message.Tool.Call>().lastOrNull()?.content
        }

        else -> null
    }
}

public fun SessionMessage.extractToolResultText(): String? {
    return when (this) {
        is AgentScript -> {
            val body = outputList.joinToString(separator = "\n").takeIf { it.isNotBlank() }
                ?: (scriptReturnValue ?: scriptStdout).orEmpty()
            if (status == AgentScriptStatus.FAILED || status == AgentScriptStatus.CANCELLED) {
                val errorLine = error?.takeIf { it.isNotBlank() }
                if (errorLine == null) {
                    "[error]\n$body"
                } else {
                    "[error] $errorLine\n$body"
                }
            } else {
                body
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
    if (this !is AgentScript) {
        return null
    }
    return if (status == AgentScriptStatus.PENDING_INPUT) {
        ToolGroupPayload.Call(
            toolName = extractToolName(),
            callId = scriptId,
            argumentsText = extractToolCallArgumentsText().orEmpty(),
            messageId = id,
        )
    } else {
        ToolGroupPayload.Result(
            toolName = extractToolName(),
            callId = scriptId,
            resultText = extractToolResultText().orEmpty(),
            messageId = id,
        )
    }
}

public fun SessionMessage.messageTypeNameUi(): String {
    return messageTypeName(this::class)
}

private fun resolveUiRole(message: SessionMessage): UiRole {
    return when (message) {
        is UserMessage -> UiRole.USER
        is AgentScript -> {
            if (message.status == AgentScriptStatus.PENDING_INPUT) {
                UiRole.ASSISTANT
            } else if (message.outputList.isNotEmpty()) {
                UiRole.ASSISTANT
            } else if (message.isSayToUserToolCall()) {
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

private fun messageTypeName(type: KClass<out SessionMessage>): String {
    return type.simpleName ?: "AgentMessage"
}
