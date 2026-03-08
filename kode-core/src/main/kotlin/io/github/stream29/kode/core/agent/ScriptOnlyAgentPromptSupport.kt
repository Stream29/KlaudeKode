package io.github.stream29.kode.core.agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

internal fun buildPrompt(
    sessionId: String,
    iteration: Int,
    systemPrompt: String,
    messages: List<Message>,
    modelParams: LLMParams?,
): Prompt {
    val normalizedParams = withCodexInstructionParams(
        params = ModelParamsFactory.enforceRequiredToolChoice(modelParams),
        systemPrompt = systemPrompt,
    )
    val messagesForPrompt = buildList {
        add(Message.System(systemPrompt, RequestMetaInfo.create(Clock.System.toDeprecatedClock())))
        addAll(messages)
    }
    return Prompt(
        id = "conversation_${sessionId}_$iteration",
        messages = messagesForPrompt,
        params = normalizedParams,
    )
}

private fun withCodexInstructionParams(
    params: LLMParams,
    systemPrompt: String,
): LLMParams {
    if (params !is OpenAIResponsesParams) {
        return params
    }
    val normalizedInstructions = systemPrompt.trim().ifBlank {
        "You are a helpful assistant"
    }
    val mergedAdditionalProperties = (params.additionalProperties ?: emptyMap()) +
            mapOf("instructions" to JsonPrimitive(normalizedInstructions))
    return params.copy(additionalProperties = mergedAdditionalProperties)
}
