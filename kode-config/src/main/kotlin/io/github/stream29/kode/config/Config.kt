package io.github.stream29.kode.config

import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import io.github.stream29.kode.config.fs.FileSystemLocations

@Deprecated(
    "Use ConfigManager from kode-config-core with FileSystemConfigProvider from kode-config-fs",
    ReplaceWith("FileSystemConfigFactory.createDefault()", "io.github.stream29.kode.config.fs.FileSystemConfigFactory")
)
public object ConfigLoader {
    private val manager: ConfigManager by lazy {
        FileSystemConfigFactory.createDefault()
    }

    public suspend fun load(): AppConfig {
        return manager.load()
    }
    
    public suspend fun save(config: AppConfig) {
        manager.save(config)
    }
}

@Deprecated("Use FileSystemLocations from kode-config-fs", ReplaceWith("FileSystemLocations", "io.github.stream29.kode.config.fs.FileSystemLocations"))
public object FileLocations {
    public val dataDir: java.io.File = FileSystemLocations.dataDir
    public val configFile: java.io.File = FileSystemLocations.configFile
}
