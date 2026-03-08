package io.github.stream29.kode.core.agent

import ai.koog.prompt.message.Message
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.agent.model.toKoogMessages

internal interface SessionQueryPort {
    suspend fun requireSession(sessionId: String)

    suspend fun loadAgentMessages(sessionId: String, agentId: String?): List<Message>
}

internal class SessionManagerSessionQueryPort(
    private val sessionManager: SessionManager,
) : SessionQueryPort {
    override suspend fun requireSession(sessionId: String) {
        sessionManager.getSessionState(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }

    override suspend fun loadAgentMessages(sessionId: String, agentId: String?): List<Message> {
        val messages = sessionManager.getAgentMessages(
            sessionId = sessionId,
            agentId = agentId,
        )
        return messages.flatMap { item -> item.toKoogMessages() }
    }
}
