package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.tool.ToolNames
import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val context = ScriptContext()

        context.sayToUser("first")
        context.sayToUser("second")
        context.suspendForUserInput()

        assertEquals(listOf("first", "second"), context.consumeOutputList())
        assertTrue(context.consumeAwaitForUserInputSignal())

        assertEquals(emptyList(), context.consumeOutputList())
        assertFalse(context.consumeAwaitForUserInputSignal())
    }

    @Test
    fun runSuspendsAtPendingInputWithoutDirectRequestInputFlow() {
        runBlocking {
            val sessionManager = SessionManager(
                repository = FakeSessionRepository(),
            )
            val session = sessionManager.createConversationSession(
                title = "suspend semantics",
                systemPrompt = "test system prompt",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )
            val recordingMessageHandler = SafeStopAfterFirstInputMessageHandler()
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = getMockExecutor {
                    mockLLMToolCall(
                        tool = ExecuteKotlinScriptTool,
                        args = ExecuteKotlinScriptArgs(script = "suspendForUserInput()"),
                        toolCallId = "call-id",
                    ) onCondition { true }
                },
                sessionManager = sessionManager,
                sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
                messageHandler = recordingMessageHandler,
                hookManager = HookManager.empty(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(),
            )

            val result = withTimeout(timeMillis = 2_000L) {
                engine.run(
                    sessionId = session.id,
                    model = TEST_MODEL,
                    modelParams = null,
                )
            }

            assertNull(result)
            assertEquals(1, recordingMessageHandler.requestInputCount)
        }
    }

    private fun runEngineAndCaptureProtocolFailure(
        promptExecutor: PromptExecutor,
    ): IllegalStateException {
        return runBlocking {
            val sessionManager = SessionManager(
                repository = FakeSessionRepository(),
            )
            val session = sessionManager.createConversationSession(
                title = "protocol fail-fast",
                systemPrompt = "test system prompt",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = promptExecutor,
                sessionManager = sessionManager,
                sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
                messageHandler = FakeMessageHandler(),
                hookManager = HookManager.empty(),
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

    private class SafeStopAfterFirstInputMessageHandler : FakeMessageHandler() {
        private var safeStopRequested: Boolean = false

        override suspend fun requestInput(): String {
            safeStopRequested = true
            return super.requestInput()
        }

        override fun isSafeStopRequested(sessionId: String): Boolean {
            return safeStopRequested
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
