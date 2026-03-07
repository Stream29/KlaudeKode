package io.github.stream29.kode.providers.api

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

public sealed interface LlmAuth {
    public data class ApiKey(
        val apiKey: String,
        val baseUrl: String? = null,
        val customHeaders: Map<String, String> = emptyMap(),
        override val authId: String? = null,
    ) : LlmAuth

    public data class OAuthAccessToken(
        val accessToken: String,
        val baseUrl: String? = null,
        val customHeaders: Map<String, String> = emptyMap(),
        override val authId: String? = null,
    ) : LlmAuth

    public val authId: String?
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
        "Auth type mismatch: " +
            "providerId='$providerId', authId='${auth.authIdOrUnknown()}', " +
            "expected='ApiKey', actual='${auth.authTypeName()}'."
    }
}

public fun requireOAuthAccessTokenAuth(providerId: String, auth: LlmAuth): LlmAuth.OAuthAccessToken {
    val oauthAuth = auth as? LlmAuth.OAuthAccessToken
    return requireNotNull(oauthAuth) {
        "Auth type mismatch: " +
            "providerId='$providerId', authId='${auth.authIdOrUnknown()}', " +
            "expected='OAuthAccessToken', actual='${auth.authTypeName()}'."
    }
}

public fun validateProviderRegistryUniqueness(providers: List<LlmProvider>) {
    val duplicateProviderIds = duplicatesBy(providers) { provider -> provider.id.trim() }
    require(duplicateProviderIds.isEmpty()) {
        "Duplicate provider ids: ${duplicateProviderIds.joinToString()}"
    }

    val duplicateProviderNames = duplicatesBy(providers) { provider -> provider.displayName.trim() }
    require(duplicateProviderNames.isEmpty()) {
        "Duplicate provider names: ${duplicateProviderNames.joinToString()}"
    }

    val duplicateProviderTypes = duplicatesBy(providers) { provider -> provider.llmProvider.id.trim() }
    require(duplicateProviderTypes.isEmpty()) {
        "Duplicate provider types: ${duplicateProviderTypes.joinToString()}"
    }

    providers.forEach { provider ->
        val providerId = provider.id.trim()
        val providerType = provider.llmProvider.id.trim()
        require(providerId.isNotBlank()) { "providerId is blank" }
        require(providerType.isNotBlank()) { "providerType is blank for providerId='$providerId'" }
        require(provider.displayName.trim().isNotBlank()) { "providerName is blank for providerId='$providerId'" }

        val duplicateModelIds = duplicatesBy(provider.models()) { model -> model.id.trim() }
        require(duplicateModelIds.isEmpty()) {
            "Duplicate model ids/names for providerId='$providerId': ${duplicateModelIds.joinToString()}"
        }
    }
}

private fun LlmAuth.authIdOrUnknown(): String {
    val normalized = authId?.trim().orEmpty()
    return if (normalized.isBlank()) "<unknown>" else normalized
}

private fun LlmAuth.authTypeName(): String {
    return this::class.simpleName ?: "<unknown>"
}

private fun <T> duplicatesBy(items: List<T>, selector: (T) -> String): List<String> {
    return items
        .groupBy { item -> selector(item).trim().lowercase() }
        .filterValues { group -> group.size > 1 }
        .values
        .map { group -> selector(group.first()).trim() }
        .sorted()
}
