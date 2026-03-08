package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig

public data class SessionExecutionContext(
    val agent: MainAgent,
    val model: LLModel,
    val modelParams: LLMParams?,
)

public fun interface SessionExecutionContextFactory {
    public suspend fun create(sessionId: String, modelId: String): SessionExecutionContext
}

internal fun defaultSessionExecutionContextFactory(
    sessionQueryPort: SessionQueryPort,
    modelCatalogPort: SessionExecutionModelCatalogPort,
    promptExecutorFactory: (List<LlmAuthConfig>) -> PromptExecutor,
    modelRuntimeResolver: (String, List<LlmModelConfig>, List<LlmAuthConfig>) -> SessionExecutionModelRuntime,
    mainAgentProvider: (String, AgentRuntimeContext, () -> PromptExecutor) -> MainAgent,
): SessionExecutionContextFactory {
    return SessionExecutionContextFactory { sessionId, modelId ->
        sessionQueryPort.requireSession(sessionId)
        val catalog = modelCatalogPort.load()
        val modelRuntime = modelRuntimeResolver(modelId, catalog.models, catalog.auths)
        val enforcedParams = ModelParamsFactory.enforceRequiredToolChoice(modelRuntime.modelParams)
        var promptExecutor: PromptExecutor? = null
        SessionExecutionContext(
            agent = mainAgentProvider(
                sessionId,
                MAIN_RUNTIME_CONTEXT,
            ) {
                val existing = promptExecutor
                if (existing != null) {
                    existing
                } else {
                    val created = promptExecutorFactory(catalog.auths)
                    promptExecutor = created
                    created
                }
            },
            model = modelRuntime.model,
            modelParams = enforcedParams,
        )
    }
}

private val MAIN_RUNTIME_CONTEXT: AgentRuntimeContext = AgentRuntimeContext(
    agentId = null,
    parentAgentId = null,
    canInteractWithUser = true,
    canCreateSubagents = false,
)
