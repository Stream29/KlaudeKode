package io.github.stream29.kode.providers.openai

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

public object OpenAiApiKeyProvider : LlmProvider {
    override val id: String = "openai-api-key"
    override val displayName: String = "OpenAI (API Key)"
    override val llmProvider: LLMProvider = OpenAiApiKeyProviderKey

    override fun models(): List<LLModel> = OPENAI_MODELS

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        val apiKeyAuth = requireApiKeyAuth(providerId = id, auth = auth)
        val settings = createSettings(apiKeyAuth.baseUrl)
        return OpenAILLMClient(
            apiKey = apiKeyAuth.apiKey,
            settings = settings,
            baseClient = createBaseClient(apiKeyAuth.customHeaders),
        )
    }
}

public object OpenAiSubscriptionBrowserProvider : LlmProvider {
    override val id: String = "openai-subscription-browser"
    override val displayName: String = "OpenAI Subscription (Browser OAuth)"
    override val llmProvider: LLMProvider = OpenAiSubscriptionBrowserProviderKey

    override fun models(): List<LLModel> = OPENAI_MODELS

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.OAuthAccessToken

    override fun createClient(auth: LlmAuth): LLMClient {
        val oauthAuth = requireOAuthAccessTokenAuth(providerId = id, auth = auth)
        val settings = createSubscriptionSettings(oauthAuth.baseUrl)
        return OpenAILLMClient(
            apiKey = oauthAuth.accessToken,
            settings = settings,
            baseClient = createBaseClient(oauthAuth.customHeaders),
        )
    }
}

public object OpenAiSubscriptionDeviceProvider : LlmProvider {
    override val id: String = "openai-subscription-device"
    override val displayName: String = "OpenAI Subscription (Headless OAuth)"
    override val llmProvider: LLMProvider = OpenAiSubscriptionDeviceProviderKey

    override fun models(): List<LLModel> = OPENAI_MODELS

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.OAuthAccessToken

    override fun createClient(auth: LlmAuth): LLMClient {
        val oauthAuth = requireOAuthAccessTokenAuth(providerId = id, auth = auth)
        val settings = createSubscriptionSettings(oauthAuth.baseUrl)
        return OpenAILLMClient(
            apiKey = oauthAuth.accessToken,
            settings = settings,
            baseClient = createBaseClient(oauthAuth.customHeaders),
        )
    }
}

public object OpenAiCompatibleProvider : LlmProvider {
    override val id: String = "openai-compatible"
    override val displayName: String = "OpenAI-Compatible"
    override val llmProvider: LLMProvider = OpenAiCompatibleProviderKey

    override fun models(): List<LLModel> = OPENAI_MODELS

    override fun supportsAuth(auth: LlmAuth): Boolean = auth is LlmAuth.ApiKey

    override fun createClient(auth: LlmAuth): LLMClient {
        val apiKeyAuth = requireApiKeyAuth(providerId = id, auth = auth)
        val baseUrl = requireNotNull(apiKeyAuth.baseUrl) { "baseUrl is required for OpenAI-Compatible" }
        val settings = createSettings(baseUrl)
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

private fun createSettings(baseUrlInput: String?): OpenAIClientSettings {
    val normalized = normalizeOpenAiBaseUrl(baseUrlInput)
    return if (normalized == null) {
        OpenAIClientSettings()
    } else {
        OpenAIClientSettings(baseUrl = normalized)
    }
}

private fun createSubscriptionSettings(baseUrlInput: String?): OpenAIClientSettings {
    val normalizedBaseUrl = normalizeOpenAiSubscriptionBaseUrl(baseUrlInput)
    return OpenAIClientSettings(
        baseUrl = normalizedBaseUrl,
        chatCompletionsPath = OPENAI_CODEX_RESPONSES_PATH,
        responsesAPIPath = OPENAI_CODEX_RESPONSES_PATH,
    )
}

private fun normalizeOpenAiBaseUrl(baseUrl: String?): String? {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    val withoutSlash = trimmed.trimEnd('/')
    return withoutSlash.removeSuffix("/v1")
}

private fun normalizeOpenAiSubscriptionBaseUrl(baseUrl: String?): String {
    val trimmed = baseUrl?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return OPENAI_CODEX_BASE_URL
    }
    val normalized = trimmed.trimEnd('/').lowercase()
    if (normalized == OPENAI_BASE_URL || normalized == OPENAI_BASE_URL_HOST_ONLY) {
        return OPENAI_CODEX_BASE_URL
    }
    if (normalized.startsWith("$OPENAI_CODEX_BASE_URL/backend-api/codex")) {
        return OPENAI_CODEX_BASE_URL
    }
    if (normalized.startsWith(OPENAI_CODEX_BASE_URL)) {
        return OPENAI_CODEX_BASE_URL
    }
    return trimmed.trimEnd('/')
}

private val OPENAI_MODELS: List<LLModel> = listOf(
    OpenAIModels.Chat.GPT5,
    OpenAIModels.Chat.GPT5Mini,
    OpenAIModels.Chat.GPT5Nano,
    OpenAIModels.Chat.GPT5Codex,
    OpenAIModels.Chat.GPT5Pro,
    OpenAIModels.Chat.GPT5_1,
    OpenAIModels.Chat.GPT5_1Codex,
    OpenAIModels.Chat.GPT5_2,
    OpenAIModels.Chat.GPT5_2Pro,
    OpenAIModels.Chat.GPT4o,
    OpenAIModels.Chat.GPT4oMini,
    OpenAIModels.Chat.GPT4_1,
    OpenAIModels.Chat.GPT4_1Mini,
    OpenAIModels.Chat.GPT4_1Nano,
    OpenAIModels.Chat.O1,
    OpenAIModels.Chat.O3,
    OpenAIModels.Chat.O3Mini,
    OpenAIModels.Chat.O4Mini,
)

private object OpenAiApiKeyProviderKey : LLMProvider("openai-api-key", "OpenAI (API Key)")
private object OpenAiSubscriptionBrowserProviderKey : LLMProvider(
    "openai-subscription-browser",
    "OpenAI Subscription (Browser OAuth)",
)

private object OpenAiSubscriptionDeviceProviderKey : LLMProvider(
    "openai-subscription-device",
    "OpenAI Subscription (Headless OAuth)",
)

private object OpenAiCompatibleProviderKey : LLMProvider("openai-compatible", "OpenAI-Compatible")

private const val OPENAI_BASE_URL: String = "https://api.openai.com/v1"
private const val OPENAI_BASE_URL_HOST_ONLY: String = "https://api.openai.com"
private const val OPENAI_CODEX_BASE_URL: String = "https://chatgpt.com"
private const val OPENAI_CODEX_RESPONSES_PATH: String = "backend-api/codex/responses"
private const val OPENAI_CLIENT_ID: String = "app_EMoamEEZ73f0CkXaXp7hrann"
