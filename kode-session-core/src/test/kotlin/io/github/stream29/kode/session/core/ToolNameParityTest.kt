package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolNameParityTest {
    @Test
    fun toolNamesConstantMatchesKotlinScriptToolNameSourceConstant() {
        val kotlinScriptToolName = readKotlinScriptToolNameFromSource()
        assertEquals(kotlinScriptToolName, ToolNames.EXECUTE_KOTLIN_SCRIPT)
    }

    @Test
    fun addAgentScriptMessageFailsFastForNonScriptToolKoogMessages() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "session fail-fast", systemPrompt = "test", workDir = null)

            val error = assertFailsWith<IllegalStateException> {
                sessionManager.addAgentScriptMessage(
                    sessionId = session.id,
                    scriptId = "script-id",
                    status = AgentScriptStatus.COMPLETED,
                    scriptReturnValue = "ok",
                    scriptStdout = "",
                    error = null,
                    outputList = emptyList(),
                    koogMessages = listOf(
                        Message.Tool.Call(
                            id = "script-id",
                            tool = "executeShell",
                            content = "{\"command\":\"ls\"}",
                            metaInfo = ResponseMetaInfo.Empty,
                        )
                    ),
                    metadata = null,
                    agentId = null,
                )
            }

            assertContains(
                charSequence = error.message.orEmpty(),
                other = "Script-only violation: tool 'executeShell' is not allowed in AgentScript.koogMessages",
            )
        }
    }

    @Test
    fun subagentApisRemainDisabledWithStableErrorMessages() {
        val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())

        val createError = assertFailsWith<IllegalStateException> {
            sessionManager.createSubAgent(
                sessionId = "session-id",
                agentId = "sub-agent-id",
                parentAgentId = null,
                mode = "parallel",
                taskDescription = "task",
                expectedResult = "result",
            )
        }
        assertEquals("Subagent is disabled in strict script-only runtime", createError.message)

        val receiveError = assertFailsWith<IllegalStateException> {
            sessionManager.injectReceiveAgentMessage(
                sessionId = "session-id",
                targetAgentId = "sub-agent-id",
                fromAgentId = "main-agent-id",
                message = "message",
            )
        }
        assertEquals("receiveAgentMessage is disabled in strict script-only runtime", receiveError.message)
    }

    private fun readKotlinScriptToolNameFromSource(): String {
        val startPath = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val relativePath = Path.of(
            "tools",
            "kotlin-script-tool",
            "src",
            "main",
            "kotlin",
            "io",
            "github",
            "stream29",
            "kode",
            "tools",
            "scripting",
            "Global.kt",
        )
        val sourcePath = generateSequence(startPath) { currentPath ->
            currentPath.parent
        }.map { candidateRoot ->
            candidateRoot.resolve(relativePath)
        }.firstOrNull { candidatePath ->
            Files.isRegularFile(candidatePath)
        } ?: error("Unable to locate tools/kotlin-script-tool Global.kt from $startPath")

        val source = Files.readString(sourcePath)
        val pattern = Regex("""public\s+const\s+val\s+kotlinScriptToolName\s*:\s*String\s*=\s*\"([^\"]+)\"""")
        val match = pattern.find(source)
            ?: error("Unable to parse kotlinScriptToolName from $sourcePath")
        return match.groupValues[1]
    }
}
