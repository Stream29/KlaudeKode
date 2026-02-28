package io.github.stream29.kode.core.testsupport

import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.*
import io.github.stream29.kode.ui.core.MessageHandler

internal open class FakeMessageHandler : MessageHandler {
    private val queuedInputs: ArrayDeque<String> = ArrayDeque()

    var requestInputCount: Int = 0
        private set

    fun enqueueInput(input: String) {
        queuedInputs.addLast(input)
    }

    override fun addMessageToUser(message: String) = Unit

    override fun log(message: String) = Unit

    override suspend fun requestInput(): String {
        requestInputCount += 1
        return if (queuedInputs.isEmpty()) "" else queuedInputs.removeFirst()
    }
}

internal class FakeSessionRepository : SessionRepository {
    private val sessions: LinkedHashMap<String, SessionState> = linkedMapOf()
    private val agentTodos: LinkedHashMap<String, List<TodoNode>> = linkedMapOf()

    override suspend fun listSessions(): List<SessionMetadata> {
        return sessions.values.map { session ->
            session.metadata.value
        }
    }

    override suspend fun loadSession(id: String): SessionState {
        val session = requireNotNull(sessions[id]) {
            "Session not found: $id"
        }
        return session.toSessionSnapshot().toSessionState()
    }

    override suspend fun persistSession(id: String, session: SessionState) {
        sessions[id] = session.toSessionSnapshot().toSessionState()
    }

    override suspend fun removeSession(id: String) {
        sessions.remove(id)
    }

    override suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoNode>? {
        return agentTodos["$sessionId:$agentId"]
    }

    override suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoNode>) {
        agentTodos["$sessionId:$agentId"] = todos
    }
}
