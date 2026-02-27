package io.github.stream29.kode.tools.scripting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KotlinScriptToolTest {
    @Test
    fun consumeOutputList_returnsItemsInAppendOrderAndClearsBuffer() {
        val scriptContext = DefaultScriptContext()

        scriptContext.sayToUser("first")
        scriptContext.sayToUser("second")

        assertEquals(expected = listOf("first", "second"), actual = scriptContext.consumeOutputList())
        assertTrue(scriptContext.consumeOutputList().isEmpty())
    }

    @Test
    fun consumeSignal_defaultsToFalseAndResetsAfterConsume() {
        val scriptContext = DefaultScriptContext()

        assertFalse(scriptContext.consumeAwaitForUserInputSignal())

        scriptContext.suspendForUserInput()
        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun suspendSignal_isIdempotentAcrossMultipleSetCalls() {
        val scriptContext = DefaultScriptContext()

        scriptContext.suspendForUserInput()
        scriptContext.suspendForUserInput()

        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun eval_returnsSuccessAndCapturesStdout() {
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = ExtendedScriptContext()

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
        val scriptContext = DefaultScriptContext()

        val result = scriptContext.eval("error(\"boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "boom")
    }

    @Test
    fun eval_returnsFailureForCompilationError() {
        val scriptContext = DefaultScriptContext()

        val result = scriptContext.eval("val broken =")

        assertIs<KotlinScriptResult.Failure>(result)
    }

    @Test
    fun eval_returnsUnitForUnitExpression() {
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = DefaultScriptContext()

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
        val scriptContext = DefaultScriptContext()

        val result = scriptContext.evalInThreadCancellable("error(\"thread-boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "thread-boom")
    }

    @Test
    fun execute_returnsSuccessForValidScript() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = DefaultScriptContext()
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
            val scriptContext = DefaultScriptContext()
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
            val scriptContext = DefaultScriptContext()
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
            val scriptContext = DefaultScriptContext()
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
            val scriptContext = DefaultScriptContext()
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
        val scriptContext = DefaultScriptContext()
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

    class ExtendedScriptContext : ScriptContext {
        private val delegate = DefaultScriptContext()

        override val systemPromptInjection: String = "Extended script context prompt injection"

        override fun sayToUser(message: String) {
            delegate.sayToUser(message)
        }

        override fun consumeOutputList(): List<String> {
            return delegate.consumeOutputList()
        }

        override fun suspendForUserInput() {
            delegate.suspendForUserInput()
        }

        override fun consumeAwaitForUserInputSignal(): Boolean {
            return delegate.consumeAwaitForUserInputSignal()
        }

        @Suppress("unused")
        fun greet(name: String): String {
            return "hello, $name"
        }
    }
}
