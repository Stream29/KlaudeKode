package io.github.stream29.kode.providers.anthropic

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.LlmAuth
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderPreset
import io.github.stream29.kode.providers.api.requireApiKeyAuth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

public object AnthropicApiKeyProvider : LlmProvider {
    override val id: String = "anthropic"
    override val displayName: String = "Anthropic (API Key)"
    override val llmProvider: LLMProvider = LLMProvider.Anthropic

    override fun models(): List<LLModel> = ANTHROPIC_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = displayName,
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("ANTHROPIC_API_KEY"),
        defaultBaseUrl = "https://api.anthropic.com/v1",
        description = "Claude models",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        val apiKeyAuth = requireApiKeyAuth(providerId = id, auth = auth)
        val settings = createSettings(apiKeyAuth.baseUrl)
        return AnthropicLLMClient(
            apiKey = apiKeyAuth.apiKey,
            settings = settings,
            baseClient = createBaseClient(apiKeyAuth.customHeaders),
        )
    }
}

private fun createBaseClient(customHeaders: Map<String, String>): HttpClient {
    if (customHeaders.isEmpty()) {
        return HttpClient()
    }
    return HttpClient {
        defaultRequest {
            customHeaders.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    header(key, value)
                }
            }
        }
    }
}

private fun createSettings(baseUrlInput: String?): AnthropicClientSettings {
    val normalized = normalizeAnthropicBaseUrl(baseUrlInput)
    return if (normalized == null) {
        AnthropicClientSettings()
    } else {
        AnthropicClientSettings(baseUrl = normalized)
    }
}

private fun normalizeAnthropicBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1")
}

private val ANTHROPIC_MODELS: List<LLModel> = listOf(
    AnthropicModels.Sonnet_4_5,
    AnthropicModels.Haiku_4_5,
    AnthropicModels.Opus_4_5,
    AnthropicModels.Opus_4_1,
    AnthropicModels.Sonnet_4,
    AnthropicModels.Opus_4,
    AnthropicModels.Sonnet_3_7,
    AnthropicModels.Sonnet_3_5,
    AnthropicModels.Haiku_3_5,
    AnthropicModels.Opus_3,
    AnthropicModels.Haiku_3,
)
