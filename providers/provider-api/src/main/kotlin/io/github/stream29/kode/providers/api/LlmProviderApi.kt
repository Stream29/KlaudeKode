package io.github.stream29.kode.providers.api

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public sealed interface LlmAuth {
    public data class ApiKey(
        val apiKey: String,
        val baseUrl: String? = null,
        val customHeaders: Map<String, String> = emptyMap(),
    ) : LlmAuth

    public data class OAuthAccessToken(
        val accessToken: String,
        val baseUrl: String? = null,
        val customHeaders: Map<String, String> = emptyMap(),
    ) : LlmAuth
}

public interface LlmProvider {
    public val id: String
    public val displayName: String
    public val llmProvider: LLMProvider

    public fun models(): List<LLModel>

    public fun supportsAuth(auth: LlmAuth): Boolean

    public fun createClient(auth: LlmAuth): LLMClient
}

public fun requireApiKeyAuth(providerId: String, auth: LlmAuth): LlmAuth.ApiKey {
    val apiKeyAuth = auth as? LlmAuth.ApiKey
    return requireNotNull(apiKeyAuth) {
        "Provider '$providerId' requires ApiKey auth but got: ${auth::class.simpleName}"
    }
}

public fun requireOAuthAccessTokenAuth(providerId: String, auth: LlmAuth): LlmAuth.OAuthAccessToken {
    val oauthAuth = auth as? LlmAuth.OAuthAccessToken
    return requireNotNull(oauthAuth) {
        "Provider '$providerId' requires OAuthAccessToken auth but got: ${auth::class.simpleName}"
    }
}
