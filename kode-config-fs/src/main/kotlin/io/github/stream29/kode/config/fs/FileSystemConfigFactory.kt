package io.github.stream29.kode.config.fs

import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.core.ConfigTemplateProvider
import java.io.File

/**
 * Pre-configured file system config locations.
 */
public object FileSystemLocations {
    public val dataDir: File = File(System.getProperty("user.home"), ".kode")
    public val configFile: File = File(dataDir, "config.yaml")
}

/**
 * Factory for creating pre-configured file system config manager.
 */
public object FileSystemConfigFactory {
    
    /**
     * Create a ConfigManager with default file system locations.
     */
    public fun createDefault(): ConfigManager {
        val provider = FileSystemConfigProvider(FileSystemLocations.configFile)
        val source = FileSystemConfigSource(FileSystemLocations.configFile)
        return ConfigManager(provider, source)
    }
    
    /**
     * Create a ConfigManager with custom config file.
     */
    public fun create(configFile: File): ConfigManager {
        val provider = FileSystemConfigProvider(configFile)
        val source = FileSystemConfigSource(configFile)
        return ConfigManager(provider, source)
    }
    
    /**
     * Create and initialize with default template if needed.
     */
    public suspend fun createAndInitialize(): ConfigManager {
        val manager = createDefault()
        if (!manager.exists()) {
            manager.initialize(AppConfig(auths = emptyList(), models = emptyList()))
        }
        return manager
    }
}
