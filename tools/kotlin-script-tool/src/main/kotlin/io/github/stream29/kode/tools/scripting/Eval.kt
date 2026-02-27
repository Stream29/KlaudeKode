package io.github.stream29.kode.tools.scripting

import io.github.stream29.kode.tools.scripting.KotlinScriptResult.Failure
import io.github.stream29.kode.tools.scripting.KotlinScriptResult.Success
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.kotlin.mainKts.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.concurrent.withLock
import kotlin.reflect.full.starProjectedType
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.renderError


public suspend fun ScriptContext.evalInThreadCancellable(script: String): KotlinScriptResult {
    return suspendCancellableCoroutine { cont ->
        val thread = Thread {
            cont.resumeWith(runCatching { eval(script) })
        }
        cont.invokeOnCancellation {
            thread.interrupt()
        }
        thread.start()
    }
}

public fun ScriptContext.eval(script: String): KotlinScriptResult {
    val receiverClass = this::class
    return scriptEvaluationMutex.withLock {
        captureStdout {
            host.evalWithTemplate<MainKtsScript>(
                script = script.toScriptSource(),
                compilation = {
                    defaultImports(
                        receiverClass,
                        DependsOn::class,
                        Repository::class,
                        Import::class,
                        CompilerOptions::class,
                        ScriptFileLocation::class,
                    )
                    jvm {
                        dependenciesFromClassContext(
                            MainKtsScriptDefinition::class,
                            "kotlin-main-kts",
                            "kotlin-stdlib",
                            "kotlin-reflect",
                            wholeClasspath = true,
                        )
                    }
                    refineConfiguration {
                        onAnnotations(
                            DependsOn::class,
                            Repository::class,
                            Import::class,
                            CompilerOptions::class,
                            handler = MainKtsConfigurator()
                        )
                        onAnnotations(ScriptFileLocation::class, handler = ScriptFileLocationCustomConfigurator())
                    }
                    implicitReceivers(receiverClass.starProjectedType)
                },
                evaluation = {
                    constructorArgs(emptyArray<String>())
                    implicitReceivers(this@eval)
                    scriptsInstancesSharing(true)
                },
            )
        }.toEvalResult()
    }
}

private inline fun <T> captureStdout(block: () -> T): WithStdout<T> {
    val originalOut = System.out
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream, true, Charsets.UTF_8)
    try {
        System.setOut(printStream)
        val result = block()
        printStream.flush()
        val stdout = outputStream.toString(Charsets.UTF_8)
        return WithStdout(result, stdout)

    } finally {
        System.setOut(originalOut)
        printStream.close()
    }
}

private data class WithStdout<T>(val value: T, val stdout: String)

private fun WithStdout<ResultWithDiagnostics<EvaluationResult>>.toEvalResult(): KotlinScriptResult {
    val (evaluationResult, stdout) = this
    return when (evaluationResult) {
        is ResultWithDiagnostics.Success<EvaluationResult> -> {
            when (val returnValue = evaluationResult.value.returnValue) {
                is ResultValue.Value -> Success(returnValue.value.toString(), stdout)
                is ResultValue.Error -> Failure(returnValue.renderError(), stdout)
                is ResultValue.Unit -> Success("kotlin.Unit", stdout)
                is ResultValue.NotEvaluated -> Failure("Script did not evaluate", stdout)
            }
        }

        is ResultWithDiagnostics.Failure -> {
            Failure(
                message = evaluationResult.reports.joinToString("\n").ifBlank { "Script evaluation failed" },
                stdout = stdout,
            )
        }
    }
}
