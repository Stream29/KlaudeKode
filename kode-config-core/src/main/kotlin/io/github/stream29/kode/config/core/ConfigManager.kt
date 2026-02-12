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
    private val source: ConfigSource?,
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
                ?: source?.read()?.let(::parse)
                ?: AppConfig()
            val normalized = normalize(loaded)
            if (normalized != loaded) {
                persist(normalized)
            }
            normalized
        } catch (_: Exception) {
            AppConfig()
        }
    }

    /**
     * Save configuration to the provider.
     */
    public suspend fun save(config: AppConfig) {
        persist(config)
    }

    /**
     * Check if configuration exists.
     */
    public suspend fun exists(): Boolean = provider.exists()

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
    public fun parse(content: String): AppConfig = if (content.isBlank()) {
        AppConfig(auths = emptyList(), models = emptyList())
    } else {
        yaml.decodeFromString<AppConfig>(content)
    }

    /**
     * Serialize AppConfig to YAML content.
     */
    public fun serialize(config: AppConfig): String = yaml.encodeToString(config)

    private fun normalize(config: AppConfig): AppConfig {
        val withDefaultModelId = normalizeDefaultModelId(config)
        return normalizeLegacyPreset(withDefaultModelId)
    }

    private suspend fun persist(config: AppConfig) {
        provider.save(config)
        source?.write(serialize(config))
    }

    private fun normalizeDefaultModelId(config: AppConfig): AppConfig {
        if (config.defaults.modelId != null || config.models.isEmpty()) {
            return config
        }
        return config.copy(
            defaults = config.defaults.copy(modelId = config.models.first().id)
        )
    }

    @Suppress("DEPRECATION")
    private fun normalizeLegacyPreset(config: AppConfig): AppConfig {
        val legacy = config.agent
        val normalizedPreset = config.preset.copy(
            builtin = config.preset.builtin ?: legacy.builtin,
            file = config.preset.file ?: legacy.file,
        )
        if (normalizedPreset == config.preset && legacy.builtin == null && legacy.file == null) {
            return config
        }
        return config.copy(
            preset = normalizedPreset,
            agent = io.github.stream29.kode.config.api.AgentConfig(),
        )
    }
}
