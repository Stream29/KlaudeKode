package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.flow.MutableStateFlow

public data class SessionExecutionModelRuntime(
    val model: LLModel,
    val modelParams: LLMParams?,
)

public data class MainAgentFactoryDependencies(
    val promptExecutorProvider: () -> PromptExecutor,
    val sessionManager: SessionManager,
    val messageHandler: MessageHandler,
    val eventListener: AgentEventListener?,
    val logger: (String) -> Unit,
    val runtimeContext: AgentRuntimeContext,
    val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext,
    val runtimeSideEffectPort: RuntimeSideEffectPort?,
    val sessionSideEffectPort: SessionSideEffectPort?,
)

public fun interface MainAgentFactory {
    public fun create(dependencies: MainAgentFactoryDependencies): MainAgent
}

public object DefaultMainAgentFactory : MainAgentFactory {
    override fun create(dependencies: MainAgentFactoryDependencies): MainAgent {
        return MainAgentImpl(
            promptExecutor = dependencies.promptExecutorProvider(),
            sessionManager = dependencies.sessionManager,
            messageHandler = dependencies.messageHandler,
            eventListener = dependencies.eventListener,
            logger = dependencies.logger,
            runtimeContext = dependencies.runtimeContext,
            scriptContextFactory = dependencies.scriptContextFactory,
            runtimeSideEffectPort = dependencies.runtimeSideEffectPort,
            sessionSideEffectPort = dependencies.sessionSideEffectPort,
        )
    }
}

public class SessionExecutionRuntime(
    private val modelCatalogPort: SessionExecutionModelCatalogPort,
    private val messageHandler: MessageHandler,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
    public val sessionManager: SessionManager,
    private val promptExecutorFactory: (List<LlmAuthConfig>) -> PromptExecutor = { resolvedAuths ->
        LlmPromptExecutorFactory.create(resolvedAuths)
    },
    private val modelRuntimeResolver: (String, List<LlmModelConfig>, List<LlmAuthConfig>) -> SessionExecutionModelRuntime =
        { modelId, configuredModels, configuredAuths ->
            val resolved = ModelFactory.resolveModelRuntime(modelId, configuredModels, configuredAuths)
            SessionExecutionModelRuntime(
                model = resolved.model,
                modelParams = resolved.params,
            )
        },
    private val executionContextFactory: SessionExecutionContextFactory? = null,
    private val sessionTitleGeneratorFactory:
    (
        SessionManager,
        SessionExecutionModelCatalogPort,
        (List<LlmAuthConfig>) -> PromptExecutor,
        (String, List<LlmModelConfig>, List<LlmAuthConfig>) -> SessionExecutionModelRuntime,
    ) -> SessionTitleGenerationPort =
        { manager, catalogPort, executorFactory, runtimeResolver ->
            SessionTitleGenerator(
                sessionQueryPort = SessionManagerSessionQueryPort(manager),
                modelCatalogPort = catalogPort,
                promptExecutorFactory = executorFactory,
                modelRuntimeResolver = runtimeResolver,
            )
        },
    private val mainAgentFactory: MainAgentFactory = DefaultMainAgentFactory,
    private val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext =
        { initialTodos, activeTodoFlow ->
            MainAgentScriptContext(initialTodos = initialTodos, activeTodoFlow = activeTodoFlow)
        },
    private val runtimeSideEffectPortFactory:
    ((MessageHandler, AgentEventListener?, (String) -> Unit) -> RuntimeSideEffectPort)? = null,
    private val sessionSideEffectPortFactory:
    ((SessionManager) -> SessionSideEffectPort)? = null,
) {
    private val mainAgentScriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext =
        { initialTodos, activeTodoFlow ->
            val context = scriptContextFactory(initialTodos, activeTodoFlow)
            check(context is MainAgentScriptContext) {
                "Main execution runtime requires MainAgentScriptContext"
            }
            context
        }

    init {
        val hasRuntimeFactory = runtimeSideEffectPortFactory != null
        val hasSessionFactory = sessionSideEffectPortFactory != null
        if (hasRuntimeFactory || hasSessionFactory) {
            check(hasRuntimeFactory && hasSessionFactory) {
                "Custom side-effect wiring requires runtime/session factories to be provided together"
            }
        }
    }

    private fun sessionQueryPort(): SessionQueryPort {
        return SessionManagerSessionQueryPort(sessionManager)
    }

    private fun resolvedExecutionContextFactory(): SessionExecutionContextFactory {
        return executionContextFactory ?: defaultSessionExecutionContextFactory(
            sessionQueryPort = sessionQueryPort(),
            modelCatalogPort = modelCatalogPort,
            promptExecutorFactory = promptExecutorFactory,
            modelRuntimeResolver = modelRuntimeResolver,
            mainAgentProvider = { sessionId, runtimeContext, promptExecutorProvider ->
                createMainAgent(
                    sessionId = sessionId,
                    runtimeContext = runtimeContext,
                    promptExecutorProvider = promptExecutorProvider,
                )
            },
        )
    }

    private fun sessionTitleGenerator(): SessionTitleGenerationPort {
        return sessionTitleGeneratorFactory(
            sessionManager,
            modelCatalogPort,
            promptExecutorFactory,
            modelRuntimeResolver,
        )
    }

    public suspend fun runWithSession(sessionId: String, userInput: String, modelId: String): String {
        val context = resolvedExecutionContextFactory().create(sessionId = sessionId, modelId = modelId)
        return context.agent.chat(
            sessionId = sessionId,
            userInput = userInput,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun continueSession(sessionId: String, modelId: String): String {
        val context = resolvedExecutionContextFactory().create(sessionId = sessionId, modelId = modelId)
        return context.agent.run(
            sessionId = sessionId,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun generateSessionTitleFromConversation(sessionId: String, modelId: String): String? {
        sessionQueryPort().requireSession(sessionId)
        return sessionTitleGenerator().generate(
            sessionId = sessionId,
            modelId = modelId,
        )
    }

    public suspend fun getModelById(modelId: String): LlmModelConfig? {
        val catalog = modelCatalogPort.load()
        return catalog.models.find { it.id == modelId }
    }

    public suspend fun createLLModel(modelId: String): LLModel {
        return resolveModelRuntime(modelId).model
    }

    private fun createMainAgent(
        sessionId: String,
        runtimeContext: AgentRuntimeContext,
        promptExecutorProvider: () -> PromptExecutor,
    ): MainAgent {
        val scopedHandler = scopedMessageHandler(sessionId)
        return mainAgentFactory.create(
            MainAgentFactoryDependencies(
                promptExecutorProvider = promptExecutorProvider,
                sessionManager = sessionManager,
                messageHandler = scopedHandler,
                eventListener = eventListener,
                logger = logger,
                runtimeContext = runtimeContext,
                scriptContextFactory = mainAgentScriptContextFactory,
                runtimeSideEffectPort = runtimeSideEffectPortFactory?.invoke(
                    scopedHandler,
                    eventListener,
                    logger,
                ),
                sessionSideEffectPort = sessionSideEffectPortFactory?.invoke(sessionManager),
            ),
        )
    }

    private suspend fun resolveModelRuntime(modelId: String): SessionExecutionModelRuntime {
        val catalog = modelCatalogPort.load()
        return modelRuntimeResolver(modelId, catalog.models, catalog.auths)
    }

    private fun scopedMessageHandler(sessionId: String): MessageHandler {
        return messageHandler.scopedToSession(sessionId = sessionId)
    }

    private fun MessageHandler.scopedToSession(sessionId: String): MessageHandler {
        val scopedSessionId = sessionId
        return object : MessageHandler {
            override fun addMessageToUser(message: String) {
                this@scopedToSession.addMessageToUser(message, scopedSessionId)
            }

            override fun log(message: String) {
                this@scopedToSession.log(message, scopedSessionId)
            }

            override suspend fun requestInput(): String {
                return this@scopedToSession.requestInput(scopedSessionId)
            }

            override fun isSafeStopRequested(sessionId: String): Boolean {
                val effectiveSessionId = if (sessionId == scopedSessionId) sessionId else scopedSessionId
                return this@scopedToSession.isSafeStopRequested(effectiveSessionId)
            }

            override fun onSafeStopReached(sessionId: String) {
                val effectiveSessionId = if (sessionId == scopedSessionId) sessionId else scopedSessionId
                this@scopedToSession.onSafeStopReached(effectiveSessionId)
            }
        }
    }

    public companion object {
        public val SYSTEM_PROMPT: String = MainAgent.DEFAULT_SYSTEM_PROMPT
    }
}
