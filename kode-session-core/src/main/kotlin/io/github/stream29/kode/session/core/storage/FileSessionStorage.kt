package io.github.stream29.kode.session.core.storage

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.stream29.kode.config.FileLocations
import io.github.stream29.kode.dispatcher.VirtualThread
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based implementation of SessionStorage.
 * Stores sessions as JSON files in a directory structure.
 */
public class FileSessionStorage(
    private val baseDir: File = File(FileLocations.dataDir, "sessions"),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : SessionStorage {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false
        )
    )
    
    init {
        baseDir.mkdirs()
        File(baseDir, "checkpoints").mkdirs()
    }

    override suspend fun saveSession(session: ConversationSession): Unit = withContext(Dispatchers.VirtualThread) {
        val sessionFile = getSessionFile(session.id)
        val jsonString = json.encodeToString(session)
        sessionFile.writeText(jsonString)
    }

    override suspend fun getSession(sessionId: String): ConversationSession? = withContext(Dispatchers.VirtualThread) {
        val sessionFile = getSessionFile(sessionId)
        if (!sessionFile.exists()) {
            return@withContext null
        }
        try {
            json.decodeFromString<ConversationSession>(sessionFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun listSessions(filter: SessionFilter?): List<SessionSummary> = withContext(Dispatchers.VirtualThread) {
        val sessionFiles = baseDir.listFiles { file ->
            file.isFile && file.extension == "json"
        } ?: emptyArray()
        
        val sessions = sessionFiles.mapNotNull { file ->
            try {
                json.decodeFromString<ConversationSession>(file.readText())
            } catch (e: Exception) {
                null
            }
        }
        
        val filtered = sessions.filter { session ->
            if (filter == null) return@filter true
            
            // Status filter
            if (filter.status != null) {
                when (filter.status) {
                    SessionStatusFilter.ACTIVE -> if (session.status != SessionStatus.ACTIVE) return@filter false
                    SessionStatusFilter.ARCHIVED -> if (session.status != SessionStatus.ARCHIVED) return@filter false
                    SessionStatusFilter.ALL -> {}
                }
            }
            
            // Tags filter
            if (filter.tags != null && filter.tags.isNotEmpty()) {
                if (!session.tags.containsAll(filter.tags)) return@filter false
            }
            
            // Search query
            if (filter.searchQuery != null) {
                val query = filter.searchQuery.lowercase()
                if (!session.title.lowercase().contains(query)) {
                    val hasMatchInMessages = session.messages.any {
                        it.content.lowercase().contains(query)
                    }
                    if (!hasMatchInMessages) return@filter false
                }
            }
            
            // Parent session filter
            if (filter.parentSessionId != null) {
                if (session.parentSessionId != filter.parentSessionId) return@filter false
            }
            
            // Date filters
            if (filter.createdAfter != null) {
                if (session.createdAt < filter.createdAfter) return@filter false
            }
            if (filter.createdBefore != null) {
                if (session.createdAt > filter.createdBefore) return@filter false
            }
            
            true
        }
        
        // Sort
        val sorted = when (filter?.sortBy) {
            SortBy.CREATED_AT -> filtered.sortedBy { it.createdAt }
            SortBy.TITLE -> filtered.sortedBy { it.title }
            else -> filtered.sortedBy { it.updatedAt }
        }.let {
            if (filter?.sortOrder == SortOrder.ASCENDING) it else it.reversed()
        }
        
        // Apply limit and offset
        val paginated = sorted.drop(filter?.offset ?: 0).take(filter?.limit ?: Int.MAX_VALUE)
        
        paginated.map { session ->
            SessionSummary(
                id = session.id,
                title = session.title,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                messageCount = session.messages.size,
                status = session.status,
                hasForks = session.childSessionIds.isNotEmpty(),
                tags = session.tags
            )
        }
    }

    override suspend fun deleteSession(sessionId: String, hardDelete: Boolean): Unit = withContext(Dispatchers.VirtualThread) {
        if (hardDelete) {
            getSessionFile(sessionId).delete()
            deleteAllCheckpoints(sessionId)
        } else {
            val session = getSessionId(sessionId)
            if (session != null) {
                saveSession(session.copy(status = SessionStatus.DELETED))
            }
        }
    }

    override suspend fun saveCheckpoint(checkpoint: SessionCheckpoint): Unit = withContext(Dispatchers.VirtualThread) {
        val checkpointDir = getCheckpointDir(checkpoint.sessionId)
        checkpointDir.mkdirs()
        val checkpointFile = File(checkpointDir, "${checkpoint.checkpointId}.json")
        checkpointFile.writeText(json.encodeToString(checkpoint))
        
        // Also save as "latest" for quick access
        val latestFile = File(checkpointDir, "latest.json")
        latestFile.writeText(json.encodeToString(checkpoint))
    }

    override suspend fun getCheckpoints(sessionId: String): List<SessionCheckpoint> = withContext(Dispatchers.VirtualThread) {
        val checkpointDir = getCheckpointDir(sessionId)
        if (!checkpointDir.exists()) return@withContext emptyList()
        
        checkpointDir.listFiles { file ->
            file.isFile && file.extension == "json" && file.name != "latest.json"
        }?.mapNotNull { file ->
            try {
                json.decodeFromString<SessionCheckpoint>(file.readText())
            } catch (e: Exception) {
                null
            }
        }?.sortedBy { it.version } ?: emptyList()
    }

    override suspend fun getLatestCheckpoint(sessionId: String): SessionCheckpoint? = withContext(Dispatchers.VirtualThread) {
        val checkpointDir = getCheckpointDir(sessionId)
        val latestFile = File(checkpointDir, "latest.json")
        if (!latestFile.exists()) {
            return@withContext null
        }
        try {
            json.decodeFromString<SessionCheckpoint>(latestFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCheckpoint(sessionId: String, checkpointId: String): SessionCheckpoint? = withContext(Dispatchers.VirtualThread) {
        val checkpointFile = File(getCheckpointDir(sessionId), "$checkpointId.json")
        if (!checkpointFile.exists()) return@withContext null
        try {
            json.decodeFromString<SessionCheckpoint>(checkpointFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteCheckpoint(sessionId: String, checkpointId: String): Unit = withContext(Dispatchers.VirtualThread) {
        File(getCheckpointDir(sessionId), "$checkpointId.json").delete()
    }

    override suspend fun deleteAllCheckpoints(sessionId: String): Unit = withContext(Dispatchers.VirtualThread) {
        getCheckpointDir(sessionId).deleteRecursively()
    }

    private fun getSessionFile(sessionId: String): File = File(baseDir, "$sessionId.json")
    
    private fun getCheckpointDir(sessionId: String): File = File(baseDir, "checkpoints/$sessionId")
    
    private suspend fun getSessionId(sessionId: String): ConversationSession? = getSession(sessionId)
}
