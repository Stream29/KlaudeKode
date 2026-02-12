package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.scripting.EvalResult
import io.github.stream29.kode.scripting.eval
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.File

@Suppress("unused")
@LLMDescription("Execute Kotlin scripts with the embedded Kotlin scripting engine")
public class KotlinScriptTool public constructor(
    private val messageHandler: MessageHandler,
    private val workingDir: File = File("."),
    private val logger: (String) -> Unit = { println(it) },
) : ToolSet {

    @Tool
    @LLMDescription(
        "Execute Kotlin script code and return stdout plus return value. " +
            "Use this when shell is not appropriate and you need Kotlin-level scripting."
    )
    public suspend fun executeKotlinScript(
        @LLMDescription("Kotlin script source code")
        script: String,
        @LLMDescription("Timeout in seconds (1-300, default 30)")
        timeout: Int = 30,
    ): KotlinScriptResult = withContext(Dispatchers.IO) {
        if (script.isBlank()) {
            return@withContext KotlinScriptResult(
                success = false,
                returnValue = null,
                stdout = "",
                error = "Script cannot be empty",
                message = "Error: Script cannot be empty",
            )
        }

        val actualTimeout = timeout.coerceIn(1, MAX_TIMEOUT_SECONDS.toInt())
        logger("🧪 Executing Kotlin script")
        messageHandler.addMessageToUser("🧪 Executing Kotlin script")

        try {
            val evalResult = withTimeout(actualTimeout * 1000L) {
                EVAL_MUTEX.withLock {
                    eval(script = script, workingDir = workingDir.absolutePath)
                }
            }

            return@withContext when (evalResult) {
                is EvalResult.Success -> {
                    KotlinScriptResult(
                        success = true,
                        returnValue = truncate(evalResult.returnValue, MAX_RETURN_VALUE_CHARS),
                        stdout = truncate(evalResult.stdout, MAX_STDOUT_CHARS),
                        error = null,
                        message = "Script executed successfully",
                    )
                }

                is EvalResult.Failure -> {
                    KotlinScriptResult(
                        success = false,
                        returnValue = null,
                        stdout = truncate(evalResult.stdout, MAX_STDOUT_CHARS),
                        error = truncate(evalResult.message, MAX_ERROR_CHARS),
                        message = "Script execution failed",
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            KotlinScriptResult(
                success = false,
                returnValue = null,
                stdout = "",
                error = "Timed out after ${actualTimeout}s",
                message = "Error: Script execution timed out",
            )
        } catch (e: Exception) {
            KotlinScriptResult(
                success = false,
                returnValue = null,
                stdout = "",
                error = e.message ?: "Unknown error",
                message = "Error executing script: ${e.message}",
            )
        }
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }
        return value.take(maxLength) + "\n[Output truncated...]"
    }

    private companion object {
        const val MAX_TIMEOUT_SECONDS: Long = 300L
        const val MAX_STDOUT_CHARS: Int = 10000
        const val MAX_RETURN_VALUE_CHARS: Int = 4000
        const val MAX_ERROR_CHARS: Int = 4000
        val EVAL_MUTEX: Mutex = Mutex()
    }
}

@Serializable
public data class KotlinScriptResult(
    val success: Boolean,
    val returnValue: String?,
    val stdout: String,
    val error: String?,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine(message)
        returnValue?.let {
            appendLine("Return: $it")
        }
        if (stdout.isNotBlank()) {
            appendLine("--- stdout ---")
            appendLine(stdout)
        }
        error?.let {
            appendLine("--- error ---")
            appendLine(it)
        }
    }
}
