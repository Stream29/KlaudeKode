package io.github.stream29.kode.session.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
public data class ConversationSession(
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
    
    /**
     * Version number for checkpoint tracking.
     */
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
    val runtimeState: SessionState = SessionState.Suspended
)

/**
 * Runtime-level session state.
 */
@Serializable
public enum class SessionState {
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
 * Agent state in a session runtime.
 */
@Serializable
public enum class AgentState {
    Running,
    Suspended,
}

@Serializable
public data class AgentConfig(
    val systemPrompt: String?,
    val taskDescription: String?,
    val expectedResult: String?,
    val canInteractWithUser: Boolean,
)

public data class Agent(
    val state: MutableStateFlow<AgentState>,
    val config: MutableStateFlow<AgentConfig>,
    val messages: MutableStateFlow<PersistentList<SessionMessage>>,
)

public data class SubAgent(
    val delegate: Agent,
    val result: CompletableDeferred<String>,
)

/**
 * Runtime session object. One instance per SessionId in one process lifecycle.
 */
public data class Session(
    val metadata: MutableStateFlow<SessionMetadata>,
    val config: MutableStateFlow<SessionConfig>,
    val agent: MutableStateFlow<Agent>,
    val subagents: MutableStateFlow<PersistentMap<String, SubAgent>>,
    val checkpoints: MutableStateFlow<PersistentList<SessionCheckpoint>>,
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
    val state: SessionState,
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
    val state: SessionState,
    val status: SessionStatus,
    val parentSessionId: String,
    val forkedFromMessageId: String,
    val version: Long,
    val tags: String,
    val childSessionIds: String,
)

@Serializable
public data class AgentSnapshot(
    val state: AgentState,
    val config: AgentConfig,
    val messages: List<SessionMessage>,
)

@Serializable
public data class SubAgentSnapshot(
    val id: String,
    val delegate: AgentSnapshot,
    val result: String?,
    val completed: Boolean,
)

@Serializable
public data class SessionDataSnapshot(
    val config: SessionConfig,
    val agent: AgentSnapshot,
    val subagents: List<SubAgentSnapshot> = emptyList(),
    val checkpoints: List<SessionCheckpoint> = emptyList(),
)

/**
 * Configuration for a session.
 */
@Serializable
public data class SessionConfig(
    /**
     * Preferred LLM model for this session.
     */
    val preferredModel: String?,
    
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
    val state: SessionState = SessionState.Suspended,
    val hasForks: Boolean,
    val tags: List<String>
)

/**
 * Checkpoint data for saving/restoring session state.
 */
@Serializable
public data class SessionCheckpoint(
    /**
     * Unique identifier for the checkpoint.
     */
    val checkpointId: String,
    
    /**
     * Session ID this checkpoint belongs to.
     */
    val sessionId: String,
    
    /**
     * When the checkpoint was created.
     */
    val createdAt: Instant,
    
    /**
     * Number of messages at the time of checkpoint.
     */
    val messageCount: Int,
    
    /**
     * Messages up to this checkpoint.
     */
    val messages: List<SessionMessage>,
    
    /**
     * Version number.
     */
    val version: Long,
    
    /**
     * Optional description/label for the checkpoint.
     */
    val label: String?,
    
    /**
     * Whether this is a tombstone (session end marker).
     */
    val isTombstone: Boolean
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

public fun Session.toConversationSession(): ConversationSession {
    val metadataValue = metadata.value
    val agentValue = agent.value
    val configValue = config.value
    return ConversationSession(
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

public fun ConversationSession.toSessionRuntime(): Session {
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
            state = MutableStateFlow(if (runtimeState == SessionState.Running) AgentState.Running else AgentState.Suspended),
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
    return Session(
        metadata = metadataFlow,
        config = configFlow,
        agent = agentFlow,
        subagents = MutableStateFlow(persistentMapOf()),
        checkpoints = MutableStateFlow(persistentListOf()),
        runJob = MutableStateFlow(null),
        mutex = Mutex(),
    )
}

public fun SessionDataSnapshot.toRuntime(metadata: SessionMetadata): Session {
    val subagentMap = subagents
        .associate { subagentSnapshot ->
            subagentSnapshot.id to SubAgent(
                delegate = Agent(
                    state = MutableStateFlow(subagentSnapshot.delegate.state),
                    config = MutableStateFlow(subagentSnapshot.delegate.config),
                    messages = MutableStateFlow(subagentSnapshot.delegate.messages.toPersistentList()),
                ),
                result = CompletableDeferred<String>().also { deferred ->
                    if (subagentSnapshot.completed) {
                        deferred.complete(subagentSnapshot.result.orEmpty())
                    }
                },
            )
        }
        .toPersistentMap()

    val normalizedState = if (metadata.state == SessionState.Running) SessionState.Suspended else metadata.state
    val normalizedAgentState = if (agent.state == AgentState.Running) AgentState.Suspended else agent.state

    return Session(
        metadata = MutableStateFlow(metadata.copy(state = normalizedState)),
        config = MutableStateFlow(config),
        agent = MutableStateFlow(
            Agent(
                state = MutableStateFlow(normalizedAgentState),
                config = MutableStateFlow(agent.config),
                messages = MutableStateFlow(agent.messages.toPersistentList()),
            )
        ),
        subagents = MutableStateFlow(subagentMap),
        checkpoints = MutableStateFlow(checkpoints.toPersistentList()),
        runJob = MutableStateFlow(null),
        mutex = Mutex(),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
public fun Session.toSnapshot(): SessionDataSnapshot {
    val agentValue = agent.value
    val subagentSnapshots = subagents.value.entries.map { entry ->
        val subagent = entry.value
        val delegate = subagent.delegate
        SubAgentSnapshot(
            id = entry.key,
            delegate = AgentSnapshot(
                state = delegate.state.value,
                config = delegate.config.value,
                messages = delegate.messages.value,
            ),
            result = if (subagent.result.isCompleted) {
                runCatching { subagent.result.getCompleted() }.getOrNull()
            } else {
                null
            },
            completed = subagent.result.isCompleted,
        )
    }
    return SessionDataSnapshot(
        config = config.value,
        agent = AgentSnapshot(
            state = agentValue.state.value,
            config = agentValue.config.value,
            messages = agentValue.messages.value,
        ),
        subagents = subagentSnapshots,
        checkpoints = checkpoints.value,
    )
}

private fun <E> List<E>.toPersistentList(): PersistentList<E> {
    return kotlinx.collections.immutable.persistentListOf<E>().addAll(this)
}

private fun <K, V> Map<K, V>.toPersistentMap(): PersistentMap<K, V> {
    return kotlinx.collections.immutable.persistentHashMapOf<K, V>().putAll(this)
}
