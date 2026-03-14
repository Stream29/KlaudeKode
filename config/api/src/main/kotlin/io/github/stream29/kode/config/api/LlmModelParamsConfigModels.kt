package io.github.stream29.kode.config.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
public data class LlmModelConfig(
    val id: String,
    val authId: String,
    val model: String,
    val displayName: String?,
    val params: LlmModelParamsConfig? = null,
    val maxContextSize: Int? = null,
    val capabilities: List<String>? = null,
)

@Serializable
public sealed interface LlmModelParamsConfig {
    public fun summaryText(): String

    public fun supportsProvider(providerId: String): Boolean

    public sealed interface OpenAiFamily : LlmModelParamsConfig {
        public val endpoint: OpenAiEndpoint
        public val chat: OpenAiChatParamsConfig
        public val responses: OpenAiResponsesParamsConfig

        public fun reasoningEffort(): OpenAiReasoningEffortConfig? {
            return chat.reasoningEffort ?: responses.reasoning?.effort
        }

        public fun withEndpoint(endpoint: OpenAiEndpoint): OpenAiFamily

        public fun withReasoningEffort(effort: OpenAiReasoningEffortConfig?): OpenAiFamily
    }

    @Serializable
    @SerialName("openai")
    public data class OpenAi(
        override val endpoint: OpenAiEndpoint = OpenAiEndpoint.Chat,
        override val chat: OpenAiChatParamsConfig = OpenAiChatParamsConfig(),
        override val responses: OpenAiResponsesParamsConfig = OpenAiResponsesParamsConfig(),
    ) : OpenAiFamily {
        override fun summaryText(): String {
            val effort = reasoningEffort()
            val prefix = "openai/${endpoint.name.lowercase()}"
            return if (effort == null) prefix else "$prefix, reasoning=${effort.name.lowercase()}"
        }

        override fun withEndpoint(endpoint: OpenAiEndpoint): OpenAiFamily {
            return copy(endpoint = endpoint)
        }

        override fun withReasoningEffort(effort: OpenAiReasoningEffortConfig?): OpenAiFamily {
            val nextChat = chat.copy(reasoningEffort = effort)
            val nextReasoning = (responses.reasoning ?: OpenAiReasoningConfig())
                .copy(effort = effort)
                .takeUnless { reasoning -> reasoning.effort == null && reasoning.summary == null }
            return copy(
                chat = nextChat,
                responses = responses.copy(reasoning = nextReasoning),
            )
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() in OPENAI_LIKE_PROVIDER_IDS
        }
    }

    @Serializable
    @SerialName("openai_compatible")
    public data class OpenAiCompatible(
        override val endpoint: OpenAiEndpoint = OpenAiEndpoint.Chat,
        override val chat: OpenAiChatParamsConfig = OpenAiChatParamsConfig(),
        override val responses: OpenAiResponsesParamsConfig = OpenAiResponsesParamsConfig(),
    ) : OpenAiFamily {
        override fun summaryText(): String {
            val effort = reasoningEffort()
            val prefix = "openai-compatible/${endpoint.name.lowercase()}"
            return if (effort == null) prefix else "$prefix, reasoning=${effort.name.lowercase()}"
        }

        override fun withEndpoint(endpoint: OpenAiEndpoint): OpenAiFamily {
            return copy(endpoint = endpoint)
        }

        override fun withReasoningEffort(effort: OpenAiReasoningEffortConfig?): OpenAiFamily {
            val nextChat = chat.copy(reasoningEffort = effort)
            val nextReasoning = (responses.reasoning ?: OpenAiReasoningConfig())
                .copy(effort = effort)
                .takeUnless { reasoning -> reasoning.effort == null && reasoning.summary == null }
            return copy(
                chat = nextChat,
                responses = responses.copy(reasoning = nextReasoning),
            )
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() in OPENAI_LIKE_PROVIDER_IDS
        }
    }

    @Serializable
    @SerialName("anthropic")
    public data class Anthropic(
        val base: BaseModelParamsConfig = BaseModelParamsConfig(),
        val topP: Double? = null,
        val topK: Int? = null,
        val stopSequences: List<String>? = null,
        val container: String? = null,
        val serviceTier: AnthropicServiceTierConfig? = null,
        val thinking: AnthropicThinkingConfig? = null,
    ) : LlmModelParamsConfig {
        override fun summaryText(): String {
            return when {
                thinking == null -> "anthropic/default"
                thinking.enabled -> "anthropic/thinking=${thinking.budgetTokens ?: 1024}"
                else -> "anthropic/thinking=disabled"
            }
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() == PROVIDER_ID_ANTHROPIC
        }
    }

    @Serializable
    @SerialName("gemini")
    public data class Gemini(
        val base: BaseModelParamsConfig = BaseModelParamsConfig(),
        val topP: Double? = null,
        val topK: Int? = null,
        val thinking: GeminiThinkingConfig? = null,
    ) : LlmModelParamsConfig {
        override fun summaryText(): String {
            val level = thinking?.thinkingLevel
            return when {
                thinking == null -> "gemini/default"
                thinking.thinkingBudget != null -> "gemini/budget=${thinking.thinkingBudget}"
                level != null -> "gemini/level=${level.name.lowercase()}"
                else -> "gemini/default"
            }
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() == PROVIDER_ID_GEMINI
        }
    }

    @Serializable
    @SerialName("deepseek")
    public data class DeepSeek(
        val base: BaseModelParamsConfig = BaseModelParamsConfig(),
        val frequencyPenalty: Double? = null,
        val presencePenalty: Double? = null,
        val logprobs: Boolean? = null,
        val stop: List<String>? = null,
        val topLogprobs: Int? = null,
        val topP: Double? = null,
    ) : LlmModelParamsConfig {
        override fun summaryText(): String {
            return "deepseek/custom"
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() == PROVIDER_ID_DEEPSEEK
        }
    }

    @Serializable
    @SerialName("openrouter")
    public data class OpenRouter(
        val base: BaseModelParamsConfig = BaseModelParamsConfig(),
        val frequencyPenalty: Double? = null,
        val presencePenalty: Double? = null,
        val logprobs: Boolean? = null,
        val stop: List<String>? = null,
        val topLogprobs: Int? = null,
        val topP: Double? = null,
        val topK: Int? = null,
        val repetitionPenalty: Double? = null,
        val minP: Double? = null,
        val topA: Double? = null,
        val transforms: List<String>? = null,
        val models: List<String>? = null,
        val route: String? = null,
        val providerPreferences: JsonObject? = null,
        val reasoningEffort: String? = null,
    ) : LlmModelParamsConfig {
        override fun summaryText(): String {
            val effort = reasoningEffort?.trim().orEmpty()
            return if (effort.isBlank()) "openrouter/custom" else "openrouter/reasoning=$effort"
        }

        override fun supportsProvider(providerId: String): Boolean {
            return providerId.trim().lowercase() == PROVIDER_ID_OPENROUTER
        }
    }
}

@Serializable
public enum class OpenAiEndpoint {
    @SerialName("chat")
    Chat,

    @SerialName("responses")
    Responses,
}

@Serializable
public data class BaseModelParamsConfig(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val numberOfChoices: Int? = null,
    val speculation: String? = null,
    val schema: JsonSchemaConfig? = null,
    val toolChoice: ToolChoiceConfig? = null,
    val user: String? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
)

@Serializable
public data class JsonSchemaConfig(
    val name: String,
    val level: JsonSchemaLevelConfig = JsonSchemaLevelConfig.Standard,
    val schema: JsonObject,
)

@Serializable
public enum class JsonSchemaLevelConfig {
    @SerialName("basic")
    Basic,

    @SerialName("standard")
    Standard,
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("mode")
public sealed interface ToolChoiceConfig {
    @Serializable
    @SerialName("auto")
    public data object Auto : ToolChoiceConfig

    @Serializable
    @SerialName("none")
    public data object None : ToolChoiceConfig

    @Serializable
    @SerialName("required")
    public data object Required : ToolChoiceConfig

    @Serializable
    @SerialName("named")
    public data class Named(
        val name: String,
    ) : ToolChoiceConfig
}

@Serializable
public data class OpenAiChatParamsConfig(
    val base: BaseModelParamsConfig = BaseModelParamsConfig(),
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val parallelToolCalls: Boolean? = null,
    val promptCacheKey: String? = null,
    val safetyIdentifier: String? = null,
    val serviceTier: OpenAiServiceTierConfig? = null,
    val store: Boolean? = null,
    val logprobs: Boolean? = null,
    val reasoningEffort: OpenAiReasoningEffortConfig? = null,
    val stop: List<String>? = null,
    val topLogprobs: Int? = null,
    val topP: Double? = null,
)

@Serializable
public data class OpenAiResponsesParamsConfig(
    val base: BaseModelParamsConfig = BaseModelParamsConfig(),
    val background: Boolean? = null,
    val include: List<String>? = null,
    val maxToolCalls: Int? = null,
    val parallelToolCalls: Boolean? = null,
    val reasoning: OpenAiReasoningConfig? = null,
    val truncation: OpenAiTruncationConfig? = null,
    val promptCacheKey: String? = null,
    val safetyIdentifier: String? = null,
    val serviceTier: OpenAiServiceTierConfig? = null,
    val store: Boolean? = null,
    val logprobs: Boolean? = null,
    val topLogprobs: Int? = null,
    val topP: Double? = null,
)

@Serializable
public data class OpenAiReasoningConfig(
    val effort: OpenAiReasoningEffortConfig? = null,
    val summary: OpenAiReasoningSummaryConfig? = null,
)

@Serializable
public enum class OpenAiReasoningEffortConfig {
    @SerialName("none")
    None,

    @SerialName("minimal")
    Minimal,

    @SerialName("low")
    Low,

    @SerialName("medium")
    Medium,

    @SerialName("high")
    High,
}

@Serializable
public enum class OpenAiReasoningSummaryConfig {
    @SerialName("auto")
    Auto,

    @SerialName("concise")
    Concise,

    @SerialName("detailed")
    Detailed,
}

@Serializable
public enum class OpenAiTruncationConfig {
    @SerialName("auto")
    Auto,

    @SerialName("disabled")
    Disabled,
}

@Serializable
public enum class OpenAiServiceTierConfig {
    @SerialName("auto")
    Auto,

    @SerialName("default")
    Default,

    @SerialName("flex")
    Flex,

    @SerialName("priority")
    Priority,
}

@Serializable
public enum class AnthropicServiceTierConfig {
    @SerialName("auto")
    Auto,

    @SerialName("standard_only")
    StandardOnly,
}

@Serializable
public data class AnthropicThinkingConfig(
    val enabled: Boolean,
    val budgetTokens: Int? = null,
)

@Serializable
public data class GeminiThinkingConfig(
    val includeThoughts: Boolean? = null,
    val thinkingBudget: Int? = null,
    val thinkingLevel: GeminiThinkingLevelConfig? = null,
)

@Serializable
public enum class GeminiThinkingLevelConfig {
    @SerialName("low")
    Low,

    @SerialName("high")
    High,
}
