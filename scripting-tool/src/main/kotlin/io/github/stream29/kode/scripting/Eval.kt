package io.github.stream29.kode.scripting

import io.github.stream29.kode.scripting.EvalResult.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.mainKts.MainKtsScript
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.coroutines.resume
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.util.renderError

@Serializable
public sealed interface EvalResult {
    @Serializable
    public data class Success(
        val returnValue: String,
        val stdout: String,
    ) : EvalResult

    @Serializable
    public data class Failure(
        val message: String,
        val stdout: String,
    ) : EvalResult
}

public suspend fun eval(script: String): EvalResult = suspendCancellableCoroutine {
    val thread = Thread.startVirtualThread {
        it.resumeWith(runCatching{ evalInternal(script) })
    }
    it.invokeOnCancellation { thread.interrupt() }
}

internal fun evalInternal(script: String): EvalResult {
    val originalOut = System.out
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream, true, Charsets.UTF_8)

    try {
        System.setOut(printStream)
        val evaluationResult = host.evalWithTemplate<MainKtsScript>(
            script = script.toScriptSource(),
            evaluation = {
                constructorArgs(emptyArray<String>())
            }
        )
        printStream.flush()
        val stdout = outputStream.toString(Charsets.UTF_8)

        return when (evaluationResult) {
            is ResultWithDiagnostics.Success<EvaluationResult> ->
                when(val returnValue = evaluationResult.value.returnValue) {
                    is ResultValue.Value -> Success(returnValue.value.toString(), stdout)
                    is ResultValue.Error -> Failure(returnValue.renderError(), stdout)
                    is ResultValue.Unit -> Success("kotlin.Unit", stdout)
                    is ResultValue.NotEvaluated -> Failure("Script did not evaluate", stdout)
                }

            is ResultWithDiagnostics.Failure -> Failure(
                message = evaluationResult.reports.joinToString("\n"),
                stdout = stdout
            )
        }
    } finally {
        System.setOut(originalOut)
        printStream.close()
    }
}