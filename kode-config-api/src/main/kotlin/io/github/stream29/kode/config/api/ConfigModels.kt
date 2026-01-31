package io.github.stream29.kode.config.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class AppConfig(
    val auths: List<LlmAuthConfig>,
    val models: List<LlmModelConfig>,
)

@Serializable
public sealed interface LlmAuthConfig {
    public val id: String
    public val provider: String
    public val apiKey: String
    public val baseUrl: String?

    @Serializable
    @SerialName("Anthropic")
    public data class Anthropic(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
    ) : LlmAuthConfig {
        override val provider: String = "Anthropic"
    }

    @Serializable
    @SerialName("OpenAI")
    public data class OpenAI(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
    ) : LlmAuthConfig {
        override val provider: String = "OpenAI"
    }

    @Serializable
    @SerialName("Moonshot")
    public data class Moonshot(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
    ) : LlmAuthConfig {
        override val provider: String = "Moonshot"
    }

    @Serializable
    @SerialName("Gemini")
    public data class Gemini(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
    ) : LlmAuthConfig {
        override val provider: String = "Gemini"
    }

    @Serializable
    @SerialName("DeepSeek")
    public data class DeepSeek(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String?,
    ) : LlmAuthConfig {
        override val provider: String = "DeepSeek"
    }

    @Serializable
    @SerialName("OpenAICompatible")
    public data class OpenAICompatible(
        override val id: String,
        override val apiKey: String,
        override val baseUrl: String,
        public val name: String,
    ) : LlmAuthConfig {
        override val provider: String = name
    }
}

@Serializable
public data class LlmModelConfig(
    val id: String,
    val authId: String,
    val model: String,
    val displayName: String?,
)
