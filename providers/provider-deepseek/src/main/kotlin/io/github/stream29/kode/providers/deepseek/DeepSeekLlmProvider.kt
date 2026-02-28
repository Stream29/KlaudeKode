package io.github.stream29.kode.providers.deepseek

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

public object DeepSeekApiKeyProvider : LlmProvider {
    override val id: String = "deepseek"
    override val displayName: String = "DeepSeek (API Key)"
    override val llmProvider: LLMProvider = LLMProvider.DeepSeek

    override fun models(): List<LLModel> = DEEPSEEK_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = displayName,
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("DEEPSEEK_API_KEY"),
        defaultBaseUrl = "https://api.deepseek.com/v1",
        description = "DeepSeek API",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        val apiKeyAuth = requireApiKeyAuth(providerId = id, auth = auth)
        val settings = createSettings(apiKeyAuth.baseUrl)
        return DeepSeekLLMClient(
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

private fun createSettings(baseUrlInput: String?): DeepSeekClientSettings {
    val normalized = normalizeDeepSeekBaseUrl(baseUrlInput)
    return if (normalized == null) {
        DeepSeekClientSettings()
    } else {
        DeepSeekClientSettings(baseUrl = normalized)
    }
}

private fun normalizeDeepSeekBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1")
}

private val DEEPSEEK_MODELS: List<LLModel> = listOf(
    DeepSeekModels.DeepSeekReasoner,
    DeepSeekModels.DeepSeekChat,
)
