package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.Session
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

public class SessionFactory(
    private val repository: SessionRepository,
) {
    private val cache: ConcurrentHashMap<String, Session> = ConcurrentHashMap()
    private val loadMutex: Mutex = Mutex()

    public suspend fun loadSession(id: String): Session {
        val existing = cache[id]
        if (existing != null) {
            return existing
        }
        return loadMutex.withLock {
            val doubleChecked = cache[id]
            if (doubleChecked != null) {
                return@withLock doubleChecked
            }
            val loaded = repository.loadSession(id)
            cache[id] = loaded
            loaded
        }
    }

    public fun put(session: Session) {
        cache[session.metadata.value.id] = session
    }

    public fun evict(id: String) {
        cache.remove(id)
    }
}
