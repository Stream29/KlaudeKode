package io.github.stream29.kode.oauth.core

import io.github.stream29.kode.config.api.OAuthConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

public data class OAuthDeviceAuthorizationSession(
    val strategy: String,
    val verificationUri: String,
    val userCode: String,
    val intervalSeconds: Long,
    val deviceCode: String? = null,
    val deviceAuthId: String? = null,
)

public interface OAuthDeviceFlowClient {
    public suspend fun startDeviceAuthorization(oauthConfig: OAuthConfig): OAuthDeviceAuthorizationSession

    public suspend fun pollForToken(
        oauthConfig: OAuthConfig,
        session: OAuthDeviceAuthorizationSession,
    ): OAuthTokenRecord
}

public class DefaultOAuthDeviceFlowClient(
    private val httpClient: HttpClient = HttpClient(CIO),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : OAuthDeviceFlowClient {
    override suspend fun startDeviceAuthorization(oauthConfig: OAuthConfig): OAuthDeviceAuthorizationSession {
        val strategy = normalizeStrategy(oauthConfig.deviceFlowStrategy)
        return when (strategy) {
            STRATEGY_OPENAI_CODEX_BRIDGE -> startOpenAiDeviceAuthorization(oauthConfig)
            else -> startRfc8628DeviceAuthorization(oauthConfig)
        }
    }

    override suspend fun pollForToken(
        oauthConfig: OAuthConfig,
        session: OAuthDeviceAuthorizationSession,
    ): OAuthTokenRecord {
        val strategy = normalizeStrategy(session.strategy)
        return when (strategy) {
            STRATEGY_OPENAI_CODEX_BRIDGE -> pollOpenAiCodexBridge(
                oauthConfig = oauthConfig,
                session = session,
            )

            else -> pollRfc8628(
                oauthConfig = oauthConfig,
                session = session,
            )
        }
    }

    private suspend fun startRfc8628DeviceAuthorization(oauthConfig: OAuthConfig): OAuthDeviceAuthorizationSession {
        val endpoint = requireField(
            value = oauthConfig.deviceAuthorizationEndpoint,
            name = "deviceAuthorizationEndpoint",
        )
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val scope = oauthConfig.scopes.joinToString(separator = " ").trim()
        val response = httpClient.post(urlString = endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append(name = "client_id", value = clientId)
                    if (scope.isNotBlank()) {
                        append(name = "scope", value = scope)
                    }
                }.formUrlEncode()
            )
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OAuth device authorization failed: ${response.status.value} $payload")
        }
        val start = json.decodeFromString(RfcDeviceAuthorizationResponse.serializer(), payload)
        return OAuthDeviceAuthorizationSession(
            strategy = STRATEGY_RFC8628,
            verificationUri = start.verificationUriComplete ?: start.verificationUri,
            userCode = start.userCode,
            intervalSeconds = max(start.interval ?: 5, 1).toLong(),
            deviceCode = start.deviceCode,
        )
    }

    private suspend fun startOpenAiDeviceAuthorization(oauthConfig: OAuthConfig): OAuthDeviceAuthorizationSession {
        val endpoint = requireField(
            value = oauthConfig.deviceAuthorizationEndpoint,
            name = "deviceAuthorizationEndpoint",
        )
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val response = httpClient.post(urlString = endpoint) {
            contentType(ContentType.Application.Json)
            setBody("{\"client_id\":\"$clientId\"}")
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OAuth device authorization failed: ${response.status.value} $payload")
        }
        val start = json.decodeFromString(OpenAiDeviceAuthorizationResponse.serializer(), payload)
        val verificationUri = oauthConfig.deviceVerificationUri
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_OPENAI_DEVICE_VERIFICATION_URI }
        return OAuthDeviceAuthorizationSession(
            strategy = STRATEGY_OPENAI_CODEX_BRIDGE,
            verificationUri = verificationUri,
            userCode = start.userCode,
            intervalSeconds = max(start.interval.toIntOrNull() ?: 5, 1).toLong(),
            deviceAuthId = start.deviceAuthId,
        )
    }

    private suspend fun pollRfc8628(
        oauthConfig: OAuthConfig,
        session: OAuthDeviceAuthorizationSession,
    ): OAuthTokenRecord {
        val tokenEndpoint = requireField(value = oauthConfig.tokenEndpoint, name = "tokenEndpoint")
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val deviceCode = requireField(value = session.deviceCode, name = "deviceCode")
        var intervalMillis = max(session.intervalSeconds, 1) * 1000L

        while (true) {
            val response = httpClient.post(urlString = tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    Parameters.build {
                        append(name = "client_id", value = clientId)
                        append(name = "device_code", value = deviceCode)
                        append(name = "grant_type", value = RFC_DEVICE_CODE_GRANT_TYPE)
                    }.formUrlEncode()
                )
            }
            val payload = response.bodyAsText()
            val tokenResponse = runCatching {
                json.decodeFromString(DeviceTokenPollResponse.serializer(), payload)
            }.getOrNull()
            if (tokenResponse?.accessToken != null) {
                return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
            }

            val errorCode = tokenResponse?.error.orEmpty()
            when (errorCode) {
                "authorization_pending" -> {
                    delay(intervalMillis + OAUTH_POLLING_SAFETY_MARGIN_MS)
                    continue
                }

                "slow_down" -> {
                    val suggested = tokenResponse?.interval?.takeIf { value -> value > 0 }?.times(1000L)
                    intervalMillis = (suggested ?: intervalMillis + 5_000L)
                    delay(intervalMillis + OAUTH_POLLING_SAFETY_MARGIN_MS)
                    continue
                }

                "expired_token" -> {
                    throw IllegalStateException("OAuth device flow expired")
                }

                "access_denied" -> {
                    throw IllegalStateException("OAuth device flow denied by user")
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("OAuth device token polling failed: ${response.status.value} $payload")
            }

            delay(intervalMillis + OAUTH_POLLING_SAFETY_MARGIN_MS)
        }
    }

    private suspend fun pollOpenAiCodexBridge(
        oauthConfig: OAuthConfig,
        session: OAuthDeviceAuthorizationSession,
    ): OAuthTokenRecord {
        val pollEndpoint = requireField(
            value = oauthConfig.deviceTokenEndpoint,
            name = "deviceTokenEndpoint",
        )
        val tokenEndpoint = requireField(value = oauthConfig.tokenEndpoint, name = "tokenEndpoint")
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val deviceAuthId = requireField(value = session.deviceAuthId, name = "deviceAuthId")
        val userCode = requireField(value = session.userCode, name = "userCode")
        val redirectUri = oauthConfig.deviceRedirectUri
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_OPENAI_DEVICE_REDIRECT_URI }

        val intervalMillis = max(session.intervalSeconds, 1) * 1000L

        while (true) {
            val pollResponse = httpClient.post(urlString = pollEndpoint) {
                contentType(ContentType.Application.Json)
                setBody("{\"device_auth_id\":\"$deviceAuthId\",\"user_code\":\"$userCode\"}")
            }
            val payload = pollResponse.bodyAsText()
            if (pollResponse.status.isSuccess()) {
                val authCodeResponse = json.decodeFromString(OpenAiDevicePollResponse.serializer(), payload)
                val tokenResponse = httpClient.post(urlString = tokenEndpoint) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        Parameters.build {
                            append(name = "grant_type", value = "authorization_code")
                            append(name = "code", value = authCodeResponse.authorizationCode)
                            append(name = "redirect_uri", value = redirectUri)
                            append(name = "client_id", value = clientId)
                            append(name = "code_verifier", value = authCodeResponse.codeVerifier)
                        }.formUrlEncode()
                    )
                }
                val tokenPayload = tokenResponse.bodyAsText()
                if (!tokenResponse.status.isSuccess()) {
                    throw IllegalStateException(
                        "OAuth token exchange failed: ${tokenResponse.status.value} $tokenPayload"
                    )
                }
                val tokens = json.decodeFromString(DeviceTokenPollResponse.serializer(), tokenPayload)
                if (tokens.accessToken.isNullOrBlank()) {
                    throw IllegalStateException("OAuth token exchange returned empty access token")
                }
                return tokens.toRecord(nowEpochSecond = currentEpochSecond())
            }

            if (pollResponse.status.value == 403 || pollResponse.status.value == 404) {
                delay(intervalMillis + OAUTH_POLLING_SAFETY_MARGIN_MS)
                continue
            }

            throw IllegalStateException("OAuth device polling failed: ${pollResponse.status.value} $payload")
        }
    }

    private fun normalizeStrategy(input: String?): String {
        val normalized = input?.trim().orEmpty().lowercase()
        return if (normalized.isBlank()) {
            STRATEGY_RFC8628
        } else {
            normalized
        }
    }

    private fun requireField(value: String?, name: String): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("OAuth config missing $name")
        }
        return normalized
    }

    private fun currentEpochSecond(): Long {
        return System.currentTimeMillis() / 1000L
    }

    @Serializable
    private data class RfcDeviceAuthorizationResponse(
        @SerialName("verification_uri")
        val verificationUri: String,
        @SerialName("verification_uri_complete")
        val verificationUriComplete: String? = null,
        @SerialName("user_code")
        val userCode: String,
        @SerialName("device_code")
        val deviceCode: String,
        val interval: Int? = null,
    )

    @Serializable
    private data class OpenAiDeviceAuthorizationResponse(
        @SerialName("device_auth_id")
        val deviceAuthId: String,
        @SerialName("user_code")
        val userCode: String,
        val interval: String,
    )

    @Serializable
    private data class OpenAiDevicePollResponse(
        @SerialName("authorization_code")
        val authorizationCode: String,
        @SerialName("code_verifier")
        val codeVerifier: String,
    )

    @Serializable
    private data class DeviceTokenPollResponse(
        @SerialName("access_token")
        val accessToken: String? = null,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        @SerialName("id_token")
        val idToken: String? = null,
        @SerialName("token_type")
        val tokenType: String = "Bearer",
        @SerialName("expires_in")
        val expiresIn: Long? = null,
        val scope: String? = null,
        val error: String? = null,
        val interval: Int? = null,
    ) {
        fun toRecord(nowEpochSecond: Long): OAuthTokenRecord {
            val resolvedAccessToken = accessToken.orEmpty()
            val accountId = extractOpenAiChatGptAccountId(
                idToken = idToken,
                accessToken = resolvedAccessToken,
            )
            return OAuthTokenRecord(
                accessToken = resolvedAccessToken,
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
        private const val STRATEGY_RFC8628: String = "rfc8628"
        private const val STRATEGY_OPENAI_CODEX_BRIDGE: String = "openai_codex_bridge"
        private const val RFC_DEVICE_CODE_GRANT_TYPE: String = "urn:ietf:params:oauth:grant-type:device_code"
        private const val OAUTH_POLLING_SAFETY_MARGIN_MS: Long = 3_000
        private const val DEFAULT_OPENAI_DEVICE_VERIFICATION_URI: String = "https://auth.openai.com/codex/device"
        private const val DEFAULT_OPENAI_DEVICE_REDIRECT_URI: String = "https://auth.openai.com/deviceauth/callback"
    }
}
