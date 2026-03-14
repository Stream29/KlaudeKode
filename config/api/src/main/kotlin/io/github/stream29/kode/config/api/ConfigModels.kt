package io.github.stream29.kode.config.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal const val PROVIDER_ID_ANTHROPIC: String = "anthropic"
internal const val PROVIDER_ID_GEMINI: String = "gemini"
internal const val PROVIDER_ID_DEEPSEEK: String = "deepseek"
internal const val PROVIDER_ID_OPENROUTER: String = "openrouter"

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
