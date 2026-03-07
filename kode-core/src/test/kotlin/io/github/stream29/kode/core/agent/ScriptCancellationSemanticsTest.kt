package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.AgentScript
import io.github.stream29.kode.session.core.model.AgentScriptStatus
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScriptCancellationSemanticsTest {
    @Test
    fun run_cancellationPersistsInterruptedScriptMessageAndKeepsKoogPayloadLegal() {
        runBlocking {
            val sessionManager = SessionManager(
                repository = FakeSessionRepository(),
            )
            val session = sessionManager.createConversationSession(
                title = "script cancellation semantics",
                systemPrompt = "test system prompt",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )
            val runtimePort = RecordingRuntimeSideEffectPort()
            val engine = ScriptOnlyAgentEngine(
                promptExecutor = getMockExecutor {
                    mockLLMToolCall(
                        tool = ExecuteKotlinScriptTool,
                        args = ExecuteKotlinScriptArgs(script = BLOCKING_SCRIPT),
                        toolCallId = "cancel-call-id",
                    ) onCondition { true }
                },
                sessionManager = sessionManager,
                sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
                messageHandler = FakeMessageHandler(),
                hookManager = HookManager.empty(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(),
                runtimeSideEffectPort = runtimePort,
            )

            val runDeferred = async(Dispatchers.Default) {
                engine.run(
                    sessionId = session.id,
                    model = TEST_MODEL,
                    modelParams = null,
                )
            }

            assertTrue(runtimePort.awaitFirstToolCall(timeoutMillis = 10_000L))
            runDeferred.cancel(CancellationException("Stopped by user"))
            assertFailsWith<CancellationException> {
                runDeferred.await()
            }

            val messages = sessionManager.getAgentMessages(session.id, agentId = null)
            val scriptMessage = assertIs<AgentScript>(messages.last())

            assertEquals(AgentScriptStatus.FAILED, scriptMessage.status)
            assertEquals(INTERRUPTED_OPERATION_MESSAGE, scriptMessage.error)
            assertEquals(INTERRUPTED_OPERATION_MESSAGE, scriptMessage.scriptReturnValue)

            assertEquals(2, scriptMessage.koogMessages.size)
            val toolCall = assertIs<Message.Tool.Call>(scriptMessage.koogMessages.first())
            val toolResult = assertIs<Message.Tool.Result>(scriptMessage.koogMessages.last())
            assertEquals(ToolNames.EXECUTE_KOTLIN_SCRIPT, toolCall.tool)
            assertEquals(ToolNames.EXECUTE_KOTLIN_SCRIPT, toolResult.tool)
            assertEquals(INTERRUPTED_OPERATION_MESSAGE, toolResult.content)
        }
    }

    private class RecordingRuntimeSideEffectPort : RuntimeSideEffectPort {
        private val firstToolCallStarted: CountDownLatch = CountDownLatch(1)

        override fun isSafeStopRequested(sessionId: String): Boolean = false

        override fun onSafeStopReached(sessionId: String) = Unit

        override fun onToolCallStarting(sessionId: String, toolName: String, arguments: String) {
            firstToolCallStarted.countDown()
        }

        override fun onToolCallCompleted(sessionId: String, toolName: String, result: String) = Unit

        override fun onToolCallFailed(sessionId: String, message: String) = Unit

        override fun log(message: String) = Unit

        fun awaitFirstToolCall(timeoutMillis: Long): Boolean {
            return firstToolCallStarted.await(timeoutMillis, TimeUnit.MILLISECONDS)
        }
    }

    @Serializable
    private data class ExecuteKotlinScriptArgs(
        val script: String,
    )

    private companion object {
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

        private const val INTERRUPTED_OPERATION_MESSAGE: String = "This operation was interrupted by user."
        private const val BLOCKING_SCRIPT: String = "Thread.sleep(10000L)\n\"done\""

        private val TEST_MODEL: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.ToolChoice),
            contextLength = 8_192,
            maxOutputTokens = 1_024,
        )
    }
}
