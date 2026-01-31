package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig

internal object ModelFactory {
    fun createModel(
        modelId: String,
        models: List<LlmModelConfig>,
        auths: List<LlmAuthConfig>
    ): LLModel {
        val modelConfig = models.find { it.id == modelId }
            ?: throw IllegalArgumentException("Model not found: $modelId")
        
        val authConfig = auths.find { it.id == modelConfig.authId }
            ?: throw IllegalArgumentException("Auth not found: ${modelConfig.authId}")
        
        return when (authConfig) {
            is LlmAuthConfig.Anthropic -> createAnthropicModel(modelConfig.model)
            is LlmAuthConfig.OpenAI -> createOpenAIModel(modelConfig.model)
            is LlmAuthConfig.Moonshot -> OpenAIModels.Chat.GPT4o
            is LlmAuthConfig.DeepSeek -> OpenAIModels.Chat.GPT4o
            is LlmAuthConfig.Gemini -> createGeminiModel(modelConfig.model)
            is LlmAuthConfig.OpenAICompatible -> OpenAIModels.Chat.GPT4o
        }
    }
    
    private fun createAnthropicModel(model: String): LLModel {
        return when (model) {
            "claude-sonnet-4-5-20250929" -> AnthropicModels.Sonnet_4_5
            "claude-haiku-4-5-20251001" -> AnthropicModels.Haiku_4_5
            "claude-opus-4-5-20251101" -> AnthropicModels.Opus_4_5
            "claude-sonnet-4-20250514" -> AnthropicModels.Sonnet_4
            "claude-opus-4-20250514" -> AnthropicModels.Opus_4
            "claude-opus-4-1-20250805" -> AnthropicModels.Opus_4_1
            "claude-3-7-sonnet-20250219" -> AnthropicModels.Sonnet_3_7
            "claude-3-5-sonnet-20241022" -> AnthropicModels.Sonnet_3_5
            "claude-3-5-haiku-20241022" -> AnthropicModels.Haiku_3_5
            "claude-3-opus-20240229" -> AnthropicModels.Opus_3
            "claude-3-haiku-20240307" -> AnthropicModels.Haiku_3
            else -> AnthropicModels.Sonnet_4_5
        }
    }
    
    private fun createOpenAIModel(model: String): LLModel {
        return when (model) {
            "gpt-5" -> OpenAIModels.Chat.GPT5
            "gpt-5-mini" -> OpenAIModels.Chat.GPT5Mini
            "gpt-5-nano" -> OpenAIModels.Chat.GPT5Nano
            "gpt-5-codex" -> OpenAIModels.Chat.GPT5Codex
            "gpt-5-pro" -> OpenAIModels.Chat.GPT5Pro
            "gpt-5.1" -> OpenAIModels.Chat.GPT5_1
            "gpt-5.1-codex" -> OpenAIModels.Chat.GPT5_1Codex
            "gpt-5.2" -> OpenAIModels.Chat.GPT5_2
            "gpt-5.2-pro" -> OpenAIModels.Chat.GPT5_2Pro
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
            "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
            "gpt-4.1-nano" -> OpenAIModels.Chat.GPT4_1Nano
            "o1" -> OpenAIModels.Chat.O1
            "o3" -> OpenAIModels.Chat.O3
            "o3-mini" -> OpenAIModels.Chat.O3Mini
            "o4-mini" -> OpenAIModels.Chat.O4Mini
            else -> OpenAIModels.Chat.GPT4o
        }
    }
    
    private fun createGeminiModel(model: String): LLModel {
        return when (model) {
            "gemini-3-pro-preview" -> GoogleModels.Gemini3_Pro_Preview
            "gemini-2.5-pro" -> GoogleModels.Gemini2_5Pro
            "gemini-2.5-flash" -> GoogleModels.Gemini2_5Flash
            "gemini-2.5-flash-lite" -> GoogleModels.Gemini2_5FlashLite
            "gemini-2.0-flash" -> GoogleModels.Gemini2_0Flash
            "gemini-2.0-flash-001" -> GoogleModels.Gemini2_0Flash001
            "gemini-2.0-flash-lite" -> GoogleModels.Gemini2_0FlashLite
            "gemini-2.0-flash-lite-001" -> GoogleModels.Gemini2_0FlashLite001
            else -> GoogleModels.Gemini2_0Flash
        }
    }
}
