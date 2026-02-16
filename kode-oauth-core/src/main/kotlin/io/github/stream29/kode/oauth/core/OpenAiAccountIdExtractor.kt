package io.github.stream29.kode.oauth.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

internal fun extractOpenAiChatGptAccountId(
    idToken: String?,
    accessToken: String?,
): String? {
    val fromIdToken = parseJwtClaims(idToken)?.extractChatGptAccountIdFromClaims()
    if (!fromIdToken.isNullOrBlank()) {
        return fromIdToken
    }
    val fromAccessToken = parseJwtClaims(accessToken)?.extractChatGptAccountIdFromClaims()
    return fromAccessToken?.takeIf { value -> value.isNotBlank() }
}

private fun parseJwtClaims(token: String?): JsonObject? {
    val normalized = token?.trim().orEmpty()
    if (normalized.isBlank()) {
        return null
    }
    val parts = normalized.split('.')
    if (parts.size != 3) {
        return null
    }
    return runCatching {
        val payload = Base64.getUrlDecoder().decode(parts[1])
        JSON.decodeFromString(JsonElement.serializer(), payload.decodeToString()) as? JsonObject
    }.getOrNull()
}

private fun JsonObject.extractChatGptAccountIdFromClaims(): String? {
    val topLevel = this["chatgpt_account_id"].asStringOrNull()
    if (!topLevel.isNullOrBlank()) {
        return topLevel
    }

    val namespaced = (this["https://api.openai.com/auth"] as? JsonObject)
        ?.get("chatgpt_account_id")
        .asStringOrNull()
    if (!namespaced.isNullOrBlank()) {
        return namespaced
    }

    val organizations = this["organizations"] as? JsonArray
    val firstOrganizationId = organizations
        ?.firstOrNull()
        ?.let { element -> element as? JsonObject }
        ?.get("id")
        .asStringOrNull()
    return firstOrganizationId?.takeIf { value -> value.isNotBlank() }
}

private fun JsonElement?.asStringOrNull(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

private val JSON: Json = Json {
    ignoreUnknownKeys = true
}
