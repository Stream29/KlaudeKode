package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

public class MainAgent(
    promptExecutor: PromptExecutor,
    private val sessionManager: SessionManager,
    sessionBridge: KoogSessionBridge,
    private val messageHandler: MessageHandler,
    private val hookManager: HookManager,
    eventListener: AgentEventListener?,
    logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
) : Agent {
    private val engine = ScriptOnlyAgentEngine(
        promptExecutor = promptExecutor,
        sessionManager = sessionManager,
        sessionBridge = sessionBridge,
        messageHandler = messageHandler,
        hookManager = hookManager,
        eventListener = eventListener,
        logger = logger,
        runtimeContext = runtimeContext,
    )

    public suspend fun chat(
        sessionId: String,
        userInput: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireInteractiveContext("Subagent cannot accept direct user chat")
        return withManagedSessionRun(sessionId) {
            val processedInput = hookManager.applyUserPromptHooks(sessionId, userInput)
            sessionManager.addUserMessage(sessionId, processedInput, runtimeContext.agentId)
            engine.run(
                sessionId = sessionId,
                model = model,
                modelParams = modelParams,
            )
        }.orEmpty()
    }

    override suspend fun run(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireInteractiveContext("Subagent cannot continue user session directly")
        return withManagedSessionRun(sessionId) {
            engine.run(
                sessionId = sessionId,
                model = model,
                modelParams = modelParams,
            )
        }.orEmpty()
    }

    private fun requireInteractiveContext(message: String) {
        if (!runtimeContext.canInteractWithUser) {
            throw IllegalStateException(message)
        }
    }

    private suspend fun <T> withManagedSessionRun(sessionId: String, block: suspend () -> T): T {
        sessionManager.beginRun(sessionId, currentJob())
        return try {
            block()
        } finally {
            sessionManager.completeRun(sessionId)
        }
    }

    private suspend fun currentJob(): Job {
        val job = currentCoroutineContext()[Job]
        return requireNotNull(job) { "MainAgent requires coroutine Job context" }
    }

    public companion object {
        public val DEFAULT_SYSTEM_PROMPT: String = ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT
    }
}
