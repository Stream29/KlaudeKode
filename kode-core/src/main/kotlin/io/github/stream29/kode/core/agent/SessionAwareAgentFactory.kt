package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File

public class SessionAwareAgentFactory(
    private val auths: List<LlmAuthConfig>,
    private val models: List<LlmModelConfig>,
    private val messageHandler: MessageHandler,
    private val workingDir: File,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit
) {
    public val sessionManager: SessionManager by lazy {
        SessionManager(FileSessionStorage())
    }

    public val sessionBridge: KoogSessionBridge by lazy {
        KoogSessionBridge(
            sessionManager = sessionManager,
            clock = Clock.System,
            json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        )
    }
    
    private val promptExecutor: MultiLLMPromptExecutor by lazy {
        MultiLLMExecutorFactory.create(auths)
    }
    
    private val toolRegistry: ToolRegistry by lazy {
        ToolRegistryFactory.create(workingDir, messageHandler, logger)
    }
    
    private val conversationAgent: ConversationAgent by lazy {
        ConversationAgent(
            promptExecutor = promptExecutor,
            toolRegistry = toolRegistry,
            sessionManager = sessionManager,
            sessionBridge = sessionBridge,
            messageHandler = messageHandler,
            eventListener = eventListener,
            logger = logger
        )
    }
    
    public val availableModels: List<LlmModelConfig>
        get() = models

    public suspend fun createSession(
        title: String,
        systemPrompt: String?,
        modelId: String
    ): String {
        val modelConfig = models.find { it.id == modelId }
        val session = sessionManager.createSession(
            title = title,
            systemPrompt = systemPrompt ?: SYSTEM_PROMPT,
            tags = emptyList(),
            configuration = io.github.stream29.kode.session.core.model.SessionConfiguration(
                preferredModel = modelConfig?.model,
                systemPrompt = systemPrompt ?: SYSTEM_PROMPT,
                maxIterations = null,
                temperature = null,
                customValues = null
            )
        )
        return session.id
    }

    public suspend fun runWithSession(sessionId: String, userInput: String, modelId: String): String {
        val model = ModelFactory.createModel(modelId, models, auths)
        return conversationAgent.chat(sessionId, userInput, model)
    }

    public suspend fun continueSession(sessionId: String, modelId: String): String {
        val model = ModelFactory.createModel(modelId, models, auths)
        return conversationAgent.chat(sessionId, "Continue from previous conversation", model)
    }
    
    public fun getModelById(modelId: String): LlmModelConfig? {
        return models.find { it.id == modelId }
    }

    public companion object {
        public val SYSTEM_PROMPT: String = ConversationAgent.DEFAULT_SYSTEM_PROMPT
    }
}
