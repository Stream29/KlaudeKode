package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionSummary

internal fun querySessionSummaries(
    metadata: List<SessionMetadata>,
    filter: SessionFilter?,
): List<SessionSummary> {
    val filtered = metadata.asSequence().filter { item ->
        item.matchesFilter(filter)
    }

    val sortBy = filter?.sortBy ?: SortBy.UPDATED_AT
    val sorted = SORTER_BY[sortBy]?.invoke(filtered) ?: filtered.sortedBy { item -> item.updatedAt }

    val sortOrder = filter?.sortOrder ?: SortOrder.DESCENDING
    val ordered = sortOrder.applyTo(sorted)

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
        if (!filter.status.matches(this.status)) {
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

private val SORTER_BY: Map<SortBy, (Sequence<SessionMetadata>) -> Sequence<SessionMetadata>> = mapOf(
    SortBy.CREATED_AT to { input -> input.sortedBy { item -> item.createdAt } },
    SortBy.UPDATED_AT to { input -> input.sortedBy { item -> item.updatedAt } },
    SortBy.TITLE to { input -> input.sortedBy { item -> item.title } },
)
