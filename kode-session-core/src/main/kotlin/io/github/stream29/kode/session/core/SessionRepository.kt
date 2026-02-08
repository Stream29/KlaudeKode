package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.Session
import io.github.stream29.kode.session.core.model.SessionMetadata

public interface SessionRepository {
    public suspend fun listSessions(): List<SessionMetadata>

    public suspend fun loadSession(id: String): Session

    public suspend fun persistSession(id: String, session: Session)

    public suspend fun removeSession(id: String)
}
