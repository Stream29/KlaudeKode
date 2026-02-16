package io.github.stream29.kode.oauth.core

import kotlinx.serialization.Serializable

@Serializable
public data class OAuthTokenRecord(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAtEpochSecond: Long? = null,
    val scope: String? = null,
    val idToken: String? = null,
    val chatGptAccountId: String? = null,
)

public data class OAuthPendingAuthorization(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
    val callbackUri: String,
)

public data class OAuthCredentialStatus(
    val connected: Boolean,
    val expired: Boolean,
    val hasRefreshToken: Boolean,
    val expiresAtEpochSecond: Long?,
    val storage: String,
    val key: String,
)
