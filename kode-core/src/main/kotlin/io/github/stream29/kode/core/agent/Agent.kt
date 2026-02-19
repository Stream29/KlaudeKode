package io.github.stream29.kode.core.agent

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

public interface Agent {
    public suspend fun run(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String
}

public data class AgentRuntimeContext(
    val agentId: String? = null,
    val parentAgentId: String? = null,
    val canInteractWithUser: Boolean = true,
    val canCreateSubagents: Boolean = true,
)
