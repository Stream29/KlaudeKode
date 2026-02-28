package io.github.stream29.kode.core.session

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.AgentScriptStatus
import io.github.stream29.kode.session.core.model.toKoogMessages
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

public class KoogSessionBridge(
    private val sessionManager: SessionManager,
) {
    public suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> {
        val messages = sessionManager.getAgentMessages(sessionId, agentId)
        return messages.flatMap { item -> item.toKoogMessages() }
    }

    public suspend fun addUserInput(sessionId: String, userInput: String): List<Message> {
        sessionManager.addUserMessage(sessionId, userInput, null)
        return prepareMessagesForAgent(sessionId, null)
    }

    public suspend fun saveToolExchange(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        outputList: List<String>,
        agentId: String?,
    ) {
        if (toolName != ToolNames.EXECUTE_KOTLIN_SCRIPT) {
            throw IllegalStateException(
                "Script-only violation: tool '$toolName' is not allowed for persistence; " +
                        "only '${ToolNames.EXECUTE_KOTLIN_SCRIPT}' is supported"
            )
        }
        val koogMessages = buildScriptExchangeMessages(
            toolCallId = toolCallId,
            toolArgs = arguments.toString(),
            result = result.toMessageContent(),
        )
        sessionManager.addAgentScriptMessage(
            sessionId = sessionId,
            scriptId = toolCallId,
            status = if (isError) AgentScriptStatus.FAILED else AgentScriptStatus.COMPLETED,
            scriptReturnValue = result.toMessageContent(),
            scriptStdout = arguments.toString(),
            error = errorMessage,
            outputList = outputList,
            koogMessages = koogMessages,
            metadata = null,
            agentId = agentId,
        )
    }

    private fun buildScriptExchangeMessages(
        toolCallId: String,
        toolArgs: String,
        result: String,
    ): List<Message> {
        return listOf(
            Message.Tool.Call(
                id = toolCallId,
                tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                content = toolArgs,
                metaInfo = ResponseMetaInfo.create(Clock.System.toDeprecatedClock()),
            ),
            Message.Tool.Result(
                id = toolCallId,
                tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                content = result,
                metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
            ),
        )
    }

    private fun JsonElement.toMessageContent(): String {
        return when (this) {
            is JsonPrimitive -> if (isString) content else toString()
            else -> toString()
        }
    }
}
