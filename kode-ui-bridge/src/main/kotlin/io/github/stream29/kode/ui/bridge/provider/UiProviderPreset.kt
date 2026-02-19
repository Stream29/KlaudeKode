package io.github.stream29.kode.ui.bridge.provider

public enum class UiProviderAuthMode {
    ApiKey,
    OAuthSubscription,
    OAuthDevice,
    CloudCredentialChain,
    WellKnown,
}

public data class UiProviderOAuthAuthCodePkcePreset(
    val authorizationEndpoint: String?,
    val tokenEndpoint: String?,
    val clientId: String?,
    val scopes: List<String>,
    val callbackUri: String?,
    val authorizationAdditionalParams: Map<String, String>,
    val tokenAdditionalParams: Map<String, String>,
)

public data class UiProviderOAuthDeviceFlowPreset(
    val strategy: String?,
    val tokenEndpoint: String?,
    val clientId: String?,
    val scopes: List<String>,
    val deviceAuthorizationEndpoint: String?,
    val deviceTokenEndpoint: String?,
    val verificationUri: String?,
    val redirectUri: String?,
)

public data class UiProviderModelPreset(
    val id: String,
    val supportsOpenAiChatEndpoint: Boolean,
    val supportsOpenAiResponsesEndpoint: Boolean,
)

public data class UiProviderPreset(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String?,
    val envKeys: List<String>,
    val authModes: Set<UiProviderAuthMode>,
    val models: List<UiProviderModelPreset>,
    val oauthAuthCodePkceByMode: Map<UiProviderAuthMode, UiProviderOAuthAuthCodePkcePreset>,
    val oauthDeviceFlowByMode: Map<UiProviderAuthMode, UiProviderOAuthDeviceFlowPreset>,
)
