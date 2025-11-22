package io.github.stream29.kode.app.file

import java.io.File

public object FileLocations {
    public val dataDir: File = File(System.getProperty("user.home")).resolve(".kode")
    public val configFile: File = dataDir.resolve("config.yml")
}