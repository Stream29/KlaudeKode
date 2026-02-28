package io.github.stream29.kode.providers.builtin

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import io.github.stream29.kode.providers.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

public const val TEST_DETERMINISTIC_PROVIDER_ID: String = "test-deterministic"
public const val TEST_DETERMINISTIC_MODEL_ID: String = "test-deterministic-tool-only"

@Serializable
private data object TestDeterministicLlmProviderKey :
    LLMProvider(
        id = TEST_DETERMINISTIC_PROVIDER_ID,
        display = "Test Deterministic",
    )

public object TestDeterministicProvider : LlmProvider {
    override val id: String = TEST_DETERMINISTIC_PROVIDER_ID
    override val displayName: String = "Test Deterministic (Mock Executor)"
    override val llmProvider: LLMProvider = TestDeterministicLlmProviderKey

    override fun models(): List<LLModel> = TEST_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = displayName,
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = emptyList(),
        defaultBaseUrl = null,
        supportsCustomBaseUrl = false,
        description = "Deterministic test provider backed by Koog mock prompt executor.",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        requireApiKeyAuth(providerId = id, auth = auth)
        return TestDeterministicLlmClient(provider = llmProvider)
    }
}

private class TestDeterministicLlmClient(
    private val provider: LLMProvider,
) : LLMClient {
    private val promptExecutor: PromptExecutor = getMockExecutor {
        mockLLMAnswer(MOCK_RESPONSE_TEXT).asDefaultResponse
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val preferredToolName = tools.firstOrNull { descriptor -> descriptor.name == EXECUTE_SCRIPT_TOOL_NAME }?.name
            ?: tools.firstOrNull()?.name
            ?: FALLBACK_TOOL_NAME
        return listOf(
            Message.Tool.Call(
                id = "test-deterministic-tool-call",
                tool = preferredToolName,
                content = SCRIPT_ARGS_JSON,
                metaInfo = ResponseMetaInfo.Empty,
            )
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        return promptExecutor.executeStreaming(prompt = prompt, model = model, tools = tools)
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        return promptExecutor.moderate(prompt = prompt, model = model)
    }

    override suspend fun models(): List<String> {
        return TEST_MODELS.map { model -> model.id }
    }

    override fun llmProvider(): LLMProvider = provider

    override val clientName: String = "TestDeterministicLlmClient"

    override fun close() {
        promptExecutor.close()
    }

    private companion object {
        private const val MOCK_RESPONSE_TEXT: String = "test-deterministic-mock-response"
        private const val EXECUTE_SCRIPT_TOOL_NAME: String = "executeKotlinScript"
        private const val FALLBACK_TOOL_NAME: String = "executeKotlinScript"
        private const val SCRIPT_ARGS_JSON: String = "{\"script\":\"println(\\\"deterministic\\\")\"}"
    }
}

private val TEST_MODELS: List<LLModel> = listOf(
    LLModel(
        provider = TestDeterministicLlmProviderKey,
        id = TEST_DETERMINISTIC_MODEL_ID,
        capabilities = listOf(
            LLMCapability.ToolChoice,
            LLMCapability.Tools,
            LLMCapability.Completion,
        ),
        contextLength = 8_192L,
    ),
)
