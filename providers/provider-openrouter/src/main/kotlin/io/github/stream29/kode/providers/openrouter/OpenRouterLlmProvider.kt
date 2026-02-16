package io.github.stream29.kode.providers.openrouter

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.LlmAuth
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderPreset
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

public object OpenRouterApiKeyProvider : LlmProvider<LlmAuth.ApiKey> {
    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter (API Key)"
    override val llmProvider: LLMProvider = LLMProvider.OpenRouter

    override fun models(): List<LLModel> = OPENROUTER_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = "OpenRouter",
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("OPENROUTER_API_KEY"),
        defaultBaseUrl = "https://openrouter.ai",
        description = "OpenRouter unified endpoint",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth.ApiKey): LLMClient {
        val baseUrl = normalizeBaseUrl(auth.baseUrl) ?: DEFAULT_BASE_URL
        val settings = OpenRouterClientSettings(baseUrl = baseUrl)
        return OpenRouterLLMClient(
            apiKey = auth.apiKey,
            settings = settings,
            baseClient = createBaseClient(auth.customHeaders),
        )
    }
}

private fun normalizeBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/api/v1")
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

private val OPENROUTER_MODELS: List<LLModel> = listOf(
    OpenRouterModels.GPT4o,
    OpenRouterModels.GPT4oMini,
    OpenRouterModels.GPT5,
    OpenRouterModels.GPT5Mini,
    OpenRouterModels.GPT5Nano,
    OpenRouterModels.GPT5Chat,
    OpenRouterModels.GPT5_2,
    OpenRouterModels.GPT5_2Pro,
    OpenRouterModels.Claude4_5Sonnet,
    OpenRouterModels.Claude4_5Haiku,
    OpenRouterModels.Claude4_5Opus,
    OpenRouterModels.Claude4_1Opus,
    OpenRouterModels.Claude4Sonnet,
    OpenRouterModels.Claude3_7Sonnet,
    OpenRouterModels.Claude3_5Sonnet,
    OpenRouterModels.Claude3Sonnet,
    OpenRouterModels.Claude3Opus,
    OpenRouterModels.Claude3Haiku,
    OpenRouterModels.Claude3VisionSonnet,
    OpenRouterModels.Claude3VisionOpus,
    OpenRouterModels.Claude3VisionHaiku,
    OpenRouterModels.GPT4Turbo,
    OpenRouterModels.GPT4,
    OpenRouterModels.GPT35Turbo,
    OpenRouterModels.DeepSeekV30324,
    OpenRouterModels.Gemini2_5Pro,
    OpenRouterModels.Gemini2_5Flash,
    OpenRouterModels.Gemini2_5FlashLite,
    OpenRouterModels.Qwen2_5,
    OpenRouterModels.Qwen3VL,
    OpenRouterModels.GPT_OSS_120b,
    OpenRouterModels.Llama3,
    OpenRouterModels.Llama3Instruct,
    OpenRouterModels.Mistral7B,
    OpenRouterModels.Mixtral8x7B,
    OpenRouterModels.Phi4Reasoning,
)

private const val DEFAULT_BASE_URL: String = "https://openrouter.ai"
