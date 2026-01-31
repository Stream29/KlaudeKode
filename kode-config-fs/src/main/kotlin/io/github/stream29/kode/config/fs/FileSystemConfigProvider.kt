package io.github.stream29.kode.config.fs

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ConfigProvider
import io.github.stream29.kode.config.api.ConfigSource
import io.github.stream29.kode.config.core.ConfigTemplateProvider
import io.github.stream29.kode.dispatcher.VirtualThread
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
    private val configFile: File
) : ConfigProvider {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase
        )
    )

    override suspend fun load(): AppConfig? {
        return withContext(Dispatchers.VirtualThread) {
            if (!configFile.exists()) {
                return@withContext null
            }

            val content = configFile.readText()
            if (content.isBlank()) {
                return@withContext AppConfig(auths = emptyList(), models = emptyList())
            }

            try {
                yaml.decodeFromString<AppConfig>(content)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to parse config file at ${configFile.absolutePath}: ${e.message}",
                    e
                )
            }
        }
    }

    override suspend fun save(config: AppConfig) {
        withContext(Dispatchers.VirtualThread) {
            configFile.parentFile?.mkdirs()
            val content = yaml.encodeToString(config)
            configFile.writeText(content)
        }
    }

    override suspend fun exists(): Boolean {
        return withContext(Dispatchers.VirtualThread) {
            configFile.exists() && configFile.length() > 0
        }
    }

    override suspend fun initialize(defaultConfig: AppConfig) {
        if (!exists()) {
            configFile.parentFile?.mkdirs()
            val template = ConfigTemplateProvider.getDefaultTemplate()
            configFile.writeText(template)
        }
    }
}

/**
 * File system based configuration source for raw content access.
 */
public class FileSystemConfigSource(
    private val configFile: File
) : ConfigSource {

    override suspend fun read(): String? {
        return withContext(Dispatchers.VirtualThread) {
            if (!configFile.exists()) null else configFile.readText()
        }
    }

    override suspend fun write(content: String) {
        withContext(Dispatchers.VirtualThread) {
            configFile.parentFile?.mkdirs()
            configFile.writeText(content)
        }
    }
}
