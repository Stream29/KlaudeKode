package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary

internal fun querySessionSummaries(
    metadata: List<SessionMetadata>,
    filter: SessionFilter?,
): List<SessionSummary> {
    val filtered = metadata.asSequence().filter { item ->
        item.matchesFilter(filter)
    }

    val sorted = when (filter?.sortBy) {
        SortBy.CREATED_AT -> filtered.sortedBy { item -> item.createdAt }
        SortBy.TITLE -> filtered.sortedBy { item -> item.title }
        else -> filtered.sortedBy { item -> item.updatedAt }
    }

    val ordered = if (filter?.sortOrder == SortOrder.ASCENDING) {
        sorted
    } else {
        sorted.toList().asReversed().asSequence()
    }

    val paged = ordered
        .drop(filter?.offset ?: 0)
        .let { sequence ->
            val limit = filter?.limit
            if (limit == null) sequence else sequence.take(limit)
        }

    return paged.map { item ->
        item.toSessionSummary()
    }.toList()
}

private fun SessionMetadata.matchesFilter(filter: SessionFilter?): Boolean {
    if (filter == null) {
        return true
    }

    if (filter.status != null) {
        val statusMatches = when (filter.status) {
            SessionStatusFilter.ACTIVE -> this.status == SessionStatus.ACTIVE
            SessionStatusFilter.ARCHIVED -> this.status == SessionStatus.ARCHIVED
            SessionStatusFilter.ALL -> true
        }
        if (!statusMatches) {
            return false
        }
    }

    if (!filter.searchQuery.isNullOrBlank()) {
        val query = filter.searchQuery.lowercase()
        if (!this.title.lowercase().contains(query) && !this.id.lowercase().contains(query)) {
            return false
        }
    }

    if (!filter.parentSessionId.isNullOrBlank() && this.parentSessionId != filter.parentSessionId) {
        return false
    }

    if (!filter.tags.isNullOrEmpty() && !this.tags.containsAll(filter.tags)) {
        return false
    }

    if (filter.createdAfter != null && this.createdAt < filter.createdAfter) {
        return false
    }

    if (filter.createdBefore != null && this.createdAt > filter.createdBefore) {
        return false
    }

    return true
}

internal fun SessionMetadata.toSessionSummary(): SessionSummary {
    return SessionSummary(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        status = status,
        state = state,
        hasForks = childSessionIds.isNotEmpty(),
        tags = tags,
    )
}
