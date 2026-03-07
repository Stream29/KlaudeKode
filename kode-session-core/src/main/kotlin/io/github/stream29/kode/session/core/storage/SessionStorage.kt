package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.model.SessionSnapshot
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import kotlin.time.Instant

/**
 * Storage interface for conversation sessions.
 * Implementations can use file system, database, or in-memory storage.
 */
public interface SessionStorage {

    /**
     * Save or update a session.
     */
    public suspend fun saveSession(session: SessionSnapshot)

    /**
     * Get a session by ID.
     * @return The session, or null if not found.
     */
    public suspend fun getSession(sessionId: String): SessionSnapshot?

    /**
     * List all sessions with optional filtering.
     */
    public suspend fun listSessions(filter: SessionFilter?): List<SessionSummary>

    /**
     * Delete a session (soft or hard delete based on implementation).
     */
    public suspend fun deleteSession(sessionId: String, hardDelete: Boolean)

}

/**
 * Filter options for listing sessions.
 */
public data class SessionFilter(
    val status: SessionStatusFilter? = null,
    val tags: List<String>? = null,
    val searchQuery: String? = null,
    val parentSessionId: String? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val sortBy: SortBy = SortBy.UPDATED_AT,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val limit: Int? = null,
    val offset: Int = 0
)

public enum class SessionStatusFilter {
    ACTIVE {
        override fun matches(status: SessionStatus): Boolean {
            return status == SessionStatus.ACTIVE
        }
    },
    ARCHIVED {
        override fun matches(status: SessionStatus): Boolean {
            return status == SessionStatus.ARCHIVED
        }
    },
    ALL {
        override fun matches(status: SessionStatus): Boolean {
            return true
        }
    },
    ;

    public abstract fun matches(status: SessionStatus): Boolean
}

public enum class SortBy {
    CREATED_AT,
    UPDATED_AT,
    TITLE
}

public enum class SortOrder {
    ASCENDING,
    DESCENDING,
    ;

    public fun <T> applyTo(sortedAscending: Sequence<T>): Sequence<T> {
        return if (this == ASCENDING) {
            sortedAscending
        } else {
            sortedAscending.toList().asReversed().asSequence()
        }
    }
}
