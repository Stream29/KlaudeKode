package io.github.stream29.kode.config.core

import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ConfigProvider
import io.github.stream29.kode.config.api.ConfigSource

/**
 * Configuration manager that orchestrates configuration operations.
 * Depends on ConfigProvider for side effects, contains pure business logic.
 */
public class ConfigManager(
    private val provider: ConfigProvider,
    private val source: ConfigSource?,
) {
    /**
     * Load configuration from the provider.
     * Returns default empty config only when configuration is missing.
     * Throws when existing configuration content is invalid.
     */
    public suspend fun load(): AppConfig {
        val loaded = provider.load()
            ?: source?.read()?.let { raw ->
                ConfigYamlCodec.parse(raw)
            }
            ?: AppConfig()
        val normalized = normalize(loaded)
        if (normalized != loaded) {
            persist(normalized)
        }
        return normalized
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
    public fun parse(content: String): AppConfig = ConfigYamlCodec.parse(content)

    /**
     * Serialize AppConfig to YAML content.
     */
    public fun serialize(config: AppConfig): String = ConfigYamlCodec.serialize(config)

    private fun normalize(config: AppConfig): AppConfig {
        return normalizeDefaultModelId(config)
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

}
