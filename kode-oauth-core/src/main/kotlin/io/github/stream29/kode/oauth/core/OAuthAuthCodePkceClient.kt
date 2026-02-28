package io.github.stream29.kode.oauth.core

import io.github.stream29.kode.config.api.OAuthConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.publicvalue.multiplatform.oidc.DefaultOpenIdConnectClient
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.OpenIdConnectClientConfig
import org.publicvalue.multiplatform.oidc.flows.Pkce
import org.publicvalue.multiplatform.oidc.types.AuthCodeRequest
import org.publicvalue.multiplatform.oidc.types.CodeChallengeMethod
import org.publicvalue.multiplatform.oidc.types.remote.AccessTokenResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

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
        if (isOpenAiBrowserFlow(oauthConfig)) {
            return createOpenAiPendingAuthorization(
                oauthConfig = oauthConfig,
                callbackUri = callbackUri,
            )
        }

        val client = createOidcClient(
            oauthConfig = oauthConfig,
            callbackUri = callbackUri,
        )
        val request = client.createAuthorizationCodeRequest {
            val additionalParams = resolveAuthorizationAdditionalParams(oauthConfig)
            additionalParams.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank() && !parameters.contains(name = key)) {
                    parameters.append(name = key, value = value)
                }
            }
        }
        return OAuthPendingAuthorization(
            authorizationUrl = request.url.toString(),
            state = request.state,
            codeVerifier = request.pkce.codeVerifier,
            callbackUri = callbackUri,
        )
    }

    override suspend fun exchangeAuthorizationCode(
        oauthConfig: OAuthConfig,
        pending: OAuthPendingAuthorization,
        code: String,
    ): OAuthTokenRecord {
        if (oauthConfig.tokenAdditionalParams.isNotEmpty() || isOpenAiBrowserFlow(oauthConfig)) {
            return legacyExchangeAuthorizationCode(
                oauthConfig = oauthConfig,
                pending = pending,
                code = code,
            )
        }

        val client = createOidcClient(
            oauthConfig = oauthConfig,
            callbackUri = pending.callbackUri,
        )
        val authCodeRequest = buildAuthCodeRequestFromPending(
            client = client,
            pending = pending,
        )
        val tokenResponse = client.exchangeToken(
            authCodeRequest = authCodeRequest,
            code = code,
        )
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    override suspend fun refreshAccessToken(oauthConfig: OAuthConfig, refreshToken: String): OAuthTokenRecord {
        if (
            oauthConfig.tokenAdditionalParams.isNotEmpty() ||
            oauthConfig.authorizationEndpoint.isNullOrBlank() ||
            isOpenAiBrowserFlow(oauthConfig)
        ) {
            return legacyRefreshAccessToken(
                oauthConfig = oauthConfig,
                refreshToken = refreshToken,
            )
        }

        val client = createOidcClient(
            oauthConfig = oauthConfig,
            callbackUri = oauthConfig.callbackUri.orEmpty(),
        )
        val tokenResponse = client.refreshToken(refreshToken = refreshToken)
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    private fun createOpenAiPendingAuthorization(
        oauthConfig: OAuthConfig,
        callbackUri: String,
    ): OAuthPendingAuthorization {
        val authorizationEndpoint = requireEndpoint(
            value = oauthConfig.authorizationEndpoint,
            name = "authorizationEndpoint",
        )
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val state = generateOpenAiState()
        val codeVerifier = generateOpenAiCodeVerifier()
        val codeChallenge = codeChallengeS256(codeVerifier)
        val scope = oauthConfig.scopes.joinToString(separator = " ").trim()
        val params = Parameters.build {
            append(name = "response_type", value = "code")
            append(name = "client_id", value = clientId)
            append(name = "redirect_uri", value = callbackUri)
            if (scope.isNotBlank()) {
                append(name = "scope", value = scope)
            }
            append(name = "code_challenge", value = codeChallenge)
            append(name = "code_challenge_method", value = "S256")
            append(name = "state", value = state)
            val additionalParams = resolveAuthorizationAdditionalParams(oauthConfig)
            additionalParams.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank() && !contains(name = key)) {
                    append(name = key, value = value)
                }
            }
        }

        return OAuthPendingAuthorization(
            authorizationUrl = "$authorizationEndpoint?${params.formUrlEncode()}",
            state = state,
            codeVerifier = codeVerifier,
            callbackUri = callbackUri,
        )
    }

    private suspend fun legacyExchangeAuthorizationCode(
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
        val tokenResponse = json.decodeFromString(LegacyOAuthTokenResponse.serializer(), payload)
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    private suspend fun legacyRefreshAccessToken(
        oauthConfig: OAuthConfig,
        refreshToken: String,
    ): OAuthTokenRecord {
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
        val tokenResponse = json.decodeFromString(LegacyOAuthTokenResponse.serializer(), payload)
        return tokenResponse.toRecord(nowEpochSecond = currentEpochSecond())
    }

    private fun createOidcClient(
        oauthConfig: OAuthConfig,
        callbackUri: String,
    ): OpenIdConnectClient {
        val authorizationEndpoint = requireEndpoint(
            value = oauthConfig.authorizationEndpoint,
            name = "authorizationEndpoint",
        )
        val tokenEndpoint = requireEndpoint(
            value = oauthConfig.tokenEndpoint,
            name = "tokenEndpoint",
        )
        val clientId = requireField(value = oauthConfig.clientId, name = "clientId")
        val scope = oauthConfig.scopes.joinToString(separator = " ").trim().ifBlank { null }
        val config = OpenIdConnectClientConfig(discoveryUri = null).apply {
            endpoints {
                this.authorizationEndpoint = authorizationEndpoint
                this.tokenEndpoint = tokenEndpoint
            }
            this.clientId = clientId
            this.scope = scope
            this.redirectUri = callbackUri
            this.codeChallengeMethod = CodeChallengeMethod.S256
        }
        return DefaultOpenIdConnectClient(config = config)
    }

    private fun buildAuthCodeRequestFromPending(
        client: OpenIdConnectClient,
        pending: OAuthPendingAuthorization,
    ): AuthCodeRequest {
        return AuthCodeRequest(
            url = Url(pending.authorizationUrl),
            config = client.config,
            pkce = Pkce(
                codeChallengeMethod = CodeChallengeMethod.S256,
                codeVerifier = pending.codeVerifier,
            ),
            state = pending.state,
            nonce = null,
        )
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

    private fun currentEpochSecond(): Long {
        return System.currentTimeMillis() / 1000L
    }

    private fun generateOpenAiState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateOpenAiCodeVerifier(length: Int = 43): String {
        val random = ByteArray(length)
        SecureRandom().nextBytes(random)
        return buildString(capacity = length) {
            random.forEach { byte ->
                val index = (byte.toInt() and 0xFF) % OPENAI_CODE_VERIFIER_CHARS.length
                append(OPENAI_CODE_VERIFIER_CHARS[index])
            }
        }
    }

    private fun codeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun isOpenAiBrowserFlow(oauthConfig: OAuthConfig): Boolean {
        val authorizationEndpoint = normalizeEndpointForComparison(oauthConfig.authorizationEndpoint)
        val tokenEndpoint = normalizeEndpointForComparison(oauthConfig.tokenEndpoint)
        return authorizationEndpoint == normalizeEndpointForComparison(OPENAI_AUTHORIZATION_ENDPOINT) &&
                tokenEndpoint == normalizeEndpointForComparison(OPENAI_TOKEN_ENDPOINT)
    }

    private fun resolveAuthorizationAdditionalParams(oauthConfig: OAuthConfig): Map<String, String> {
        if (oauthConfig.authorizationAdditionalParams.isNotEmpty()) {
            return oauthConfig.authorizationAdditionalParams
        }
        return if (normalizeEndpointForComparison(oauthConfig.authorizationEndpoint) ==
            normalizeEndpointForComparison(OPENAI_AUTHORIZATION_ENDPOINT)
        ) {
            OPENAI_COMPAT_AUTHORIZATION_ADDITIONAL_PARAMS
        } else {
            emptyMap()
        }
    }

    private fun normalizeEndpointForComparison(endpoint: String?): String {
        val normalized = endpoint?.trim().orEmpty()
        if (normalized.isBlank()) {
            return ""
        }
        val withoutQuery = normalized.substringBefore('?').substringBefore('#')
        return withoutQuery.trimEnd('/').lowercase()
    }

    private fun AccessTokenResponse.toRecord(nowEpochSecond: Long): OAuthTokenRecord {
        val accountId = extractOpenAiChatGptAccountId(
            idToken = id_token,
            accessToken = access_token,
        )
        return OAuthTokenRecord(
            accessToken = access_token,
            refreshToken = refresh_token,
            tokenType = token_type?.ifBlank { "Bearer" } ?: "Bearer",
            expiresAtEpochSecond = expires_in?.let { seconds -> nowEpochSecond + seconds.toLong() },
            scope = scope,
            idToken = id_token,
            chatGptAccountId = accountId,
        )
    }

    @Serializable
    private data class LegacyOAuthTokenResponse(
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
        private const val OPENAI_TOKEN_ENDPOINT: String = "https://auth.openai.com/oauth/token"
        private const val OPENAI_CODE_VERIFIER_CHARS: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private val OPENAI_COMPAT_AUTHORIZATION_ADDITIONAL_PARAMS: Map<String, String> = mapOf(
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "originator" to "opencode",
        )
    }
}
