package io.github.stream29.kode.config.api

/**
 * Interface for configuration providers.
 * Implementations handle the side effects of reading/writing configuration.
 */
public interface ConfigProvider {
    /**
     * Load configuration from the provider.
     * @return the loaded AppConfig, or null if not found
     */
    public suspend fun load(): AppConfig?

    /**
     * Save configuration to the provider.
     * @param config the configuration to save
     */
    public suspend fun save(config: AppConfig)

    /**
     * Check if configuration exists.
     */
    public suspend fun exists(): Boolean

    /**
     * Initialize the provider with default configuration if needed.
     * @param defaultConfig the default configuration to use
     */
    public suspend fun initialize(defaultConfig: AppConfig)
}

/**
 * Interface for configuration sources that can provide raw content.
 */
public interface ConfigSource {
    /**
     * Read raw configuration content.
     * @return raw content as string, or null if not found
     */
    public suspend fun read(): String?

    /**
     * Write raw configuration content.
     * @param content the content to write
     */
    public suspend fun write(content: String)
}
