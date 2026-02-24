package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionMetadata

public interface SessionRepository {
    public suspend fun listSessions(): List<SessionMetadata>

    public suspend fun loadSession(id: String): SessionState

    public suspend fun persistSession(id: String, session: SessionState)

    public suspend fun removeSession(id: String)
}
