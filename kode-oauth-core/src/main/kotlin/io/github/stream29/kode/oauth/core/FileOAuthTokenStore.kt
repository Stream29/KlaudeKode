package io.github.stream29.kode.oauth.core

import kotlinx.serialization.json.Json
import java.io.File

public class FileOAuthTokenStore(
    private val baseDir: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    },
) : OAuthTokenStore {
    override suspend fun load(storage: String, key: String): OAuthTokenRecord? {
        val normalizedStorage = normalizeStorage(storage)
        return when (normalizedStorage) {
            STORAGE_ENV -> {
                System.getenv(key)?.trim()?.takeIf { value -> value.isNotBlank() }?.let { token ->
                    OAuthTokenRecord(accessToken = token)
                }
            }

            STORAGE_FILE -> {
                val file = resolveFile(key)
                if (!file.isFile) {
                    null
                } else {
                    val content = file.readText().trim()
                    if (content.isBlank()) {
                        null
                    } else {
                        runCatching {
                            json.decodeFromString<OAuthTokenRecord>(content)
                        }.getOrElse {
                            OAuthTokenRecord(accessToken = content)
                        }
                    }
                }
            }

            else -> null
        }
    }

    override suspend fun save(storage: String, key: String, token: OAuthTokenRecord) {
        val normalizedStorage = normalizeStorage(storage)
        if (normalizedStorage != STORAGE_FILE) {
            throw IllegalArgumentException("OAuth token save is only supported for file storage")
        }
        val file = resolveFile(key)
        file.parentFile?.mkdirs()
        val content = json.encodeToString(OAuthTokenRecord.serializer(), token)
        file.writeText(content)
    }

    override suspend fun delete(storage: String, key: String) {
        val normalizedStorage = normalizeStorage(storage)
        if (normalizedStorage != STORAGE_FILE) {
            return
        }
        val file = resolveFile(key)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun resolveFile(key: String): File {
        val trimmed = key.trim()
        val expanded = if (trimmed.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        val candidate = File(expanded)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            File(baseDir, expanded)
        }
    }

    private fun normalizeStorage(storage: String): String {
        val normalized = storage.trim().lowercase()
        return if (normalized.isBlank()) {
            STORAGE_FILE
        } else {
            normalized
        }
    }

    private companion object {
        private const val STORAGE_FILE: String = "file"
        private const val STORAGE_ENV: String = "env"
    }
}
