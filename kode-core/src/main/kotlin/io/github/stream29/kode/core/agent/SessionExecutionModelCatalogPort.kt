package io.github.stream29.kode.core.agent

import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig

public data class SessionExecutionModelCatalog(
    val auths: List<LlmAuthConfig>,
    val models: List<LlmModelConfig>,
)

public fun interface SessionExecutionModelCatalogPort {
    public suspend fun load(): SessionExecutionModelCatalog
}

public class StaticSessionExecutionModelCatalogPort(
    private val catalog: SessionExecutionModelCatalog,
) : SessionExecutionModelCatalogPort {
    override suspend fun load(): SessionExecutionModelCatalog {
        return catalog
    }
}
