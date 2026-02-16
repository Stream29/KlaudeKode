package io.github.stream29.kode.config.core

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.github.stream29.kode.config.api.AppConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

public object ConfigYamlCodec {
    private val yaml: Yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
        ),
    )

    public fun parse(content: String): AppConfig {
        if (content.isBlank()) {
            return AppConfig(auths = emptyList(), models = emptyList())
        }
        return yaml.decodeFromString<AppConfig>(content)
    }

    public fun serialize(config: AppConfig): String {
        return yaml.encodeToString(config)
    }
}
