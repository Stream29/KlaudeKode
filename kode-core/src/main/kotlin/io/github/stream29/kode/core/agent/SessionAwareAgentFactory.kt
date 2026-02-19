package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.core.hooks.HookManager
import java.io.File

public class SessionAwareAgentFactory(
    private val auths: List<LlmAuthConfig>,
    private val models: List<LlmModelConfig>,
    private val messageHandler: MessageHandler,
    private val eventListener: AgentEventListener?,
    private val hookManager: HookManager,
    private val logger: (String) -> Unit,
    public val sessionManager: SessionManager,
) {
    private data class SessionExecutionContext(
        val agent: MainAgent,
        val model: LLModel,
        val modelParams: LLMParams?,
    )

    public val sessionBridge: KoogSessionBridge by lazy {
        KoogSessionBridge(
            sessionManager = sessionManager,
        )
    }
    
    public val promptExecutor: MultiLLMPromptExecutor by lazy {
        MultiLLMExecutorFactory.create(auths)
    }
    
    public val availableModels: List<LlmModelConfig>
        get() = models

    public suspend fun createSession(
        title: String,
        systemPrompt: String?,
        modelId: String,
        workDir: String?
    ): String {
        val modelConfig = models.find { it.id == modelId }
        val normalizedWorkDir = normalizeWorkingDir(workDir)
        val resolvedSystemPrompt = systemPrompt ?: SYSTEM_PROMPT
        val session = sessionManager.createSession(
            title = title,
            systemPrompt = resolvedSystemPrompt,
            tags = emptyList(),
            configuration = io.github.stream29.kode.session.core.model.SessionConfiguration(
                preferredModel = modelConfig?.model,
                systemPrompt = resolvedSystemPrompt,
                workDir = normalizedWorkDir,
                maxIterations = null,
                temperature = null,
                customValues = mapOf(SESSION_CONFIG_MODEL_ID_KEY to modelId)
            )
        )
        return session.id
    }

    public suspend fun runWithSession(sessionId: String, userInput: String, modelId: String): String {
        val context = prepareExecutionContext(sessionId = sessionId, modelId = modelId)
        return context.agent.chat(
            sessionId = sessionId,
            userInput = userInput,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun continueSession(sessionId: String, modelId: String): String {
        val context = prepareExecutionContext(sessionId = sessionId, modelId = modelId)
        return context.agent.run(
            sessionId = sessionId,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun generateSessionTitleFromConversation(sessionId: String, modelId: String): String? {
        return null
    }
    
    public fun getModelById(modelId: String): LlmModelConfig? {
        return models.find { it.id == modelId }
    }

    public fun createLLModel(modelId: String): LLModel {
        return ModelFactory.createModel(modelId, models, auths)
    }

    private fun createMainAgent(
        sessionId: String,
        runtimeContext: AgentRuntimeContext,
    ): MainAgent {
        return MainAgent(
            promptExecutor = promptExecutor,
            sessionManager = sessionManager,
            sessionBridge = sessionBridge,
            messageHandler = scopedMessageHandler(sessionId),
            hookManager = hookManager,
            eventListener = eventListener,
            logger = logger,
            runtimeContext = runtimeContext,
        )
    }
    
    private suspend fun prepareExecutionContext(sessionId: String, modelId: String): SessionExecutionContext {
        requireSession(sessionId)
        val modelRuntime = ModelFactory.resolveModelRuntime(modelId, models, auths)
        val enforcedParams = ModelParamsFactory.enforceRequiredToolChoice(modelRuntime.params)
        return SessionExecutionContext(
            agent = createMainAgent(
                sessionId = sessionId,
                runtimeContext = AgentRuntimeContext(
                    agentId = null,
                    parentAgentId = null,
                    canInteractWithUser = true,
                    canCreateSubagents = false,
                ),
            ),
            model = modelRuntime.model,
            modelParams = enforcedParams,
        )
    }

    private suspend fun requireSession(sessionId: String): ConversationSession {
        return sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }

    private fun scopedMessageHandler(sessionId: String): MessageHandler {
        return SessionScopedMessageHandler(
            sessionId = sessionId,
            delegate = messageHandler,
        )
    }

    private fun normalizeWorkingDir(path: String?): String? {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return null
        }
        val expanded = if (trimmed.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        return File(expanded).absolutePath
    }

    private class SessionScopedMessageHandler(
        private val sessionId: String,
        private val delegate: MessageHandler
    ) : MessageHandler {
        override fun addMessageToUser(message: String) {
            delegate.addMessageToUser(message, sessionId)
        }

        override fun log(message: String) {
            delegate.log(message, sessionId)
        }

        override suspend fun requestInput(): String {
            return delegate.requestInput(sessionId)
        }

        override fun isSafeStopRequested(sessionId: String): Boolean {
            return delegate.isSafeStopRequested(this.sessionId)
        }

        override fun onSafeStopReached(sessionId: String) {
            delegate.onSafeStopReached(this.sessionId)
        }
    }

    public companion object {
        public val SYSTEM_PROMPT: String = MainAgent.DEFAULT_SYSTEM_PROMPT
        private const val SESSION_CONFIG_MODEL_ID_KEY: String = "preferred_model_id"
    }
}
