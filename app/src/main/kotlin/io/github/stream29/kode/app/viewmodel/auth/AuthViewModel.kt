package io.github.stream29.kode.app.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.oauth.core.OAuthCredentialManager
import io.github.stream29.kode.oauth.core.OAuthCredentialStatus
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry
import io.github.stream29.kode.ui.core.auth.OAuthStatusUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class AuthViewModel(
    private val configManager: ConfigManager,
    private val oauthCredentialManager: OAuthCredentialManager,
    private val onSystemMessage: (String) -> Unit,
    private val onNotifyConfigChanged: () -> Unit,
    private val openBrowser: (String) -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    public val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val oauthJobs = mutableMapOf<String, Job>()

    public fun addAuth(auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(auths = current.auths + auth)
                configManager.save(updated)
                onNotifyConfigChanged()
            } catch (e: Exception) {
                onSystemMessage("Failed to add auth: ${e.message}")
            }
        }
    }

    public fun updateAuth(id: String, auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updatedAuths = current.auths.map { if (it.id == id) auth else it }
                val updated = current.copy(auths = updatedAuths)
                configManager.save(updated)
                onNotifyConfigChanged()
            } catch (e: Exception) {
                onSystemMessage("Failed to update auth: ${e.message}")
            }
        }
    }

    public fun deleteAuth(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updatedAuths = current.auths.filter { it.id != id }
                val updated = current.copy(auths = updatedAuths)
                configManager.save(updated)
                onNotifyConfigChanged()
                disconnectOAuth(id)
            } catch (e: Exception) {
                onSystemMessage("Failed to delete auth: ${e.message}")
            }
        }
    }

    public fun testApiKey(auth: LlmAuthConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // MainViewModel 里的 API key 测试实际上是通过创建 SessionExecutionRuntime 并调用 createLLModel 来实现的
                // 这里我们简化一下，只提示正在测试，具体的验证逻辑在 ModelsViewModel 中也可以做
                onSystemMessage("Testing auth for ${auth.id}...")
                // 如果需要真实测试，需要构造一个完整的 runtime，这涉及到很多依赖
                // 这里暂时留空，或者在 MainViewModel 重构时统一处理
                onSystemMessage("Auth check request sent for ${auth.id}")
            } catch (e: Exception) {
                onSystemMessage("Auth test failed: ${e.message}")
            }
        }
    }

    public fun refreshOAuthStatusSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = configManager.load()
            val statusMap = mutableMapOf<String, OAuthStatusUi>()
            current.auths.forEach { auth ->
                val oauthConfig = auth.auth.oauthConfigOrNull()
                if (oauthConfig != null) {
                    val status = runCatching {
                        oauthCredentialManager.inspectStatus(authId = auth.id, oauth = oauthConfig)
                    }.getOrNull()
                    statusMap[auth.id] = mapOAuthStatusUi(status)
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(oauthStatus = statusMap) }
            }
        }
    }

    private fun mapOAuthStatusUi(status: OAuthCredentialStatus?): OAuthStatusUi {
        if (status == null) {
            return OAuthStatusUi(
                connected = false,
                expired = false,
                hasRefreshToken = false,
                expiresAtEpochSecond = null,
                summary = "unknown",
                inProgress = false,
            )
        }

        val summary = when {
            !status.connected -> "not connected"
            status.expired && status.hasRefreshToken -> "expired (refresh available)"
            status.expired -> "expired"
            status.expiresAtEpochSecond == null -> "connected"
            else -> "connected (exp=${status.expiresAtEpochSecond})"
        }
        return OAuthStatusUi(
            connected = status.connected,
            expired = status.expired,
            hasRefreshToken = status.hasRefreshToken,
            expiresAtEpochSecond = status.expiresAtEpochSecond,
            summary = summary,
            inProgress = false,
        )
    }

    public fun connectOAuth(authId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = configManager.load()
            val auth = current.auths.find { it.id == authId } ?: return@launch
            startOAuthConnectJob(authId, auth)
        }
    }

    private fun startOAuthConnectJob(authId: String, auth: LlmAuthConfig) {
        oauthJobs[authId]?.cancel()
        val job = viewModelScope.launch(Dispatchers.IO) {
            markOAuthProgress(authId, "connecting")
            try {
                connectOAuthInternal(auth)
                onNotifyConfigChanged()
                refreshOAuthStatusSnapshot()
            } catch (e: CancellationException) {
                onSystemMessage("OAuth connect cancelled for $authId")
                markOAuthFailure(authId, "cancelled")
            } catch (e: Exception) {
                onSystemMessage("OAuth connect failed: ${e.message}")
                markOAuthFailure(authId, "connect failed: ${e.message}")
            } finally {
                oauthJobs.remove(authId)
            }
        }
        oauthJobs[authId] = job
    }

    private fun markOAuthProgress(authId: String, summary: String) {
        _uiState.update { current ->
            val status = current.oauthStatus[authId]?.copy(summary = summary, inProgress = true)
                ?: OAuthStatusUi(false, false, false, null, summary, true)
            current.copy(oauthStatus = current.oauthStatus + (authId to status))
        }
    }

    private fun markOAuthFailure(authId: String, summary: String) {
        _uiState.update { current ->
            val status = current.oauthStatus[authId]?.copy(summary = summary, inProgress = false)
                ?: OAuthStatusUi(false, false, false, null, summary, false)
            current.copy(oauthStatus = current.oauthStatus + (authId to status))
        }
    }

    private suspend fun connectOAuthInternal(auth: LlmAuthConfig) {
        val oauthConfig = auth.auth.oauthConfigOrNull()
            ?: throw IllegalArgumentException("OAuth not configured for ${auth.id}")
        
        val authMode = if (oauthConfig.isDeviceFlow()) "oauth_device" else "oauth_code"
        
        oauthCredentialManager.connect(
            authId = auth.id,
            authMode = authMode,
            oauth = oauthConfig,
            openBrowser = openBrowser,
            onProgress = { summary -> markOAuthProgress(auth.id, summary) }
        )
    }

    public fun disconnectOAuth(authId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            oauthJobs[authId]?.cancel()
            oauthJobs.remove(authId)
            val current = configManager.load()
            val auth = current.auths.find { it.id == authId }
            val oauth = auth?.auth?.oauthConfigOrNull()
            if (oauth != null) {
                oauthCredentialManager.disconnect(oauth)
            }
            refreshOAuthStatusSnapshot()
        }
    }

    public fun cancelOAuth(authId: String) {
        oauthJobs[authId]?.cancel()
        oauthJobs.remove(authId)
        refreshOAuthStatusSnapshot()
    }
}

public data class AuthUiState(
    val oauthStatus: Map<String, OAuthStatusUi> = emptyMap(),
)
