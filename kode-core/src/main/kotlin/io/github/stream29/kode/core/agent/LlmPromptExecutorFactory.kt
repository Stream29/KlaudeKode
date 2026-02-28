package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE
import io.github.stream29.kode.oauth.core.*
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import io.github.stream29.kode.config.api.LlmAuth as ConfigLlmAuth
import io.github.stream29.kode.providers.api.LlmAuth as RuntimeLlmAuth

internal object LlmPromptExecutorFactory {
    fun create(auths: List<LlmAuthConfig>): MultiLLMPromptExecutor {
        val clients = mutableMapOf<LLMProvider, LLMClient>()
        val ownerByProvider = mutableMapOf<LLMProvider, LlmAuthConfig>()

        auths.forEach { auth ->
            val providerId = auth.providerId.trim()
            require(providerId.isNotBlank()) { "providerId is blank for auth '${auth.id}'" }

            val provider = BuiltinLlmProviderRegistry.findProvider(providerId)
                ?: throw IllegalArgumentException("Provider not found: $providerId (authId=${auth.id})")
            val existingOwner = ownerByProvider[provider.llmProvider]
            if (existingOwner != null && existingOwner.id != auth.id) {
                throw IllegalArgumentException(
                    "Auth '${auth.id}' conflicts with '${existingOwner.id}': " +
                            "$providerId and ${existingOwner.providerId} share runtime provider '${provider.llmProvider.id}'."
                )
            }
            if (existingOwner == null) {
                val runtimeAuth = resolveRuntimeAuth(auth)
                if (!provider.supportsAuth(runtimeAuth)) {
                    throw IllegalArgumentException(
                        "Provider '$providerId' does not support auth for config '${auth.id}': ${runtimeAuth::class.simpleName}."
                    )
                }
                val client = provider.createClient(runtimeAuth)
                clients[provider.llmProvider] = client
                ownerByProvider[provider.llmProvider] = auth
            }
        }

        return MultiLLMPromptExecutor(clients)
    }

    private fun resolveRuntimeAuth(authConfig: LlmAuthConfig): RuntimeLlmAuth {
        return when (val auth = authConfig.auth) {
            is ConfigLlmAuth.ApiKey -> {
                val apiKey = resolveApiKey(authConfigId = authConfig.id, auth = auth)
                RuntimeLlmAuth.ApiKey(
                    apiKey = apiKey,
                    baseUrl = auth.baseUrl,
                    customHeaders = auth.customHeaders,
                )
            }

            is ConfigLlmAuth.OAuth -> {
                val tokenRecord = resolveOAuthTokenRecord(authId = authConfig.id, auth = auth)
                RuntimeLlmAuth.OAuthAccessToken(
                    accessToken = tokenRecord.accessToken,
                    baseUrl = auth.baseUrl,
                    customHeaders = resolveOAuthCustomHeaders(
                        providerId = authConfig.providerId,
                        configured = auth.customHeaders,
                        tokenRecord = tokenRecord,
                    ),
                )
            }
        }
    }

    private fun resolveApiKey(authConfigId: String, auth: ConfigLlmAuth.ApiKey): String {
        val direct = auth.apiKey.trim()
        if (direct.isNotBlank()) {
            return direct
        }

        val candidates = auth.envKeys
            .map { key -> key.trim() }
            .filter { key -> key.isNotBlank() }
            .distinct()
        val fromEnv = candidates.firstNotNullOfOrNull { key ->
            System.getenv(key)?.trim()?.takeIf { value -> value.isNotBlank() }
        }
        if (fromEnv != null) {
            return fromEnv
        }

        throw IllegalArgumentException(
            "Missing API key for auth '$authConfigId'. Provide LlmAuth.ApiKey.apiKey or set one of envKeys: ${candidates.joinToString()}."
        )
    }

    private fun resolveOAuthTokenRecord(authId: String, auth: ConfigLlmAuth.OAuth): OAuthTokenRecord {
        val token = runBlocking {
            oauthCredentialManager.ensureValidTokenRecord(authId = authId, oauth = auth.oauth)
        }
        if (token != null && token.accessToken.isNotBlank()) {
            return token
        }
        throw IllegalArgumentException(
            "Missing OAuth access token for auth '$authId'. Run interactive OAuth connect first (storage=${auth.oauth.storage}, key=${auth.oauth.key})."
        )
    }

    private fun resolveOAuthCustomHeaders(
        providerId: String,
        configured: Map<String, String>,
        tokenRecord: OAuthTokenRecord,
    ): Map<String, String> {
        val normalizedProviderId = providerId.trim().lowercase()
        if (normalizedProviderId !in OPENAI_SUBSCRIPTION_PROVIDER_IDS) {
            return configured
        }
        val accountId = tokenRecord.chatGptAccountId?.trim().orEmpty()
        if (accountId.isBlank()) {
            return configured
        }
        val hasHeader = configured.keys.any { key -> key.equals(OPENAI_ACCOUNT_HEADER, ignoreCase = true) }
        if (hasHeader) {
            return configured
        }
        return configured + mapOf(OPENAI_ACCOUNT_HEADER to accountId)
    }

    private val oauthCredentialManager: OAuthCredentialManager by lazy {
        val baseDir = File(System.getProperty("user.home"), ".kode/oauth")
        DefaultOAuthCredentialManager(
            authCodePkceClient = DefaultOAuthAuthCodePkceClient(),
            deviceFlowClient = DefaultOAuthDeviceFlowClient(),
            tokenStore = FileOAuthTokenStore(baseDir = baseDir),
        )
    }

    private val OPENAI_SUBSCRIPTION_PROVIDER_IDS: Set<String> = setOf(
        PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER,
        PROVIDER_ID_OPENAI_SUBSCRIPTION_DEVICE,
    )
    private const val OPENAI_ACCOUNT_HEADER: String = "ChatGPT-Account-Id"
}
