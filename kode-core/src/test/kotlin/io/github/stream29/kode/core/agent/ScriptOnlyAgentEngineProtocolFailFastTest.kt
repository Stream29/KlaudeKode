package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.test.*

class ScriptOnlyAgentEngineProtocolFailFastTest {
    @Test
    fun runFailsFastWhenAssistantTextAppearsInToolOnlyMode() {
        val error = runEngineAndCaptureProtocolFailure(
            promptExecutor = getMockExecutor {
                mockLLMAnswer("hello from assistant").asDefaultResponse
            },
        )

        assertContains(
            charSequence = error.message.orEmpty(),
            other = "Tool-only mode violation: assistant text is not allowed",
        )
    }

    @Test
    fun runFailsFastWhenNonScriptToolCallAppears() {
        val error = runEngineAndCaptureProtocolFailure(
            promptExecutor = getMockExecutor {
                mockLLMToolCall(
                    tool = ExecuteShellTool,
                    args = ExecuteShellArgs(command = "ls"),
                    toolCallId = "call-id",
                ) onCondition { true }
            },
        )

        assertContains(
            charSequence = error.message.orEmpty(),
            other = "Script-only violation: tool 'executeShell' is not allowed",
        )
    }

    @Test
    fun scriptContextSideChannelConsumeMethodsAreDeterministicAndConsumeOnce() {
        ScriptContextDeterminismVerifier.verify(context = MainAgentScriptContext())
    }

    @Test
    fun runSuspendsAtPendingInputWithoutDirectRequestInputFlow() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "suspend semantics", systemPrompt = "test system prompt", workDir = null)
            val runtimeProbe = RuntimeOrchestrationProbe(input = "restored-by-orchestration")
            val sessionProbe = SessionOrchestrationProbe()
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = getMockExecutor {
                    mockLLMToolCall(
                        tool = ExecuteKotlinScriptTool,
                        args = ExecuteKotlinScriptArgs(script = "suspendForUserInput()"),
                        toolCallId = "call-id",
                    ) onCondition { true }
                },
                sessionManager = sessionManager,
                messageHandler = FailOnDirectInputMessageHandler(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(),
                runtimeSideEffectPort = runtimeProbe.port,
                sessionSideEffectPort = sessionProbe.port,
            )

            val result = withTimeout(timeMillis = 2_000L) {
                engine.run(
                    sessionId = session.id,
                    model = TEST_MODEL,
                    modelParams = null,
                )
            }

            assertNull(result)
            assertEquals(1, runtimeProbe.requestInputCalls)
            assertEquals(1, sessionProbe.suspendForUserInputCalls)
            assertEquals(1, sessionProbe.resumeRunCalls)
            assertEquals(1, sessionProbe.addUserMessageCalls)
            assertEquals(1, sessionProbe.saveToolExchangeCalls)
            assertEquals(listOf("restored-by-orchestration"), sessionProbe.appendedContents)

            val sessionSnapshot = assertNotNull(sessionManager.getSession(session.id))
            assertTrue(sessionSnapshot.messages.none { message -> message is UserMessage })

            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    @Test
    fun runBuildsFallbackPromptFromConcreteScriptContextInjection() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "fallback prompt", systemPrompt = "ignored", workDir = null)
            var capturedSystemPrompt: String? = null
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = object : PromptExecutor {
                    override suspend fun execute(
                        prompt: ai.koog.prompt.dsl.Prompt,
                        model: LLModel,
                        tools: List<ai.koog.agents.core.tools.ToolDescriptor>
                    ): List<ai.koog.prompt.message.Message.Response> {
                        val sysMsg = prompt.messages.firstOrNull { msg -> msg is ai.koog.prompt.message.Message.System }
                        capturedSystemPrompt = (sysMsg as? ai.koog.prompt.message.Message.System)?.content
                        throw FallbackCapturedException()
                    }

                    override fun executeStreaming(
                        prompt: ai.koog.prompt.dsl.Prompt,
                        model: LLModel,
                        tools: List<ai.koog.agents.core.tools.ToolDescriptor>
                    ): kotlinx.coroutines.flow.Flow<ai.koog.prompt.streaming.StreamFrame> {
                        error("Not supported")
                    }

                    override suspend fun moderate(
                        prompt: ai.koog.prompt.dsl.Prompt,
                        model: LLModel
                    ): ai.koog.prompt.dsl.ModerationResult {
                        error("Not supported")
                    }

                    override fun close() {}
                },
                sessionManager = sessionManager,
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(),
                scriptContextFactory = { _, _ ->
                    MainAgentScriptContext(
                        userCommunicationScriptContext = object : UserCommunicationScriptContext by UserCommunicationScriptContextImpl() {
                            override val defaultImports: List<String> = emptyList()
                            override val systemPromptInjection: String = "Prompt injection from PromptInjectionScriptContext"
                        }
                    )
                },
                sessionSideEffectPort = object : SessionSideEffectPort {
                    override suspend fun prepareMessagesForAgent(
                        sessionId: String,
                        agentId: String?
                    ): List<ai.koog.prompt.message.Message> {
                        return emptyList()
                    }

                    override suspend fun resolveSystemPrompt(
                        sessionId: String,
                        agentId: String?,
                        fallback: String
                    ): String {
                        return fallback
                    }

                    override suspend fun suspendForUserInput(sessionId: String) {
                        error("suspendForUserInput should not be called in this test")
                    }

                    override suspend fun saveToolExchange(
                        sessionId: String,
                        toolName: String,
                        toolCallId: String,
                        arguments: JsonElement,
                        result: JsonElement,
                        isError: Boolean,
                        errorMessage: String?,
                        outputList: List<String>,
                        awaitForUserInput: Boolean,
                        agentId: String?,
                    ) {
                        error("saveToolExchange should not be called in this test")
                    }
                },
            )

            assertFailsWith<FallbackCapturedException> {
                engine.run(
                    sessionId = session.id,
                    model = TEST_MODEL,
                    modelParams = null,
                )
            }

            assertContains(
                charSequence = capturedSystemPrompt.orEmpty(),
                other = "Prompt injection from PromptInjectionScriptContext",
            )
            assertContains(
                charSequence = capturedSystemPrompt.orEmpty(),
                other = "You are a coding agent named `Kode`.",
            )
        }
    }

    private fun runEngineAndCaptureProtocolFailure(
        promptExecutor: PromptExecutor,
    ): IllegalStateException {
        return runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "protocol fail-fast", systemPrompt = "test system prompt", workDir = null)
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = promptExecutor,
                sessionManager = sessionManager,
                messageHandler = FakeMessageHandler(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(),
            )

            assertFailsWith<IllegalStateException>(
                message = "ScriptOnlyAgentEngine should fail fast for protocol violations",
            ) {
                engine.run(
                    sessionId = session.id,
                    model = TEST_MODEL,
                    modelParams = null,
                )
            }
        }
    }

    @Serializable
    private data class ExecuteShellArgs(
        val command: String,
    )

    @Serializable
    private data class ExecuteKotlinScriptArgs(
        val script: String,
    )

    private companion object {
        private object ExecuteShellTool : Tool<ExecuteShellArgs, String>(
            argsSerializer = serializer<ExecuteShellArgs>(),
            resultSerializer = serializer<String>(),
            name = "executeShell",
            description = "Test-only illegal non-script tool",
        ) {
            override suspend fun execute(args: ExecuteShellArgs): String {
                return args.command
            }
        }

        private object ExecuteKotlinScriptTool : Tool<ExecuteKotlinScriptArgs, String>(
            argsSerializer = serializer<ExecuteKotlinScriptArgs>(),
            resultSerializer = serializer<String>(),
            name = ToolNames.EXECUTE_KOTLIN_SCRIPT,
            description = "Test-only executeKotlinScript tool call",
        ) {
            override suspend fun execute(args: ExecuteKotlinScriptArgs): String {
                return ""
            }
        }

        private val TEST_MODEL: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.ToolChoice),
            contextLength = 8_192,
            maxOutputTokens = 1_024,
        )
    }
}
