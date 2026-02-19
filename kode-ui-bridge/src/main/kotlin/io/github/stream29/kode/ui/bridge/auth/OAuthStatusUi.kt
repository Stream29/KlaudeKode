package io.github.stream29.kode.ui.bridge.auth

public data class OAuthStatusUi(
    val connected: Boolean,
    val expired: Boolean,
    val hasRefreshToken: Boolean,
    val expiresAtEpochSecond: Long?,
    val summary: String,
    val inProgress: Boolean,
)
