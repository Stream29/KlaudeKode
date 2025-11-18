# Implementation Kickoff: Koog Code Agent

> **Status**: Ready to Implement 🚀
>
> **Start Date**: 2025-11-18
>
> **Goal**: Build MVP in 3 months

## Decision Summary

Based on review of [issues-and-uncertainties.md](./issues-and-uncertainties.md), we've decided:

### ✅ Accepted Risks

1. **Performance**: Cold start acceptable with parallel tool execution
2. **Security**: No strict sandboxing - trust controlled environment
3. **Context Management**: Follow Claude Code's approach
4. **LLM Reliability**: Learn from experience, iterate

### 🎯 Philosophy

**"Learn from Claude Code, build with Kotlin"**

- When uncertain → Look at how Claude Code does it
- Keep it simple → Don't over-engineer
- Ship fast → Iterate based on feedback
- Leverage Koog → Use framework strengths

## Phase 1: Foundation (Weeks 1-8)

### Week 1-2: Project Setup & Core Tools

#### Project Structure

```
koog-code-agent/
├── app/
│   └── src/main/kotlin/
│       └── io/github/stream29/koogagent/
│           ├── App.kt                  # Main entry point
│           └── agent/
│               └── CodingAgent.kt      # Agent configuration
├── tools/
│   └── src/main/kotlin/
│       └── io/github/stream29/koogagent/tools/
│           ├── file/
│           │   ├── ReadTool.kt
│           │   ├── WriteTool.kt
│           │   └── EditTool.kt
│           ├── script/
│           │   └── KotlinScriptTool.kt
│           └── search/
│               ├── GlobTool.kt
│               └── GrepTool.kt
├── core/
│   └── src/main/kotlin/
│       └── io/github/stream29/koogagent/core/
│           ├── safety/
│           │   └── PathValidator.kt
│           └── context/
│               └── FileCache.kt
├── buildSrc/                          # Convention plugins
├── gradle/
│   └── libs.versions.toml             # Dependencies
└── settings.gradle.kts
```

#### Dependencies to Add

```toml
[versions]
koog = "0.5.2"
kotlin = "2.2.20"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.8.1"
ktor = "3.0.0"
kotlin-scripting = "2.2.20"
ivy = "2.5.3"

[libraries]
# Koog framework
koog-agents = { module = "ai.koog:koog-agents", version.ref = "koog" }

# Kotlin scripting
kotlin-scripting-jvm-host = { module = "org.jetbrains.kotlin:kotlin-scripting-jvm-host", version.ref = "kotlin-scripting" }
kotlin-main-kts = { module = "org.jetbrains.kotlin:kotlin-main-kts", version.ref = "kotlin-scripting" }
apache-ivy = { module = "org.apache.ivy:ivy", version.ref = "ivy" }

# Ktor client (required by Koog)
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }

# Testing
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

[bundles]
kotlin-scripting = ["kotlin-scripting-jvm-host", "kotlin-main-kts", "apache-ivy"]
testing = ["kotlin-test", "kotlinx-coroutines-test"]
```

### Week 1: Tasks

#### Day 1-2: Project Setup ✅

```bash
# Initialize project structure
cd /home/admin/ACodeSpace/push/KlaudeKode
mkdir -p {tools,core}/src/{main,test}/kotlin
mkdir -p app/src/main/kotlin/io/github/stream29/koogagent

# Update gradle configuration
# Add Koog dependencies
# Set up multi-module structure
```

#### Day 3-4: ReadTool Implementation

**Goal**: Read files with line numbers, just like Claude Code's Read tool

```kotlin
// tools/src/main/kotlin/io/github/stream29/koogagent/tools/file/ReadTool.kt
package io.github.stream29.koogagent.tools.file

import ai.koog.agents.core.tool.Tool
import ai.koog.agents.core.agent.AIAgentEnvironment
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ReadArgs(
    val filePath: String,
    val offset: Int? = null,
    val limit: Int? = null
)

@Serializable
data class ReadResult(
    val content: String,
    val totalLines: Int,
    val linesShown: Int,
    val truncated: Boolean
)

object ReadTool : Tool<ReadArgs, ReadResult> {
    override val name = "read_file"
    override val description = """
        Read a file from the filesystem with line numbers.
        Use offset and limit to read specific sections of large files.
    """.trimIndent()

    override suspend fun execute(
        args: ReadArgs,
        env: AIAgentEnvironment
    ): ReadResult {
        val file = File(args.filePath)

        if (!file.exists()) {
            throw IllegalArgumentException("File not found: ${args.filePath}")
        }

        if (!file.isFile) {
            throw IllegalArgumentException("Not a file: ${args.filePath}")
        }

        val lines = file.readLines()
        val totalLines = lines.size

        val offset = args.offset ?: 0
        val limit = args.limit ?: 2000 // Default limit like Claude Code

        val selectedLines = lines
            .drop(offset)
            .take(limit)

        val content = selectedLines
            .mapIndexed { index, line ->
                val lineNumber = offset + index + 1
                String.format("%6d→%s", lineNumber, line)
            }
            .joinToString("\n")

        return ReadResult(
            content = content,
            totalLines = totalLines,
            linesShown = selectedLines.size,
            truncated = (offset + selectedLines.size) < totalLines
        )
    }
}
```

**Tests**:
```kotlin
// tools/src/test/kotlin/io/github/stream29/koogagent/tools/file/ReadToolTest.kt
class ReadToolTest {
    @Test
    fun `read simple file`() = runBlocking {
        val tempFile = createTempFile().apply {
            writeText("Line 1\nLine 2\nLine 3")
        }

        val result = ReadTool.execute(
            ReadArgs(tempFile.absolutePath),
            mockEnv
        )

        assertEquals(3, result.totalLines)
        assertTrue(result.content.contains("     1→Line 1"))
        assertTrue(result.content.contains("     2→Line 2"))
    }

    @Test
    fun `read with offset and limit`() = runBlocking {
        // Test reading specific range
    }

    @Test
    fun `handle large files`() = runBlocking {
        // Test truncation
    }
}
```

#### Day 5-7: EditTool Implementation

**Goal**: Exact string replacement like Claude Code

```kotlin
// tools/src/main/kotlin/io/github/stream29/koogagent/tools/file/EditTool.kt
package io.github.stream29.koogagent.tools.file

import ai.koog.agents.core.tool.Tool
import ai.koog.agents.core.agent.AIAgentEnvironment
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class EditArgs(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

@Serializable
data class EditResult(
    val success: Boolean,
    val occurrencesReplaced: Int,
    val message: String
)

object EditTool : Tool<EditArgs, EditResult> {
    override val name = "edit_file"
    override val description = """
        Edit a file by replacing exact string matches.
        Fails if the old_string is not found or if multiple matches exist (unless replaceAll is true).
    """.trimIndent()

    override suspend fun execute(
        args: EditArgs,
        env: AIAgentEnvironment
    ): EditResult {
        val file = File(args.filePath)

        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found: ${args.filePath}")
        }

        val content = file.readText()

        // Find all occurrences
        val occurrences = countOccurrences(content, args.oldString)

        when {
            occurrences == 0 -> {
                return EditResult(
                    success = false,
                    occurrencesReplaced = 0,
                    message = "String not found in file"
                )
            }
            occurrences > 1 && !args.replaceAll -> {
                return EditResult(
                    success = false,
                    occurrencesReplaced = 0,
                    message = "Found $occurrences matches. Use replaceAll=true or provide more context."
                )
            }
            else -> {
                val newContent = content.replace(args.oldString, args.newString)
                file.writeText(newContent)

                return EditResult(
                    success = true,
                    occurrencesReplaced = occurrences,
                    message = "Successfully replaced $occurrences occurrence(s)"
                )
            }
        }
    }

    private fun countOccurrences(text: String, pattern: String): Int {
        var count = 0
        var index = 0

        while (true) {
            index = text.indexOf(pattern, index)
            if (index == -1) break
            count++
            index += pattern.length
        }

        return count
    }
}
```

### Week 2: Script & Search Tools

#### Day 1-3: KotlinScriptTool

```kotlin
// tools/src/main/kotlin/io/github/stream29/koogagent/tools/script/KotlinScriptTool.kt
package io.github.stream29.koogagent.tools.script

import ai.koog.agents.core.tool.Tool
import ai.koog.agents.core.agent.AIAgentEnvironment
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.mainKts.MainKtsScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import java.io.File

@Serializable
data class ScriptArgs(
    val scriptContent: String? = null,
    val scriptPath: String? = null,
    val description: String? = null
)

@Serializable
data class ScriptResult(
    val success: Boolean,
    val returnValue: String? = null,
    val error: String? = null,
    val executionTimeMs: Long
)

object KotlinScriptTool : Tool<ScriptArgs, ScriptResult> {
    override val name = "kotlin_script"
    override val description = """
        Execute Kotlin scripts (.kts) for complex operations.
        Scripts have access to Java stdlib and can declare dependencies with @file:DependsOn.
    """.trimIndent()

    private val scriptHost = BasicJvmScriptingHost()

    override suspend fun execute(
        args: ScriptArgs,
        env: AIAgentEnvironment
    ): ScriptResult {
        require(args.scriptContent != null || args.scriptPath != null) {
            "Either scriptContent or scriptPath must be provided"
        }

        val startTime = System.currentTimeMillis()

        val scriptFile = if (args.scriptContent != null) {
            // Create temp file for inline script
            File.createTempFile("koog-script-", ".kts").apply {
                writeText(args.scriptContent)
                deleteOnExit()
            }
        } else {
            File(args.scriptPath!!)
        }

        return try {
            val result = scriptHost.evalWithTemplate<MainKtsScript>(
                script = scriptFile.toScriptSource(),
                evaluation = {
                    constructorArgs(emptyArray<String>())
                }
            )

            val executionTime = System.currentTimeMillis() - startTime

            result.valueOrNull()?.let { evalResult ->
                ScriptResult(
                    success = true,
                    returnValue = evalResult.returnValue.toString(),
                    executionTimeMs = executionTime
                )
            } ?: run {
                val errors = result.reports.joinToString("\n") { report ->
                    "${report.severity}: ${report.message}"
                }
                ScriptResult(
                    success = false,
                    error = errors,
                    executionTimeMs = executionTime
                )
            }
        } catch (e: Exception) {
            ScriptResult(
                success = false,
                error = e.stackTraceToString(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
```

#### Day 4-5: GlobTool

```kotlin
// tools/src/main/kotlin/io/github/stream29/koogagent/tools/search/GlobTool.kt
package io.github.stream29.koogagent.tools.search

import ai.koog.agents.core.tool.Tool
import ai.koog.agents.core.agent.AIAgentEnvironment
import kotlinx.serialization.Serializable
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

@Serializable
data class GlobArgs(
    val pattern: String,
    val path: String? = null
)

@Serializable
data class GlobResult(
    val files: List<String>,
    val count: Int
)

object GlobTool : Tool<GlobArgs, GlobResult> {
    override val name = "glob"
    override val description = """
        Search for files matching a glob pattern (e.g., "**/*.kt", "src/**/*.java").
        Returns sorted list of matching files.
    """.trimIndent()

    override suspend fun execute(
        args: GlobArgs,
        env: AIAgentEnvironment
    ): GlobResult {
        val basePath = Paths.get(args.path ?: ".")
        val matcher = FileSystems.getDefault()
            .getPathMatcher("glob:${args.pattern}")

        val matchingFiles = mutableListOf<Path>()

        Files.walkFileTree(basePath, object : SimpleFileVisitor<Path>() {
            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                val relativePath = basePath.relativize(file)
                if (matcher.matches(relativePath)) {
                    matchingFiles.add(file)
                }
                return FileVisitResult.CONTINUE
            }
        })

        // Sort by last modified time (most recent first)
        val sorted = matchingFiles
            .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
            .map { it.toString() }

        return GlobResult(
            files = sorted,
            count = sorted.size
        )
    }
}
```

#### Day 6-7: GrepTool (Simple Version)

```kotlin
// tools/src/main/kotlin/io/github/stream29/koogagent/tools/search/GrepTool.kt
package io.github.stream29.koogagent.tools.search

import ai.koog.agents.core.tool.Tool
import ai.koog.agents.core.agent.AIAgentEnvironment
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GrepArgs(
    val pattern: String,
    val path: String? = null,
    val caseInsensitive: Boolean = false,
    val outputMode: String = "files" // "files" | "content" | "count"
)

@Serializable
data class GrepResult(
    val matches: List<String>,
    val count: Int
)

object GrepTool : Tool<GrepArgs, GrepResult> {
    override val name = "grep"
    override val description = """
        Search file contents using regex patterns.
        Output modes: files (file paths), content (matching lines), count (match counts).
    """.trimIndent()

    override suspend fun execute(
        args: GrepArgs,
        env: AIAgentEnvironment
    ): GrepResult {
        val basePath = File(args.path ?: ".")
        val regex = if (args.caseInsensitive) {
            Regex(args.pattern, RegexOption.IGNORE_CASE)
        } else {
            Regex(args.pattern)
        }

        val results = mutableListOf<String>()

        basePath.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                try {
                    val lines = file.readLines()
                    val matches = lines.filter { regex.containsMatchIn(it) }

                    if (matches.isNotEmpty()) {
                        when (args.outputMode) {
                            "files" -> results.add(file.path)
                            "content" -> {
                                matches.forEach { line ->
                                    results.add("${file.path}: $line")
                                }
                            }
                            "count" -> {
                                results.add("${file.path}: ${matches.size}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read (binary, permission denied, etc.)
                }
            }

        return GrepResult(
            matches = results,
            count = results.size
        )
    }
}
```

### Week 3-4: Agent Assembly

#### Create Main Agent

```kotlin
// app/src/main/kotlin/io/github/stream29/koogagent/agent/CodingAgent.kt
package io.github.stream29.koogagent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import io.github.stream29.koogagent.tools.file.*
import io.github.stream29.koogagent.tools.script.KotlinScriptTool
import io.github.stream29.koogagent.tools.search.*

fun createCodingAgent(apiKey: String): AIAgent {
    return AIAgent(
        promptExecutor = simpleAnthropicExecutor(apiKey),
        llmModel = AnthropicModels.Sonnet_4_5,

        toolRegistry = ToolRegistry {
            // File operations
            tool(ReadTool)
            tool(WriteTool)
            tool(EditTool)

            // Script execution
            tool(KotlinScriptTool)

            // Search
            tool(GlobTool)
            tool(GrepTool)
        },

        systemPrompt = """
            You are a highly skilled programming assistant with access to file system tools.

            Your capabilities:
            - Read files with line numbers
            - Edit files with exact string replacement
            - Execute Kotlin scripts for complex operations
            - Search files using glob patterns
            - Search content using grep/regex

            Guidelines:
            - Always read files before editing
            - Use exact string matches for edits
            - For complex operations, use Kotlin scripts
            - Keep changes focused and minimal
            - Test your changes when possible

            Be precise, efficient, and safe in your operations.
        """.trimIndent(),

        strategy = singleRunStrategy(),
        maxIterations = 50,
        temperature = 0.3 // Low temperature for more consistent code generation
    )
}
```

#### Main Entry Point

```kotlin
// app/src/main/kotlin/io/github/stream29/koogagent/App.kt
package io.github.stream29.koogagent

import io.github.stream29.koogagent.agent.createCodingAgent
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println("Usage: koog-code-agent <task>")
        println("Example: koog-code-agent \"Add error handling to the User class\"")
        return@runBlocking
    }

    val apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY environment variable not set")

    val task = args.joinToString(" ")

    println("🤖 Koog Code Agent")
    println("Task: $task")
    println("Working directory: ${System.getProperty("user.dir")}")
    println()

    val agent = createCodingAgent(apiKey)

    try {
        val result = agent.run(task)
        println("\n✅ Result:")
        println(result)
    } catch (e: Exception) {
        println("\n❌ Error:")
        println(e.message)
        e.printStackTrace()
    }
}
```

### Week 5-6: Testing & Refinement

#### Comprehensive Test Suite

```kotlin
// Integration tests
class CodingAgentIntegrationTest {
    @Test
    fun `can read and understand file`() = runBlocking {
        // Create test file
        val testFile = createTempFile("Test.kt").apply {
            writeText("""
                class Test {
                    fun hello() = "Hello World"
                }
            """.trimIndent())
        }

        val agent = createCodingAgent(testApiKey)
        val result = agent.run(
            "Read ${testFile.absolutePath} and tell me what the hello function returns"
        )

        assertTrue(result.contains("Hello World"))
    }

    @Test
    fun `can edit file`() = runBlocking {
        val testFile = createTempFile("Test.kt").apply {
            writeText("val x = 1")
        }

        val agent = createCodingAgent(testApiKey)
        agent.run(
            "Change x = 1 to x = 2 in ${testFile.absolutePath}"
        )

        assertEquals("val x = 2", testFile.readText())
    }

    @Test
    fun `can search files`() = runBlocking {
        // Test glob and grep tools
    }
}
```

### Week 7-8: Polish & Documentation

#### User Guide

```markdown
# Koog Code Agent - User Guide

## Installation

```bash
./gradlew installDist
```

## Usage

```bash
export ANTHROPIC_API_KEY=your_key_here
./app/build/install/koog-code-agent/bin/koog-code-agent "Your task here"
```

## Examples

### Read and analyze code
```bash
koog-code-agent "Read App.kt and explain what it does"
```

### Make specific edits
```bash
koog-code-agent "Add error handling to the User class"
```

### Complex refactoring
```bash
koog-code-agent "Refactor the authentication code to use coroutines"
```

## Available Tools

- **read_file**: Read files with line numbers
- **edit_file**: Edit files with exact string replacement
- **kotlin_script**: Execute Kotlin scripts for complex operations
- **glob**: Search for files by pattern
- **grep**: Search file contents

## Tips

- Be specific in your requests
- Agent works best on one file at a time
- For large changes, break into smaller tasks
```

## Success Criteria for Phase 1

### Week 4 Checkpoint

Must have:
- ✅ All 6 core tools implemented and tested
- ✅ Agent can read and understand code
- ✅ Agent can make simple edits
- ✅ Agent can search files and content
- ✅ Scripts execute successfully
- ✅ 80%+ test coverage

### Week 8 Milestone

Must have:
- ✅ Complete test suite passing
- ✅ Documentation written
- ✅ Working CLI application
- ✅ Successfully completed 10+ real coding tasks
- ✅ Performance acceptable (< 5s per tool call)

Nice to have:
- 🎯 Pre-compiled script templates
- 🎯 Parallel tool execution
- 🎯 File operation caching

## Beyond Phase 1

### Phase 2: Agent Enhancements (Months 3-4)
- TodoWrite tool for task tracking
- Context management improvements
- Event handling and logging
- Multiple LLM support

### Phase 3: Git Integration (Months 5-6)
- Git status, diff, log
- Safe commit workflow
- Secret detection
- GitHub CLI integration

### Phase 4: Advanced (Months 7+)
- Sub-agent architecture
- MCP integration
- Advanced memory features
- Production deployment

## Daily Development Workflow

### Morning Routine
```bash
# 1. Check status
git status

# 2. Review todos
cat TODO.md

# 3. Run tests
./gradlew test

# 4. Start work on current task
```

### Before Committing
```bash
# 1. Run all tests
./gradlew check

# 2. Format code
./gradlew ktlintFormat

# 3. Commit
git commit -m "feat: implement ReadTool"
```

### Weekly Review
- What worked well?
- What blockers encountered?
- Adjust plan if needed
- Update documentation

## Monitoring Progress

### Week 1 Metrics
- [ ] Project structure created
- [ ] ReadTool implemented (Day 3-4)
- [ ] EditTool implemented (Day 5-7)
- [ ] Tests passing

### Week 2 Metrics
- [ ] WriteTool implemented
- [ ] KotlinScriptTool implemented
- [ ] GlobTool implemented
- [ ] GrepTool implemented

### Week 3-4 Metrics
- [ ] Agent assembled
- [ ] Integration tests passing
- [ ] Successfully completed 5+ tasks

## Next Steps

1. ✅ **TODAY**: Set up project structure
   ```bash
   cd /home/admin/ACodeSpace/push/KlaudeKode
   # Create module structure
   # Add dependencies
   # Commit initial structure
   ```

2. **Tomorrow**: Start implementing ReadTool

3. **This Week**: Complete core file tools

Let's build this! 🚀
