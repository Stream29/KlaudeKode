package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.core.hooks.HookManager
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import java.io.File

public class SessionAwareAgentFactory(
    private val auths: List<LlmAuthConfig>,
    private val models: List<LlmModelConfig>,
    private val messageHandler: MessageHandler,
    private val disabledTools: Set<String>,
    private val mcpToolRegistry: ToolRegistry?,
    private val eventListener: AgentEventListener?,
    private val hookManager: HookManager,
    private val logger: (String) -> Unit,
    public val sessionManager: SessionManager,
) {
    private data class SessionExecutionContext(
        val conversationAgent: ConversationAgent,
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
        return context.conversationAgent.chat(
            sessionId = sessionId,
            userInput = userInput,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun continueSession(sessionId: String, modelId: String): String {
        val context = prepareExecutionContext(sessionId = sessionId, modelId = modelId)
        return context.conversationAgent.continueSession(
            sessionId = sessionId,
            model = context.model,
            modelParams = context.modelParams,
        )
    }

    public suspend fun generateSessionTitleFromConversation(sessionId: String, modelId: String): String? {
        val history = sessionBridge.prepareMessagesForAgent(sessionId = sessionId, agentId = null)
        if (history.isEmpty()) {
            return null
        }

        val model = ModelFactory.createModel(modelId, models, auths)
        val nowMeta = RequestMetaInfo.create(Clock.System.toDeprecatedClock())
        val messages = history + Message.User(SESSION_TITLE_USER_INSTRUCTION, nowMeta)
        val prompt = Prompt(
            messages = messages,
            id = "session_title_${System.currentTimeMillis()}",
            params = LLMParams(
                toolChoice = LLMParams.ToolChoice.Named(SESSION_TITLE_TOOL_NAME),
            ),
        )
        val responses = promptExecutor.execute(prompt, model, listOf(sessionTitleToolDescriptor()))
        val titleFromToolCall = responses
            .filterIsInstance<Message.Tool.Call>()
            .lastOrNull { call -> call.tool == SESSION_TITLE_TOOL_NAME }
            ?.contentJsonResult
            ?.getOrNull()
            ?.get(SESSION_TITLE_TOOL_ARG)
            ?.jsonPrimitive
            ?.contentOrNull
        return normalizeGeneratedTitle(titleFromToolCall.orEmpty())
    }
    
    public fun getModelById(modelId: String): LlmModelConfig? {
        return models.find { it.id == modelId }
    }

    public fun createLLModel(modelId: String): LLModel {
        return ModelFactory.createModel(modelId, models, auths)
    }

    public fun buildToolRegistry(workingDir: File, sessionId: String, ownerAgentId: String? = null): ToolRegistry {
        val scopedHandler = scopedMessageHandler(sessionId)
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
        return mergeMcpRegistry(baseRegistry)
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
            messageHandler = scopedMessageHandler(sessionId),
            hookManager = hookManager,
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
                val session = requireSession(sessionId)
                val workingDirectory = resolveWorkingDir(session)
                val modelId = resolveModelIdForSession(session)
                val modelRuntime = ModelFactory.resolveModelRuntime(modelId, models, auths)
                val enforcedParams = ModelParamsFactory.enforceRequiredToolChoice(modelRuntime.params)
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
                return subConversationAgent.runSubAgent(
                    sessionId = sessionId,
                    model = modelRuntime.model,
                    modelParams = enforcedParams,
                )
            }
        }
    }

    private suspend fun prepareExecutionContext(sessionId: String, modelId: String): SessionExecutionContext {
        val session = requireSession(sessionId)
        val workingDir = resolveWorkingDir(session)
        val modelRuntime = ModelFactory.resolveModelRuntime(modelId, models, auths)
        val enforcedParams = ModelParamsFactory.enforceRequiredToolChoice(modelRuntime.params)
        return SessionExecutionContext(
            conversationAgent = createConversationAgent(sessionId, workingDir),
            model = modelRuntime.model,
            modelParams = enforcedParams,
        )
    }

    private suspend fun requireSession(sessionId: String): ConversationSession {
        return sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }

    private fun resolveModelIdForSession(session: ConversationSession): String {
        val preferredModelId = session.configuration.customValues
            ?.get(SESSION_CONFIG_MODEL_ID_KEY)
            ?.trim()
            .orEmpty()
        if (preferredModelId.isNotBlank()) {
            val byId = models.firstOrNull { it.id == preferredModelId }
            if (byId != null) {
                return byId.id
            }
        }

        val preferredModelName = session.configuration.preferredModel?.trim().orEmpty()
        return models.firstOrNull { it.model == preferredModelName }?.id ?: models.first().id
    }

    private fun mergeMcpRegistry(baseRegistry: ToolRegistry): ToolRegistry {
        return mcpToolRegistry?.let { registry -> baseRegistry + registry } ?: baseRegistry
    }

    private fun scopedMessageHandler(sessionId: String): MessageHandler {
        return SessionScopedMessageHandler(
            sessionId = sessionId,
            delegate = messageHandler,
        )
    }

    private fun resolveWorkingDir(
        session: ConversationSession
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

    private fun normalizeGeneratedTitle(raw: String): String? {
        val line = raw
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .trim('"', '\'', '`')
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (line.isBlank()) {
            return null
        }
        return if (line.length > 80) {
            line.take(80).trimEnd()
        } else {
            line
        }
    }

    private fun sessionTitleToolDescriptor(): ToolDescriptor {
        return ToolDescriptor(
            name = SESSION_TITLE_TOOL_NAME,
            description = "Output the generated conversation title.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = SESSION_TITLE_TOOL_ARG,
                    description = "Generated conversation title in plain text.",
                    type = ToolParameterType.String,
                )
            ),
            optionalParameters = emptyList(),
        )
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
        public val SYSTEM_PROMPT: String = ConversationAgent.DEFAULT_SYSTEM_PROMPT
        private const val SESSION_TITLE_TOOL_NAME: String = "output_title"
        private const val SESSION_TITLE_TOOL_ARG: String = "title"
        private const val SESSION_CONFIG_MODEL_ID_KEY: String = "preferred_model_id"
        private const val SESSION_TITLE_USER_INSTRUCTION: String =
            "请为当前对话总结一个简洁标题。标题语言必须与对话主要语言保持一致。只需调用 output_title 工具返回标题。"
    }
}
