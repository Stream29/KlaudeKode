package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionState

public interface SessionRuntimeStore {
    public suspend fun loadSession(id: String): SessionState

    public fun put(session: SessionState)

    public fun evict(id: String)
}
