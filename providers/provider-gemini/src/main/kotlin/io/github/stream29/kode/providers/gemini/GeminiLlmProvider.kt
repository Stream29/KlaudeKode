package io.github.stream29.kode.providers.gemini

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.LlmAuth
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderPreset
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

public object GeminiApiKeyProvider : LlmProvider<LlmAuth.ApiKey> {
    override val id: String = "gemini"
    override val displayName: String = "Google Gemini (API Key)"
    override val llmProvider: LLMProvider = LLMProvider.Google

    override fun models(): List<LLModel> = GEMINI_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = "Google Gemini",
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("GEMINI_API_KEY", "GOOGLE_API_KEY"),
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        description = "Google Gemini models",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth.ApiKey): LLMClient {
        val settings = createSettings(auth.baseUrl)
        return GoogleLLMClient(
            apiKey = auth.apiKey,
            settings = settings,
            baseClient = createBaseClient(auth.customHeaders),
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

private fun createSettings(baseUrlInput: String?): GoogleClientSettings {
    val normalized = normalizeGoogleBaseUrl(baseUrlInput)
    return if (normalized == null) {
        GoogleClientSettings()
    } else {
        GoogleClientSettings(baseUrl = normalized)
    }
}

private fun normalizeGoogleBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1beta")
}

private val GEMINI_MODELS: List<LLModel> = listOf(
    GoogleModels.Gemini3_Pro_Preview,
    GoogleModels.Gemini2_5Pro,
    GoogleModels.Gemini2_5Flash,
    GoogleModels.Gemini2_5FlashLite,
    GoogleModels.Gemini2_0Flash,
    GoogleModels.Gemini2_0Flash001,
    GoogleModels.Gemini2_0FlashLite,
    GoogleModels.Gemini2_0FlashLite001,
)
