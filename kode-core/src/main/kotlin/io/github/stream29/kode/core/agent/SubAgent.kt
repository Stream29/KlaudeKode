package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.agent.model.TodoItem
import kotlinx.coroutines.flow.MutableStateFlow

public class SubAgentImpl(
    promptExecutor: PromptExecutor,
    sessionManager: SessionManager,
    sessionBridge: KoogSessionBridge,
    messageHandler: MessageHandler,
    eventListener: AgentEventListener?,
    logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
    private val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext =
        { initialTodos, activeTodoFlow ->
            SubAgentScriptContext(initialTodos = initialTodos, activeTodoFlow = activeTodoFlow)
        },
) : SubAgent {
    private val engine = ScriptOnlyAgentEngine(
        promptExecutor = promptExecutor,
        sessionManager = sessionManager,
        sessionBridge = sessionBridge,
        messageHandler = messageHandler,
        eventListener = eventListener,
        logger = logger,
        runtimeContext = runtimeContext,
        scriptContextFactory = scriptContextFactory,
    )

    init {
        check(!runtimeContext.canInteractWithUser) {
            "Subagent runtime must disable direct user interaction"
        }
        check(!runtimeContext.canCreateSubagents) {
            "Subagent runtime must disable subagent creation"
        }
    }

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
