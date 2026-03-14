package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionState

public interface SessionPersistencePort {
    public suspend fun listSessionMetadata(): List<SessionMetadata>

    public suspend fun persistSession(id: String, session: SessionState)

    public suspend fun removeSession(id: String)
}

public class RepositorySessionPersistencePort(
    private val repository: SessionRepository,
) : SessionPersistencePort {
    override suspend fun listSessionMetadata(): List<SessionMetadata> {
        return repository.listSessions()
    }

    override suspend fun persistSession(id: String, session: SessionState) {
        repository.persistSession(
            id = id,
            session = session,
        )
    }

    override suspend fun removeSession(id: String) {
        repository.removeSession(id = id)
    }
}

public data class SessionManagerDependencies(
    val runtimeStore: SessionRuntimeStore,
    val persistencePort: SessionPersistencePort,
    val observerCoordinatorFactory: SessionPersistenceObserverCoordinatorFactory =
        DefaultSessionPersistenceObserverCoordinatorFactory,
    val subAgentCoordinatorFactory: SessionSubAgentCoordinatorFactory =
        DefaultSessionSubAgentCoordinatorFactory,
)

public fun SessionRepository.toSessionManagerDependencies(
    observerCoordinatorFactory: SessionPersistenceObserverCoordinatorFactory =
        DefaultSessionPersistenceObserverCoordinatorFactory,
    subAgentCoordinatorFactory: SessionSubAgentCoordinatorFactory =
        DefaultSessionSubAgentCoordinatorFactory,
): SessionManagerDependencies {
    return SessionManagerDependencies(
        runtimeStore = SessionFactory(repository = this),
        persistencePort = RepositorySessionPersistencePort(repository = this),
        observerCoordinatorFactory = observerCoordinatorFactory,
        subAgentCoordinatorFactory = subAgentCoordinatorFactory,
    )
}
