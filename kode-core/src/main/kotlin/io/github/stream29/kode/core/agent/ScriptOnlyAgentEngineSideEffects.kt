package io.github.stream29.kode.core.agent

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.tool.ToolNames
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Job
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

internal class RuntimeSideEffectAdapter(
    private val messageHandler: MessageHandler,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
) : RuntimeSideEffectPort, RuntimeInputPort {
    override fun isSafeStopRequested(sessionId: String): Boolean {
        return messageHandler.isSafeStopRequested(sessionId)
    }

    override fun onSafeStopReached(sessionId: String) {
        messageHandler.onSafeStopReached(sessionId)
    }

    override fun onToolCallStarting(sessionId: String, toolName: String, arguments: String) {
        eventListener?.onEvent(
            AgentEvent.ToolCallStarting(
                toolName = toolName,
                arguments = arguments,
            ),
            sessionId,
        )
    }

    override fun onToolCallCompleted(sessionId: String, toolName: String, result: String) {
        eventListener?.onEvent(
            AgentEvent.ToolCallCompleted(
                toolName = toolName,
                result = result,
            ),
            sessionId,
        )
    }

    override fun onToolCallFailed(sessionId: String, message: String) {
        eventListener?.onEvent(
            AgentEvent.Error(
                message = message,
                exception = null,
            ),
            sessionId,
        )
    }

    override fun log(message: String) {
        logger(message)
    }

    override suspend fun requestInput(sessionId: String): String {
        return messageHandler.requestInput(sessionId)
    }
}

internal class SessionSideEffectAdapter(
    private val sessionManager: SessionManager,
    private val sessionQueryPort: SessionQueryPort,
) : SessionSideEffectPort, SessionRunLifecyclePort {
    override suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> {
        return sessionQueryPort.loadAgentMessages(
            sessionId = sessionId,
            agentId = agentId,
        )
    }

    override suspend fun resolveSystemPrompt(sessionId: String, agentId: String?, fallback: String): String {
        return sessionManager.getAgentConfig(sessionId = sessionId, agentId = agentId).systemPrompt ?: fallback
    }

    override suspend fun suspendForUserInput(sessionId: String) {
        sessionManager.suspendForUserInput(sessionId)
    }

    override suspend fun saveToolExchange(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        outputList: List<String>,
        awaitForUserInput: Boolean,
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
            status = when {
                awaitForUserInput -> AgentScriptStatus.PENDING_INPUT
                isError -> AgentScriptStatus.FAILED
                else -> AgentScriptStatus.COMPLETED
            },
            scriptReturnValue = result.toMessageContent(),
            scriptStdout = arguments.toString(),
            error = errorMessage,
            outputList = outputList,
            koogMessages = koogMessages,
            metadata = null,
            agentId = agentId,
        )
    }

    override suspend fun resumeRun(sessionId: String, job: Job) {
        sessionManager.resumeRun(sessionId = sessionId, ownerJob = job)
    }

    override suspend fun addUserMessage(sessionId: String, content: String, agentId: String?) {
        sessionManager.addUserMessage(
            sessionId = sessionId,
            content = content,
            agentId = agentId,
        )
    }
}

internal interface RuntimeInputPort {
    suspend fun requestInput(sessionId: String): String
}

internal interface SessionRunLifecyclePort {
    suspend fun resumeRun(sessionId: String, job: Job)

    suspend fun addUserMessage(sessionId: String, content: String, agentId: String?)
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
