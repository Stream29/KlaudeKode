package io.github.stream29.kode.config.core

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ConfigProvider
import io.github.stream29.kode.config.api.ConfigSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Configuration manager that orchestrates configuration operations.
 * Depends on ConfigProvider for side effects, contains pure business logic.
 */
public class ConfigManager(
    private val provider: ConfigProvider,
    private val source: ConfigSource?
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase
        )
    )

    /**
     * Load configuration from the provider.
     * Returns default empty config if not found or parsing fails.
     */
    public suspend fun load(): AppConfig {
        return try {
            val loaded = provider.load()
                ?: source?.read()?.let { parse(it) }
                ?: AppConfig()
            val normalized = normalize(loaded)
            if (normalized != loaded) {
                provider.save(normalized)
                source?.write(serialize(normalized))
            }
            normalized
        } catch (e: Exception) {
            AppConfig()
        }
    }

    /**
     * Save configuration to the provider.
     */
    public suspend fun save(config: AppConfig) {
        provider.save(config)
        source?.write(serialize(config))
    }

    /**
     * Check if configuration exists.
     */
    public suspend fun exists(): Boolean {
        return provider.exists()
    }

    /**
     * Initialize with default configuration if not exists.
     */
    public suspend fun initialize(defaultConfig: AppConfig) {
        if (!exists()) {
            save(defaultConfig)
        }
    }

    /**
     * Parse YAML content to AppConfig.
     */
    public fun parse(content: String): AppConfig {
        return if (content.isBlank()) {
            AppConfig(auths = emptyList(), models = emptyList())
        } else {
            yaml.decodeFromString<AppConfig>(content)
        }
    }

    /**
     * Serialize AppConfig to YAML content.
     */
    public fun serialize(config: AppConfig): String {
        return yaml.encodeToString(config)
    }

    private fun normalize(config: AppConfig): AppConfig {
        var updated = config
        if (updated.defaults.modelId == null && updated.models.isNotEmpty()) {
            updated = updated.copy(
                defaults = updated.defaults.copy(modelId = updated.models.first().id)
            )
        }
        return updated
    }
}
