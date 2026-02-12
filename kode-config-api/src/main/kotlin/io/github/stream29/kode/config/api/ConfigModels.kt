package io.github.stream29.kode.config.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val PROVIDER_ANTHROPIC: String = "Anthropic"
private const val PROVIDER_OPEN_AI: String = "OpenAI"
private const val PROVIDER_MOONSHOT: String = "Moonshot"
private const val PROVIDER_GEMINI: String = "Gemini"
private const val PROVIDER_DEEP_SEEK: String = "DeepSeek"
private const val PROVIDER_OPEN_AI_COMPATIBLE: String = "OpenAICompatible"

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
    @Suppress("DEPRECATION")
    val agent: AgentConfig = AgentConfig(),
    val ui: UiConfig = UiConfig(),
    val approvals: LegacyApprovalsConfig = LegacyApprovalsConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val tools: ToolsConfig = ToolsConfig(),
)

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
public data class McpServerConfig(
    val transport: String = "stdio",
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String>? = null,
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val auth: String? = null,
)

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
@Deprecated("Use PresetConfig via AppConfig.preset")
public data class AgentConfig(
    val builtin: String? = null,
    val file: String? = null,
)

@Serializable
public data class UiConfig(
    val theme: String = "dark",
    val messageAlignment: String = "left",
    val messageMaxWidthRatio: Float = 0.9f,
    val lastOpenedSessionId: String? = null,
)

@Serializable
public data class LegacyApprovalsConfig(
    val yoloDefault: Boolean = true,
    val autoApproveActions: List<String> = emptyList(),
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
public sealed interface LlmAuthConfig {
    public val id: String
    public val provider: String
    public val apiKey: String
    public val baseUrl: String?
    public val customHeaders: Map<String, String>?
    public val env: Map<String, String>?
    public val oauth: OAuthConfig?

    @Serializable
    @SerialName(PROVIDER_ANTHROPIC)
    public data class Anthropic(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = PROVIDER_ANTHROPIC
    }

    @Serializable
    @SerialName(PROVIDER_OPEN_AI)
    public data class OpenAI(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = PROVIDER_OPEN_AI
    }

    @Serializable
    @SerialName(PROVIDER_MOONSHOT)
    public data class Moonshot(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = PROVIDER_MOONSHOT
    }

    @Serializable
    @SerialName(PROVIDER_GEMINI)
    public data class Gemini(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = PROVIDER_GEMINI
    }

    @Serializable
    @SerialName(PROVIDER_DEEP_SEEK)
    public data class DeepSeek(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = PROVIDER_DEEP_SEEK
    }

    @Serializable
    @SerialName(PROVIDER_OPEN_AI_COMPATIBLE)
    public data class OpenAICompatible(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String,
        public val name: String,
        override val customHeaders: Map<String, String>? = null,
        override val env: Map<String, String>? = null,
        override val oauth: OAuthConfig? = null,
    ) : LlmAuthConfig {
        override val provider: String = name
    }
}

@Serializable
public data class OAuthConfig(
    val storage: String = "file",
    val key: String,
)

@Serializable
public data class LlmModelConfig(
    val id: String,
    val authId: String,
    val model: String,
    val displayName: String?,
    val maxContextSize: Int? = null,
    val capabilities: List<String>? = null,
)
