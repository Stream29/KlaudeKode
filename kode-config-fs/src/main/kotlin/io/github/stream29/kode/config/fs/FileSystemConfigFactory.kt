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
        val homeDir = System.getProperty("user.home")
        val defaultDir = File(homeDir, ".kode")
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) {
            return defaultDir
        }

        val expandedPath = if (raw.startsWith("~")) {
            homeDir + raw.removePrefix("~")
        } else {
            raw
        }
        return File(expandedPath).absoluteFile
    }
}

/**
 * Factory for creating pre-configured file system config manager.
 */
public object FileSystemConfigFactory {

    /**
     * Create a ConfigManager with default file system locations.
     */
    public fun createDefault(): ConfigManager = create(configFile = FileSystemLocations.configFile)

    /**
     * Create a ConfigManager with custom config file.
     */
    public fun create(configFile: File): ConfigManager {
        val provider = FileSystemConfigProvider(configFile)
        val source = FileSystemConfigSource(configFile)
        return ConfigManager(provider = provider, source = source)
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
