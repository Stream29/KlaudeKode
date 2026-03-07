package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.Agent
import io.github.stream29.kode.session.core.model.AgentConfig
import io.github.stream29.kode.session.core.model.AgentState
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SubAgent
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubAgentLifecycleBoundaryTest {
    @Test
    fun completedSubAgentStaysTerminalAndDoesNotAcceptFurtherLifecycleUpdates() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = SessionManager(repository = repository)
            val session = createConversationSession(
                sessionManager = sessionManager,
                title = "complete-subagent",
            )
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            val subAgentId = "sub-complete"
            val job = Job()

            installRunningSubAgent(runtime = runtime, subAgentId = subAgentId)
            sessionManager.registerSubAgentJob(
                sessionId = session.id,
                agentId = subAgentId,
                job = job,
            )

            assertEquals(listOf(subAgentId), sessionManager.listActiveSubAgentIds(session.id))

            val persistBeforeFinish = repository.persistSessionCalls
            assertTrue(sessionManager.completeSubAgentResult(session.id, subAgentId, "done"))
            assertEquals(persistBeforeFinish + 1, repository.persistSessionCalls)
            assertTrue(job.isCancelled)

            val pollResult = sessionManager.pollSubAgentResult(session.id, subAgentId)
            val awaitResult = sessionManager.awaitSubAgentResult(session.id, subAgentId, timeoutSeconds = 1)
            assertIs<SessionManager.SubAgentPollResult.Completed>(pollResult)
            assertIs<SessionManager.SubAgentPollResult.Completed>(awaitResult)
            assertEquals("done", pollResult.result)
            assertEquals("done", awaitResult.result)
            assertEquals(emptyList(), sessionManager.listActiveSubAgentIds(session.id))

            assertFalse(sessionManager.completeSubAgentResult(session.id, subAgentId, "ignored"))
            assertFalse(sessionManager.cancelSubAgent(session.id, subAgentId, "ignored"))
            assertFalse(sessionManager.killSubAgent(session.id, subAgentId))
            assertEquals(persistBeforeFinish + 1, repository.persistSessionCalls)
        }
    }

    @Test
    fun killedSubAgentIsMissingForPollAwaitListAndSecondKill() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = SessionManager(repository = repository)
            val session = createConversationSession(
                sessionManager = sessionManager,
                title = "kill-subagent",
            )
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            val subAgentId = "sub-kill"
            val job = Job()

            installRunningSubAgent(runtime = runtime, subAgentId = subAgentId)
            sessionManager.registerSubAgentJob(
                sessionId = session.id,
                agentId = subAgentId,
                job = job,
            )

            assertEquals(listOf(subAgentId), sessionManager.listActiveSubAgentIds(session.id))

            val persistBeforeKill = repository.persistSessionCalls
            assertTrue(sessionManager.killSubAgent(session.id, subAgentId))
            assertEquals(persistBeforeKill + 1, repository.persistSessionCalls)
            assertTrue(job.isCancelled)

            assertIs<SessionManager.SubAgentPollResult.Missing>(sessionManager.pollSubAgentResult(session.id, subAgentId))
            assertIs<SessionManager.SubAgentPollResult.Missing>(
                sessionManager.awaitSubAgentResult(session.id, subAgentId, timeoutSeconds = 1)
            )
            assertEquals(emptyList(), sessionManager.listActiveSubAgentIds(session.id))

            assertFalse(sessionManager.killSubAgent(session.id, subAgentId))
            assertFalse(sessionManager.completeSubAgentResult(session.id, subAgentId, "ignored"))
            assertFalse(sessionManager.cancelSubAgent(session.id, subAgentId, "ignored"))
            assertEquals(persistBeforeKill + 1, repository.persistSessionCalls)
        }
    }

    private suspend fun createConversationSession(
        sessionManager: SessionManager,
        title: String,
    ) = sessionManager.createConversationSession(
        title = title,
        systemPrompt = "test",
        preferredModel = null,
        preferredModelId = "test-model",
        workDir = null,
    )

    private suspend fun installRunningSubAgent(runtime: SessionState, subAgentId: String) {
        runtime.mutex.lock()
        try {
            val subAgent = SubAgent(
                delegate = Agent(
                    state = MutableStateFlow(AgentState.Running),
                    config = MutableStateFlow(
                        AgentConfig(
                            systemPrompt = "sub-agent",
                            taskDescription = "task",
                            expectedResult = "result",
                            canInteractWithUser = false,
                        )
                    ),
                    messages = MutableStateFlow(kotlinx.collections.immutable.persistentListOf()),
                    todoState = MutableStateFlow(emptyList()),
                ),
                result = CompletableDeferred(),
            )
            runtime.subagents.value = runtime.subagents.value.put(subAgentId, subAgent)
        } finally {
            runtime.mutex.unlock()
        }
    }
}
