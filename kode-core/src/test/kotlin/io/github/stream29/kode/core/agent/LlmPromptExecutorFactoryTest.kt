package io.github.stream29.kode.core.agent

import io.github.stream29.kode.config.api.LlmAuth
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_API_KEY
import io.github.stream29.kode.config.api.PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER
import io.github.stream29.kode.providers.api.LlmAuth as RuntimeLlmAuth
import io.github.stream29.kode.providers.builtin.BuiltinLlmProviderRegistry
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LlmPromptExecutorFactoryTest {
    @Test
    fun factoryAcceptsMultipleAuthConfigsForSameProvider() {
        val auths = listOf(
            LlmAuthConfig(
                id = "auth-openai-primary",
                providerId = PROVIDER_ID_OPENAI_API_KEY,
                auth = LlmAuth.ApiKey(
                    apiKey = "sk-primary",
                ),
            ),
            LlmAuthConfig(
                id = "auth-openai-secondary",
                providerId = PROVIDER_ID_OPENAI_API_KEY,
                auth = LlmAuth.ApiKey(
                    apiKey = "sk-secondary",
                ),
            ),
        )

        val executor = LlmPromptExecutorFactory.create(auths)

        assertNotNull(executor)
    }

    @Test
    fun providerFailsFastOnMismatchedAuthTypeWithProviderAndAuthId() {
        val provider = assertNotNull(BuiltinLlmProviderRegistry.findProvider(PROVIDER_ID_OPENAI_API_KEY))

        val error = assertFailsWith<IllegalArgumentException> {
            provider.createClient(
                RuntimeLlmAuth.OAuthAccessToken(
                    accessToken = "token",
                    authId = "auth-oauth",
                )
            )
        }

        assertContains(error.message.orEmpty(), "providerId='openai-api-key'")
        assertContains(error.message.orEmpty(), "authId='auth-oauth'")
        assertContains(error.message.orEmpty(), "expected='ApiKey'")
    }

    @Test
    fun factoryRejectsUnsupportedAuthTypeWithProviderAndAuthId() {
        val auths = listOf(
            LlmAuthConfig(
                id = "auth-api-key",
                providerId = PROVIDER_ID_OPENAI_SUBSCRIPTION_BROWSER,
                auth = LlmAuth.ApiKey(
                    apiKey = "sk-test",
                ),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            LlmPromptExecutorFactory.create(auths)
        }

        assertContains(error.message.orEmpty(), "providerId='openai-subscription-browser'")
        assertContains(error.message.orEmpty(), "authId='auth-api-key'")
        assertContains(error.message.orEmpty(), "actual='ApiKey'")
    }

    @Test
    fun modelRuntimeProviderMappingUsesAuthProviderIdConsistently() {
        val authConfig = LlmAuthConfig(
            id = "auth-openai",
            providerId = PROVIDER_ID_OPENAI_API_KEY,
            auth = LlmAuth.ApiKey(apiKey = "sk-test"),
        )
        val providerModelId = firstOpenAiModelId()
        val modelConfig = LlmModelConfig(
            id = "model-custom",
            authId = authConfig.id,
            model = providerModelId,
            displayName = "Registered OpenAI",
        )

        val runtime = ModelFactory.resolveModelRuntime(
            modelId = modelConfig.id,
            models = listOf(modelConfig),
            auths = listOf(authConfig),
        )

        assertEquals(PROVIDER_ID_OPENAI_API_KEY, runtime.model.provider.id)
        assertEquals(providerModelId, runtime.model.id)
    }

    private fun firstOpenAiModelId(): String {
        val provider = assertNotNull(BuiltinLlmProviderRegistry.findProvider(PROVIDER_ID_OPENAI_API_KEY))
        val firstModel = assertNotNull(provider.models().firstOrNull())
        return firstModel.id
    }
}
