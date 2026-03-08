package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PendingInputPersistenceContractTest {
    @Test
    fun pendingInputMarkerRemainsVisibleAfterRestartAndStillBlocksContinueLegality() {
        runBlocking {
            val tempDir = Files.createTempDirectory("pending-input-restart-contract-test")
            try {
                val manager = SessionManager(dependencies = FileSessionStorage(dataDir = tempDir.toFile()).toSessionManagerDependencies())
                val session = createConversationSession(sessionManager = manager, title = "pending-input restart")
                appendPendingScript(
                    sessionManager = manager,
                    sessionId = session.id,
                    scriptId = "pending-script-id",
                )

                val pendingBeforeRestart = manager.getTrailingPendingScript(
                    sessionId = session.id,
                    agentId = null,
                )
                assertNotNull(pendingBeforeRestart)

                val reloadedManager = SessionManager(dependencies = FileSessionStorage(dataDir = tempDir.toFile()).toSessionManagerDependencies())
                val pendingAfterRestart = reloadedManager.getTrailingPendingScript(
                    sessionId = session.id,
                    agentId = null,
                )
                val pendingScriptInfo = assertNotNull(pendingAfterRestart)
                assertEquals("pending-script-id", pendingScriptInfo.scriptId)

                val error = assertFailsWith<IllegalStateException> {
                    reloadedManager.prepareConversationContinuation(
                        sessionId = session.id,
                        input = "",
                        agentId = null,
                    )
                }
                assertTrue(error.message.orEmpty().contains("blocks continue; resolve pending-input state first"))

                val reloadedRuntime = assertNotNull(reloadedManager.getSessionState(session.id))
                assertEquals(SessionRunState.Suspended, reloadedRuntime.metadata.value.state)
                val trailing = assertIs<AgentScript>(reloadedRuntime.agent.value.messages.value.last())
                assertEquals(AgentScriptStatus.PENDING_INPUT, trailing.status)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun suspendedSessionWithoutPendingMarkerRemainsContinuableAfterRestart() {
        runBlocking {
            val tempDir = Files.createTempDirectory("suspended-no-pending-restart-contract-test")
            try {
                val manager = SessionManager(dependencies = FileSessionStorage(dataDir = tempDir.toFile()).toSessionManagerDependencies())
                val session = createConversationSession(sessionManager = manager, title = "no pending restart")

                val reloadedManager = SessionManager(dependencies = FileSessionStorage(dataDir = tempDir.toFile()).toSessionManagerDependencies())
                reloadedManager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "",
                    agentId = null,
                )

                val reloadedRuntime = assertNotNull(reloadedManager.getSessionState(session.id))
                assertEquals(SessionRunState.Suspended, reloadedRuntime.metadata.value.state)
                assertTrue(reloadedRuntime.agent.value.messages.value.isEmpty())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun createConversationSession(
        sessionManager: SessionManager,
        title: String,
    ) = sessionManager.createConversationSession(title = title, systemPrompt = "test", workDir = null)

    private suspend fun appendPendingScript(
        sessionManager: SessionManager,
        sessionId: String,
        scriptId: String,
    ) {
        sessionManager.addAgentScriptMessage(
            sessionId = sessionId,
            scriptId = scriptId,
            status = AgentScriptStatus.PENDING_INPUT,
            scriptReturnValue = null,
            scriptStdout = "",
            error = null,
            outputList = emptyList(),
            koogMessages = listOf(
                Message.Tool.Call(
                    id = scriptId,
                    tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                    content = "{\"script\":\"suspendForUserInput()\"}",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            ),
            metadata = null,
            agentId = null,
        )
    }
}
