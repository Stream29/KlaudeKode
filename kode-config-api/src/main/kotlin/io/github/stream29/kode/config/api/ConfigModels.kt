package io.github.stream29.kode.config.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

private const val PROVIDER_ID_ANTHROPIC: String = "anthropic"
private const val PROVIDER_ID_GEMINI: String = "gemini"
private const val PROVIDER_ID_DEEPSEEK: String = "deepseek"
private const val PROVIDER_ID_OPENROUTER: String = "openrouter"

public const val PROVIDER_ID_OPENAI_API_KEY: String = "openai-api-key"
public const val PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER: String = "openai-subscription-browser"
public const val PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE: String = "openai-subscription-device"
public const val PROVIDER_ID_OPENAI_COMPATIBLE: String = "openai-compatible"
public const val PROVIDER_ID_GROQ: String = "groq"
public const val PROVIDER_ID_XAI: String = "xai"
public const val PROVIDER_ID_MOONSHOT: String = "moonshot"
public const val PROVIDER_ID_MISTRAL: String = "mistral"

public const val AUTH_MODE_API_KEY: String = "api_key"
public const val AUTH_MODE_OAUTH_SUBSCRIPTION: String = "oauth_subscription"
public const val AUTH_MODE_OAUTH_DEVICE: String = "oauth_device"
public const val AUTH_MODE_CLOUD_CREDENTIAL_CHAIN: String = "cloud_credential_chain"
public const val AUTH_MODE_WELL_KNOWN: String = "well_known"

public val OPENAI_LIKE_PROVIDER_IDS: Set<String> = setOf(
    PROVIDER_ID_OPENAI_API_KEY,
    PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER,
    PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE,
    PROVIDER_ID_OPENAI_COMPATIBLE,
    PROVIDER_ID_GROQ,
    PROVIDER_ID_XAI,
    PROVIDER_ID_MOONSHOT,
    PROVIDER_ID_MISTRAL,
)

public val OPENAI_NATIVE_PROVIDER_IDS: Set<String> = setOf(
    PROVIDER_ID_OPENAI_API_KEY,
    PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER,
    PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE,
)

public val OPENAI_COMPATIBLE_PROVIDER_IDS: Set<String> = setOf(
    PROVIDER_ID_OPENAI_COMPATIBLE,
    PROVIDER_ID_GROQ,
    PROVIDER_ID_XAI,
    PROVIDER_ID_MOONSHOT,
    PROVIDER_ID_MISTRAL,
)
private const val AUTH_TYPE_API_KEY: String = "api_key"
private const val AUTH_TYPE_OAUTH: String = "oauth"

@Serializable
public data class AppConfig(
    val auths: List<LlmAuthConfig> = emptyList(),
    val models: List<LlmModelConfig> = emptyList(),
    val storage: StorageConfig = StorageConfig(),
    val defaults: DefaultsConfig = DefaultsConfig(),
    val loopControl: LoopControlConfig = LoopControlConfig(),
    val services: ServicesConfig = ServicesConfig(),
    val mcp: McpConfig = McpConfig(),
    val skills: SkillsConfig = SkillsConfig(),
    val preset: PresetConfig = PresetConfig(),
    val ui: UiConfig = UiConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val tools: ToolsConfig = ToolsConfig(),
)

@Serializable
public data class LlmAuthConfig(
    val id: String,
    val providerId: String,
    val name: String? = null,
    val auth: LlmAuth,
)

@Serializable
public sealed interface LlmAuth {
    public val baseUrl: String?
    public val customHeaders: Map<String, String>

    public fun apiKeyOrNull(): String? {
        return (this as? ApiKey)?.apiKey
    }

    public fun oauthConfigOrNull(): OAuthConfig? {
        return (this as? OAuth)?.oauth
    }

    public fun isOAuth(): Boolean {
        return this is OAuth
    }

    @Serializable
    @SerialName(AUTH_TYPE_API_KEY)
    public data class ApiKey(
        val apiKey: String,
        val envKeys: List<String> = emptyList(),
        override val baseUrl: String? = null,
        override val customHeaders: Map<String, String> = emptyMap(),
    ) : LlmAuth

    @Serializable
    @SerialName(AUTH_TYPE_OAUTH)
    public data class OAuth(
        val oauth: OAuthConfig,
        override val baseUrl: String? = null,
        override val customHeaders: Map<String, String> = emptyMap(),
    ) : LlmAuth
}

@Serializable
public data class StorageConfig(
    val dataDir: String = "~/.kode/",
)

@Serializable
public data class DefaultsConfig(
    val modelId: String? = null,
    val thinking: Boolean = false,
    val workDir: String? = null,
)

@Serializable
public data class LoopControlConfig(
    val maxStepsPerTurn: Int = 100,
    val maxRetriesPerStep: Int = 3,
    val maxRalphIterations: Int = 0,
    val reservedContextSize: Int = 50000,
)

@Serializable
public data class ServicesConfig(
    val webSearch: ServiceConfig? = null,
    val webFetch: ServiceConfig? = null,
)

@Serializable
public data class ServiceConfig(
    val provider: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val customHeaders: Map<String, String>? = null,
    val env: Map<String, String>? = null,
    val oauth: OAuthConfig? = null,
)

@Serializable
public data class McpConfig(
    val client: McpClientConfig = McpClientConfig(),
    val servers: Map<String, McpServerConfig> = emptyMap(),
)

@Serializable
public data class McpClientConfig(
    val toolCallTimeoutMs: Int = 60000,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("transport")
public sealed interface McpServerConfig {
    public val transport: String
    public val command: String?
    public val args: List<String>
    public val env: Map<String, String>?
    public val url: String?
    public val headers: Map<String, String>?
    public val auth: String?

    @Serializable
    @SerialName("stdio")
    public data class Stdio(
        override val command: String? = null,
        override val args: List<String> = emptyList(),
        override val env: Map<String, String>? = null,
    ) : McpServerConfig {
        override val transport: String = McpTransportType.Stdio.configValue
        override val url: String? = null
        override val headers: Map<String, String>? = null
        override val auth: String? = null
    }

    @Serializable
    @SerialName("http")
    public data class Http(
        override val url: String? = null,
        override val headers: Map<String, String>? = null,
        override val auth: String? = null,
    ) : McpServerConfig {
        override val transport: String = McpTransportType.Http.configValue
        override val command: String? = null
        override val args: List<String> = emptyList()
        override val env: Map<String, String>? = null
    }

    @Serializable
    @SerialName("sse")
    public data class Sse(
        override val url: String? = null,
        override val headers: Map<String, String>? = null,
        override val auth: String? = null,
    ) : McpServerConfig {
        override val transport: String = McpTransportType.Sse.configValue
        override val command: String? = null
        override val args: List<String> = emptyList()
        override val env: Map<String, String>? = null
    }
}

public enum class McpTransportType(
    public val configValue: String,
) {
    Stdio(configValue = "stdio"),
    Http(configValue = "http"),
    Sse(configValue = "sse"),
    Unsupported(configValue = "unsupported"),
    ;

    public fun isRemote(): Boolean {
        return this == Http || this == Sse
    }

    public fun usesCommandProcess(): Boolean {
        return this == Stdio
    }

    public fun usesUrlTransport(): Boolean {
        return this.isRemote()
    }

    public companion object {
        public fun fromValue(value: String): McpTransportType {
            return when (value.trim().lowercase()) {
                "stdio" -> Stdio
                "http" -> Http
                "sse" -> Sse
                else -> Unsupported
            }
        }
    }
}

public fun McpServerConfig.transportType(): McpTransportType {
    return when (this) {
        is McpServerConfig.Stdio -> McpTransportType.Stdio
        is McpServerConfig.Http -> McpTransportType.Http
        is McpServerConfig.Sse -> McpTransportType.Sse
    }
}

public fun McpServerConfig.supportsBrowserOAuth(): Boolean {
    return transportType() == McpTransportType.Http && auth?.trim()?.lowercase() == "oauth"
}

@Serializable
public data class SkillsConfig(
    val dir: String? = null,
)

@Serializable
public data class PresetConfig(
    val builtin: String? = null,
    val file: String? = null,
)

@Serializable
public data class UiConfig(
    val theme: String = "dark",
    val messageAlignment: String = "left",
    val messageMaxWidthRatio: Float = 0.9f,
    val sendKeyMode: String = "ctrl_or_cmd_enter_send",
    val lastOpenedSessionId: String? = null,
)

@Serializable
public data class LoggingConfig(
    val level: String = "info",
    val file: String? = null,
)

@Serializable
public data class ToolsConfig(
    val disabled: List<String> = emptyList(),
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("flow")
public sealed interface OAuthConfig {
    public val storage: String
    public val key: String
    public val tokenEndpoint: String?
    public val clientId: String?
    public val scopes: List<String>
    public val tokenAdditionalParams: Map<String, String>

    public val authorizationEndpoint: String?
        get() = null
    public val callbackUri: String?
        get() = null
    public val authorizationAdditionalParams: Map<String, String>
        get() = emptyMap()
    public val deviceFlowStrategy: String?
        get() = null
    public val deviceAuthorizationEndpoint: String?
        get() = null
    public val deviceTokenEndpoint: String?
        get() = null
    public val deviceVerificationUri: String?
        get() = null
    public val deviceRedirectUri: String?
        get() = null

    public fun isDeviceFlow(providerId: String? = null): Boolean {
        val normalizedProviderId = providerId?.trim().orEmpty().lowercase()
        return this is DeviceFlow || normalizedProviderId == "openai-subscription-device"
    }

    public fun requiresDeviceTokenPollEndpoint(): Boolean {
        return deviceFlowStrategy?.trim()?.lowercase() == "openai_codex_bridge"
    }

    public fun hasAuthCodeRequiredFields(): Boolean {
        return !authorizationEndpoint.isNullOrBlank() && !tokenEndpoint.isNullOrBlank() && !clientId.isNullOrBlank()
    }

    public fun hasDeviceFlowRequiredFields(): Boolean {
        val hasBaseFields = !tokenEndpoint.isNullOrBlank() &&
            !clientId.isNullOrBlank() &&
            !deviceAuthorizationEndpoint.isNullOrBlank()
        if (!hasBaseFields) {
            return false
        }
        if (!requiresDeviceTokenPollEndpoint()) {
            return true
        }
        return !deviceTokenEndpoint.isNullOrBlank()
    }

    public fun canInteractiveConnect(providerId: String? = null): Boolean {
        if (key.isBlank()) {
            return false
        }
        return if (isDeviceFlow(providerId = providerId)) {
            hasDeviceFlowRequiredFields()
        } else {
            hasAuthCodeRequiredFields()
        }
    }

    @Serializable
    @SerialName("auth_code_pkce")
    public data class AuthCodePkce(
        override val storage: String = "file",
        override val key: String,
        override val authorizationEndpoint: String? = null,
        override val tokenEndpoint: String? = null,
        override val clientId: String? = null,
        override val scopes: List<String> = emptyList(),
        override val callbackUri: String? = null,
        override val authorizationAdditionalParams: Map<String, String> = emptyMap(),
        override val tokenAdditionalParams: Map<String, String> = emptyMap(),
    ) : OAuthConfig

    @Serializable
    @SerialName("device_flow")
    public data class DeviceFlow(
        override val storage: String = "file",
        override val key: String,
        override val tokenEndpoint: String? = null,
        override val clientId: String? = null,
        override val scopes: List<String> = emptyList(),
        override val tokenAdditionalParams: Map<String, String> = emptyMap(),
        override val deviceFlowStrategy: String? = null,
        override val deviceAuthorizationEndpoint: String? = null,
        override val deviceTokenEndpoint: String? = null,
        override val deviceVerificationUri: String? = null,
        override val deviceRedirectUri: String? = null,
    ) : OAuthConfig
}

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
