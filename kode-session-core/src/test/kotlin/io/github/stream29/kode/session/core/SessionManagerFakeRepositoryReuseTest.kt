package io.github.stream29.kode.session.core

import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SessionManagerFakeRepositoryReuseTest {
    @Test
    fun prepareConversationContinuationPersistsTrailingUserMessageWithFakeRepository() {
        runBlocking {
            val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
            val session = sessionManager.createConversationSession(title = "reuse fake repository", systemPrompt = "test", workDir = null)

            val input = "hello from fake repository"
            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = input,
                agentId = null,
            )

            val snapshot = assertNotNull(sessionManager.getSession(session.id))
            val trailing = assertIs<UserMessage>(snapshot.messages.last())
            assertEquals(input, trailing.content)
        }
    }
}
