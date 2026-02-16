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

public interface LlmProvider<Auth : LlmAuth> {
    public val id: String
    public val displayName: String
    public val llmProvider: LLMProvider

    public fun models(): List<LLModel>

    public fun supportsAuth(auth: LlmAuth): Boolean

    public fun createClient(auth: Auth): LLMClient

    public fun createClientAny(auth: LlmAuth): LLMClient {
        if (!supportsAuth(auth)) {
            throw IllegalArgumentException("Provider '$id' does not support auth: ${auth::class.simpleName}")
        }

        @Suppress("UNCHECKED_CAST")
        return createClient(auth as Auth)
    }
}
