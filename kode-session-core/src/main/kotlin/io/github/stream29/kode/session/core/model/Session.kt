package io.github.stream29.kode.session.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
    val childSessionIds: List<String>
)

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
 * Configuration for a session.
 */
@Serializable
public data class SessionConfiguration(
    /**
     * Preferred LLM model for this session.
     */
    val preferredModel: String?,
    
    /**
     * System prompt for this session.
     */
    val systemPrompt: String?,
    
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
