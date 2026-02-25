package io.github.stream29.kode.core.port

import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonElement

public interface SessionSideEffectPort {
    public suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message>

    public suspend fun resolveSystemPrompt(sessionId: String, agentId: String?, fallback: String): String

    public suspend fun suspendForUserInput(sessionId: String)

    public suspend fun saveToolExchange(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
        outputList: List<String>,
        agentId: String?,
    )
}
