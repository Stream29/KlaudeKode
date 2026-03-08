package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

internal class SessionTitleGenerator(
    private val sessionQueryPort: SessionQueryPort,
    private val modelCatalogPort: SessionExecutionModelCatalogPort,
    private val promptExecutorFactory: (List<LlmAuthConfig>) -> PromptExecutor,
    private val modelRuntimeResolver:
    (String, List<LlmModelConfig>, List<LlmAuthConfig>) -> SessionExecutionModelRuntime,
) : SessionTitleGenerationPort {
    override suspend fun generate(sessionId: String, modelId: String): String? {
        val history = prepareMessagesForAgent(sessionId = sessionId, agentId = null)
        if (history.isEmpty()) {
            return null
        }

        val catalog = modelCatalogPort.load()
        val modelRuntime = modelRuntimeResolver(modelId, catalog.models, catalog.auths)
        val promptExecutor = promptExecutorFactory(catalog.auths)
        val nowMeta = RequestMetaInfo.create(Clock.System.toDeprecatedClock())
        val messages = history + Message.User(SESSION_TITLE_USER_INSTRUCTION, nowMeta)
        val prompt = Prompt(
            messages = messages,
            id = "session_title_${System.currentTimeMillis()}",
            params = LLMParams(
                toolChoice = LLMParams.ToolChoice.Named(SESSION_TITLE_TOOL_NAME),
            ),
        )
        val responses = promptExecutor.execute(prompt, modelRuntime.model, listOf(sessionTitleToolDescriptor()))
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

    private suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> {
        return sessionQueryPort.loadAgentMessages(
            sessionId = sessionId,
            agentId = agentId,
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

    private companion object {
        private const val SESSION_TITLE_TOOL_NAME: String = "output_title"
        private const val SESSION_TITLE_TOOL_ARG: String = "title"
        private const val SESSION_TITLE_USER_INSTRUCTION: String =
            "请为当前对话总结一个简洁标题。标题语言必须与对话主要语言保持一致。只需调用 output_title 工具返回标题。"
    }
}

public fun interface SessionTitleGenerationPort {
    public suspend fun generate(sessionId: String, modelId: String): String?
}
