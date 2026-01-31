package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shell tool for executing bash commands.
 * Based on kimi-cli's shell tool implementation.
 */
@Suppress("unused")
@LLMDescription("Execute bash/shell commands in the terminal")
public class ShellTool public constructor(
    private val messageHandler: MessageHandler,
    private val workingDir: File = File("."),
    private val logger: (String) -> Unit = { println(it) }
) : ToolSet {

    public companion object {
        private const val MAX_TIMEOUT_SECONDS = 5 * 60L // 5 minutes max
    }

    @Tool
    @LLMDescription(
        "Execute a bash command in the shell. " +
        "Use this to run build commands, execute scripts, or perform system operations. " +
        "Commands run with a default timeout of 60 seconds."
    )
    public suspend fun executeShellCommand(
        @LLMDescription("The bash command to execute")
        command: String,
        @LLMDescription("Timeout in seconds (1-300, default 60)")
        timeout: Int = 60
    ): ShellResult = withContext(Dispatchers.IO) {
        if (command.isBlank()) {
            return@withContext ShellResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Command cannot be empty",
                message = "Error: Command cannot be empty"
            )
        }

        // Validate timeout
        val actualTimeout = timeout.coerceIn(1, MAX_TIMEOUT_SECONDS.toInt())

        // Log command execution
        logger("🔧 Executing shell command: $command")
        messageHandler.addMessageToUser("🔧 Executing: `$command`")

        try {
            val processBuilder = ProcessBuilder("/bin/bash", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(false)

            val process = processBuilder.start()

            // Read stdout and stderr concurrently
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }

            // Wait for process with timeout
            val finished = process.waitFor(actualTimeout.toLong(), TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                logger("⏱️ Command timed out after ${actualTimeout}s")
                return@withContext ShellResult(
                    success = false,
                    exitCode = -1,
                    stdout = stdout.take(5000),
                    stderr = stderr.take(5000),
                    message = "Error: Command timed out after ${actualTimeout} seconds"
                )
            }

            val exitCode = process.exitValue()
            val success = exitCode == 0

            val truncatedStdout = if (stdout.length > 10000) stdout.take(10000) + "\n[Output truncated...]" else stdout
            val truncatedStderr = if (stderr.length > 5000) stderr.take(5000) + "\n[Error output truncated...]" else stderr

            logger("✅ Command completed with exit code: $exitCode")

            ShellResult(
                success = success,
                exitCode = exitCode,
                stdout = truncatedStdout,
                stderr = truncatedStderr,
                message = if (success) "Command executed successfully" else "Command failed with exit code: $exitCode"
            )
        } catch (e: Exception) {
            logger("❌ Command execution failed: ${e.message}")
            ShellResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown error",
                message = "Error executing command: ${e.message}"
            )
        }
    }

    @Tool
    @LLMDescription("Execute a Gradle command using the wrapper")
    public suspend fun runGradleCommand(
        @LLMDescription("The Gradle arguments (e.g., 'build', 'test', 'run')")
        args: String
    ): ShellResult {
        val gradleCmd = if (System.getProperty("os.name").lowercase().contains("win")) {
            "./gradlew.bat"
        } else {
            "./gradlew"
        }
        return executeShellCommand("$gradleCmd $args", timeout = 300)
    }
}

/**
 * Result of a shell command execution
 */
@Serializable
public data class ShellResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val message: String
) {
    override fun toString(): String = buildString {
        appendLine(message)
        appendLine("Exit code: $exitCode")
        if (stdout.isNotBlank()) {
            appendLine("--- stdout ---")
            appendLine(stdout)
        }
        if (stderr.isNotBlank()) {
            appendLine("--- stderr ---")
            appendLine(stderr)
        }
    }
}
