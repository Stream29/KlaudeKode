package io.github.stream29.kode.session.core.testsupport

import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.*

internal class FakeSessionRepository : SessionRepository {
    private val sessions: LinkedHashMap<String, SessionState> = linkedMapOf()
    private val agentTodos: LinkedHashMap<String, List<TodoItem>> = linkedMapOf()
    internal var listSessionsCalls: Int = 0
        private set
    internal var loadSessionCalls: Int = 0
        private set
    internal var persistSessionCalls: Int = 0
        private set
    internal var removeSessionCalls: Int = 0
        private set

    override suspend fun listSessions(): List<SessionMetadata> {
        listSessionsCalls += 1
        return sessions.values.map { session ->
            session.metadata.value
        }
    }

    override suspend fun loadSession(id: String): SessionState {
        loadSessionCalls += 1
        val session = requireNotNull(sessions[id]) {
            "Session not found: $id"
        }
        return session.toSessionSnapshot().toSessionState()
    }

    override suspend fun persistSession(id: String, session: SessionState) {
        persistSessionCalls += 1
        sessions[id] = session.toSessionSnapshot().toSessionState()
    }

    override suspend fun removeSession(id: String) {
        removeSessionCalls += 1
        sessions.remove(id)
    }

    override suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoItem> {
        return agentTodos["$sessionId:$agentId"] ?: emptyList()
    }

    override suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoItem>) {
        agentTodos["$sessionId:$agentId"] = todos
    }
}
