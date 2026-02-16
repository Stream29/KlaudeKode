package io.github.stream29.kode.oauth.core

import io.github.stream29.kode.config.api.OAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max

public interface OAuthAuthCodePkceClient {
    public fun createPendingAuthorization(
        oauthConfig: OAuthConfig,
        callbackUri: String,
    ): OAuthPendingAuthorization

    public suspend fun exchangeAuthorizationCode(
        oauthConfig: OAuthConfig,
        pending: OAuthPendingAuthorization,
        code: String,
    ): OAuthTokenRecord

    public suspend fun refreshAccessToken(
        oauthConfig: OAuthConfig,
        refreshToken: String,
    ): OAuthTokenRecord
}

public class DefaultOAuthAuthCodePkceClient(
    private val httpClient: HttpClient = HttpClient(CIO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : OAuthAuthCodePkceClient {
    override fun createPendingAuthorization(oauthConfig: OAuthConfig, callbackUri: String): OAuthPendingAuthorization {
        val authorizationEndpoint = requireEndpoint(
            value = oauthConfig.authorizationEndpoint,
            name = "authorizationEndpoint",
        )
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val state = randomUrlSafeString(length = 32)
        val codeVerifier = randomUrlSafeString(length = 64)
        val codeChallenge = codeChallengeS256(codeVerifier)
        val scope = oauthConfig.scopes.joinToString(separator = " ").trim()
        val params = Parameters.build {
            append(name = "response_type", value = "code")
            append(name = "client_id", value = clientId)
            append(name = "redirect_uri", value = callbackUri)
            append(name = "code_challenge", value = codeChallenge)
            append(name = "code_challenge_method", value = "S256")
            append(name = "state", value = state)
            if (scope.isNotBlank()) {
                append(name = "scope", value = scope)
            }
            val additionalParams = resolveAuthorizationAdditionalParams(oauthConfig)
            additionalParams.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank() && !contains(name = key)) {
                    append(name = key, value = value)
                }
            }
        }
        val authorizationUrl = "$authorizationEndpoint?${params.formUrlEncode()}"
        return OAuthPendingAuthorization(
            authorizationUrl = authorizationUrl,
            state = state,
            codeVerifier = codeVerifier,
            callbackUri = callbackUri,
        )
    }

    override suspend fun exchangeAuthorizationCode(
        oauthConfig: OAuthConfig,
        pending: OAuthPendingAuthorization,
        code: String,
    ): OAuthTokenRecord {
        val tokenEndpoint = requireEndpoint(value = oauthConfig.tokenEndpoint, name = "tokenEndpoint")
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val response = httpClient.post(urlString = tokenEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append(name = "grant_type", value = "authorization_code")
                    append(name = "code", value = code)
                    append(name = "redirect_uri", value = pending.callbackUri)
                    append(name = "client_id", value = clientId)
                    append(name = "code_verifier", value = pending.codeVerifier)
                    oauthConfig.tokenAdditionalParams.forEach { (key, value) ->
                        if (key.isNotBlank() && value.isNotBlank() && !contains(name = key)) {
                            append(name = key, value = value)
                        }
                    }
                }.formUrlEncode()
            )
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OAuth token exchange failed: ${response.status.value} $payload")
        }
        val tokenResponse = json.decodeFromString(OAuthTokenResponse.serializer(), payload)
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    override suspend fun refreshAccessToken(oauthConfig: OAuthConfig, refreshToken: String): OAuthTokenRecord {
        val tokenEndpoint = requireEndpoint(value = oauthConfig.tokenEndpoint, name = "tokenEndpoint")
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val response = httpClient.post(urlString = tokenEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append(name = "grant_type", value = "refresh_token")
                    append(name = "refresh_token", value = refreshToken)
                    append(name = "client_id", value = clientId)
                    oauthConfig.tokenAdditionalParams.forEach { (key, value) ->
                        if (key.isNotBlank() && value.isNotBlank() && !contains(name = key)) {
                            append(name = key, value = value)
                        }
                    }
                }.formUrlEncode()
            )
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OAuth token refresh failed: ${response.status.value} $payload")
        }
        val tokenResponse = json.decodeFromString(OAuthTokenResponse.serializer(), payload)
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    private fun requireEndpoint(value: String?, name: String): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("OAuth config missing $name")
        }
        return normalized
    }

    private fun requireField(value: String?, name: String): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("OAuth config missing $name")
        }
        return normalized
    }

    private fun codeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun randomUrlSafeString(length: Int): String {
        val targetLength = max(43, length)
        val random = ByteArray(targetLength)
        SecureRandom().nextBytes(random)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
        return encoded.take(targetLength)
    }

    private fun currentEpochSecond(): Long {
        return System.currentTimeMillis() / 1000L
    }

    private fun resolveAuthorizationAdditionalParams(oauthConfig: OAuthConfig): Map<String, String> {
        if (oauthConfig.authorizationAdditionalParams.isNotEmpty()) {
            return oauthConfig.authorizationAdditionalParams
        }
        return if (oauthConfig.authorizationEndpoint == OPENAI_AUTHORIZATION_ENDPOINT) {
            OPENAI_COMPAT_AUTHORIZATION_ADDITIONAL_PARAMS
        } else {
            emptyMap()
        }
    }

    @Serializable
    private data class OAuthTokenResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        @SerialName("id_token")
        val idToken: String? = null,
        @SerialName("token_type")
        val tokenType: String = "Bearer",
        @SerialName("expires_in")
        val expiresIn: Long? = null,
        val scope: String? = null,
    ) {
        fun toRecord(nowEpochSecond: Long): OAuthTokenRecord {
            val accountId = extractOpenAiChatGptAccountId(
                idToken = idToken,
                accessToken = accessToken,
            )
            return OAuthTokenRecord(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = tokenType,
                expiresAtEpochSecond = expiresIn?.let { seconds -> nowEpochSecond + seconds },
                scope = scope,
                idToken = idToken,
                chatGptAccountId = accountId,
            )
        }
    }

    private companion object {
        private const val OPENAI_AUTHORIZATION_ENDPOINT: String = "https://auth.openai.com/oauth/authorize"
        private val OPENAI_COMPAT_AUTHORIZATION_ADDITIONAL_PARAMS: Map<String, String> = mapOf(
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "originator" to "opencode",
        )
    }
}
