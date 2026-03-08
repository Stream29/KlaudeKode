package io.github.stream29.kode.core.agent

import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import ai.koog.prompt.message.Message
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class FailOnDirectInputMessageHandler : FakeMessageHandler() {
    override suspend fun requestInput(): String {
        error("requestInput must be orchestrated outside ScriptOnlyAgentEngine")
    }
}

internal class RuntimeOrchestrationProbe(
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
        val qualifiedName = "${ScriptOnlyAgentEngine::class.java.name}$$simpleName"
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

internal class SessionOrchestrationProbe {
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
        val qualifiedName = "${ScriptOnlyAgentEngine::class.java.name}$$simpleName"
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

internal class FallbackCapturedException : RuntimeException()

internal object ScriptContextDeterminismVerifier {
    fun verify(context: MainAgentScriptContext) {
        context.sayToUser("first")
        context.sayToUser("second")
        context.suspendForUserInput()

        kotlin.test.assertEquals(listOf("first", "second"), context.consumeOutputList())
        kotlin.test.assertTrue(context.consumeAwaitForUserInputSignal())

        kotlin.test.assertEquals(emptyList(), context.consumeOutputList())
        kotlin.test.assertFalse(context.consumeAwaitForUserInputSignal())

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
        kotlin.test.assertTrue(outputConsumeDone.await(2, TimeUnit.SECONDS))
        kotlin.test.assertFalse(outputConsumeFailed.get())
        kotlin.test.assertEquals(listOf(0, 1), outputConsumeSizes.sorted())

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
        kotlin.test.assertTrue(signalConsumeDone.await(2, TimeUnit.SECONDS))
        kotlin.test.assertFalse(signalConsumeFailed.get())
        kotlin.test.assertEquals(1, consumedSignalCount.get())
        kotlin.test.assertFalse(context.consumeAwaitForUserInputSignal())
    }
}
