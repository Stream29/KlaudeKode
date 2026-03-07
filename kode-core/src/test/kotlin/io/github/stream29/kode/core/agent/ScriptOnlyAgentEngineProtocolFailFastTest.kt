package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    fun koogSessionBridgeFailsFastWhenPersistingNonScriptToolExchange() {
        runBlocking {
            val sessionManager = SessionManager(
                repository = FakeSessionRepository(),
            )
            val session = sessionManager.createConversationSession(
                title = "bridge fail-fast",
                systemPrompt = "test system prompt",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )
            val bridge = KoogSessionBridge(sessionManager = sessionManager)

            val error = assertFailsWith<IllegalStateException> {
                bridge.saveToolExchange(
                    sessionId = session.id,
                    toolName = "executeShell",
                    toolCallId = "call-id",
                    arguments = JsonPrimitive("{\"command\":\"ls\"}"),
                    result = JsonPrimitive("ok"),
                    isError = false,
                    errorMessage = null,
                    outputList = emptyList(),
                    awaitForUserInput = false,
                    agentId = null,
                )
            }

            assertContains(
                charSequence = error.message.orEmpty(),
                other = "Script-only violation: tool 'executeShell' is not allowed for persistence",
            )
        }
    }

    @Test
    fun scriptContextSideChannelConsumeMethodsAreDeterministicAndConsumeOnce() {
        val context = MainAgentScriptContext()

        context.sayToUser("first")
        context.sayToUser("second")
        context.suspendForUserInput()

        assertEquals(listOf("first", "second"), context.consumeOutputList())
        assertTrue(context.consumeAwaitForUserInputSignal())

        assertEquals(emptyList(), context.consumeOutputList())
        assertFalse(context.consumeAwaitForUserInputSignal())

        context.sayToUser("concurrent-output")
        val outputConsumeStart = CountDownLatch(1)
        val outputConsumeDone = CountDownLatch(2)
        val outputConsumeFailed = AtomicBoolean(false)
        val outputConsumeSizes: MutableList<Int> = mutableListOf()
        val outputConsumeSizesLock: Any = Any()
        repeat(2) {
            val consumer = Thread(
                {
                    if (!outputConsumeStart.await(1, TimeUnit.SECONDS)) {
                        outputConsumeFailed.set(true)
                        outputConsumeDone.countDown()
                        return@Thread
                    }
                    val consumed = context.consumeOutputList()
                    synchronized(outputConsumeSizesLock) {
                        outputConsumeSizes.add(consumed.size)
                    }
                    outputConsumeDone.countDown()
                },
                "context-output-consumer-$it",
            )
            consumer.isDaemon = true
            consumer.start()
        }
        outputConsumeStart.countDown()
        assertTrue(outputConsumeDone.await(2, TimeUnit.SECONDS))
        assertFalse(outputConsumeFailed.get())
        assertEquals(listOf(0, 1), outputConsumeSizes.sorted())

        context.suspendForUserInput()
        val signalConsumeStart = CountDownLatch(1)
        val signalConsumeDone = CountDownLatch(8)
        val signalConsumeFailed = AtomicBoolean(false)
        val consumedSignalCount = AtomicInteger(0)
        repeat(8) {
            val consumer = Thread(
                {
                    if (!signalConsumeStart.await(1, TimeUnit.SECONDS)) {
                        signalConsumeFailed.set(true)
                        signalConsumeDone.countDown()
                        return@Thread
                    }
                    if (context.consumeAwaitForUserInputSignal()) {
                        consumedSignalCount.incrementAndGet()
                    }
                    signalConsumeDone.countDown()
                },
                "context-signal-consumer-$it",
            )
            consumer.isDaemon = true
            consumer.start()
        }
        signalConsumeStart.countDown()
        assertTrue(signalConsumeDone.await(2, TimeUnit.SECONDS))
        assertFalse(signalConsumeFailed.get())
        assertEquals(1, consumedSignalCount.get())
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
                sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
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
            val sessionManager = SessionManager(
                repository = FakeSessionRepository(),
            )
            val session = sessionManager.createConversationSession(
                title = "fallback prompt",
                systemPrompt = "ignored",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )
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
                sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
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

    private class FailOnDirectInputMessageHandler : FakeMessageHandler() {
        override suspend fun requestInput(): String {
            error("requestInput must be orchestrated outside ScriptOnlyAgentEngine")
        }
    }

    private class RuntimeOrchestrationProbe(
        private val input: String,
    ) {
        var requestInputCalls: Int = 0
            private set

        private var safeStopRequested: Boolean = false

        val port: RuntimeSideEffectPort = buildProxy()

        private fun buildProxy(): RuntimeSideEffectPort {
            val runtimeInputPort = loadEnginePrivateInterface(
                simpleName = "RuntimeInputPort",
            )
            val proxy = Proxy.newProxyInstance(
                ScriptOnlyAgentEngineProtocolFailFastTest::class.java.classLoader,
                arrayOf(RuntimeSideEffectPort::class.java, runtimeInputPort),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "requestInput" -> {
                        requestInputCalls += 1
                        safeStopRequested = true
                        input
                    }

                    "isSafeStopRequested" -> safeStopRequested
                    "onToolCallCompleted", "onToolCallFailed" -> {
                        safeStopRequested = true
                        Unit
                    }

                    "onSafeStopReached", "onToolCallStarting", "log" -> Unit
                    "toString" -> "RuntimeOrchestrationProbePort"
                    "hashCode" -> System.identityHashCode(proxyInstance)
                    "equals" -> proxyInstance === args?.firstOrNull()
                    else -> defaultPrimitiveProxyValue(method = method)
                }
            }
            return proxy as RuntimeSideEffectPort
        }

        private fun loadEnginePrivateInterface(simpleName: String): Class<*> {
            val qualifiedName = "${ScriptOnlyAgentEngine::class.java.name}\$$simpleName"
            return Class.forName(qualifiedName)
        }

        private fun defaultPrimitiveProxyValue(method: Method): Any? {
            return when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Character.TYPE -> 0.toChar()
                else -> null
            }
        }
    }

    private class SessionOrchestrationProbe {
        var suspendForUserInputCalls: Int = 0
            private set

        var saveToolExchangeCalls: Int = 0
            private set

        var resumeRunCalls: Int = 0
            private set

        var addUserMessageCalls: Int = 0
            private set

        val appendedContents: MutableList<String> = mutableListOf()

        val port: SessionSideEffectPort = buildProxy()

        private fun buildProxy(): SessionSideEffectPort {
            val runLifecyclePort = loadEnginePrivateInterface(
                simpleName = "SessionRunLifecyclePort",
            )
            val proxy = Proxy.newProxyInstance(
                ScriptOnlyAgentEngineProtocolFailFastTest::class.java.classLoader,
                arrayOf(SessionSideEffectPort::class.java, runLifecyclePort),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "prepareMessagesForAgent" -> emptyList<Message>()
                    "resolveSystemPrompt" -> args?.get(2) as String
                    "suspendForUserInput" -> {
                        suspendForUserInputCalls += 1
                        Unit
                    }

                    "saveToolExchange" -> {
                        saveToolExchangeCalls += 1
                        Unit
                    }

                    "resumeRun" -> {
                        resumeRunCalls += 1
                        Unit
                    }

                    "addUserMessage" -> {
                        addUserMessageCalls += 1
                        appendedContents += args?.get(1) as String
                        Unit
                    }

                    "toString" -> "SessionOrchestrationProbePort"
                    "hashCode" -> System.identityHashCode(proxyInstance)
                    "equals" -> proxyInstance === args?.firstOrNull()
                    else -> defaultPrimitiveProxyValue(method = method)
                }
            }
            return proxy as SessionSideEffectPort
        }

        private fun loadEnginePrivateInterface(simpleName: String): Class<*> {
            val qualifiedName = "${ScriptOnlyAgentEngine::class.java.name}\$$simpleName"
            return Class.forName(qualifiedName)
        }

        private fun defaultPrimitiveProxyValue(method: Method): Any? {
            return when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Character.TYPE -> 0.toChar()
                else -> null
            }
        }
    }

    private class FallbackCapturedException : RuntimeException()

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
