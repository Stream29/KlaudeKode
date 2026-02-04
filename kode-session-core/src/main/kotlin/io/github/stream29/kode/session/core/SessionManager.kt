package io.github.stream29.kode.session.core

import io.github.stream29.kode.session.core.model.*
import io.github.stream29.kode.session.core.storage.SessionStorage
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages conversation sessions including creation, forking, reverting, and persistence.
 */
@OptIn(ExperimentalUuidApi::class)
public class SessionManager(
    private val storage: SessionStorage
) {
    private val clock = Clock.System
    
    /**
     * Create a new session.
     */
    public suspend fun createSession(
        title: String,
        systemPrompt: String?,
        tags: List<String>,
        configuration: SessionConfiguration
    ): ConversationSession {
        val sessionId = generateId()
        val now = clock.now()
        
        val session = ConversationSession(
            id = sessionId,
            title = title,
            createdAt = now,
            updatedAt = now,
            messages = emptyList(),
            status = SessionStatus.ACTIVE,
            parentSessionId = null,
            forkedFromMessageId = null,
            configuration = configuration.copy(systemPrompt = systemPrompt ?: configuration.systemPrompt),
            tags = tags,
            version = 1L,
            childSessionIds = emptyList()
        )
        
        storage.saveSession(session)
        return session
    }
    
    /**
     * Get a session by ID.
     */
    public suspend fun getSession(sessionId: String): ConversationSession? {
        return storage.getSession(sessionId)
    }
    
    /**
     * Add a message to a session.
     */
    public suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        structuredData: kotlinx.serialization.json.JsonElement?,
        contentType: ContentType,
        metadata: Map<String, String>?
    ): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val message = SessionMessage(
            id = generateId(),
            role = role,
            content = content,
            structuredData = structuredData,
            contentType = contentType,
            timestamp = clock.now(),
            metadata = metadata
        )
        
        val updatedSession = session.copy(
            messages = session.messages + message,
            updatedAt = clock.now(),
            version = session.version + 1
        )
        
        storage.saveSession(updatedSession)
        return updatedSession
    }
    
    /**
     * Add a user message to a session.
     */
    public suspend fun addUserMessage(sessionId: String, content: String): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content,
            structuredData = null,
            contentType = ContentType.TEXT,
            metadata = null
        )
    }
    
    /**
     * Add an assistant message to a session.
     */
    public suspend fun addAssistantMessage(
        sessionId: String,
        content: String,
        metadata: Map<String, String>?
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = content,
            structuredData = null,
            contentType = ContentType.TEXT,
            metadata = metadata
        )
    }
    
    /**
     * Add a tool call to a session.
     */
    public suspend fun addToolCall(
        sessionId: String,
        toolCallData: ToolCallData
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.TOOL_CALL,
            content = "Calling tool: ${toolCallData.toolName}",
            structuredData = kotlinx.serialization.json.Json.encodeToJsonElement(ToolCallData.serializer(), toolCallData),
            contentType = ContentType.TOOL_CALL,
            metadata = null
        )
    }
    
    /**
     * Add a tool result to a session.
     */
    public suspend fun addToolResult(
        sessionId: String,
        toolResultData: ToolResultData
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.TOOL_RESULT,
            content = if (toolResultData.isError) "Error: ${toolResultData.errorMessage}" else "Tool result",
            structuredData = kotlinx.serialization.json.Json.encodeToJsonElement(ToolResultData.serializer(), toolResultData),
            contentType = if (toolResultData.isError) ContentType.ERROR else ContentType.TOOL_RESULT,
            metadata = null
        )
    }
    
    /**
     * Create a checkpoint of the current session state.
     */
    public suspend fun createCheckpoint(
        sessionId: String,
        label: String?
    ): SessionCheckpoint {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val checkpoint = SessionCheckpoint(
            checkpointId = generateId(),
            sessionId = sessionId,
            createdAt = clock.now(),
            messageCount = session.messages.size,
            messages = session.messages,
            version = session.version,
            label = label,
            isTombstone = false
        )
        
        storage.saveCheckpoint(checkpoint)
        return checkpoint
    }
    
    /**
     * Revert a session to a specific checkpoint.
     */
    public suspend fun revertToCheckpoint(
        sessionId: String,
        checkpointId: String
    ): ConversationSession {
        val checkpoint = storage.getCheckpoint(sessionId, checkpointId)
            ?: throw IllegalArgumentException("Checkpoint not found: $checkpointId")
        
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val revertedSession = session.copy(
            messages = checkpoint.messages,
            updatedAt = clock.now(),
            version = checkpoint.version + 1
        )
        
        storage.saveSession(revertedSession)
        return revertedSession
    }
    
    /**
     * Revert a session to its latest checkpoint.
     */
    public suspend fun revertToLatestCheckpoint(sessionId: String): ConversationSession? {
        val checkpoint = storage.getLatestCheckpoint(sessionId)
            ?: return null
        return revertToCheckpoint(sessionId, checkpoint.checkpointId)
    }
    
    /**
     * Fork a session at a specific message.
     * Creates a new session with messages up to and including the specified message.
     */
    public suspend fun forkSession(
        parentSessionId: String,
        atMessageId: String?,
        newTitle: String?
    ): ConversationSession {
        val parentSession = storage.getSession(parentSessionId)
            ?: throw IllegalArgumentException("Parent session not found: $parentSessionId")
        
        val messages = if (atMessageId != null) {
            val index = parentSession.messages.indexOfFirst { it.id == atMessageId }
            if (index == -1) {
                throw IllegalArgumentException("Message not found: $atMessageId")
            }
            parentSession.messages.take(index + 1)
        } else {
            parentSession.messages.toList()
        }
        
        val newSessionId = generateId()
        val now = clock.now()
        
        val forkedSession = ConversationSession(
            id = newSessionId,
            title = newTitle ?: "${parentSession.title} (Fork)",
            createdAt = now,
            updatedAt = now,
            messages = messages,
            status = SessionStatus.ACTIVE,
            parentSessionId = parentSessionId,
            forkedFromMessageId = atMessageId,
            configuration = parentSession.configuration,
            tags = parentSession.tags,
            version = 1L,
            childSessionIds = emptyList()
        )
        
        storage.saveSession(forkedSession)
        
        // Update parent with child reference
        val updatedParent = parentSession.copy(
            childSessionIds = parentSession.childSessionIds + newSessionId,
            updatedAt = now
        )
        storage.saveSession(updatedParent)
        
        return forkedSession
    }
    
    /**
     * Duplicate a session (creates an independent copy).
     */
    public suspend fun duplicateSession(
        sessionId: String,
        newTitle: String?
    ): ConversationSession {
        val original = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val newSessionId = generateId()
        val now = clock.now()
        
        val duplicated = ConversationSession(
            id = newSessionId,
            title = newTitle ?: "${original.title} (Copy)",
            createdAt = now,
            updatedAt = now,
            messages = original.messages.map { it.copy(id = generateId()) },
            status = SessionStatus.ACTIVE,
            parentSessionId = null,
            forkedFromMessageId = null,
            configuration = original.configuration,
            tags = original.tags,
            version = 1L,
            childSessionIds = emptyList()
        )
        
        storage.saveSession(duplicated)
        return duplicated
    }
    
    /**
     * Archive a session (mark as read-only).
     */
    public suspend fun archiveSession(sessionId: String): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val archived = session.copy(
            status = SessionStatus.ARCHIVED,
            updatedAt = clock.now()
        )
        
        storage.saveSession(archived)
        return archived
    }

    /**
     * Restore an archived session to active status.
     */
    public suspend fun restoreSession(sessionId: String): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val restored = session.copy(
            status = SessionStatus.ACTIVE,
            updatedAt = clock.now()
        )

        storage.saveSession(restored)
        return restored
    }
    
    /**
     * Delete a session.
     */
    public suspend fun deleteSession(sessionId: String, hardDelete: Boolean) {
        storage.deleteSession(sessionId, hardDelete)
    }
    
    /**
     * List all sessions.
     */
    public suspend fun listSessions(filter: io.github.stream29.kode.session.core.storage.SessionFilter?): List<SessionSummary> {
        return storage.listSessions(filter)
    }

    /**
     * Export a session to a JSON file.
     */
    public suspend fun exportSession(sessionId: String, targetFile: File) {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(json.encodeToString(session))
    }

    /**
     * Import a session from a JSON file. Creates a new session ID.
     */
    public suspend fun importSession(sourceFile: File, newTitle: String?): ConversationSession {
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Import file not found: ${sourceFile.absolutePath}")
        }
        val json = Json {
            ignoreUnknownKeys = true
        }
        val imported = json.decodeFromString<ConversationSession>(sourceFile.readText())
        val now = clock.now()
        val session = imported.copy(
            id = generateId(),
            title = newTitle ?: imported.title,
            createdAt = now,
            updatedAt = now,
            status = SessionStatus.ACTIVE,
            parentSessionId = null,
            forkedFromMessageId = null,
            version = 1L,
            childSessionIds = emptyList()
        )
        storage.saveSession(session)
        return session
    }
    
    /**
     * Update session title.
     */
    public suspend fun updateTitle(sessionId: String, newTitle: String): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val updated = session.copy(
            title = newTitle,
            updatedAt = clock.now()
        )
        
        storage.saveSession(updated)
        return updated
    }
    
    /**
     * Add tags to a session.
     */
    public suspend fun addTags(sessionId: String, tags: List<String>): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val updated = session.copy(
            tags = (session.tags + tags).distinct(),
            updatedAt = clock.now()
        )
        
        storage.saveSession(updated)
        return updated
    }
    
    /**
     * Remove tags from a session.
     */
    public suspend fun removeTags(sessionId: String, tags: List<String>): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val updated = session.copy(
            tags = session.tags - tags.toSet(),
            updatedAt = clock.now()
        )
        
        storage.saveSession(updated)
        return updated
    }
    
    /**
     * Get all checkpoints for a session.
     */
    public suspend fun getCheckpoints(sessionId: String): List<SessionCheckpoint> {
        return storage.getCheckpoints(sessionId)
    }
    
    /**
     * Delete a checkpoint.
     */
    public suspend fun deleteCheckpoint(sessionId: String, checkpointId: String) {
        storage.deleteCheckpoint(sessionId, checkpointId)
    }
    
    /**
     * Clear all messages from a session (but keep the session).
     */
    public suspend fun clearMessages(sessionId: String): ConversationSession {
        val session = storage.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val cleared = session.copy(
            messages = emptyList(),
            updatedAt = clock.now(),
            version = session.version + 1
        )
        
        storage.saveSession(cleared)
        return cleared
    }
    
    private fun generateId(): String = Uuid.random().toString()
}
