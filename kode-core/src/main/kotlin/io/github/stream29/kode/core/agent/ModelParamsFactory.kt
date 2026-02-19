package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicServiceTier
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.deepseek.DeepSeekParams
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.clients.openai.models.Truncation
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.AnthropicServiceTierConfig
import io.github.stream29.kode.config.api.AnthropicThinkingConfig
import io.github.stream29.kode.config.api.BaseModelParamsConfig
import io.github.stream29.kode.config.api.GeminiThinkingConfig
import io.github.stream29.kode.config.api.GeminiThinkingLevelConfig
import io.github.stream29.kode.config.api.JsonSchemaConfig
import io.github.stream29.kode.config.api.JsonSchemaLevelConfig
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.LlmModelParamsConfig
import io.github.stream29.kode.config.api.OpenAiEndpoint
import io.github.stream29.kode.config.api.OpenAiReasoningConfig
import io.github.stream29.kode.config.api.OpenAiReasoningEffortConfig
import io.github.stream29.kode.config.api.OpenAiReasoningSummaryConfig
import io.github.stream29.kode.config.api.OpenAiServiceTierConfig
import io.github.stream29.kode.config.api.OpenAiTruncationConfig
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE
import io.github.stream29.kode.config.api.ToolChoiceConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object ModelParamsFactory {
    fun enforceRequiredToolChoice(params: LLMParams?): LLMParams {
        val required = LLMParams.ToolChoice.Required
        val effective = params ?: return LLMParams(toolChoice = required)
        if (effective.toolChoice == required) {
            return effective
        }

        return when (effective) {
            is OpenAIChatParams -> effective.copy(toolChoice = required)
            is OpenAIResponsesParams -> effective.copy(toolChoice = required)
            is AnthropicParams -> effective.copy(toolChoice = required)
            is GoogleParams -> effective.copy(toolChoice = required)
            is DeepSeekParams -> effective.copy(toolChoice = required)
            else -> effective.copy(toolChoice = required)
        }
    }

    fun create(
        modelConfig: LlmModelConfig,
        authConfig: LlmAuthConfig,
        model: LLModel,
    ): LLMParams? {
        val paramsConfig = modelConfig.params ?: return null
        require(paramsConfig.supportsProvider(authConfig.providerId)) {
            "Model '${modelConfig.id}' params '${paramsConfig.summaryText()}' do not match provider '${authConfig.providerId}'"
        }

        return when (paramsConfig) {
            is LlmModelParamsConfig.OpenAi -> {
                toOpenAiParams(
                    endpoint = resolveOpenAiEndpoint(
                        providerId = authConfig.providerId,
                        configuredEndpoint = paramsConfig.endpoint,
                    ),
                    chat = paramsConfig.chat,
                    responses = paramsConfig.responses,
                    model = model,
                    modelConfig = modelConfig,
                )
            }

            is LlmModelParamsConfig.OpenAiCompatible -> {
                toOpenAiParams(
                    endpoint = resolveOpenAiEndpoint(
                        providerId = authConfig.providerId,
                        configuredEndpoint = paramsConfig.endpoint,
                    ),
                    chat = paramsConfig.chat,
                    responses = paramsConfig.responses,
                    model = model,
                    modelConfig = modelConfig,
                )
            }

            is LlmModelParamsConfig.Anthropic -> toAnthropicParams(paramsConfig)
            is LlmModelParamsConfig.Gemini -> toGeminiParams(paramsConfig)
            is LlmModelParamsConfig.DeepSeek -> toDeepSeekParams(paramsConfig)
            is LlmModelParamsConfig.OpenRouter -> toOpenRouterParams(paramsConfig)
        }
    }

    private fun toOpenAiParams(
        endpoint: OpenAiEndpoint,
        chat: io.github.stream29.kode.config.api.OpenAiChatParamsConfig,
        responses: io.github.stream29.kode.config.api.OpenAiResponsesParamsConfig,
        model: LLModel,
        modelConfig: LlmModelConfig,
    ): LLMParams {
        return when (endpoint) {
            OpenAiEndpoint.Chat -> {
                require(model.supports(LLMCapability.OpenAIEndpoint.Completions)) {
                    "Model '${modelConfig.id}' (${model.id}) does not support OpenAI chat/completions endpoint"
                }
                toOpenAiChatParams(chat)
            }

            OpenAiEndpoint.Responses -> {
                require(model.supports(LLMCapability.OpenAIEndpoint.Responses)) {
                    "Model '${modelConfig.id}' (${model.id}) does not support OpenAI responses endpoint"
                }
                toOpenAiResponsesParams(responses)
            }
        }
    }

    private fun toOpenAiChatParams(config: io.github.stream29.kode.config.api.OpenAiChatParamsConfig): OpenAIChatParams {
        val base = toBaseParams(config.base)
        return OpenAIChatParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = base.additionalProperties,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty,
            parallelToolCalls = config.parallelToolCalls,
            promptCacheKey = config.promptCacheKey,
            safetyIdentifier = config.safetyIdentifier,
            serviceTier = mapOpenAiServiceTier(config.serviceTier),
            store = config.store,
            logprobs = config.logprobs,
            reasoningEffort = mapOpenAiReasoningEffort(config.reasoningEffort),
            stop = config.stop,
            topLogprobs = config.topLogprobs,
            topP = config.topP,
        )
    }

    private fun toOpenAiResponsesParams(config: io.github.stream29.kode.config.api.OpenAiResponsesParamsConfig): OpenAIResponsesParams {
        val base = toBaseParams(config.base)
        val include = config.include
            ?.mapNotNull { includeValue -> mapOpenAiInclude(includeValue) }
            ?.takeIf { includes -> includes.isNotEmpty() }

        return OpenAIResponsesParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = base.additionalProperties,
            background = config.background,
            include = include,
            maxToolCalls = config.maxToolCalls,
            parallelToolCalls = config.parallelToolCalls,
            reasoning = mapOpenAiReasoning(config.reasoning),
            truncation = mapOpenAiTruncation(config.truncation),
            promptCacheKey = config.promptCacheKey,
            safetyIdentifier = config.safetyIdentifier,
            serviceTier = mapOpenAiServiceTier(config.serviceTier),
            store = config.store,
            logprobs = config.logprobs,
            topLogprobs = config.topLogprobs,
            topP = config.topP,
        )
    }

    private fun toAnthropicParams(config: LlmModelParamsConfig.Anthropic): AnthropicParams {
        val base = toBaseParams(config.base)
        return AnthropicParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = base.additionalProperties,
            topP = config.topP,
            topK = config.topK,
            stopSequences = config.stopSequences,
            container = config.container,
            serviceTier = mapAnthropicServiceTier(config.serviceTier),
            thinking = mapAnthropicThinking(config.thinking),
        )
    }

    private fun toGeminiParams(config: LlmModelParamsConfig.Gemini): GoogleParams {
        val base = toBaseParams(config.base)
        return GoogleParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = base.additionalProperties,
            topP = config.topP,
            topK = config.topK,
            thinkingConfig = mapGeminiThinking(config.thinking),
        )
    }

    private fun toDeepSeekParams(config: LlmModelParamsConfig.DeepSeek): DeepSeekParams {
        val base = toBaseParams(config.base)
        return DeepSeekParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = base.additionalProperties,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty,
            logprobs = config.logprobs,
            stop = config.stop,
            topLogprobs = config.topLogprobs,
            topP = config.topP,
        )
    }

    private fun toOpenRouterParams(config: LlmModelParamsConfig.OpenRouter): LLMParams {
        val base = toBaseParams(config.base)
        val additional = base.additionalProperties.orEmpty().toMutableMap()

        config.frequencyPenalty?.let { additional["frequency_penalty"] = JsonPrimitive(it) }
        config.presencePenalty?.let { additional["presence_penalty"] = JsonPrimitive(it) }
        config.logprobs?.let { additional["logprobs"] = JsonPrimitive(it) }
        config.stop?.let { additional["stop"] = JsonArray(it.map(::JsonPrimitive)) }
        config.topLogprobs?.let { additional["top_logprobs"] = JsonPrimitive(it) }
        config.topP?.let { additional["top_p"] = JsonPrimitive(it) }
        config.topK?.let { additional["top_k"] = JsonPrimitive(it) }
        config.repetitionPenalty?.let { additional["repetition_penalty"] = JsonPrimitive(it) }
        config.minP?.let { additional["min_p"] = JsonPrimitive(it) }
        config.topA?.let { additional["top_a"] = JsonPrimitive(it) }
        config.transforms?.let { additional["transforms"] = JsonArray(it.map(::JsonPrimitive)) }
        config.models?.let { additional["models"] = JsonArray(it.map(::JsonPrimitive)) }
        config.route?.let { additional["route"] = JsonPrimitive(it) }
        config.providerPreferences?.let { additional["provider"] = it }
        config.reasoningEffort?.takeIf { value -> value.isNotBlank() }?.let { effort ->
            additional["reasoning"] = buildJsonObject {
                put("effort", JsonPrimitive(effort.trim()))
            }
        }

        return LLMParams(
            temperature = base.temperature,
            maxTokens = base.maxTokens,
            numberOfChoices = base.numberOfChoices,
            speculation = base.speculation,
            schema = base.schema,
            toolChoice = base.toolChoice,
            user = base.user,
            additionalProperties = additional.takeIf { map -> map.isNotEmpty() },
        )
    }

    private fun toBaseParams(config: BaseModelParamsConfig): BaseParams {
        return BaseParams(
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            numberOfChoices = config.numberOfChoices,
            speculation = config.speculation,
            schema = mapSchema(config.schema),
            toolChoice = mapToolChoice(config.toolChoice),
            user = config.user,
            additionalProperties = config.additionalProperties,
        )
    }

    private fun mapSchema(config: JsonSchemaConfig?): LLMParams.Schema? {
        config ?: return null
        return when (config.level) {
            JsonSchemaLevelConfig.Basic -> LLMParams.Schema.JSON.Basic(
                name = config.name,
                schema = config.schema,
            )

            JsonSchemaLevelConfig.Standard -> LLMParams.Schema.JSON.Standard(
                name = config.name,
                schema = config.schema,
            )
        }
    }

    private fun mapToolChoice(config: ToolChoiceConfig?): LLMParams.ToolChoice? {
        return when (config) {
            null -> null
            ToolChoiceConfig.Auto -> LLMParams.ToolChoice.Auto
            ToolChoiceConfig.None -> LLMParams.ToolChoice.None
            ToolChoiceConfig.Required -> LLMParams.ToolChoice.Required
            is ToolChoiceConfig.Named -> {
                val name = config.name.trim()
                require(name.isNotBlank()) { "toolChoice.name is required when toolChoice=named" }
                LLMParams.ToolChoice.Named(name)
            }
        }
    }

    private fun resolveOpenAiEndpoint(
        providerId: String,
        configuredEndpoint: OpenAiEndpoint,
    ): OpenAiEndpoint {
        val normalizedProviderId = providerId.trim().lowercase()
        if (normalizedProviderId == PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER ||
            normalizedProviderId == PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE
        ) {
            return OpenAiEndpoint.Responses
        }
        return configuredEndpoint
    }

    private fun mapOpenAiReasoning(config: OpenAiReasoningConfig?): ReasoningConfig? {
        config ?: return null
        return ReasoningConfig(
            effort = mapOpenAiReasoningEffort(config.effort),
            summary = mapOpenAiReasoningSummary(config.summary),
        )
    }

    private fun mapOpenAiReasoningEffort(config: OpenAiReasoningEffortConfig?): ReasoningEffort? {
        return when (config) {
            null -> null
            OpenAiReasoningEffortConfig.None -> ReasoningEffort.NONE
            OpenAiReasoningEffortConfig.Minimal -> ReasoningEffort.MINIMAL
            OpenAiReasoningEffortConfig.Low -> ReasoningEffort.LOW
            OpenAiReasoningEffortConfig.Medium -> ReasoningEffort.MEDIUM
            OpenAiReasoningEffortConfig.High -> ReasoningEffort.HIGH
        }
    }

    private fun mapOpenAiReasoningSummary(config: OpenAiReasoningSummaryConfig?): ReasoningSummary? {
        return when (config) {
            null -> null
            OpenAiReasoningSummaryConfig.Auto -> ReasoningSummary.AUTO
            OpenAiReasoningSummaryConfig.Concise -> ReasoningSummary.CONCISE
            OpenAiReasoningSummaryConfig.Detailed -> ReasoningSummary.DETAILED
        }
    }

    private fun mapOpenAiTruncation(config: OpenAiTruncationConfig?): Truncation? {
        return when (config) {
            null -> null
            OpenAiTruncationConfig.Auto -> Truncation.AUTO
            OpenAiTruncationConfig.Disabled -> Truncation.DISABLED
        }
    }

    private fun mapOpenAiServiceTier(config: OpenAiServiceTierConfig?): ServiceTier? {
        return when (config) {
            null -> null
            OpenAiServiceTierConfig.Auto -> ServiceTier.AUTO
            OpenAiServiceTierConfig.Default -> ServiceTier.DEFAULT
            OpenAiServiceTierConfig.Flex -> ServiceTier.FLEX
            OpenAiServiceTierConfig.Priority -> ServiceTier.PRIORITY
        }
    }

    private fun mapAnthropicServiceTier(config: AnthropicServiceTierConfig?): AnthropicServiceTier? {
        return when (config) {
            null -> null
            AnthropicServiceTierConfig.Auto -> AnthropicServiceTier.AUTO
            AnthropicServiceTierConfig.StandardOnly -> AnthropicServiceTier.STANDARD_ONLY
        }
    }

    private fun mapAnthropicThinking(config: AnthropicThinkingConfig?): AnthropicThinking? {
        config ?: return null
        if (!config.enabled) {
            return AnthropicThinking.Disabled()
        }
        val budget = config.budgetTokens ?: 1_024
        require(budget >= 1_024) {
            "Anthropic thinking budget must be >= 1024 when enabled"
        }
        return AnthropicThinking.Enabled(budgetTokens = budget)
    }

    private fun mapGeminiThinking(config: GeminiThinkingConfig?): GoogleThinkingConfig? {
        config ?: return null
        return GoogleThinkingConfig(
            includeThoughts = config.includeThoughts,
            thinkingBudget = config.thinkingBudget,
            thinkingLevel = when (config.thinkingLevel) {
                null -> null
                GeminiThinkingLevelConfig.Low -> GoogleThinkingLevel.LOW
                GeminiThinkingLevelConfig.High -> GoogleThinkingLevel.HIGH
            }
        )
    }

    private fun mapOpenAiInclude(value: String): OpenAIInclude? {
        return when (value.trim()) {
            "web_search_call.action.sources" -> OpenAIInclude.WEB_SEARCH_CALL_ACTION_SOURCES
            "code_interpreter_call.outputs" -> OpenAIInclude.CODE_INTERPRETER_CALL_OUTPUTS
            "computer_call_output.output.image_url" -> OpenAIInclude.COMPUTER_CALL_OUTPUT_IMAGE_URL
            "file_search_call.results" -> OpenAIInclude.FILE_SEARCH_CALL_RESULTS
            "message.input_image.image_url" -> OpenAIInclude.INPUT_IMAGE_URL
            "message.output_text.logprobs" -> OpenAIInclude.OUTPUT_TEXT_LOGPROBS
            "reasoning.encrypted_content" -> OpenAIInclude.REASONING_ENCRYPTED_CONTENT
            else -> null
        }
    }

    private data class BaseParams(
        val temperature: Double?,
        val maxTokens: Int?,
        val numberOfChoices: Int?,
        val speculation: String?,
        val schema: LLMParams.Schema?,
        val toolChoice: LLMParams.ToolChoice?,
        val user: String?,
        val additionalProperties: Map<String, JsonElement>?,
    )
}
