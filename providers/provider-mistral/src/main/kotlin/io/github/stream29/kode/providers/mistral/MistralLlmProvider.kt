package io.github.stream29.kode.providers.mistral

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

public object MistralApiKeyProvider : LlmProvider {
    override val id: String = "mistral"
    override val displayName: String = "Mistral (API Key)"
    override val llmProvider: LLMProvider = MistralProviderKey

    override fun models(): List<LLModel> = MISTRAL_MODELS

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        val apiKeyAuth = requireApiKeyAuth(providerId = id, auth = auth)
        val baseUrl = normalizeBaseUrl(apiKeyAuth.baseUrl) ?: DEFAULT_BASE_URL
        val settings = OpenAIClientSettings(baseUrl = baseUrl)
        return OpenAILLMClient(
            apiKey = apiKeyAuth.apiKey,
            settings = settings,
            baseClient = createBaseClient(apiKeyAuth.customHeaders),
        )
    }
}

private object MistralProviderKey : LLMProvider("mistral", "Mistral")

private fun normalizeBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1")
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

private val BASE_CAPABILITIES: List<LLMCapability> = listOf(
    LLMCapability.Temperature,
    LLMCapability.ToolChoice,
    LLMCapability.Tools,
    LLMCapability.Completion,
    LLMCapability.MultipleChoices,
    LLMCapability.Schema.JSON.Basic,
    LLMCapability.Schema.JSON.Standard,
    LLMCapability.OpenAIEndpoint.Completions,
)

private val MISTRAL_MODELS: List<LLModel> = listOf(
    LLModel(
        provider = MistralProviderKey,
        id = "mistral-large-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 128_000,
    ),
    LLModel(
        provider = MistralProviderKey,
        id = "mistral-medium-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 128_000,
    ),
    LLModel(
        provider = MistralProviderKey,
        id = "mistral-small-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 32_000,
    ),
    LLModel(
        provider = MistralProviderKey,
        id = "magistral-medium-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 128_000,
    ),
    LLModel(
        provider = MistralProviderKey,
        id = "codestral-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 256_000,
    ),
    LLModel(
        provider = MistralProviderKey,
        id = "devstral-medium-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 128_000,
    ),
)

private const val DEFAULT_BASE_URL: String = "https://api.mistral.ai"
