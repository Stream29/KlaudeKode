package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionManagerDependencyInversionTest {
    @Test
    fun managerOperatesWithInjectedPortsWithoutDirectRepositoryDependency() {
        runBlocking {
            val runtimeStore = InMemoryRuntimeStore()
            val persistencePort = InMemoryPersistencePort()
            val sessionManager = SessionManager(
                dependencies = SessionManagerDependencies(
                    runtimeStore = runtimeStore,
                    persistencePort = persistencePort,
                ),
            )

            val created = sessionManager.createConversationSession(title = "dependency inversion", systemPrompt = "test", workDir = null)

            assertTrue(runtimeStore.putIds.contains(created.id))
            assertTrue(persistencePort.persistedIds.contains(created.id))

            val reloaded = assertNotNull(sessionManager.getSession(created.id))
            assertEquals(created.id, reloaded.id)

            val listed = sessionManager.listSessions(filter = null)
            assertEquals(1, listed.size)
            assertEquals(created.id, listed.single().id)

            sessionManager.deleteSession(
                sessionId = created.id,
                hardDelete = true,
            )

            assertTrue(runtimeStore.evictedIds.contains(created.id))
            assertTrue(persistencePort.removedIds.contains(created.id))
        }
    }

    private class InMemoryRuntimeStore : SessionRuntimeStore {
        private val sessions: MutableMap<String, SessionState> = linkedMapOf()
        val putIds: MutableList<String> = mutableListOf()
        val evictedIds: MutableList<String> = mutableListOf()

        override suspend fun loadSession(id: String): SessionState {
            return requireNotNull(sessions[id]) {
                "Session not found: $id"
            }
        }

        override fun put(session: SessionState) {
            val id = session.metadata.value.id
            putIds += id
            sessions.putIfAbsent(id, session)
        }

        override fun evict(id: String) {
            evictedIds += id
            sessions.remove(id)
        }
    }

    private class InMemoryPersistencePort : SessionPersistencePort {
        private val sessions: MutableMap<String, SessionState> = linkedMapOf()
        val persistedIds: MutableList<String> = mutableListOf()
        val removedIds: MutableList<String> = mutableListOf()

        override suspend fun listSessionMetadata(): List<SessionMetadata> {
            return sessions.values.map { state -> state.metadata.value }
        }

        override suspend fun persistSession(id: String, session: SessionState) {
            persistedIds += id
            sessions[id] = session
        }

        override suspend fun removeSession(id: String) {
            removedIds += id
            sessions.remove(id)
        }
    }
}
