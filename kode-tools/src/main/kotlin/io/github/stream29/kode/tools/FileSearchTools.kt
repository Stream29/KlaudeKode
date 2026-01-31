package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * File search tools for glob and grep operations.
 * Based on kimi-cli's file tools.
 */
@Suppress("unused")
@LLMDescription("Search and find files using glob patterns and content search")
public class FileSearchTools public constructor(
    private val messageHandler: MessageHandler,
    private val workingDir: File = File("."),
    private val logger: (String) -> Unit = { println(it) }
) : ToolSet {

    public companion object {
        private const val MAX_MATCHES = 1000
        private const val MAX_LINE_LENGTH = 500
    }

    @Tool
    @LLMDescription(
        "Find files matching a glob pattern. " +
        "Glob patterns use wildcards like * (match any characters) and ** (match any directories). " +
        "Examples: '*.kt' (all Kotlin files), 'src/**/*.java' (Java files in src), '**/*.md' (all markdown files). " +
        "Returns a list of matching file paths."
    )
    public fun globFiles(
        @LLMDescription("Glob pattern to match files (e.g., '*.kt', 'src/**/*.java')")
        pattern: String,
        @LLMDescription("Directory to search in (defaults to working directory)")
        directory: String? = null,
        @LLMDescription("Whether to include directories in results (default false)")
        includeDirs: Boolean = false
    ): GlobResult {
        val searchDir = directory?.let { File(it) } ?: workingDir
        
        if (!searchDir.exists()) {
            return GlobResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = "Directory does not exist: ${searchDir.absolutePath}"
            )
        }

        if (!searchDir.isDirectory) {
            return GlobResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = "Not a directory: ${searchDir.absolutePath}"
            )
        }

        // Validate pattern safety - disallow ** at the start without directory context
        if (pattern.startsWith("**") && directory == null) {
            val lsResult = searchDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
            return GlobResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = buildString {
                    appendLine("Pattern `$pattern` starts with '**' which is not allowed without a specific directory.")
                    appendLine("This would recursively search all directories and may include large directories like `node_modules`.")
                    appendLine("Use a more specific pattern or provide a directory.")
                    appendLine()
                    appendLine("Contents of ${searchDir.absolutePath}:")
                    lsResult.forEach { appendLine("  $it") }
                }
            )
        }

        logger("🔍 Glob search: '$pattern' in ${searchDir.absolutePath}")

        try {
            val matches = mutableListOf<String>()
            val matcher = createGlobMatcher(pattern)
            
            searchDir.walkTopDown().onEnter { dir ->
                // Skip hidden directories and common non-source directories
                val name = dir.name
                !name.startsWith(".") && name !in setOf(
                    "node_modules", "build", ".gradle", "out", 
                    "dist", "target", ".git", ".idea"
                )
            }.forEach { file ->
                if (matches.size >= MAX_MATCHES) return@forEach
                
                val relativePath = file.relativeTo(searchDir).path
                if (matcher.matches(Paths.get(relativePath))) {
                    if (includeDirs || file.isFile) {
                        matches.add(relativePath)
                    }
                }
            }

            matches.sort()

            val message = if (matches.isEmpty()) {
                "No matches found for pattern '$pattern'"
            } else {
                val truncatedMsg = if (matches.size >= MAX_MATCHES) {
                    " (showing first $MAX_MATCHES results)"
                } else ""
                "Found ${matches.size} matches for pattern '$pattern'$truncatedMsg"
            }

            logger("✅ $message")

            return GlobResult(
                success = true,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = matches,
                message = message
            )

        } catch (e: Exception) {
            logger("❌ Glob search failed: ${e.message}")
            return GlobResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = "Error searching for pattern: ${e.message}"
            )
        }
    }

    @Tool
    @LLMDescription(
        "Search for text patterns in files using regular expressions. " +
        "Searches file contents and returns matching lines with file paths and line numbers. " +
        "Similar to the 'grep' command. " +
        "Examples: search for 'class Main', find imports 'import ai.koog', find TODO comments."
    )
    public fun grepFiles(
        @LLMDescription("Regular expression pattern to search for")
        pattern: String,
        @LLMDescription("Glob pattern for files to search (e.g., '*.kt', '*.java', defaults to all files)")
        filePattern: String = "*",
        @LLMDescription("Directory to search in (defaults to working directory)")
        directory: String? = null,
        @LLMDescription("Maximum number of matches to return (1-100, default 50)")
        maxResults: Int = 50
    ): GrepResult {
        val searchDir = directory?.let { File(it) } ?: workingDir
        val actualMaxResults = maxResults.coerceIn(1, 100)

        if (!searchDir.exists() || !searchDir.isDirectory) {
            return GrepResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = "Invalid directory: ${searchDir.absolutePath}"
            )
        }

        logger("🔎 Grep search: '$pattern' in files matching '$filePattern'")
        messageHandler.addMessageToUser("🔎 Searching for: $pattern")

        try {
            val regex = Regex(pattern, RegexOption.MULTILINE)
            val globMatcher = createGlobMatcher(filePattern)
            val matches = mutableListOf<GrepMatch>()

            searchDir.walkTopDown().onEnter { dir ->
                val name = dir.name
                !name.startsWith(".") && name !in setOf(
                    "node_modules", "build", ".gradle", "out",
                    "dist", "target", ".git"
                )
            }.filter { file ->
                file.isFile && globMatcher.matches(Paths.get(file.relativeTo(searchDir).path))
            }.take(1000) // Limit files to search
            .forEach { file ->
                if (matches.size >= actualMaxResults) return@forEach

                try {
                    val content = file.readText()
                    val lines = content.lines()

                    lines.forEachIndexed { index, line ->
                        if (matches.size >= actualMaxResults) return@forEachIndexed

                        if (regex.containsMatchIn(line)) {
                            val lineNum = index + 1
                            val truncatedLine = if (line.length > MAX_LINE_LENGTH) {
                                line.take(MAX_LINE_LENGTH) + "..."
                            } else line

                            matches.add(GrepMatch(
                                file = file.relativeTo(searchDir).path,
                                line = lineNum,
                                content = truncatedLine.trim()
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read as text
                    logger("⚠️ Skipped ${file.name}: ${e.message}")
                }
            }

            val message = if (matches.isEmpty()) {
                "No matches found for pattern '$pattern'"
            } else {
                "Found ${matches.size} matches for pattern '$pattern'"
            }

            logger("✅ $message")

            return GrepResult(
                success = true,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = matches,
                message = message
            )

        } catch (e: Exception) {
            logger("❌ Grep search failed: ${e.message}")
            return GrepResult(
                success = false,
                pattern = pattern,
                directory = searchDir.absolutePath,
                matches = emptyList(),
                message = "Error searching files: ${e.message}"
            )
        }
    }

    @Tool
    @LLMDescription(
        "Find files by name (case-insensitive). " +
        "Simpler alternative to glob when looking for specific filenames."
    )
    public fun findFilesByName(
        @LLMDescription("Name or partial name to search for")
        name: String,
        @LLMDescription("Directory to search in (defaults to working directory)")
        directory: String? = null
    ): GlobResult {
        return globFiles(
            pattern = "**/*${name}*",
            directory = directory,
            includeDirs = false
        )
    }

    /**
     * Create a glob PathMatcher from a pattern
     */
    private fun createGlobMatcher(pattern: String): PathMatcher {
        val globPattern = if (pattern.startsWith("glob:")) pattern else "glob:$pattern"
        return FileSystems.getDefault().getPathMatcher(globPattern)
    }
}

/**
 * A single grep match
 */
@Serializable
public data class GrepMatch(
    val file: String,
    val line: Int,
    val content: String
)

/**
 * Result of a glob search
 */
@Serializable
public data class GlobResult(
    val success: Boolean,
    val pattern: String,
    val directory: String,
    val matches: List<String>,
    val message: String
) {
    override fun toString(): String = buildString {
        appendLine(message)
        appendLine("Pattern: $pattern")
        appendLine("Directory: $directory")
        if (matches.isNotEmpty()) {
            appendLine()
            appendLine("Matches:")
            matches.forEach { appendLine("  $it") }
        }
    }
}

/**
 * Result of a grep search
 */
@Serializable
public data class GrepResult(
    val success: Boolean,
    val pattern: String,
    val directory: String,
    val matches: List<GrepMatch>,
    val message: String
) {
    override fun toString(): String = buildString {
        appendLine(message)
        appendLine("Pattern: $pattern")
        appendLine("Directory: $directory")
        if (matches.isNotEmpty()) {
            appendLine()
            appendLine("Matches:")
            matches.forEach { match ->
                appendLine("${match.file}:${match.line}: ${match.content}")
            }
        }
    }
}
