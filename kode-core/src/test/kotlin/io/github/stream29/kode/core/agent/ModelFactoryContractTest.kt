package io.github.stream29.kode.core.agent

import io.github.stream29.kode.config.api.LlmAuth
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_API_KEY
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ModelFactoryContractTest {
    @Test
    fun unknownModelIdFailsExplicitly() {
        val authConfig = openAiAuthConfig(id = "auth-openai")
        val knownModelConfig = LlmModelConfig(
            id = "known-model",
            authId = authConfig.id,
            model = firstOpenAiModelId(),
            displayName = "Known OpenAI",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ModelFactory.resolveModelRuntime(
                modelId = "unknown-model-id",
                models = listOf(knownModelConfig),
                auths = listOf(authConfig),
            )
        }

        val message = error.message.orEmpty()
        assertContains(message, "Model not found")
        assertContains(message, "unknown-model-id")
    }

    @Test
    fun unknownModelNameFailsExplicitlyForProvider() {
        val authConfig = openAiAuthConfig(id = "auth-openai")
        val unknownProviderModelConfig = LlmModelConfig(
            id = "model-unknown-provider-model",
            authId = authConfig.id,
            model = "unknown-provider-model-id",
            displayName = "Unknown Provider Model",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ModelFactory.resolveModelRuntime(
                modelId = unknownProviderModelConfig.id,
                models = listOf(unknownProviderModelConfig),
                auths = listOf(authConfig),
            )
        }

        val message = error.message.orEmpty()
        assertContains(message, "Unknown model")
        assertContains(message, "unknown-provider-model-id")
        assertContains(message, PROVIDER_ID_OPENAI_API_KEY)
    }

    @Test
    fun resolveModelRuntimeSupportsMultipleAuthMappingsWithinSameProvider() {
        val primaryAuth = openAiAuthConfig(id = "auth-openai-primary")
        val secondaryAuth = openAiAuthConfig(id = "auth-openai-secondary")
        val providerModelId = firstOpenAiModelId()
        val modelConfigs = listOf(
            LlmModelConfig(
                id = "model-primary",
                authId = primaryAuth.id,
                model = providerModelId,
                displayName = "Primary OpenAI",
            ),
            LlmModelConfig(
                id = "model-secondary",
                authId = secondaryAuth.id,
                model = providerModelId,
                displayName = "Secondary OpenAI",
            ),
        )
        val authConfigs = listOf(primaryAuth, secondaryAuth)

        val primaryRuntime = ModelFactory.resolveModelRuntime(
            modelId = "model-primary",
            models = modelConfigs,
            auths = authConfigs,
        )
        val secondaryRuntime = ModelFactory.resolveModelRuntime(
            modelId = "model-secondary",
            models = modelConfigs,
            auths = authConfigs,
        )

        assertEquals(PROVIDER_ID_OPENAI_API_KEY, primaryRuntime.model.provider.id)
        assertEquals(PROVIDER_ID_OPENAI_API_KEY, secondaryRuntime.model.provider.id)
        assertEquals(providerModelId, primaryRuntime.model.id)
        assertEquals(providerModelId, secondaryRuntime.model.id)
    }

    private fun openAiAuthConfig(id: String): LlmAuthConfig {
        return LlmAuthConfig(
            id = id,
            providerId = PROVIDER_ID_OPENAI_API_KEY,
            auth = LlmAuth.ApiKey(
                apiKey = "sk-test",
            ),
        )
    }

    private fun firstOpenAiModelId(): String {
        val provider = assertNotNull(BuiltinLlmProviderRegistry.findProvider(PROVIDER_ID_OPENAI_API_KEY))
        val firstModel = assertNotNull(provider.models().firstOrNull())
        return firstModel.id
    }
}
