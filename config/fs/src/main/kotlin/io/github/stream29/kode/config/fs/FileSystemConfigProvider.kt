package io.github.stream29.kode.config.fs

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ConfigProvider
import io.github.stream29.kode.config.api.ConfigSource
import io.github.stream29.kode.config.core.ConfigTemplateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * File system based configuration provider.
 * Handles all file I/O side effects.
 */
public class FileSystemConfigProvider(
    private val configFile: File,
    private val legacyReadFiles: List<File>,
) : ConfigProvider {

    private val ioDispatcher = Dispatchers.IO

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase
        )
    )

    private val readCandidates: List<File> = buildList {
        add(configFile)
        addAll(legacyReadFiles)
    }.distinctBy { file -> file.absolutePath }

    override suspend fun load(): AppConfig? {
        return withContext(ioDispatcher) {
            val sourceFile = resolveConfigFileForRead()
            if (sourceFile == null) {
                return@withContext null
            }

            val content = sourceFile.readText()
            if (content.isBlank()) {
                return@withContext AppConfig(auths = emptyList(), models = emptyList())
            }

            return@withContext decodeConfig(content = content)
        }
    }

    private fun decodeConfig(content: String): AppConfig {
        return try {
            yaml.decodeFromString<AppConfig>(content)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse config file at ${configFile.absolutePath}: ${e.message}",
                e
            )
        }
    }

    override suspend fun save(config: AppConfig) {
        withContext(ioDispatcher) {
            configFile.parentFile?.mkdirs()
            val content = yaml.encodeToString(config)
            configFile.writeText(content)
        }
    }

    override suspend fun exists(): Boolean {
        return withContext(ioDispatcher) {
            readCandidates.any { file -> file.exists() && file.length() > 0 }
        }
    }

    override suspend fun initialize(defaultConfig: AppConfig) {
        if (!exists()) {
            configFile.parentFile?.mkdirs()
            val template = ConfigTemplateProvider.getDefaultTemplate()
            configFile.writeText(template)
        }
    }

    private fun resolveConfigFileForRead(): File? {
        return readCandidates.firstOrNull { file -> file.isFile }
    }
}

/**
 * File system based configuration source for raw content access.
 */
public class FileSystemConfigSource(
    private val configFile: File,
    private val legacyReadFiles: List<File>,
) : ConfigSource {

    private val ioDispatcher = Dispatchers.IO

    private val readCandidates: List<File> = buildList {
        add(configFile)
        addAll(legacyReadFiles)
    }.distinctBy { file -> file.absolutePath }

    override suspend fun read(): String? {
        return withContext(ioDispatcher) {
            val sourceFile = resolveConfigFileForRead()
            if (sourceFile == null) {
                return@withContext null
            }
            return@withContext sourceFile.readText()
        }
    }

    override suspend fun write(content: String) {
        withContext(ioDispatcher) {
            configFile.parentFile?.mkdirs()
            configFile.writeText(content)
        }
    }

    private fun resolveConfigFileForRead(): File? {
        return readCandidates.firstOrNull { file -> file.isFile }
    }
}
