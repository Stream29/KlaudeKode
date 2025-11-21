package io.github.stream29.koogagent

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AppConfig(
    val llm: LlmConfig
)

@Serializable
data class LlmConfig(
    val apiKey: String
)

object ConfigLoader {
    fun load(): AppConfig {
        val locations = listOf(
            File("config.yml"),
            File("../config.yml"), // Check parent directory (useful for gradle subproject execution)
            File(System.getProperty("user.home"), "config.yml"),
            File(System.getProperty("user.home"), ".koog/config.yml")
        )
        
        val configFile = locations.find { it.exists() }
        
        if (configFile == null) {
             error("❌ Configuration file not found. Checked locations:\n" + locations.joinToString("\n") { " - ${it.absolutePath}" } + "\nPlease create a config.yml file with your LLM settings.")
        }
        
        return Yaml.default.decodeFromString(AppConfig.serializer(), configFile.readText())
    }
}
