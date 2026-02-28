package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler

public class SubAgent(
    promptExecutor: PromptExecutor,
    sessionManager: SessionManager,
    sessionBridge: KoogSessionBridge,
    messageHandler: MessageHandler,
    hookManager: HookManager,
    eventListener: AgentEventListener?,
    logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
    private val scriptContextFactory: () -> MainAgentScriptContext = { MainAgentScriptContext() },
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
        scriptContextFactory = scriptContextFactory,
    )

    override suspend fun run(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireNotNull(runtimeContext.agentId) { "Subagent context requires agentId" }
        return engine.run(
            sessionId = sessionId,
            model = model,
            modelParams = modelParams,
        ).orEmpty()
    }
}
