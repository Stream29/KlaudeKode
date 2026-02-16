package io.github.stream29.kode.app.util

import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig

internal fun formatModelDisplayName(model: LlmModelConfig, auths: List<LlmAuthConfig>): String {
    val provider = auths
        .firstOrNull { auth -> auth.id == model.authId }
        ?.let { auth -> auth.name ?: auth.providerId }
        ?: "Unknown"
    val name = model.displayName ?: model.model
    return "$provider - $name"
}
