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

public interface MainAgent : Agent {
    public suspend fun chat(
        sessionId: String,
        userInput: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String

    public companion object {
        public val DEFAULT_SYSTEM_PROMPT: String = ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT
    }
}

public interface SubAgent : Agent

public data class AgentRuntimeContext(
    val agentId: String? = null,
    val parentAgentId: String? = null,
    val canInteractWithUser: Boolean = true,
    val canCreateSubagents: Boolean = true,
)
