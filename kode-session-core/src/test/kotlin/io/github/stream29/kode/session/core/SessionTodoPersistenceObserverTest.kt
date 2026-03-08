package io.github.stream29.kode.session.core

import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionTodoPersistenceObserverTest {
    @Test
    fun directAgentTodoMetadataMutationIsObservedAndPersisted() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = SessionManager(dependencies = repository.toSessionManagerDependencies())
            val session = createConversationSession(sessionManager = sessionManager, title = "todo-observer")
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            val persistCallsBefore = repository.persistSessionCalls

            val changed = runtime.agent.value.writeTodoToMetadata(
                listOf(TodoItem(name = "observed", completed = false)),
            )
            assertTrue(changed)

            withTimeout(timeMillis = 2_000L) {
                while (repository.persistSessionCalls <= persistCallsBefore) {
                    delay(10)
                }
            }

            assertEquals(persistCallsBefore + 1, repository.persistSessionCalls)
            val persistedTodos = sessionManager.getAgentTodo(session.id, "main-${session.id}")
            assertEquals("observed", persistedTodos.single().name)
        }
    }

    @Test
    fun updateAgentTodoPersistsExactlyOnceWhenObserverIsEnabled() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = SessionManager(dependencies = repository.toSessionManagerDependencies())
            val session = createConversationSession(sessionManager = sessionManager, title = "todo-managed-update")
            val persistCallsBefore = repository.persistSessionCalls

            sessionManager.updateAgentTodo(
                sessionId = session.id,
                agentId = "main-${session.id}",
                todos = listOf(TodoItem(name = "managed", completed = true)),
            )
            delay(100)

            assertEquals(persistCallsBefore + 1, repository.persistSessionCalls)
            val persistedTodos = sessionManager.getAgentTodo(session.id, "main-${session.id}")
            assertEquals("managed", persistedTodos.single().name)
            assertTrue(persistedTodos.single().completed)
        }
    }

    private suspend fun createConversationSession(
        sessionManager: SessionManager,
        title: String,
    ) = sessionManager.createConversationSession(title = title, systemPrompt = "test", workDir = null)
}
