package io.github.stream29.kode.config.fs

import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.core.ConfigManager
import java.io.File

/**
 * Pre-configured file system config locations.
 */
public object FileSystemLocations {
    public val dataDir: File
        get() = resolveDataDir(path = null)

    public val configFile: File
        get() = File(dataDir, "config.yaml")

    public fun resolveDataDir(path: String?): File {
        val defaultDir = File(System.getProperty("user.home"), ".kode")
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) {
            return defaultDir
        }

        val expanded = if (raw.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + raw.removePrefix("~")
        } else {
            raw
        }
        return File(expanded).absoluteFile
    }
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
