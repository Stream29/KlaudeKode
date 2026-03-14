package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionState
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

public class SessionFactory(
    private val repository: SessionRepository,
) : SessionRuntimeStore {
    private val cache: ConcurrentHashMap<String, SessionState> = ConcurrentHashMap()
    private val inFlightLoads: ConcurrentHashMap<String, CompletableDeferred<SessionState>> = ConcurrentHashMap()

    override suspend fun loadSession(id: String): SessionState {
        cache[id]?.let { existing ->
            return existing
        }

        val deferred = CompletableDeferred<SessionState>()
        val existingLoad = inFlightLoads.putIfAbsent(id, deferred)
        if (existingLoad != null) {
            return existingLoad.await()
        }

        try {
            cache[id]?.let { cached ->
                deferred.complete(cached)
                return cached
            }

            val loaded = repository.loadSession(id)
            val canonical = cache.putIfAbsent(id, loaded) ?: loaded
            deferred.complete(canonical)
            return canonical
        } catch (throwable: Throwable) {
            deferred.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlightLoads.remove(id, deferred)
        }
    }

    override fun put(session: SessionState) {
        cache.putIfAbsent(session.metadata.value.id, session)
    }

    override fun evict(id: String) {
        cache.remove(id)
    }
}
