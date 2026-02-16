package io.github.stream29.kode.providers.xai

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.LlmAuth
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderPreset
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

public object XaiApiKeyProvider : LlmProvider<LlmAuth.ApiKey> {
    override val id: String = "xai"
    override val displayName: String = "xAI (API Key)"
    override val llmProvider: LLMProvider = XaiProviderKey

    override fun models(): List<LLModel> = XAI_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = "xAI",
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("XAI_API_KEY"),
        defaultBaseUrl = "https://api.x.ai/v1",
        description = "xAI Grok models",
        models = models(),
    )

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth.ApiKey): LLMClient {
        val baseUrl = normalizeBaseUrl(auth.baseUrl) ?: DEFAULT_BASE_URL
        val settings = OpenAIClientSettings(baseUrl = baseUrl)
        return OpenAILLMClient(
            apiKey = auth.apiKey,
            settings = settings,
            baseClient = createBaseClient(auth.customHeaders),
        )
    }
}

private object XaiProviderKey : LLMProvider("xai", "xAI")

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
    LLMCapability.Vision.Image,
    LLMCapability.OpenAIEndpoint.Completions,
)

private val XAI_MODELS: List<LLModel> = listOf(
    LLModel(
        provider = XaiProviderKey,
        id = "grok-4",
        capabilities = BASE_CAPABILITIES,
        contextLength = 256_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-4-fast",
        capabilities = BASE_CAPABILITIES,
        contextLength = 2_000_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-4-fast-non-reasoning",
        capabilities = BASE_CAPABILITIES,
        contextLength = 2_000_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-4-1-fast",
        capabilities = BASE_CAPABILITIES,
        contextLength = 2_000_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-4-1-fast-non-reasoning",
        capabilities = BASE_CAPABILITIES,
        contextLength = 2_000_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-code-fast-1",
        capabilities = BASE_CAPABILITIES,
        contextLength = 256_000,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3-fast",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3-fast-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3-mini",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-3-mini-fast-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-2-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-2-vision",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-2-vision-latest",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-2-vision-1212",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-vision-beta",
        capabilities = BASE_CAPABILITIES,
        contextLength = 8_192,
    ),
    LLModel(
        provider = XaiProviderKey,
        id = "grok-beta",
        capabilities = BASE_CAPABILITIES,
        contextLength = 131_072,
    ),
)

private const val DEFAULT_BASE_URL: String = "https://api.x.ai"
