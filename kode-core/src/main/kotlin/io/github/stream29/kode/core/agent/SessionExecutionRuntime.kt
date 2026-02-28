package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.port.ToolSideEffectPort
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.tools.scripting.ScriptContext
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.core.hooks.HookManager
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

public class SessionExecutionRuntime(
    private val auths: List<LlmAuthConfig>,
    private val models: List<LlmModelConfig>,
    private val messageHandler: MessageHandler,
    private val eventListener: AgentEventListener?,
    private val hookManager: HookManager,
    private val logger: (String) -> Unit,
    public val sessionManager: SessionManager,
    private val scriptContextFactory: () -> MainAgentScriptContext = { MainAgentScriptContext() },
    private val runtimeSideEffectPortFactory:
        ((MessageHandler, AgentEventListener?, (String) -> Unit) -> RuntimeSideEffectPort)? = null,
    private val toolSideEffectPortFactory: ((HookManager) -> ToolSideEffectPort)? = null,
    private val sessionSideEffectPortFactory:
        ((SessionManager, KoogSessionBridge) -> SessionSideEffectPort)? = null,
) {
    init {
        val hasRuntimeFactory = runtimeSideEffectPortFactory != null
        val hasToolFactory = toolSideEffectPortFactory != null
        val hasSessionFactory = sessionSideEffectPortFactory != null
        if (hasRuntimeFactory || hasToolFactory || hasSessionFactory) {
            check(hasRuntimeFactory && hasToolFactory && hasSessionFactory) {
                "Custom side-effect wiring requires runtime/tool/session factories to be provided together"
            }
        }
    }

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
        LlmPromptExecutorFactory.create(auths)
    }

    public val availableModels: List<LlmModelConfig>
        get() = models

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
        requireSessionState(sessionId)
        val history = sessionBridge.prepareMessagesForAgent(sessionId = sessionId, agentId = null)
        if (history.isEmpty()) {
            return null
        }

        val model = createLLModel(modelId)
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

    private fun createMainAgent(
        sessionId: String,
        runtimeContext: AgentRuntimeContext,
    ): MainAgent {
        val scopedHandler = scopedMessageHandler(sessionId)
        return MainAgent(
            promptExecutor = promptExecutor,
            sessionManager = sessionManager,
            sessionBridge = sessionBridge,
            messageHandler = scopedHandler,
            hookManager = hookManager,
            eventListener = eventListener,
            logger = logger,
            runtimeContext = runtimeContext,
            scriptContextFactory = scriptContextFactory,
            runtimeSideEffectPort = runtimeSideEffectPortFactory?.invoke(
                scopedHandler,
                eventListener,
                logger,
            ),
            toolSideEffectPort = toolSideEffectPortFactory?.invoke(hookManager),
            sessionSideEffectPort = sessionSideEffectPortFactory?.invoke(sessionManager, sessionBridge),
        )
    }

    private suspend fun prepareExecutionContext(sessionId: String, modelId: String): SessionExecutionContext {
        requireSessionState(sessionId)
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

    private suspend fun requireSessionState(sessionId: String) {
        sessionManager.getSessionState(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }

    private fun scopedMessageHandler(sessionId: String): MessageHandler {
        return SessionScopedMessageHandler(
            sessionId = sessionId,
            delegate = messageHandler,
        )
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
                ),
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
        public val SYSTEM_PROMPT: String = MainAgent.DEFAULT_SYSTEM_PROMPT
        private const val SESSION_TITLE_TOOL_NAME: String = "output_title"
        private const val SESSION_TITLE_TOOL_ARG: String = "title"
        private const val SESSION_TITLE_USER_INSTRUCTION: String =
            "请为当前对话总结一个简洁标题。标题语言必须与对话主要语言保持一致。只需调用 output_title 工具返回标题。"
    }
}
