package io.github.stream29.kode.providers.api

import ai.koog.prompt.llm.LLModel

public enum class ProviderAuthMode {
    ApiKey,
    OAuthSubscription,
    OAuthDevice,
    CloudCredentialChain,
    WellKnown,
}

public data class ProviderOAuthAuthCodePkcePreset(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scopes: List<String> = emptyList(),
    val callbackUri: String = "http://127.0.0.1:{port}/oauth/callback",
    val authorizationAdditionalParams: Map<String, String> = emptyMap(),
    val tokenAdditionalParams: Map<String, String> = emptyMap(),
)

public data class ProviderOAuthDeviceFlowPreset(
    val strategy: String = "rfc8628",
    val deviceAuthorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scopes: List<String> = emptyList(),
    val verificationUri: String? = null,
    val deviceTokenEndpoint: String? = null,
    val redirectUri: String? = null,
)

public data class ProviderPreset(
    val id: String,
    val displayName: String,
    val authModes: Set<ProviderAuthMode>,
    val envKeys: List<String> = emptyList(),
    val defaultBaseUrl: String? = null,
    val supportsCustomBaseUrl: Boolean = true,
    val description: String = "",
    val models: List<LLModel> = emptyList(),
    val oauthAuthCodePkceByMode: Map<ProviderAuthMode, ProviderOAuthAuthCodePkcePreset> = emptyMap(),
    val oauthDeviceFlowByMode: Map<ProviderAuthMode, ProviderOAuthDeviceFlowPreset> = emptyMap(),
)
