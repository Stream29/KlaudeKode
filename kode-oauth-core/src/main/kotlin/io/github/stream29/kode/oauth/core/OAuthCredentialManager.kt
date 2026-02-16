package io.github.stream29.kode.oauth.core

import io.github.stream29.kode.config.api.OAuthConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

public interface OAuthCredentialManager {
    public suspend fun connect(
        authId: String,
        authMode: String,
        oauth: OAuthConfig,
        openBrowser: (String) -> Unit,
        onProgress: (String) -> Unit,
    ): OAuthTokenRecord

    public suspend fun ensureValidAccessToken(
        authId: String,
        oauth: OAuthConfig,
    ): String?

    public suspend fun ensureValidTokenRecord(
        authId: String,
        oauth: OAuthConfig,
    ): OAuthTokenRecord?

    public suspend fun inspectStatus(
        authId: String,
        oauth: OAuthConfig,
    ): OAuthCredentialStatus

    public suspend fun disconnect(oauth: OAuthConfig)
}

public class DefaultOAuthCredentialManager(
    private val authCodePkceClient: OAuthAuthCodePkceClient,
    private val deviceFlowClient: OAuthDeviceFlowClient,
    private val tokenStore: OAuthTokenStore,
    private val tokenRefreshSkewSeconds: Long = 120,
) : OAuthCredentialManager {
    override suspend fun connect(
        authId: String,
        authMode: String,
        oauth: OAuthConfig,
        openBrowser: (String) -> Unit,
        onProgress: (String) -> Unit,
    ): OAuthTokenRecord {
        require(authId.isNotBlank()) { "authId is blank" }
        require(oauth.key.isNotBlank()) { "OAuth key is blank for authId=$authId" }
        val storage = oauth.storage.trim().lowercase().ifBlank { STORAGE_FILE }
        if (storage != STORAGE_FILE) {
            throw IllegalArgumentException("OAuth interactive connect requires file storage")
        }

        val normalizedMode = authMode.trim().lowercase()
        if (normalizedMode == AUTH_MODE_OAUTH_DEVICE) {
            val session = deviceFlowClient.startDeviceAuthorization(oauthConfig = oauth)
            onProgress("Open ${session.verificationUri} and enter code: ${session.userCode}")
            openBrowser(session.verificationUri)
            val token = deviceFlowClient.pollForToken(
                oauthConfig = oauth,
                session = session,
            )
            tokenStore.save(
                storage = oauth.storage,
                key = oauth.key,
                token = token,
            )
            return token
        }

        val binding = buildLoopbackBinding(oauth.callbackUri)
        val expectedStateRef = AtomicReference<String?>(null)
        val codeDeferred = CompletableDeferred<String>()

        val server = embeddedServer(factory = CIO, host = binding.host, port = binding.requestedPort) {
            routing {
                get(binding.path) {
                    val expectedState = expectedStateRef.get()
                    val state = call.request.queryParameters["state"].orEmpty()
                    val code = call.request.queryParameters["code"].orEmpty()
                    val error = call.request.queryParameters["error"].orEmpty()
                    if (error.isNotBlank()) {
                        if (!codeDeferred.isCompleted) {
                            codeDeferred.completeExceptionally(
                                IllegalStateException("OAuth callback returned error: $error")
                            )
                        }
                        call.respondText(
                            text = "OAuth authorization failed: $error",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    if (expectedState.isNullOrBlank() || state != expectedState) {
                        if (!codeDeferred.isCompleted) {
                            codeDeferred.completeExceptionally(
                                IllegalStateException("OAuth callback state mismatch")
                            )
                        }
                        call.respondText(
                            text = "OAuth state mismatch",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    if (code.isBlank()) {
                        if (!codeDeferred.isCompleted) {
                            codeDeferred.completeExceptionally(
                                IllegalStateException("OAuth callback missing code")
                            )
                        }
                        call.respondText(
                            text = "OAuth callback missing code",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }
                    if (!codeDeferred.isCompleted) {
                        codeDeferred.complete(code)
                    }
                    call.respondText(
                        text = "OAuth authorization completed. You can close this tab.",
                        status = HttpStatusCode.OK,
                    )
                }
            }
        }

        server.start(wait = false)
        try {
            val boundPort = waitForBoundPort(server)
            val callbackUri = resolveCallbackUri(binding, boundPort)
            val pending = authCodePkceClient.createPendingAuthorization(
                oauthConfig = oauth,
                callbackUri = callbackUri,
            )
            expectedStateRef.set(pending.state)
            onProgress("Opening browser for OAuth authorization")
            openBrowser(pending.authorizationUrl)
            val code = codeDeferred.await()
            val token = authCodePkceClient.exchangeAuthorizationCode(
                oauthConfig = oauth,
                pending = pending,
                code = code,
            )
            tokenStore.save(
                storage = oauth.storage,
                key = oauth.key,
                token = token,
            )
            return token
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        }
    }

    override suspend fun ensureValidAccessToken(authId: String, oauth: OAuthConfig): String? {
        return ensureValidTokenRecord(authId = authId, oauth = oauth)?.accessToken
    }

    override suspend fun ensureValidTokenRecord(authId: String, oauth: OAuthConfig): OAuthTokenRecord? {
        require(authId.isNotBlank()) { "authId is blank" }
        val token = tokenStore.load(storage = oauth.storage, key = oauth.key) ?: return null
        val expiresAt = token.expiresAtEpochSecond
        if (expiresAt == null || currentEpochSecond() + tokenRefreshSkewSeconds < expiresAt) {
            return token
        }
        val refreshToken = token.refreshToken?.trim().orEmpty()
        if (refreshToken.isBlank()) {
            return null
        }
        return runCatching {
            val refreshed = authCodePkceClient.refreshAccessToken(
                oauthConfig = oauth,
                refreshToken = refreshToken,
            )
            tokenStore.save(storage = oauth.storage, key = oauth.key, token = refreshed)
            refreshed
        }.getOrElse {
            tokenStore.delete(storage = oauth.storage, key = oauth.key)
            null
        }
    }

    override suspend fun inspectStatus(authId: String, oauth: OAuthConfig): OAuthCredentialStatus {
        require(authId.isNotBlank()) { "authId is blank" }
        val token = tokenStore.load(storage = oauth.storage, key = oauth.key)
        val expiresAt = token?.expiresAtEpochSecond
        val now = currentEpochSecond()
        val expired = expiresAt?.let { epoch -> epoch <= now } ?: false
        return OAuthCredentialStatus(
            connected = token != null && token.accessToken.isNotBlank(),
            expired = expired,
            hasRefreshToken = !token?.refreshToken.isNullOrBlank(),
            expiresAtEpochSecond = expiresAt,
            storage = oauth.storage,
            key = oauth.key,
        )
    }

    override suspend fun disconnect(oauth: OAuthConfig) {
        tokenStore.delete(storage = oauth.storage, key = oauth.key)
    }

    private suspend fun waitForBoundPort(server: EmbeddedServer<*, *>): Int {
        var resolvedPort: Int? = null
        while (resolvedPort == null) {
            val resolved = server.engine.resolvedConnectors()
            if (resolved.isNotEmpty()) {
                resolvedPort = resolved.first().port
            } else {
                delay(25L)
            }
        }
        return checkNotNull(resolvedPort)
    }

    private fun buildLoopbackBinding(callbackUri: String?): LoopbackBinding {
        val template = callbackUri?.trim().orEmpty().ifBlank { DEFAULT_CALLBACK_URI_TEMPLATE }
        val hasPlaceholder = template.contains(PORT_PLACEHOLDER)
        val parsableUri = if (hasPlaceholder) {
            template.replace(PORT_PLACEHOLDER, "0")
        } else {
            template
        }
        val uri = runCatching {
            URI(parsableUri)
        }.getOrElse {
            throw IllegalArgumentException("Invalid callback URI: $template")
        }
        val host = uri.host?.trim().orEmpty().ifBlank { DEFAULT_CALLBACK_HOST }
        val requestedPort = if (hasPlaceholder) {
            0
        } else {
            uri.port.takeIf { port -> port > 0 } ?: DEFAULT_CALLBACK_PORT
        }
        val path = uri.path?.trim().orEmpty().ifBlank { DEFAULT_CALLBACK_PATH }
        return LoopbackBinding(
            template = template,
            hasPortPlaceholder = hasPlaceholder,
            host = host,
            requestedPort = requestedPort,
            path = if (path.startsWith("/")) path else "/$path",
            scheme = uri.scheme?.ifBlank { DEFAULT_CALLBACK_SCHEME } ?: DEFAULT_CALLBACK_SCHEME,
        )
    }

    private fun resolveCallbackUri(binding: LoopbackBinding, boundPort: Int): String {
        if (binding.hasPortPlaceholder) {
            return binding.template.replace(PORT_PLACEHOLDER, boundPort.toString())
        }
        val uri = URI(binding.template)
        if (uri.port > 0) {
            return binding.template
        }
        return "${binding.scheme}://${binding.host}:$boundPort${binding.path}"
    }

    private fun currentEpochSecond(): Long {
        return System.currentTimeMillis() / 1000L
    }

    private data class LoopbackBinding(
        val template: String,
        val hasPortPlaceholder: Boolean,
        val host: String,
        val requestedPort: Int,
        val path: String,
        val scheme: String,
    )

    private companion object {
        private const val STORAGE_FILE: String = "file"
        private const val AUTH_MODE_OAUTH_DEVICE: String = "oauth_device"
        private const val PORT_PLACEHOLDER: String = "{port}"
        private const val DEFAULT_CALLBACK_SCHEME: String = "http"
        private const val DEFAULT_CALLBACK_HOST: String = "127.0.0.1"
        private const val DEFAULT_CALLBACK_PORT: Int = 1455
        private const val DEFAULT_CALLBACK_PATH: String = "/oauth/callback"
        private const val DEFAULT_CALLBACK_URI_TEMPLATE: String =
            "$DEFAULT_CALLBACK_SCHEME://$DEFAULT_CALLBACK_HOST:$PORT_PLACEHOLDER$DEFAULT_CALLBACK_PATH"
    }
}
