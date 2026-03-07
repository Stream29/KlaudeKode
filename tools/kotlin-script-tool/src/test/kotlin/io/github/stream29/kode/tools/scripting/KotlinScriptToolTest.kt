package io.github.stream29.kode.tools.scripting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class KotlinScriptToolTest {
    class TestScriptContext : ScriptContext {
        override val defaultImports: List<String> = emptyList()
        override val systemPromptInjection: String = "Extended script context prompt injection"

        private val outputLock: Any = Any()
        private val outputList: MutableList<String> = mutableListOf()
        private val awaitInput: AtomicBoolean = AtomicBoolean(false)

        fun sayToUser(message: String) {
            synchronized(outputLock) {
                outputList.add(message)
            }
        }

        fun consumeOutputList(): List<String> {
            synchronized(outputLock) {
                val snapshot = outputList.toList()
                outputList.clear()
                return snapshot
            }
        }

        fun suspendForUserInput() {
            awaitInput.set(true)
        }

        fun consumeAwaitForUserInputSignal(): Boolean {
            return awaitInput.getAndSet(false)
        }

        fun greet(name: String): String {
            return "hello, $name"
        }
    }

    @Test
    fun consumeOutputList_returnsItemsInAppendOrderAndClearsBuffer() {
        val scriptContext = TestScriptContext()

        scriptContext.sayToUser("first")
        scriptContext.sayToUser("second")

        assertEquals(expected = listOf("first", "second"), actual = scriptContext.consumeOutputList())
        assertTrue(scriptContext.consumeOutputList().isEmpty())

        val concurrentWriteStart = CountDownLatch(1)
        val firstWriteDone = CountDownLatch(1)
        val concurrentWriteDone = CountDownLatch(2)
        val concurrentWriteFailed = AtomicBoolean(false)

        val firstWriter = Thread(
            {
                if (!concurrentWriteStart.await(1, TimeUnit.SECONDS)) {
                    concurrentWriteFailed.set(true)
                    concurrentWriteDone.countDown()
                    return@Thread
                }
                scriptContext.sayToUser("thread-first")
                firstWriteDone.countDown()
                concurrentWriteDone.countDown()
            },
            "test-script-context-writer-1",
        )
        val secondWriter = Thread(
            {
                if (!concurrentWriteStart.await(1, TimeUnit.SECONDS)) {
                    concurrentWriteFailed.set(true)
                    concurrentWriteDone.countDown()
                    return@Thread
                }
                if (!firstWriteDone.await(1, TimeUnit.SECONDS)) {
                    concurrentWriteFailed.set(true)
                    concurrentWriteDone.countDown()
                    return@Thread
                }
                scriptContext.sayToUser("thread-second")
                concurrentWriteDone.countDown()
            },
            "test-script-context-writer-2",
        )
        firstWriter.isDaemon = true
        secondWriter.isDaemon = true
        firstWriter.start()
        secondWriter.start()

        concurrentWriteStart.countDown()
        assertTrue(concurrentWriteDone.await(2, TimeUnit.SECONDS))
        assertFalse(concurrentWriteFailed.get())
        assertEquals(expected = listOf("thread-first", "thread-second"), actual = scriptContext.consumeOutputList())
        assertTrue(scriptContext.consumeOutputList().isEmpty())
    }

    @Test
    fun consumeSignal_defaultsToFalseAndResetsAfterConsume() {
        val scriptContext = TestScriptContext()

        assertFalse(scriptContext.consumeAwaitForUserInputSignal())

        scriptContext.suspendForUserInput()
        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun suspendSignal_isIdempotentAcrossMultipleSetCalls() {
        val scriptContext = TestScriptContext()

        scriptContext.suspendForUserInput()
        scriptContext.suspendForUserInput()

        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun consumeSignal_isConsumeOnceAcrossConcurrentConsumers() {
        val scriptContext = TestScriptContext()
        scriptContext.suspendForUserInput()

        val consumeStart = CountDownLatch(1)
        val consumeDone = CountDownLatch(8)
        val consumeFailed = AtomicBoolean(false)
        val trueCount = AtomicInteger(0)

        repeat(8) {
            val consumer = Thread(
                {
                    if (!consumeStart.await(1, TimeUnit.SECONDS)) {
                        consumeFailed.set(true)
                        consumeDone.countDown()
                        return@Thread
                    }
                    if (scriptContext.consumeAwaitForUserInputSignal()) {
                        trueCount.incrementAndGet()
                    }
                    consumeDone.countDown()
                },
                "test-script-context-signal-consumer-$it",
            )
            consumer.isDaemon = true
            consumer.start()
        }

        consumeStart.countDown()
        assertTrue(consumeDone.await(2, TimeUnit.SECONDS))
        assertFalse(consumeFailed.get())
        assertEquals(1, trueCount.get())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun eval_returnsSuccessAndCapturesStdout() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                println("hello-script")
                40 + 2
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "42", actual = success.returnValue)
        assertContains(success.stdout, "hello-script")
    }

    @Test
    fun eval_canRaiseAwaitInputSignalThroughReceiver() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                suspendForUserInput()
                "awaited"
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "awaited", actual = success.returnValue)
        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun eval_canAppendUserVisibleOutputsThroughReceiver() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                sayToUser("hello")
                println("debug-only")
                sayToUser("world")
                "ok"
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "ok", actual = success.returnValue)
        assertContains(success.stdout, "debug-only")
        assertEquals(expected = listOf("hello", "world"), actual = scriptContext.consumeOutputList())
    }

    @Test
    fun eval_canCallMethodsDefinedOnConcreteScriptContextImplementation() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                greet(name = "kode")
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "hello, kode", actual = success.returnValue)
    }

    @Test
    fun eval_returnsFailureForScriptError() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval("error(\"boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "boom")
    }

    @Test
    fun eval_returnsFailureForCompilationError() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval("val broken =")

        assertIs<KotlinScriptResult.Failure>(result)
    }

    @Test
    fun eval_returnsUnitForUnitExpression() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                println("unit-check")
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "kotlin.Unit", actual = success.returnValue)
        assertContains(success.stdout, "unit-check")
    }

    @Test
    fun eval_capturesStdoutOnFailure() {
        val scriptContext = TestScriptContext()

        val result = scriptContext.eval(
            script =
                """
                println("before-boom")
                error("boom")
                """.trimIndent()
        )

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.stdout, "before-boom")
        assertContains(failure.message, "boom")
    }

    @Test
    fun evalInThreadCancellable_returnsSuccess() = runTest {
        val scriptContext = TestScriptContext()

        val result = scriptContext.evalInThreadCancellable(
            script =
                """
                println("thread-success")
                7 * 6
                """.trimIndent()
        )

        val success = assertIs<KotlinScriptResult.Success>(result)
        assertEquals(expected = "42", actual = success.returnValue)
        assertContains(success.stdout, "thread-success")
    }

    @Test
    fun evalInThreadCancellable_returnsFailureForScriptError() = runTest {
        val scriptContext = TestScriptContext()

        val result = scriptContext.evalInThreadCancellable("error(\"thread-boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "thread-boom")
    }

    @Test
    fun requestThreadCancellation_interruptsInterruptibleThread() {
        val started = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val worker = Thread(
            {
                started.countDown()
                try {
                    Thread.sleep(10_000L)
                } catch (_: InterruptedException) {
                    interrupted.set(true)
                }
            },
            "interruptible-worker",
        )
        worker.isDaemon = true
        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))

        requestThreadCancellation(targetThread = worker)
        worker.join(2_000L)

        assertTrue(interrupted.get())
        assertFalse(worker.isAlive)
    }

    @Test
    fun requestThreadCancellation_interruptsNonCooperativeThreadWithoutLeaking() {
        val started = CountDownLatch(1)
        val keepRunning = AtomicBoolean(true)
        val interruptedCount = AtomicInteger(0)
        val worker = Thread(
            {
                started.countDown()
                while (keepRunning.get()) {
                    try {
                        Thread.sleep(10_000L)
                    } catch (_: InterruptedException) {
                        interruptedCount.incrementAndGet()
                    }
                }
            },
            "non-cooperative-worker",
        )
        worker.isDaemon = true
        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))

        requestThreadCancellation(targetThread = worker)
        Thread.sleep(1_200L)
        assertTrue(interruptedCount.get() > 0)

        keepRunning.set(false)
        worker.interrupt()
        worker.join(2_000L)
        assertFalse(worker.isAlive)
    }

    @Test
    fun execute_returnsSuccessForValidScript() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = TestScriptContext()
            val tool = KotlinScriptTool(scriptContext = scriptContext)

            val result = tool.execute(
                args = KotlinScriptParams(
                    script = "1 + 2",
                    timeoutSeconds = 5,
                )
            )

            val success = assertIs<KotlinScriptResult.Success>(result)
            assertEquals(expected = "3", actual = success.returnValue)
        }
    }

    @Test
    fun execute_timesOutForLongRunningScript() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = TestScriptContext()
            val tool = KotlinScriptTool(scriptContext = scriptContext)

            assertFailsWith<TimeoutCancellationException> {
                tool.execute(
                    args = KotlinScriptParams(
                        script =
                            """
                        Thread.sleep(5000L)
                        "done"
                        """.trimIndent(),
                        timeoutSeconds = 1,
                    )
                )
            }

            assertFalse(scriptContext.consumeAwaitForUserInputSignal())
        }
    }

    @Test
    fun execute_returnsFailureForScriptError() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = TestScriptContext()
            val tool = KotlinScriptTool(scriptContext = scriptContext)

            val result = tool.execute(
                args = KotlinScriptParams(
                    script = "error(\"tool-boom\")",
                    timeoutSeconds = 5,
                )
            )

            val failure = assertIs<KotlinScriptResult.Failure>(result)
            assertContains(failure.message, "tool-boom")
        }
    }

    @Test
    fun execute_propagatesAwaitSignalToProvidedContext() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = TestScriptContext()
            val tool = KotlinScriptTool(scriptContext = scriptContext)

            val result = tool.execute(
                args = KotlinScriptParams(
                    script =
                        """
                    suspendForUserInput()
                    "done"
                    """.trimIndent(),
                    timeoutSeconds = 5,
                )
            )

            val success = assertIs<KotlinScriptResult.Success>(result)
            assertEquals(expected = "done", actual = success.returnValue)
            assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        }
    }

    @Test
    fun execute_propagatesUserVisibleOutputsToProvidedContext() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = TestScriptContext()
            val tool = KotlinScriptTool(scriptContext = scriptContext)

            val result = tool.execute(
                args = KotlinScriptParams(
                    script =
                        """
                    sayToUser("hello-user")
                    println("debug-only")
                    "done"
                    """.trimIndent(),
                    timeoutSeconds = 5,
                )
            )

            val success = assertIs<KotlinScriptResult.Success>(result)
            assertEquals(expected = "done", actual = success.returnValue)
            assertContains(success.stdout, "debug-only")
            assertEquals(expected = listOf("hello-user"), actual = scriptContext.consumeOutputList())
        }
    }

    @Test
    fun execute_zeroTimeoutSeconds_timesOutImmediately() = runTest {
        val scriptContext = TestScriptContext()
        val tool = KotlinScriptTool(scriptContext = scriptContext)

        assertFailsWith<TimeoutCancellationException> {
            tool.execute(
                args = KotlinScriptParams(
                    script = "1 + 1",
                    timeoutSeconds = 0,
                )
            )
        }
    }

}
