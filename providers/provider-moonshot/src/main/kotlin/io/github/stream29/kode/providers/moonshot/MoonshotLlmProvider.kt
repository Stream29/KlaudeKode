package io.github.stream29.kode.providers.moonshot

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
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

public object MoonshotApiKeyProvider : LlmProvider {
    override val id: String = "moonshot"
    override val displayName: String = "Moonshot (API Key)"
    override val llmProvider: LLMProvider = MoonshotProviderKey

    override fun models(): List<LLModel> = MOONSHOT_MODELS

    public val preset: ProviderPreset = ProviderPreset(
        id = id,
        displayName = "Moonshot",
        authModes = setOf(ProviderAuthMode.ApiKey),
        envKeys = listOf("MOONSHOT_API_KEY"),
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        description = "Moonshot Kimi models",
        models = models(),
    )

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

private object MoonshotProviderKey : LLMProvider("moonshot", "Moonshot")

private fun normalizeBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1")
}

private val OPENAI_TEMPLATE: LLModel = OpenAIModels.Chat.GPT4o

private val MOONSHOT_MODELS: List<LLModel> = listOf(
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2.5",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 262_144,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-thinking-turbo",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 262_144,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-thinking",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 262_144,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-turbo-preview",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 262_144,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-0905-preview",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 262_144,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-0711-preview",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 131_072,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "kimi-k2-0711",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 131_072,
    ),
    LLModel(
        provider = MoonshotProviderKey,
        id = "moonshot-v1-128k",
        capabilities = OPENAI_TEMPLATE.capabilities,
        contextLength = 128_000,
    ),
)

private const val DEFAULT_BASE_URL: String = "https://api.moonshot.cn"
