package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionRunOwnershipTest {
    @Test
    fun beginAndCompleteStaySingleTransitionForSameOwner() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = createSessionManager(repository = repository)
            val session = createConversationSession(sessionManager = sessionManager, title = "single-transition")
            val ownerJob = Job()
            val persistCallsBefore = repository.persistSessionCalls

            sessionManager.beginRun(session.id, ownerJob)
            assertEquals(persistCallsBefore + 1, repository.persistSessionCalls)

            sessionManager.beginRun(session.id, ownerJob)
            assertEquals(persistCallsBefore + 1, repository.persistSessionCalls)
            val runningRuntime = assertNotNull(sessionManager.getSessionState(session.id))
            assertSame(ownerJob, runningRuntime.runJob.value)
            assertEquals(SessionRunState.Running, runningRuntime.metadata.value.state)

            sessionManager.completeRun(session.id)
            assertEquals(persistCallsBefore + 2, repository.persistSessionCalls)

            sessionManager.completeRun(session.id)
            assertEquals(persistCallsBefore + 2, repository.persistSessionCalls)
            val suspendedRuntime = assertNotNull(sessionManager.getSessionState(session.id))
            assertNull(suspendedRuntime.runJob.value)
            assertEquals(SessionRunState.Suspended, suspendedRuntime.metadata.value.state)
        }
    }

    @Test
    fun beginRunRejectsDifferentActiveOwner() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "owner-conflict")
            val firstOwner = Job()
            val secondOwner = Job()

            sessionManager.beginRun(session.id, firstOwner)

            val error = assertFailsWith<IllegalStateException> {
                sessionManager.beginRun(session.id, secondOwner)
            }

            assertTrue(error.message.orEmpty().contains("already owned"))
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertSame(firstOwner, runtime.runJob.value)
            assertEquals(SessionRunState.Running, runtime.metadata.value.state)
        }
    }

    @Test
    fun resumeRunRejectsDifferentOwnerUntilCompleteThenAllowsTakeover() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "resume-owner")
            val firstOwner = Job()
            val secondOwner = Job()

            sessionManager.beginRun(session.id, firstOwner)

            val error = assertFailsWith<IllegalStateException> {
                sessionManager.resumeRun(session.id, secondOwner)
            }
            assertTrue(error.message.orEmpty().contains("already owned"))

            sessionManager.completeRun(session.id)
            sessionManager.resumeRun(session.id, secondOwner)

            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertSame(secondOwner, runtime.runJob.value)
            assertEquals(SessionRunState.Running, runtime.metadata.value.state)
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

    private fun createSessionManager(repository: FakeSessionRepository = FakeSessionRepository()): SessionManager {
        return SessionManager(
            repository = repository,
        )
    }
}
