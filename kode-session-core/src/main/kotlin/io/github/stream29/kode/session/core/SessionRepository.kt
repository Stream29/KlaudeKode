package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.agent.model.TodoItem

public interface RootSessionRepository {
    public suspend fun listSessions(): List<SessionMetadata>

    public fun session(id: String): ScopedSessionRepository
}

public interface ScopedSessionRepository {
    public suspend fun load(): SessionState

    public suspend fun persist(session: SessionState)

    public suspend fun remove()

    public fun agent(agentId: String): ScopedAgentRepository
}

public interface ScopedAgentRepository {
    public suspend fun readTodo(): List<TodoItem>

    public suspend fun writeTodo(todos: List<TodoItem>)
}

public interface SessionRepository {
    public val rootRepository: RootSessionRepository
        get() = DelegatingRootSessionRepository(repository = this)

    public suspend fun listSessions(): List<SessionMetadata>

    public suspend fun loadSession(id: String): SessionState

    public suspend fun persistSession(id: String, session: SessionState)

    public suspend fun removeSession(id: String)

    public suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoItem>

    public suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoItem>)
}

private class DelegatingRootSessionRepository(
    private val repository: SessionRepository,
) : RootSessionRepository {
    override suspend fun listSessions(): List<SessionMetadata> {
        return repository.listSessions()
    }

    override fun session(id: String): ScopedSessionRepository {
        return DelegatingScopedSessionRepository(
            repository = repository,
            sessionId = id,
        )
    }
}

private class DelegatingScopedSessionRepository(
    private val repository: SessionRepository,
    private val sessionId: String,
) : ScopedSessionRepository {
    override suspend fun load(): SessionState {
        return repository.loadSession(id = sessionId)
    }

    override suspend fun persist(session: SessionState) {
        repository.persistSession(
            id = sessionId,
            session = session,
        )
    }

    override suspend fun remove() {
        repository.removeSession(id = sessionId)
    }

    override fun agent(agentId: String): ScopedAgentRepository {
        return DelegatingScopedAgentRepository(
            repository = repository,
            sessionId = sessionId,
            agentId = agentId,
        )
    }
}

private class DelegatingScopedAgentRepository(
    private val repository: SessionRepository,
    private val sessionId: String,
    private val agentId: String,
) : ScopedAgentRepository {
    override suspend fun readTodo(): List<TodoItem> {
        return repository.readAgentTodo(
            sessionId = sessionId,
            agentId = agentId,
        )
    }

    override suspend fun writeTodo(todos: List<TodoItem>) {
        repository.writeAgentTodo(
            sessionId = sessionId,
            agentId = agentId,
            todos = todos,
        )
    }
}
