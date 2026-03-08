package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.model.AgentState
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionStateMachineTwoPhaseStopTest {
    @Test
    fun stopRunUsesSoftThenHardSemanticsWithoutHalfState() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "two-phase stop")
            val ownerJob = Job()

            sessionManager.beginRun(session.id, ownerJob)

            val firstRollback = sessionManager.stopRun(session.id)
            assertFalse(firstRollback)
            assertTrue(ownerJob.isCancelled)

            val afterFirstStop = assertNotNull(sessionManager.getSessionState(session.id))
            assertSame(ownerJob, afterFirstStop.runJob.value)
            assertEquals(AgentState.Running, afterFirstStop.agent.value.state.value)
            assertEquals(SessionRunState.Running, afterFirstStop.metadata.value.state)
            assertEquals(listOf("main-${session.id}"), sessionManager.listActiveAgentIds(session.id))

            val secondRollback = sessionManager.stopRun(session.id)
            assertFalse(secondRollback)

            val afterSecondStop = assertNotNull(sessionManager.getSessionState(session.id))
            assertNull(afterSecondStop.runJob.value)
            assertEquals(AgentState.Suspended, afterSecondStop.agent.value.state.value)
            assertEquals(SessionRunState.Suspended, afterSecondStop.metadata.value.state)
            assertTrue(sessionManager.listActiveAgentIds(session.id).isEmpty())
        }
    }

    @Test
    fun softStopCanConvergeToSuspendedWithoutHardStopEscalation() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "soft stop convergence")
            val ownerJob = Job()

            sessionManager.beginRun(session.id, ownerJob)
            sessionManager.stopRun(session.id)
            sessionManager.completeRun(session.id)

            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertNull(runtime.runJob.value)
            assertEquals(AgentState.Suspended, runtime.agent.value.state.value)
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)

            val duplicated = sessionManager.duplicateSession(sessionId = session.id, newTitle = "soft-stop-duplicate")
            assertEquals(SessionRunState.Suspended, duplicated.runtimeState)
        }
    }

    @Test
    fun stopRunSoftPhaseRollsBackPendingInputButKeepsRunningOwnershipUntilSecondStop() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(
                sessionManager = sessionManager,
                title = "soft-stop pending-input",
            )
            val ownerJob = Job()

            sessionManager.beginRun(session.id, ownerJob)
            appendPendingScript(
                sessionManager = sessionManager,
                sessionId = session.id,
            )

            assertNotNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            val firstRollback = sessionManager.stopRun(session.id)
            assertTrue(firstRollback)
            assertTrue(ownerJob.isCancelled)
            assertNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            val afterSoftStop = assertNotNull(sessionManager.getSessionState(session.id))
            assertSame(ownerJob, afterSoftStop.runJob.value)
            assertEquals(AgentState.Running, afterSoftStop.agent.value.state.value)
            assertEquals(SessionRunState.Running, afterSoftStop.metadata.value.state)

            val runningContinueError = assertFailsWith<IllegalStateException> {
                sessionManager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "blocked while running",
                    agentId = null,
                )
            }
            assertTrue(runningContinueError.message.orEmpty().contains("requires a suspended session"))

            val secondRollback = sessionManager.stopRun(session.id)
            assertFalse(secondRollback)

            val afterSecondStop = assertNotNull(sessionManager.getSessionState(session.id))
            assertNull(afterSecondStop.runJob.value)
            assertEquals(AgentState.Suspended, afterSecondStop.agent.value.state.value)
            assertEquals(SessionRunState.Suspended, afterSecondStop.metadata.value.state)

            val continueInput = "resume after stop convergence"
            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = continueInput,
                agentId = null,
            )

            val snapshot = assertNotNull(sessionManager.getSession(session.id))
            val trailingUser = assertIs<UserMessage>(snapshot.messages.last())
            assertEquals(continueInput, trailingUser.content)
        }
    }

    @Test
    fun forkAndDuplicateLegalityFollowSuspendedOwnership() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "fork legality")
            val ownerJob = Job()

            sessionManager.beginRun(session.id, ownerJob)

            val runningForkError = assertFailsWith<IllegalStateException> {
                sessionManager.forkSession(
                    parentSessionId = session.id,
                    atMessageId = null,
                    newTitle = "running fork",
                )
            }
            assertTrue(runningForkError.message.orEmpty().contains("Only suspended sessions can be forked"))

            val runningDuplicateError = assertFailsWith<IllegalStateException> {
                sessionManager.duplicateSession(sessionId = session.id, newTitle = "running duplicate")
            }
            assertTrue(runningDuplicateError.message.orEmpty().contains("Only suspended sessions can be duplicated"))

            sessionManager.completeRun(session.id)

            val forked = sessionManager.forkSession(
                parentSessionId = session.id,
                atMessageId = null,
                newTitle = "suspended fork",
            )
            assertEquals(session.id, forked.parentSessionId)
            assertEquals(SessionRunState.Suspended, forked.runtimeState)

            val duplicated = sessionManager.duplicateSession(sessionId = session.id, newTitle = "suspended duplicate")
            assertEquals(SessionRunState.Suspended, duplicated.runtimeState)
            assertNull(duplicated.parentSessionId)
        }
    }

    private suspend fun createConversationSession(
        sessionManager: SessionManager,
        title: String,
    ) = sessionManager.createConversationSession(title = title, systemPrompt = "test", workDir = null)

    private suspend fun appendPendingScript(
        sessionManager: SessionManager,
        sessionId: String,
    ) {
        sessionManager.addAgentScriptMessage(
            sessionId = sessionId,
            scriptId = "pending-script-id",
            status = AgentScriptStatus.PENDING_INPUT,
            scriptReturnValue = null,
            scriptStdout = "",
            error = null,
            outputList = emptyList(),
            koogMessages = listOf(
                Message.Tool.Call(
                    id = "pending-script-id",
                    tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                    content = "{\"script\":\"suspendForUserInput()\"}",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            ),
            metadata = null,
            agentId = null,
        )
    }

    private fun createSessionManager(repository: FakeSessionRepository = FakeSessionRepository()): SessionManager {
        return SessionManager(dependencies = repository.toSessionManagerDependencies())
    }
}
