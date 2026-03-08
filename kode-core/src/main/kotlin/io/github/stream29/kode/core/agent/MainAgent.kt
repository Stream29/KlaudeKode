package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow

public class MainAgentImpl(
    promptExecutor: PromptExecutor,
    private val sessionManager: SessionManager,
    private val messageHandler: MessageHandler,
    eventListener: AgentEventListener?,
    logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
    private val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext =
        { initialTodos, activeTodoFlow ->
            MainAgentScriptContext(initialTodos = initialTodos, activeTodoFlow = activeTodoFlow)
        },
    private val runtimeSideEffectPort: RuntimeSideEffectPort? = null,
    private val sessionSideEffectPort: SessionSideEffectPort? = null,
) : MainAgent {
    private val engine = buildEngine(
        promptExecutor = promptExecutor,
        eventListener = eventListener,
        logger = logger,
    )

    init {
        val hasCustomRuntimePort = runtimeSideEffectPort != null
        val hasCustomSessionPort = sessionSideEffectPort != null
        if (hasCustomRuntimePort || hasCustomSessionPort) {
            check(hasCustomRuntimePort && hasCustomSessionPort) {
                "Custom side-effect wiring requires runtime/session ports to be provided together"
            }
        }
    }

    override suspend fun chat(
        sessionId: String,
        userInput: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireInteractiveContext("Subagent cannot accept direct user chat")
        return withManagedSessionRun(sessionId) {
            sessionManager.addUserMessage(sessionId, userInput, runtimeContext.agentId)
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

    private fun buildEngine(
        promptExecutor: PromptExecutor,
        eventListener: AgentEventListener?,
        logger: (String) -> Unit,
    ): ScriptOnlyAgentEngine {
        if (runtimeSideEffectPort == null || sessionSideEffectPort == null) {
            return ScriptOnlyAgentEngine(
                promptExecutor = promptExecutor,
                sessionManager = sessionManager,
                messageHandler = messageHandler,
                eventListener = eventListener,
                logger = logger,
                runtimeContext = runtimeContext,
                scriptContextFactory = scriptContextFactory,
            )
        }

        return ScriptOnlyAgentEngine(
            promptExecutor = promptExecutor,
            sessionManager = sessionManager,
            messageHandler = messageHandler,
            eventListener = eventListener,
            logger = logger,
            runtimeContext = runtimeContext,
            scriptContextFactory = scriptContextFactory,
            runtimeSideEffectPort = runtimeSideEffectPort,
            sessionSideEffectPort = sessionSideEffectPort,
        )
    }

    private suspend fun currentJob(): Job {
        val job = currentCoroutineContext()[Job]
        return requireNotNull(job) { "MainAgent requires coroutine Job context" }
    }

}
