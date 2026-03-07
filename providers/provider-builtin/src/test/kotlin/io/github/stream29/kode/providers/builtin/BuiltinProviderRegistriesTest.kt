package io.github.stream29.kode.providers.builtin

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.providers.api.LlmAuth
import io.github.stream29.kode.providers.api.LlmProvider
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderPreset
import io.github.stream29.kode.providers.api.validateProviderPresetRegistryUniqueness
import io.github.stream29.kode.providers.api.validateProviderRegistryUniqueness
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuiltinProviderRegistriesTest {
    @Test
    fun builtinRegistriesInitializeSuccessfully() {
        assertTrue(BuiltinLlmProviderRegistry.listProviders().isNotEmpty())
        assertTrue(BuiltinProviderPresetRegistry.listPresets().isNotEmpty())
    }

    @Test
    fun providerUniquenessRejectsDuplicateProviderIds() {
        val providers = listOf(
            fakeProvider(
                id = "provider-a",
                displayName = "Provider A",
                providerType = "provider-a",
                modelIds = listOf("model-a"),
            ),
            fakeProvider(
                id = "provider-a",
                displayName = "Provider B",
                providerType = "provider-a",
                modelIds = listOf("model-b"),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            validateProviderRegistryUniqueness(providers)
        }
        assertTrue(error.message.orEmpty().contains("Duplicate provider ids"))
    }

    @Test
    fun providerUniquenessRejectsDuplicateProviderNames() {
        val providers = listOf(
            fakeProvider(
                id = "provider-a",
                displayName = "Provider",
                providerType = "provider-a",
                modelIds = listOf("model-a"),
            ),
            fakeProvider(
                id = "provider-b",
                displayName = "Provider",
                providerType = "provider-b",
                modelIds = listOf("model-b"),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            validateProviderRegistryUniqueness(providers)
        }
        assertTrue(error.message.orEmpty().contains("Duplicate provider names"))
    }

    @Test
    fun providerUniquenessRejectsDuplicateProviderTypes() {
        val providers = listOf(
            fakeProvider(
                id = "provider-a",
                displayName = "Provider A",
                providerType = "shared-provider-type",
                modelIds = listOf("model-a"),
            ),
            fakeProvider(
                id = "provider-b",
                displayName = "Provider B",
                providerType = "shared-provider-type",
                modelIds = listOf("model-b"),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            validateProviderRegistryUniqueness(providers)
        }
        assertTrue(error.message.orEmpty().contains("Duplicate provider types"))
    }

    @Test
    fun providerUniquenessRejectsDuplicateModelIdsWithinProvider() {
        val providers = listOf(
            fakeProvider(
                id = "provider-a",
                displayName = "Provider A",
                providerType = "provider-a",
                modelIds = listOf("model-a", "model-a"),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            validateProviderRegistryUniqueness(providers)
        }
        assertTrue(error.message.orEmpty().contains("Duplicate model ids/names"))
        assertTrue(error.message.orEmpty().contains("providerId='provider-a'"))
    }

    @Test
    fun presetUniquenessRejectsDuplicatePresetIds() {
        val presets = listOf(
            ProviderPreset(
                id = "preset-a",
                displayName = "Preset A",
                authModes = setOf(ProviderAuthMode.ApiKey),
            ),
            ProviderPreset(
                id = "preset-a",
                displayName = "Preset B",
                authModes = setOf(ProviderAuthMode.ApiKey),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            validateProviderPresetRegistryUniqueness(presets)
        }
        assertTrue(error.message.orEmpty().contains("Duplicate provider preset ids"))
    }

    private fun fakeProvider(
        id: String,
        displayName: String,
        providerType: String,
        modelIds: List<String>,
    ): LlmProvider {
        return object : LlmProvider {
            override val id: String = id
            override val displayName: String = displayName
            override val llmProvider: LLMProvider = FakeRuntimeProvider(providerType)

            override fun models(): List<LLModel> {
                return modelIds.map { modelId ->
                    LLModel(
                        provider = llmProvider,
                        id = modelId,
                        capabilities = emptyList(),
                        contextLength = 8_192L,
                    )
                }
            }

            override fun supportsAuth(auth: LlmAuth): Boolean {
                return true
            }

            override fun createClient(auth: LlmAuth): LLMClient {
                throw UnsupportedOperationException("unused in uniqueness tests")
            }
        }
    }

    private class FakeRuntimeProvider(
        id: String,
    ) : LLMProvider(id = id, display = id)
}
