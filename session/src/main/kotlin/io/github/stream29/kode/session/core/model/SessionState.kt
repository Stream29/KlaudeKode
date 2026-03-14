package io.github.stream29.kode.session.core.model

import io.github.stream29.kode.agent.model.Agent
import io.github.stream29.kode.agent.model.AgentConfig
import io.github.stream29.kode.agent.model.AgentState
import io.github.stream29.kode.agent.model.SessionMessage
import io.github.stream29.kode.agent.model.SubAgent
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents a conversation session.
 * This is the core data model for session management, independent from Koog.
 */
@Serializable
public data class SessionSnapshot(
    /**
     * Unique identifier for the session.
     */
    val id: String,

    /**
     * Human-readable title for the session.
     */
    val title: String,

    /**
     * When the session was created.
     */
    val createdAt: Instant,

    /**
     * When the session was last modified.
     */
    val updatedAt: Instant,

    /**
     * The conversation history.
     */
    val messages: List<SessionMessage>,

    /**
     * Current status of the session.
     */
    val status: SessionStatus,

    /**
     * ID of the parent session (for forked sessions).
     */
    val parentSessionId: String?,

    /**
     * ID of the specific message where this session was forked from parent.
     */
    val forkedFromMessageId: String?,

    val version: Long,

    /**
     * Session-specific configuration (e.g., model preferences).
     */
    val configuration: SessionConfiguration,

    /**
     * User-defined tags for organization.
     */
    val tags: List<String>,

    /**
     * Child session IDs (forks of this session).
     */
    val childSessionIds: List<String>,

    /**
     * Runtime state that tracks run/suspend ownership.
     */
    val runtimeState: SessionRunState = SessionRunState.Suspended
)

/**
 * Runtime-level session state.
 */
@Serializable
public enum class SessionRunState {
    Running,
    Suspended,
}

/**
 * Status of a conversation session.
 */
@Serializable
public enum class SessionStatus {
    /**
     * Session is active and can be continued.
     */
    ACTIVE,

    /**
     * Session is archived (read-only).
     */
    ARCHIVED,

    /**
     * Session was deleted (soft delete).
     */
    DELETED
}

/**
 * Runtime session object. One instance per SessionId in one process lifecycle.
 */
public data class SessionState(
    val metadata: MutableStateFlow<SessionMetadata>,
    val config: MutableStateFlow<SessionConfig>,
    val agent: MutableStateFlow<Agent>,
    val subagents: MutableStateFlow<PersistentMap<String, SubAgent>>,
    val runJob: MutableStateFlow<Job?>,
    val mutex: Mutex,
)

@Serializable
public data class SessionMetadata(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Int,
    val state: SessionRunState,
    val status: SessionStatus,
    val parentSessionId: String?,
    val forkedFromMessageId: String?,
    val version: Long,
    val tags: List<String>,
    val childSessionIds: List<String>,
)

@Serializable
public data class SessionMetadataCsvRow(
    val id: String,
    val title: String,
    val createdAtIso: String,
    val updatedAtIso: String,
    val messageCount: Int,
    val state: SessionRunState,
    val status: SessionStatus,
    val parentSessionId: String,
    val forkedFromMessageId: String,
    val version: Long,
    val tags: String,
    val childSessionIds: String,
)

/**
 * Configuration for a session.
 */
@Serializable
public data class SessionConfig(
    /**
     * System prompt for this session.
     */
    val systemPrompt: String?,

    /**
     * Working directory for this session.
     */
    val workDir: String? = null,

    /**
     * Maximum number of iterations allowed.
     */
    val maxIterations: Int?,

    /**
     * Temperature setting.
     */
    val temperature: Double?,

    /**
     * Custom configuration values.
     */
    val customValues: Map<String, String>?
)

public typealias SessionConfiguration = SessionConfig

/**
 * Summary information about a session (for listing).
 */
@Serializable
public data class SessionSummary(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Int,
    val status: SessionStatus,
    val state: SessionRunState = SessionRunState.Suspended,
    val hasForks: Boolean,
    val tags: List<String>
)

public fun SessionMetadata.toCsvRow(): SessionMetadataCsvRow {
    return SessionMetadataCsvRow(
        id = id,
        title = title,
        createdAtIso = createdAt.toString(),
        updatedAtIso = updatedAt.toString(),
        messageCount = messageCount,
        state = state,
        status = status,
        parentSessionId = parentSessionId.orEmpty(),
        forkedFromMessageId = forkedFromMessageId.orEmpty(),
        version = version,
        tags = tags.joinToString("|"),
        childSessionIds = childSessionIds.joinToString("|"),
    )
}

public fun SessionMetadataCsvRow.toMetadata(): SessionMetadata {
    return SessionMetadata(
        id = id,
        title = title,
        createdAt = Instant.parse(createdAtIso),
        updatedAt = Instant.parse(updatedAtIso),
        messageCount = messageCount,
        state = state,
        status = status,
        parentSessionId = parentSessionId.ifBlank { null },
        forkedFromMessageId = forkedFromMessageId.ifBlank { null },
        version = version,
        tags = tags.split("|").map { it.trim() }.filter { it.isNotBlank() },
        childSessionIds = childSessionIds.split("|").map { it.trim() }.filter { it.isNotBlank() },
    )
}

public fun SessionState.toSessionSnapshot(): SessionSnapshot {
    val metadataValue = metadata.value
    val agentValue = agent.value
    val configValue = config.value
    return SessionSnapshot(
        id = metadataValue.id,
        title = metadataValue.title,
        createdAt = metadataValue.createdAt,
        updatedAt = metadataValue.updatedAt,
        messages = agentValue.messages.value,
        status = metadataValue.status,
        parentSessionId = metadataValue.parentSessionId,
        forkedFromMessageId = metadataValue.forkedFromMessageId,
        version = metadataValue.version,
        configuration = configValue,
        tags = metadataValue.tags,
        childSessionIds = metadataValue.childSessionIds,
        runtimeState = metadataValue.state,
    )
}

public fun SessionSnapshot.toSessionState(): SessionState {
    val metadataFlow = MutableStateFlow(
        SessionMetadata(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messageCount = messages.size,
            state = runtimeState,
            status = status,
            parentSessionId = parentSessionId,
            forkedFromMessageId = forkedFromMessageId,
            version = version,
            tags = tags,
            childSessionIds = childSessionIds,
        )
    )
    val configFlow = MutableStateFlow(configuration)
    val agentFlow = MutableStateFlow(
        Agent(
            state = MutableStateFlow(if (runtimeState == SessionRunState.Running) AgentState.Running else AgentState.Suspended),
            config = MutableStateFlow(
                AgentConfig(
                    systemPrompt = configuration.systemPrompt,
                    taskDescription = null,
                    expectedResult = null,
                    canInteractWithUser = true,
                )
            ),
            messages = MutableStateFlow(messages.toPersistentList()),
        )
    )
    return SessionState(
        metadata = metadataFlow,
        config = configFlow,
        agent = agentFlow,
        subagents = MutableStateFlow(persistentMapOf()),
        runJob = MutableStateFlow(null),
        mutex = Mutex(),
    )
}

private fun <E> List<E>.toPersistentList(): PersistentList<E> {
    return kotlinx.collections.immutable.persistentListOf<E>().addAll(this)
}
