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
    fun consumeSignal_defaultsToFalseAndResetsAfterConsume() {
        val scriptContext = ScriptContext()

        assertFalse(scriptContext.consumeAwaitForUserInputSignal())

        scriptContext.suspendForUserInput()
        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun suspendSignal_isIdempotentAcrossMultipleSetCalls() {
        val scriptContext = ScriptContext()

        scriptContext.suspendForUserInput()
        scriptContext.suspendForUserInput()

        assertTrue(scriptContext.consumeAwaitForUserInputSignal())
        assertFalse(scriptContext.consumeAwaitForUserInputSignal())
    }

    @Test
    fun eval_returnsSuccessAndCapturesStdout() {
        val scriptContext = ScriptContext()

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
        val scriptContext = ScriptContext()

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
    fun eval_returnsFailureForScriptError() {
        val scriptContext = ScriptContext()

        val result = scriptContext.eval("error(\"boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "boom")
    }

    @Test
    fun eval_returnsFailureForCompilationError() {
        val scriptContext = ScriptContext()

        val result = scriptContext.eval("val broken =")

        assertIs<KotlinScriptResult.Failure>(result)
    }

    @Test
    fun eval_returnsUnitForUnitExpression() {
        val scriptContext = ScriptContext()

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
        val scriptContext = ScriptContext()

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
        val scriptContext = ScriptContext()

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
        val scriptContext = ScriptContext()

        val result = scriptContext.evalInThreadCancellable("error(\"thread-boom\")")

        val failure = assertIs<KotlinScriptResult.Failure>(result)
        assertContains(failure.message, "thread-boom")
    }

    @Test
    fun execute_returnsSuccessForValidScript() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val scriptContext = ScriptContext()
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
            val scriptContext = ScriptContext()
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
            val scriptContext = ScriptContext()
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
            val scriptContext = ScriptContext()
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
    fun execute_zeroTimeoutSeconds_timesOutImmediately() = runTest {
        val scriptContext = ScriptContext()
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
