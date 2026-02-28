package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.TodoNode

public interface SessionRepository {
    public suspend fun listSessions(): List<SessionMetadata>

    public suspend fun loadSession(id: String): SessionState

    public suspend fun persistSession(id: String, session: SessionState)

    public suspend fun removeSession(id: String)

    public suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoNode>?

    public suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoNode>)
}
