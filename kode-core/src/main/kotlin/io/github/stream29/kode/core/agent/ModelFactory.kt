package io.github.stream29.kode.core.agent

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry

internal data class ResolvedModelRuntime(
    val model: LLModel,
    val params: LLMParams?,
)

internal object ModelFactory {
    fun createModel(
        modelId: String,
        models: List<LlmModelConfig>,
        auths: List<LlmAuthConfig>
    ): LLModel {
        return resolveModelRuntime(modelId, models, auths).model
    }

    fun resolveModelRuntime(
        modelId: String,
        models: List<LlmModelConfig>,
        auths: List<LlmAuthConfig>
    ): ResolvedModelRuntime {
        val modelConfig = models.find { it.id == modelId }
            ?: throw IllegalArgumentException("Model not found: $modelId")

        val authConfig = auths.find { it.id == modelConfig.authId }
            ?: throw IllegalArgumentException("Auth not found: ${modelConfig.authId}")

        val providerId = authConfig.providerId.trim()
        require(providerId.isNotBlank()) { "providerId is blank for auth '${authConfig.id}'" }
        val provider = BuiltinLlmProviderRegistry.findProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        val baseModel = provider.models().firstOrNull { it.id == modelConfig.model }
            ?: createDynamicModel(
                provider = provider.llmProvider,
                providerTemplate = provider.models().firstOrNull(),
                modelConfig = modelConfig
            )

        val resolvedModel = applyOverrides(
            baseModel = baseModel,
            modelConfig = modelConfig,
            provider = provider.llmProvider,
        )
        val resolvedParams = ModelParamsFactory.create(
            modelConfig = modelConfig,
            authConfig = authConfig,
            model = resolvedModel,
        )
        return ResolvedModelRuntime(
            model = resolvedModel,
            params = resolvedParams,
        )
    }

    private fun applyOverrides(baseModel: LLModel, modelConfig: LlmModelConfig, provider: LLMProvider): LLModel {
        val configuredCapabilities = modelConfig.capabilities
            ?.mapNotNull { capability -> parseCapability(capability) }
            ?.distinct()
            ?.takeIf { list -> list.isNotEmpty() }

        val capabilities = if (configuredCapabilities == null) {
            baseModel.capabilities
        } else {
            (baseModel.capabilities + configuredCapabilities).distinct()
        }
        val contextLength = modelConfig.maxContextSize?.toLong() ?: baseModel.contextLength
        return baseModel.copy(
            provider = provider,
            capabilities = capabilities,
            contextLength = contextLength,
        )
    }

    private fun createDynamicModel(
        provider: LLMProvider,
        providerTemplate: LLModel?,
        modelConfig: LlmModelConfig,
    ): LLModel {
        val defaultCapabilities = providerTemplate?.capabilities ?: OPEN_AI_DEFAULT_CAPABILITIES
        val defaultContextSize = providerTemplate?.contextLength ?: 128_000
        val configuredCapabilities = modelConfig.capabilities
            ?.mapNotNull { capability -> parseCapability(capability) }
            ?.distinct()
            ?.takeIf { list -> list.isNotEmpty() }

        val capabilities = if (configuredCapabilities == null) {
            defaultCapabilities
        } else {
            (defaultCapabilities + configuredCapabilities).distinct()
        }
        return LLModel(
            provider = provider,
            id = modelConfig.model,
            capabilities = capabilities,
            contextLength = modelConfig.maxContextSize?.toLong() ?: defaultContextSize,
        )
    }

    private fun parseCapability(input: String): LLMCapability? {
        return when (input.lowercase()) {
            "temperature" -> LLMCapability.Temperature
            "tool", "tools" -> LLMCapability.Tools
            "tool_choice", "toolchoice" -> LLMCapability.ToolChoice
            "reasoning", "speculation" -> LLMCapability.Speculation
            "vision", "image", "vision_image" -> LLMCapability.Vision.Image
            "document" -> LLMCapability.Document
            "completion" -> LLMCapability.Completion
            "multiple_choices", "multiplechoices" -> LLMCapability.MultipleChoices
            "json_basic", "schema_json_basic" -> LLMCapability.Schema.JSON.Basic
            "json_standard", "schema_json_standard" -> LLMCapability.Schema.JSON.Standard
            "openai_completions", "openai_endpoint_completions" -> LLMCapability.OpenAIEndpoint.Completions
            "openai_responses", "openai_endpoint_responses" -> LLMCapability.OpenAIEndpoint.Responses
            else -> null
        }
    }

    private val OPEN_AI_DEFAULT_CAPABILITIES: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.ToolChoice,
        LLMCapability.Tools,
        LLMCapability.Completion,
        LLMCapability.MultipleChoices,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
        LLMCapability.Vision.Image,
        LLMCapability.Document,
        LLMCapability.OpenAIEndpoint.Completions,
        LLMCapability.OpenAIEndpoint.Responses,
    )
}
