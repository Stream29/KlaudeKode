package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.ApprovalHandler
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.core.hooks.HookManager
import kotlinx.serialization.json.Json
import java.io.File

public class SessionAwareAgentFactory(
    private val auths: List<LlmAuthConfig>,
    private val models: List<LlmModelConfig>,
    private val messageHandler: MessageHandler,
    private val approvalHandler: ApprovalHandler?,
    private val disabledTools: Set<String>,
    private val mcpToolRegistry: ToolRegistry?,
    private val eventListener: AgentEventListener?,
    private val hookManager: HookManager,
    private val logger: (String) -> Unit,
    public val sessionManager: SessionManager,
) {
    public val sessionBridge: KoogSessionBridge by lazy {
        KoogSessionBridge(
            sessionManager = sessionManager,
            json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
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
        val session = sessionManager.createSession(
            title = title,
            systemPrompt = systemPrompt ?: SYSTEM_PROMPT,
            tags = emptyList(),
            configuration = io.github.stream29.kode.session.core.model.SessionConfiguration(
                preferredModel = modelConfig?.model,
                systemPrompt = systemPrompt ?: SYSTEM_PROMPT,
                workDir = normalizedWorkDir,
                maxIterations = null,
                temperature = null,
                customValues = null
            )
        )
        return session.id
    }

    public suspend fun runWithSession(sessionId: String, userInput: String, modelId: String): String {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val workingDir = resolveWorkingDir(session)
        val conversationAgent = createConversationAgent(sessionId, workingDir)
        val model = ModelFactory.createModel(modelId, models, auths)
        return conversationAgent.chat(sessionId, userInput, model)
    }

    public suspend fun continueSession(sessionId: String, modelId: String): String {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val workingDir = resolveWorkingDir(session)
        val conversationAgent = createConversationAgent(sessionId, workingDir)
        val model = ModelFactory.createModel(modelId, models, auths)
        return conversationAgent.continueSession(sessionId, model)
    }
    
    public fun getModelById(modelId: String): LlmModelConfig? {
        return models.find { it.id == modelId }
    }

    public fun createLLModel(modelId: String): LLModel {
        return ModelFactory.createModel(modelId, models, auths)
    }

    public fun buildToolRegistry(workingDir: File, sessionId: String, ownerAgentId: String? = null): ToolRegistry {
        val scopedHandler = SessionScopedMessageHandler(
            sessionId = sessionId,
            delegate = messageHandler
        )
        val baseRegistry = ToolRegistryFactory.create(
            workingDir = workingDir,
            messageHandler = scopedHandler,
            logger = logger,
            disabledTools = disabledTools,
            taskAgentFactory = createTaskAgentFactory(workingDir),
            ownerSessionId = sessionId,
            ownerAgentId = ownerAgentId,
            sessionManager = sessionManager,
        )
        return if (mcpToolRegistry == null) {
            baseRegistry
        } else {
            baseRegistry + mcpToolRegistry
        }
    }

    private fun createConversationAgent(
        sessionId: String,
        workingDir: File,
        runtimeContext: AgentRuntimeContext = AgentRuntimeContext(),
    ): ConversationAgent {
        val toolRegistry = buildToolRegistry(workingDir, sessionId, runtimeContext.agentId)
        return ConversationAgent(
            promptExecutor = promptExecutor,
            toolRegistry = toolRegistry,
            sessionManager = sessionManager,
            sessionBridge = sessionBridge,
            messageHandler = SessionScopedMessageHandler(sessionId, messageHandler),
            hookManager = hookManager,
            approvalHandler = approvalHandler,
            eventListener = eventListener,
            logger = logger,
            runtimeContext = runtimeContext,
        )
    }
    
    private fun createTaskAgentFactory(workingDir: File): io.github.stream29.kode.tools.AgentFactory? {
        if (models.isEmpty()) {
            return null
        }

        return object : io.github.stream29.kode.tools.AgentFactory {
            override fun createAgent(): io.github.stream29.kode.tools.SimpleAgent {
                return object : io.github.stream29.kode.tools.SimpleAgent {
                    override suspend fun run(task: String): String {
                        val modelId = models.first().id
                        val sessionId = createSession(
                            title = "Task ${System.currentTimeMillis()}",
                            systemPrompt = null,
                            modelId = modelId,
                            workDir = workingDir.absolutePath
                        )
                        return runWithSession(sessionId, task, modelId)
                    }
                }
            }

            override suspend fun runSubAgent(
                sessionId: String,
                agentId: String,
                parentAgentId: String,
                mode: String,
                taskDescription: String,
                expectedResult: String,
            ): String {
                if (mode.isBlank() && taskDescription.isBlank() && expectedResult.isBlank()) {
                    logger("Subagent context payload is empty")
                }
                val session = sessionManager.getSession(sessionId)
                    ?: throw IllegalArgumentException("Session not found: $sessionId")
                val workingDirectory = resolveWorkingDir(session)
                val modelId = models.firstOrNull { it.model == session.configuration.preferredModel }?.id ?: models.first().id
                val model = ModelFactory.createModel(modelId, models, auths)
                val subConversationAgent = createConversationAgent(
                    sessionId = sessionId,
                    workingDir = workingDirectory,
                    runtimeContext = AgentRuntimeContext(
                        agentId = agentId,
                        parentAgentId = parentAgentId,
                        canInteractWithUser = false,
                        canCreateSubagents = false,
                    ),
                )
                return subConversationAgent.runSubAgent(sessionId, model)
            }
        }
    }

    private fun resolveWorkingDir(
        session: io.github.stream29.kode.session.core.model.ConversationSession
    ): File {
        val configured = normalizeWorkingDir(session.configuration.workDir)
        val fallback = defaultWorkingDir().absolutePath
        return File(configured ?: fallback)
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

    private fun defaultWorkingDir(): File {
        val path = System.getProperty("user.dir") ?: "."
        return File(path)
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
    }

    public companion object {
        public val SYSTEM_PROMPT: String = ConversationAgent.DEFAULT_SYSTEM_PROMPT
    }
}
