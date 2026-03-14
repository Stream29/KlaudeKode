package io.github.stream29.kode.providers.groq

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

public object GroqApiKeyProvider : LlmProvider {
    override val id: String = "groq"
    override val displayName: String = "Groq (API Key)"
    override val llmProvider: LLMProvider = GroqProviderKey

    override fun models(): List<LLModel> = GROQ_MODELS

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

private object GroqProviderKey : LLMProvider("groq", "Groq")

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

private val GROQ_MODELS: List<LLModel> = listOf(
    LLModel(
        provider = GroqProviderKey,
        id = "llama-3.3-70b-versatile",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "openai/gpt-oss-120b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "openai/gpt-oss-20b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "qwen/qwen3-32b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "meta-llama/llama-4-maverick-17b-128e-instruct",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "meta-llama/llama-4-scout-17b-16e-instruct",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "llama-3.1-8b-instant",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "moonshotai/kimi-k2-instruct-0905",
        capabilities = BASE_CAPABILITIES,
        contextLength = 262_144,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "mistral-saba-24b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 32_768,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "deepseek-r1-distill-llama-70b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "qwen-qwq-32b",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "llama3-70b-8192",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "llama3-8b-8192",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "gemma2-9b-it",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = GroqProviderKey,
        id = "moonshotai/kimi-k2-instruct",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
)

private const val DEFAULT_BASE_URL: String = "https://api.groq.com/openai"
