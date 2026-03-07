package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.SessionSnapshot
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.model.toSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Clock

class SessionFactoryConcurrencyTest {
    @Test
    fun concurrentLoadSessionReturnsSameRuntimeInstanceForSameId() {
        runBlocking {
            val repository = DelayedLoadRepository()
            val sessionFactory = SessionFactory(repository = repository)
            val sessionId = "same-session-id"

            val loaded = (1..16).map {
                async(Dispatchers.Default) {
                    sessionFactory.loadSession(sessionId)
                }
            }.awaitAll()

            val canonical = loaded.first()
            loaded.forEach { runtime ->
                assertSame(canonical, runtime)
            }
            assertEquals(1, repository.loadSessionCalls.get())
        }
    }

    @Test
    fun failedLoadDoesNotPoisonCacheAndConcurrentRetryStillKeepsSingleInstance() {
        runBlocking {
            val repository = FailFirstThenSucceedRepository()
            val sessionFactory = SessionFactory(repository = repository)
            val sessionId = "retry-session-id"

            assertFailsWith<IllegalStateException> {
                sessionFactory.loadSession(sessionId)
            }

            val loadedAfterRetry = (1..12).map {
                async(Dispatchers.Default) {
                    sessionFactory.loadSession(sessionId)
                }
            }.awaitAll()

            val canonical = loadedAfterRetry.first()
            loadedAfterRetry.forEach { runtime ->
                assertSame(canonical, runtime)
            }
            assertEquals(2, repository.loadSessionCalls.get())

            val cached = sessionFactory.loadSession(sessionId)
            assertSame(canonical, cached)
            assertEquals(2, repository.loadSessionCalls.get())
        }
    }

    private class DelayedLoadRepository : BaseSessionRepository() {
        val loadSessionCalls: AtomicInteger = AtomicInteger(0)

        override suspend fun loadSession(id: String): SessionState {
            loadSessionCalls.incrementAndGet()
            delay(80)
            return createRuntime(sessionId = id, title = "loaded-$id")
        }
    }

    private class FailFirstThenSucceedRepository : BaseSessionRepository() {
        val loadSessionCalls: AtomicInteger = AtomicInteger(0)

        override suspend fun loadSession(id: String): SessionState {
            val attempt = loadSessionCalls.incrementAndGet()
            if (attempt == 1) {
                delay(40)
                throw IllegalStateException("load failed")
            }

            delay(40)
            return createRuntime(sessionId = id, title = "loaded-$attempt")
        }
    }

    private abstract class BaseSessionRepository : SessionRepository {
        override suspend fun listSessions(): List<SessionMetadata> {
            return emptyList()
        }

        override suspend fun persistSession(id: String, session: SessionState) {
            throw UnsupportedOperationException("persistSession is not used in this test")
        }

        override suspend fun removeSession(id: String) {
            throw UnsupportedOperationException("removeSession is not used in this test")
        }

        override suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoNode>? {
            return null
        }

        override suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoNode>) {
            throw UnsupportedOperationException("writeAgentTodo is not used in this test")
        }

        protected fun createRuntime(sessionId: String, title: String): SessionState {
            val now = Clock.System.now()
            return SessionSnapshot(
                id = sessionId,
                title = title,
                createdAt = now,
                updatedAt = now,
                messages = emptyList(),
                status = SessionStatus.ACTIVE,
                parentSessionId = null,
                forkedFromMessageId = null,
                version = 1L,
                configuration = io.github.stream29.kode.session.core.model.SessionConfiguration(
                    preferredModel = null,
                    systemPrompt = "test",
                    workDir = null,
                    maxIterations = null,
                    temperature = null,
                    customValues = emptyMap(),
                ),
                tags = emptyList(),
                childSessionIds = emptyList(),
                runtimeState = SessionRunState.Suspended,
            ).toSessionState()
        }
    }
}
