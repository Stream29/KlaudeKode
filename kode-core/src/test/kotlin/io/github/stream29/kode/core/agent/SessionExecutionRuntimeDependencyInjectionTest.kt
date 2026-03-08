package io.github.stream29.kode.core.agent

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.config.api.LlmAuth
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionExecutionRuntimeDependencyInjectionTest {
    @Test
    fun runAndContinueUseInjectedExecutionContextFactory() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(
                title = "context factory",
                systemPrompt = "test",
                workDir = null,
            )

            val recordAgent = RecordingMainAgent()
            val requestedContexts: MutableList<Pair<String, String>> = mutableListOf()
            val runtime = SessionExecutionRuntime(
                modelCatalogPort = StaticSessionExecutionModelCatalogPort(
                    SessionExecutionModelCatalog(
                        auths = emptyList(),
                        models = emptyList(),
                    ),
                ),
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                sessionManager = sessionManager,
                promptExecutorFactory = {
                    error("promptExecutorFactory should not be used when execution context factory is injected")
                },
                modelRuntimeResolver = { _, _, _ ->
                    error("modelRuntimeResolver should not be used when execution context factory is injected")
                },
                executionContextFactory = SessionExecutionContextFactory { sessionId, modelId ->
                    requestedContexts += sessionId to modelId
                    SessionExecutionContext(
                        agent = recordAgent,
                        model = TEST_MODEL.copy(id = "injected-$modelId"),
                        modelParams = null,
                    )
                },
            )

            val chatResult = runtime.runWithSession(
                sessionId = session.id,
                userInput = "hello",
                modelId = "model-a",
            )
            val runResult = runtime.continueSession(
                sessionId = session.id,
                modelId = "model-b",
            )

            assertEquals("chat-ok", chatResult)
            assertEquals("run-ok", runResult)
            assertEquals(
                listOf(
                    session.id to "model-a",
                    session.id to "model-b",
                ),
                requestedContexts,
            )
            assertEquals(listOf("injected-model-a"), recordAgent.chatModelIds)
            assertEquals(listOf("injected-model-b"), recordAgent.runModelIds)
        }
    }

    @Test
    fun runAndContinueSupportInjectedFactoryAndResolverForIsolatedTests() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "runtime dependency test", systemPrompt = "test", workDir = null)

            val fakeMainAgent = RecordingMainAgent()
            val createdDependencies = mutableListOf<MainAgentFactoryDependencies>()
            var resolverCalls = 0
            var promptExecutorFactoryCalls = 0

            val runtime = SessionExecutionRuntime(
                modelCatalogPort = StaticSessionExecutionModelCatalogPort(
                    SessionExecutionModelCatalog(
                        auths = emptyList(),
                        models = emptyList(),
                    ),
                ),
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                sessionManager = sessionManager,
                promptExecutorFactory = {
                    promptExecutorFactoryCalls += 1
                    error("promptExecutor should not be constructed in this injected-factory test")
                },
                modelRuntimeResolver = { modelId, _, _ ->
                    resolverCalls += 1
                    assertEquals("test-model-id", modelId)
                    SessionExecutionModelRuntime(
                        model = TEST_MODEL,
                        modelParams = null,
                    )
                },
                mainAgentFactory = MainAgentFactory { dependencies ->
                    createdDependencies += dependencies
                    fakeMainAgent
                },
            )

            val chatResult = runtime.runWithSession(
                sessionId = session.id,
                userInput = "hello",
                modelId = "test-model-id",
            )
            val continueResult = runtime.continueSession(
                sessionId = session.id,
                modelId = "test-model-id",
            )

            assertEquals("chat-ok", chatResult)
            assertEquals("run-ok", continueResult)
            assertEquals(2, resolverCalls)
            assertEquals(0, promptExecutorFactoryCalls)
            assertEquals(2, createdDependencies.size)
            assertTrue(createdDependencies.all { dependencies -> dependencies.runtimeContext.canInteractWithUser })
            assertTrue(createdDependencies.all { dependencies -> !dependencies.runtimeContext.canCreateSubagents })
            assertEquals(listOf("hello"), fakeMainAgent.chatInputs)
            assertEquals(1, fakeMainAgent.runCalls)
        }
    }

    @Test
    fun generateSessionTitleDelegatesToInjectedTitleGeneratorPort() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "title delegation", systemPrompt = "test", workDir = null)

            var titleCalls = 0
            val runtime = SessionExecutionRuntime(
                modelCatalogPort = StaticSessionExecutionModelCatalogPort(
                    SessionExecutionModelCatalog(
                        auths = emptyList(),
                        models = emptyList(),
                    ),
                ),
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                sessionManager = sessionManager,
                promptExecutorFactory = {
                    error("promptExecutor should not be constructed when title port is injected")
                },
                modelRuntimeResolver = { _, _, _ ->
                    error("modelRuntimeResolver should not be called when title port is injected")
                },
                sessionTitleGeneratorFactory = { _, _, _, _ ->
                    SessionTitleGenerationPort { sessionId, modelId ->
                        titleCalls += 1
                        assertEquals(session.id, sessionId)
                        assertEquals("title-model", modelId)
                        "delegated-title"
                    }
                },
            )

            val title = runtime.generateSessionTitleFromConversation(
                sessionId = session.id,
                modelId = "title-model",
            )

            assertEquals("delegated-title", title)
            assertEquals(1, titleCalls)
        }
    }

    @Test
    fun runtimeLoadsLatestModelCatalogForEachExecution() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(
                title = "dynamic model",
                systemPrompt = "test",
                workDir = null,
            )

            val catalogPort = MutableModelCatalogPort(
                SessionExecutionModelCatalog(
                    auths = listOf(testAuthConfig()),
                    models = listOf(testModelConfig(model = "runtime-model-v1")),
                ),
            )
            val recordingAgent = RecordingMainAgent()

            val runtime = SessionExecutionRuntime(
                modelCatalogPort = catalogPort,
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                sessionManager = sessionManager,
                promptExecutorFactory = {
                    error("promptExecutor should not be constructed in this custom-main-agent test")
                },
                modelRuntimeResolver = { modelId, configuredModels, _ ->
                    val configured = configuredModels.first { model -> model.id == modelId }
                    SessionExecutionModelRuntime(
                        model = TEST_MODEL.copy(id = configured.model),
                        modelParams = null,
                    )
                },
                mainAgentFactory = MainAgentFactory { recordingAgent },
            )

            runtime.runWithSession(
                sessionId = session.id,
                userInput = "hello",
                modelId = "dynamic-model",
            )

            catalogPort.catalog = SessionExecutionModelCatalog(
                auths = listOf(testAuthConfig()),
                models = listOf(testModelConfig(model = "runtime-model-v2")),
            )

            runtime.continueSession(
                sessionId = session.id,
                modelId = "dynamic-model",
            )

            assertEquals(listOf("runtime-model-v1"), recordingAgent.chatModelIds)
            assertEquals(listOf("runtime-model-v2"), recordingAgent.runModelIds)
            assertEquals(2, catalogPort.loadCalls)
        }
    }

    private class RecordingMainAgent : MainAgent {
        val chatInputs: MutableList<String> = mutableListOf()
        var runCalls: Int = 0
        val chatModelIds: MutableList<String> = mutableListOf()
        val runModelIds: MutableList<String> = mutableListOf()

        override suspend fun chat(
            sessionId: String,
            userInput: String,
            model: LLModel,
            modelParams: LLMParams?,
        ): String {
            chatInputs += userInput
            chatModelIds += model.id
            return "chat-ok"
        }

        override suspend fun run(
            sessionId: String,
            model: LLModel,
            modelParams: LLMParams?,
        ): String {
            runCalls += 1
            runModelIds += model.id
            return "run-ok"
        }
    }

    private class MutableModelCatalogPort(
        var catalog: SessionExecutionModelCatalog,
    ) : SessionExecutionModelCatalogPort {
        var loadCalls: Int = 0

        override suspend fun load(): SessionExecutionModelCatalog {
            loadCalls += 1
            return catalog
        }
    }

    private fun testAuthConfig(): LlmAuthConfig {
        return LlmAuthConfig(
            id = "test-auth",
            providerId = "test-provider",
            name = null,
            auth = LlmAuth.ApiKey(apiKey = "test-key"),
        )
    }

    private fun testModelConfig(model: String): LlmModelConfig {
        return LlmModelConfig(
            id = "dynamic-model",
            authId = "test-auth",
            model = model,
            displayName = model,
            params = null,
            maxContextSize = null,
            capabilities = null,
        )
    }

    private companion object {
        private val TEST_MODEL: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model-id",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.ToolChoice),
            contextLength = 8_192,
            maxOutputTokens = 1_024,
        )
    }
}
