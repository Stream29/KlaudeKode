package io.github.stream29.kode.providers.builtin

import io.github.stream29.kode.providers.anthropic.AnthropicApiKeyProvider
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderPreset
import io.github.stream29.kode.providers.deepseek.DeepSeekApiKeyProvider
import io.github.stream29.kode.providers.gemini.GeminiApiKeyProvider
import io.github.stream29.kode.providers.groq.GroqApiKeyProvider
import io.github.stream29.kode.providers.mistral.MistralApiKeyProvider
import io.github.stream29.kode.providers.moonshot.MoonshotApiKeyProvider
import io.github.stream29.kode.providers.openai.OpenAiApiKeyProvider
import io.github.stream29.kode.providers.openai.OpenAiCompatibleProvider
import io.github.stream29.kode.providers.openai.OpenAiSubscriptionBrowserProvider
import io.github.stream29.kode.providers.openai.OpenAiSubscriptionDeviceProvider
import io.github.stream29.kode.providers.openrouter.OpenRouterApiKeyProvider
import io.github.stream29.kode.providers.xai.XaiApiKeyProvider

public object BuiltinLlmProviderRegistry {
    private val providersById: Map<String, LlmProvider> by lazy {
        val all = buildList {
            add(AnthropicApiKeyProvider)
            add(OpenAiApiKeyProvider)
            add(OpenAiSubscriptionBrowserProvider)
            add(OpenAiSubscriptionDeviceProvider)
            add(OpenAiCompatibleProvider)
            add(GeminiApiKeyProvider)
            add(DeepSeekApiKeyProvider)
            add(MoonshotApiKeyProvider)
            add(OpenRouterApiKeyProvider)
            add(GroqApiKeyProvider)
            add(MistralApiKeyProvider)
            add(XaiApiKeyProvider)
            add(TestDeterministicProvider)
        }

        val duplicates = all.groupBy { it.id }.filterValues { list -> list.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate LLM provider ids: ${duplicates.sorted().joinToString()}" }

        all.associateBy { it.id }
    }

    public fun listProviders(): List<LlmProvider> {
        return providersById.values.sortedBy { it.displayName.lowercase() }
    }

    public fun findProvider(id: String): LlmProvider? {
        val normalized = id.trim()
        if (normalized.isBlank()) {
            return null
        }
        return providersById[normalized]
    }
}

public object BuiltinProviderPresetRegistry {
    private val presetsById: Map<String, ProviderPreset> by lazy {
        val all = buildList {
            add(AnthropicApiKeyProvider.preset)
            add(OpenAiApiKeyProvider.preset)
            add(OpenAiSubscriptionBrowserProvider.preset)
            add(OpenAiSubscriptionDeviceProvider.preset)
            add(OpenAiCompatibleProvider.preset)
            add(GeminiApiKeyProvider.preset)
            add(DeepSeekApiKeyProvider.preset)
            add(MoonshotApiKeyProvider.preset)
            add(OpenRouterApiKeyProvider.preset)
            add(GroqApiKeyProvider.preset)
            add(MistralApiKeyProvider.preset)
            add(XaiApiKeyProvider.preset)
            add(TestDeterministicProvider.preset)
        }

        val duplicates = all.groupBy { it.id }.filterValues { list -> list.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate provider preset ids: ${duplicates.sorted().joinToString()}" }

        all.associateBy { it.id }
    }

    public fun listPresets(): List<ProviderPreset> {
        return presetsById.values.sortedBy { it.displayName.lowercase() }
    }

    public fun findPreset(id: String): ProviderPreset? {
        val normalized = id.trim()
        if (normalized.isBlank()) {
            return null
        }
        return presetsById[normalized]
    }
}
