package io.github.stream29.kode.scripting

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.mainKts.MainKtsScript
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.coroutines.resume
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.toScriptSource

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
        it.resume(evalInternal(script))
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
            is ResultWithDiagnostics.Success<*> -> EvalResult.Success(
                returnValue = evaluationResult.value.toString(),
                stdout = stdout
            )

            is ResultWithDiagnostics.Failure -> EvalResult.Failure(
                message = evaluationResult.reports.joinToString("\n"),
                stdout = stdout
            )
        }
    } finally {
        System.setOut(originalOut)
        printStream.close()
    }
}