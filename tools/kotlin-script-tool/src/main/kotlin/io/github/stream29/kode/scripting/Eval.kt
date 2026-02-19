package io.github.stream29.kode.scripting

import io.github.stream29.kode.scripting.EvalResult.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.mainKts.MainKtsScript
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.util.renderError
import kotlin.system.exitProcess

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

public suspend fun eval(script: String): EvalResult = eval(script = script, workingDir = null)

public suspend fun eval(script: String, workingDir: String?): EvalResult = runInterruptible(Dispatchers.IO) {
    runEvalInSubprocessBlocking(script = script, workingDir = workingDir)
}

private fun runEvalInSubprocessBlocking(script: String, workingDir: String?): EvalResult {
    val tempDir = Files.createTempDirectory("kode-script-eval-")
    val scriptFile = tempDir.resolve("script.main.kts")
    val resultFile = tempDir.resolve("result.json")
    val outputFile = tempDir.resolve("process.log")

    return try {
        Files.writeString(scriptFile, script, StandardCharsets.UTF_8)
        val javaCommand = resolveJavaCommand()
        val classPath = System.getProperty("java.class.path").orEmpty()
        if (classPath.isBlank()) {
            return Failure(message = "Missing java.class.path for script subprocess", stdout = "")
        }

        val processBuilder = ProcessBuilder(
            javaCommand,
            "-cp",
            classPath,
            EvalSubprocessMain::class.java.name,
            scriptFile.toAbsolutePath().toString(),
            resultFile.toAbsolutePath().toString(),
        )

        val resolvedWorkDir = workingDir?.trim().orEmpty().ifBlank { null }
        if (resolvedWorkDir != null) {
            processBuilder.directory(File(resolvedWorkDir))
        }
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(outputFile.toFile())

        val process = processBuilder.start()
        try {
            process.waitFor()
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw e
        }

        val output = runCatching {
            Files.readString(outputFile, StandardCharsets.UTF_8)
        }.getOrDefault("")

        if (process.exitValue() != 0) {
            return Failure(
                message = "Script subprocess failed with exit code ${process.exitValue()}",
                stdout = output,
            )
        }

        if (!Files.exists(resultFile)) {
            return Failure(
                message = "Script subprocess finished without result payload",
                stdout = output,
            )
        }

        val payload = Files.readString(resultFile, StandardCharsets.UTF_8)
        return runCatching {
            EVAL_JSON.decodeFromString(EvalResult.serializer(), payload)
        }.getOrElse { decodeError ->
            Failure(
                message = "Failed to decode script result: ${decodeError.message}",
                stdout = output,
            )
        }
    } catch (e: Exception) {
        Failure(message = e.message ?: "Script subprocess error", stdout = "")
    } finally {
        runCatching { Files.deleteIfExists(scriptFile) }
        runCatching { Files.deleteIfExists(resultFile) }
        runCatching { Files.deleteIfExists(outputFile) }
        runCatching { Files.deleteIfExists(tempDir) }
    }
}

private fun resolveJavaCommand(): String {
    val currentCommand = runCatching {
        ProcessHandle.current().info().command().orElse(null)
    }.getOrNull()
    if (!currentCommand.isNullOrBlank()) {
        return currentCommand
    }
    return File(System.getProperty("java.home"), "bin/java").absolutePath
}

internal fun evalInternal(script: String): EvalResult {
    val originalOut = System.out
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream, true, Charsets.UTF_8)

    try {
        System.setOut(printStream)
        val evaluationResult = host.evalWithTemplate<MainKtsScript>(
            script = script.toScriptSource(),
            evaluation = { constructorArgs(emptyArray<String>()) },
        )
        printStream.flush()
        val stdout = outputStream.toString(Charsets.UTF_8)

        return when (evaluationResult) {
            is ResultWithDiagnostics.Success<EvaluationResult> ->
                when (val returnValue = evaluationResult.value.returnValue) {
                    is ResultValue.Value -> Success(returnValue.value.toString(), stdout)
                    is ResultValue.Error -> Failure(returnValue.renderError(), stdout)
                    is ResultValue.Unit -> Success("kotlin.Unit", stdout)
                    is ResultValue.NotEvaluated -> Failure("Script did not evaluate", stdout)
                }

            is ResultWithDiagnostics.Failure -> Failure(
                message = evaluationResult.reports.joinToString("\n"),
                stdout = stdout,
            )
        }
    } finally {
        System.setOut(originalOut)
        printStream.close()
    }
}

internal object EvalSubprocessMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 2) {
            System.err.println("Usage: EvalSubprocessMain <scriptFile> <resultFile>")
            exitProcess(2)
        }

        val scriptPath = Path.of(args[0])
        val resultPath = Path.of(args[1])
        val script = Files.readString(scriptPath, StandardCharsets.UTF_8)
        val result = runCatching {
            evalInternal(script)
        }.getOrElse { error ->
            Failure(message = error.message ?: "Script execution failed", stdout = "")
        }

        val parent = resultPath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val payload = EVAL_JSON.encodeToString(EvalResult.serializer(), result)
        Files.writeString(resultPath, payload, StandardCharsets.UTF_8)
    }
}

private val EVAL_JSON: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
