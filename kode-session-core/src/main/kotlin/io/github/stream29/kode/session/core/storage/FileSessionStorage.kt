package io.github.stream29.kode.session.core.storage

import app.softwork.serialization.csv.CSVFormat
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.dispatcher.VirtualThread
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.session.core.model.Session
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionDataSnapshot
import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionMetadataCsvRow
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.model.toCsvRow
import io.github.stream29.kode.session.core.model.toMetadata
import io.github.stream29.kode.session.core.model.toSnapshot
import io.github.stream29.kode.session.core.model.toConversationSession
import io.github.stream29.kode.session.core.model.toSessionRuntime
import io.github.stream29.kode.session.core.model.toRuntime
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
public class FileSessionStorage(
    dataDir: File = FileSystemLocations.dataDir,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : SessionStorage, SessionRepository {

    private val sessionDirRoot: File = File(dataDir, "sessions")
    private val metadataCsvFile: File = File(dataDir, "session-meta.csv")
    private val rwMutex: Mutex = Mutex()

    init {
        dataDir.mkdirs()
        sessionDirRoot.mkdirs()
    }

    override suspend fun listSessions(): List<SessionMetadata> {
        return withContext(Dispatchers.VirtualThread) {
            rwMutex.withLock {
                readMetadataRows().map { it.toMetadata() }
            }
        }
    }

    override suspend fun loadSession(id: String): Session {
        return withContext(Dispatchers.VirtualThread) {
            rwMutex.withLock {
                val metadataMap = readMetadataRows().associateBy { it.id }
                val metadataRow = metadataMap[id]
                    ?: throw IllegalArgumentException("Session not found: $id")
                val metadata = metadataRow.toMetadata()
                val sessionDataFile = getSessionDataFile(id)

                val loadedRuntime = if (sessionDataFile.isFile) {
                    val snapshot = json.decodeFromString(SessionDataSnapshot.serializer(), sessionDataFile.readText())
                    snapshot.toRuntime(metadata)
                } else {
                    buildEmptyRuntime(metadata)
                }

                val normalizedMetadata = if (loadedRuntime.metadata.value.state == SessionState.Running) {
                    loadedRuntime.metadata.value.copy(state = SessionState.Suspended)
                } else {
                    loadedRuntime.metadata.value
                }

                loadedRuntime.metadata.value = metadata.copy(
                    state = normalizedMetadata.state,
                )
                loadedRuntime
            }
        }
    }

    override suspend fun persistSession(id: String, session: Session) {
        withContext(Dispatchers.VirtualThread) {
            rwMutex.withLock {
                val metadata = session.metadata.value.copy(
                    messageCount = session.agent.value.messages.value.size,
                )
                session.metadata.value = metadata
                upsertMetadata(metadata)

                val sessionFolder = getSessionDirectory(id)
                sessionFolder.mkdirs()
                val sessionDataFile = getSessionDataFile(id)
                val snapshot = session.toSnapshot()
                sessionDataFile.writeText(
                    text = json.encodeToString(SessionDataSnapshot.serializer(), snapshot),
                )
            }
        }
    }

    override suspend fun removeSession(id: String) {
        withContext(Dispatchers.VirtualThread) {
            rwMutex.withLock {
                val filtered = readMetadataRows().filterNot { row -> row.id == id }
                writeMetadataRows(filtered)
                getSessionDirectory(id).deleteRecursively()
            }
        }
    }

    override suspend fun saveSession(session: ConversationSession) {
        persistSession(session.id, session.toSessionRuntime())
    }

    override suspend fun getSession(sessionId: String): ConversationSession? {
        return try {
            loadSession(sessionId).toConversationSession()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun listSessions(filter: SessionFilter?): List<SessionSummary> {
        val metadataList = listSessions()
        return querySessionSummaries(metadataList, filter)
    }

    override suspend fun deleteSession(sessionId: String, hardDelete: Boolean) {
        if (hardDelete) {
            removeSession(sessionId)
            return
        }

        val session = getSession(sessionId) ?: return
        saveSession(session.copy(status = SessionStatus.DELETED))
    }

    override suspend fun saveCheckpoint(checkpoint: SessionCheckpoint) {
        val runtime = loadSession(checkpoint.sessionId)
        runtime.checkpoints.value = runtime.checkpoints.value.add(checkpoint)
        persistSession(checkpoint.sessionId, runtime)
    }

    override suspend fun getCheckpoints(sessionId: String): List<SessionCheckpoint> {
        return runCatching {
            loadSession(sessionId).checkpoints.value
        }.getOrDefault(emptyList())
    }

    override suspend fun getLatestCheckpoint(sessionId: String): SessionCheckpoint? {
        return getCheckpoints(sessionId).maxByOrNull { checkpoint -> checkpoint.version }
    }

    override suspend fun getCheckpoint(sessionId: String, checkpointId: String): SessionCheckpoint? {
        return getCheckpoints(sessionId).firstOrNull { checkpoint -> checkpoint.checkpointId == checkpointId }
    }

    override suspend fun deleteCheckpoint(sessionId: String, checkpointId: String) {
        val runtime = loadSession(sessionId)
        runtime.checkpoints.value = runtime.checkpoints.value
            .filterNot { checkpoint -> checkpoint.checkpointId == checkpointId }
            .toPersistentList()
        persistSession(sessionId, runtime)
    }

    override suspend fun deleteAllCheckpoints(sessionId: String) {
        val runtime = loadSession(sessionId)
        runtime.checkpoints.value = persistentListOf()
        persistSession(sessionId, runtime)
    }

    private fun buildEmptyRuntime(metadata: SessionMetadata): Session {
        val emptySession = ConversationSession(
            id = metadata.id,
            title = metadata.title,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt,
            messages = emptyList(),
            status = metadata.status,
            parentSessionId = metadata.parentSessionId,
            forkedFromMessageId = metadata.forkedFromMessageId,
            version = metadata.version,
            configuration = io.github.stream29.kode.session.core.model.SessionConfig(
                preferredModel = null,
                systemPrompt = null,
                workDir = null,
                maxIterations = null,
                temperature = null,
                customValues = null,
            ),
            tags = metadata.tags,
            childSessionIds = metadata.childSessionIds,
            runtimeState = SessionState.Suspended,
        )
        return emptySession.toSessionRuntime()
    }

    private fun readMetadataRows(): List<SessionMetadataCsvRow> {
        if (!metadataCsvFile.exists()) {
            return emptyList()
        }
        val content = metadataCsvFile.readText().trim()
        if (content.isBlank()) {
            return emptyList()
        }
        return runCatching {
            CSVFormat.decodeFromString(
                deserializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                string = content,
            )
        }.getOrElse {
            runCatching {
                CSVFormat.decodeFromString(
                    deserializer = ListSerializer(LegacySessionMetadataCsvRow.serializer()),
                    string = content,
                ).map { legacy ->
                    SessionMetadataCsvRow(
                        id = legacy.id,
                        title = legacy.title,
                        createdAtIso = legacy.createdAtIso,
                        updatedAtIso = legacy.updatedAtIso,
                        messageCount = 0,
                        state = legacy.state,
                        status = legacy.status,
                        parentSessionId = legacy.parentSessionId,
                        forkedFromMessageId = legacy.forkedFromMessageId,
                        version = legacy.version,
                        tags = legacy.tags,
                        childSessionIds = legacy.childSessionIds,
                    )
                }
            }.getOrElse {
                emptyList()
            }
        }
    }

    private fun writeMetadataRows(rows: List<SessionMetadataCsvRow>) {
        metadataCsvFile.parentFile?.mkdirs()
        val serialized = if (rows.isEmpty()) {
            ""
        } else {
            CSVFormat.encodeToString(
                serializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                value = rows,
            )
        }
        metadataCsvFile.writeText(serialized)
    }

    private fun upsertMetadata(metadata: SessionMetadata) {
        val rows = readMetadataRows().toMutableList()
        val index = rows.indexOfFirst { row -> row.id == metadata.id }
        val row = metadata.toCsvRow()
        if (index >= 0) {
            rows[index] = row
        } else {
            rows += row
        }
        writeMetadataRows(rows)
    }

    private fun getSessionDirectory(sessionId: String): File {
        return File(sessionDirRoot, sessionId)
    }

    private fun getSessionDataFile(sessionId: String): File {
        return File(getSessionDirectory(sessionId), "session.json")
    }

    private fun <E> List<E>.toPersistentList(): PersistentList<E> {
        return persistentListOf<E>().addAll(this)
    }

    @Serializable
    private data class LegacySessionMetadataCsvRow(
        val id: String,
        val title: String,
        val createdAtIso: String,
        val updatedAtIso: String,
        val state: SessionState,
        val status: SessionStatus,
        val parentSessionId: String,
        val forkedFromMessageId: String,
        val version: Long,
        val tags: String,
        val childSessionIds: String,
    )
}
