package io.github.stream29.kode.app.file

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.github.stream29.kode.dispatcher.VirtualThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
public data class AppConfig(
    val llm: List<LlmConfig> = emptyList(),
)

@Serializable
public sealed interface LlmConfig {
    @Serializable
    @SerialName("Anthropic")
    public data class Anthropic(
        public val apiKey: String,
    ): LlmConfig
}


public object Config {
    private val yaml = Yaml (
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase
        )
    )
    public suspend fun load(): AppConfig {
        return withContext(Dispatchers.VirtualThread) {
            if (!FileLocations.configFile.exists()) {
                FileLocations.dataDir.mkdirs()
                FileLocations.configFile.createNewFile()
            }
            yaml.decodeFromString<AppConfig>(FileLocations.configFile.readText())
        }
    }
}
