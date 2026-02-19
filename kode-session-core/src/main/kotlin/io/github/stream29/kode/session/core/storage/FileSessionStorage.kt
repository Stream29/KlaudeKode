package io.github.stream29.kode.session.core.storage

import app.softwork.serialization.csv.CSVFormat
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.AgentMessage
import io.github.stream29.kode.session.core.model.Agent
import io.github.stream29.kode.session.core.model.AgentConfig
import io.github.stream29.kode.session.core.model.AgentState
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.session.core.model.Session
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionConfig
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionMetadata
import io.github.stream29.kode.session.core.model.SessionMetadataCsvRow
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.model.SubAgent
import io.github.stream29.kode.session.core.model.toConversationSession
import io.github.stream29.kode.session.core.model.toCsvRow
import io.github.stream29.kode.session.core.model.toMetadata
import io.github.stream29.kode.session.core.model.toSessionRuntime
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

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
        return withContext(Dispatchers.IO) {
            rwMutex.withLock {
                readMetadataRows().map { row -> row.toMetadata() }
            }
        }
    }

    override suspend fun loadSession(id: String): Session {
        return withContext(Dispatchers.IO) {
            rwMutex.withLock {
                val metadataRow = readMetadataRows().firstOrNull { row -> row.id == id }
                    ?: throw IllegalArgumentException("Session not found: $id")
                val metadata = metadataRow.toMetadata()
                val sessionMeta = readSessionMeta(sessionId = id) ?: SessionFileMeta(config = emptySessionConfig())
                val storedAgentMetas = readAllAgentMetas(sessionId = id)

                val mainAgentId = mainAgentId(sessionId = id)
                val mainAgentMeta = storedAgentMetas
                    .firstOrNull { item -> item.kind == AgentKind.MAIN && item.agentId == mainAgentId }
                    ?: storedAgentMetas.firstOrNull { item -> item.kind == AgentKind.MAIN }
                    ?: defaultAgentMeta(
                        agentId = mainAgentId,
                        kind = AgentKind.MAIN,
                        config = AgentConfig(
                            systemPrompt = sessionMeta.config.systemPrompt,
                            taskDescription = null,
                            expectedResult = null,
                            canInteractWithUser = true,
                        ),
                    )

                val mainAgentMessages = loadWindowMessages(
                    sessionId = id,
                    agentId = mainAgentMeta.agentId,
                    agentMeta = mainAgentMeta,
                ).messages.toPersistentList()

                val subagentMetas = storedAgentMetas.filter { item ->
                    item.kind == AgentKind.SUBAGENT && item.agentId != mainAgentMeta.agentId
                }
                var subagentMap = persistentHashMapOf<String, SubAgent>()
                subagentMetas.forEach { subMeta ->
                    val subMessages = loadWindowMessages(
                        sessionId = id,
                        agentId = subMeta.agentId,
                        agentMeta = subMeta,
                    ).messages.toPersistentList()
                    val deferred = CompletableDeferred<String>()
                    if (subMeta.completed) {
                        deferred.complete(subMeta.result.orEmpty())
                    }
                    val subAgent = SubAgent(
                        delegate = Agent(
                            state = MutableStateFlow(normalizeAgentState(subMeta.state)),
                            config = MutableStateFlow(subMeta.config),
                            messages = MutableStateFlow(subMessages),
                        ),
                        result = deferred,
                    )
                    subagentMap = subagentMap.put(subMeta.agentId, subAgent)
                }

                val normalizedSessionState = if (metadata.state == SessionState.Running) {
                    SessionState.Suspended
                } else {
                    metadata.state
                }
                val totalMainMessageCount = clampToInt(mainAgentMeta.nextSeq)

                Session(
                    metadata = MutableStateFlow(
                        metadata.copy(
                            state = normalizedSessionState,
                            messageCount = totalMainMessageCount,
                        )
                    ),
                    config = MutableStateFlow(sessionMeta.config),
                    agent = MutableStateFlow(
                        Agent(
                            state = MutableStateFlow(normalizeAgentState(mainAgentMeta.state)),
                            config = MutableStateFlow(mainAgentMeta.config),
                            messages = MutableStateFlow(mainAgentMessages),
                        )
                    ),
                    subagents = MutableStateFlow(subagentMap),
                    checkpoints = MutableStateFlow(persistentListOf()),
                    runJob = MutableStateFlow(null),
                    mutex = Mutex(),
                )
            }
        }
    }

    override suspend fun persistSession(id: String, session: Session) {
        withContext(Dispatchers.IO) {
            rwMutex.withLock {
                val sessionFolder = getSessionDirectory(sessionId = id)
                sessionFolder.mkdirs()

                val mainAgentId = mainAgentId(sessionId = id)
                val mainAgentValue = session.agent.value
                val mainMeta = persistAgent(
                    sessionId = id,
                    agentId = mainAgentId,
                    kind = AgentKind.MAIN,
                    state = mainAgentValue.state.value,
                    config = mainAgentValue.config.value,
                    messages = mainAgentValue.messages.value,
                    result = null,
                    completed = false,
                )

                val subagentIds = mutableSetOf(mainAgentId)
                session.subagents.value.forEach { (agentId, subAgent) ->
                    val completed = subAgent.result.isCompleted
                    val resultText = if (completed) {
                        runCatching { subAgent.result.await() }.getOrNull()
                    } else {
                        null
                    }
                    persistAgent(
                        sessionId = id,
                        agentId = agentId,
                        kind = AgentKind.SUBAGENT,
                        state = subAgent.delegate.state.value,
                        config = subAgent.delegate.config.value,
                        messages = subAgent.delegate.messages.value,
                        result = resultText,
                        completed = completed,
                    )
                    subagentIds += agentId
                }

                cleanupStaleAgentDirectories(
                    sessionId = id,
                    validAgentIds = subagentIds,
                )

                writeSessionMeta(
                    sessionId = id,
                    meta = SessionFileMeta(config = session.config.value),
                )

                val metadata = session.metadata.value.copy(
                    messageCount = clampToInt(mainMeta.nextSeq),
                )
                session.metadata.value = metadata
                upsertMetadata(metadata)
            }
        }
    }

    override suspend fun removeSession(id: String) {
        withContext(Dispatchers.IO) {
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
        return runCatching {
            loadSession(sessionId).toConversationSession()
        }.getOrNull()
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

    private fun persistAgent(
        sessionId: String,
        agentId: String,
        kind: AgentKind,
        state: AgentState,
        config: AgentConfig,
        messages: List<SessionMessage>,
        result: String?,
        completed: Boolean,
    ): AgentFileMeta {
        val existingMeta = readAgentMeta(sessionId = sessionId, agentId = agentId)
        val fallbackMeta = defaultAgentMeta(
            agentId = agentId,
            kind = kind,
            config = config,
        )
        val baseMeta = (existingMeta ?: fallbackMeta).copy(
            agentId = agentId,
            kind = kind,
            state = state,
            config = config,
            result = if (kind == AgentKind.SUBAGENT) result else null,
            completed = if (kind == AgentKind.SUBAGENT) completed else false,
        )

        val existingWindow = loadWindowMessages(
            sessionId = sessionId,
            agentId = agentId,
            agentMeta = baseMeta,
        )

        val reconciledMeta = reconcileAgentWindow(
            sessionId = sessionId,
            agentId = agentId,
            existingMeta = baseMeta,
            existingWindow = existingWindow,
            runtimeMessages = messages,
        )

        writeAgentMeta(sessionId = sessionId, meta = reconciledMeta)
        return reconciledMeta
    }

    private fun reconcileAgentWindow(
        sessionId: String,
        agentId: String,
        existingMeta: AgentFileMeta,
        existingWindow: LoadedWindow,
        runtimeMessages: List<SessionMessage>,
    ): AgentFileMeta {
        val activeStartSeq = existingMeta.activeStartSeq.coerceAtLeast(0)
        val nextSeq = existingMeta.nextSeq.coerceAtLeast(activeStartSeq)
        val normalizedMeta = existingMeta.copy(
            activeStartSeq = activeStartSeq,
            nextSeq = nextSeq,
        )

        if (runtimeMessages.isEmpty()) {
            return normalizedMeta.copy(
                activeStartSeq = nextSeq,
                nextSeq = nextSeq,
            )
        }

        if (!existingWindow.complete) {
            val segmentStart = nextSeq
            writeMessages(
                sessionId = sessionId,
                agentId = agentId,
                startSeq = segmentStart,
                messages = runtimeMessages,
            )
            return normalizedMeta.copy(
                activeStartSeq = segmentStart,
                nextSeq = segmentStart + runtimeMessages.size,
            )
        }

        val existingMessages = existingWindow.messages
        if (existingMessages.isEmpty()) {
            val segmentStart = nextSeq
            writeMessages(
                sessionId = sessionId,
                agentId = agentId,
                startSeq = segmentStart,
                messages = runtimeMessages,
            )
            return normalizedMeta.copy(
                activeStartSeq = segmentStart,
                nextSeq = segmentStart + runtimeMessages.size,
            )
        }

        if (existingMessages == runtimeMessages) {
            return normalizedMeta
        }

        val suffixShift = findSuffixShift(
            existing = existingMessages,
            runtime = runtimeMessages,
        )
        if (suffixShift != null) {
            return normalizedMeta.copy(activeStartSeq = activeStartSeq + suffixShift)
        }

        val divergence = firstDivergenceIndex(
            existing = existingMessages,
            runtime = runtimeMessages,
        )

        if (divergence == 0) {
            val segmentStart = nextSeq
            writeMessages(
                sessionId = sessionId,
                agentId = agentId,
                startSeq = segmentStart,
                messages = runtimeMessages,
            )
            return normalizedMeta.copy(
                activeStartSeq = segmentStart,
                nextSeq = segmentStart + runtimeMessages.size,
            )
        }

        val rewriteStartSeq = activeStartSeq + divergence
        deleteMessageRange(
            sessionId = sessionId,
            agentId = agentId,
            startInclusive = rewriteStartSeq,
            endExclusive = nextSeq,
        )
        writeMessages(
            sessionId = sessionId,
            agentId = agentId,
            startSeq = rewriteStartSeq,
            messages = runtimeMessages.drop(divergence),
        )
        return normalizedMeta.copy(nextSeq = activeStartSeq + runtimeMessages.size)
    }

    private fun findSuffixShift(existing: List<SessionMessage>, runtime: List<SessionMessage>): Long? {
        if (runtime.isEmpty() || runtime.size >= existing.size) {
            return null
        }
        val maxStart = existing.size - runtime.size
        var start = 1
        while (start <= maxStart) {
            if (existing.subList(start, existing.size) == runtime) {
                return start.toLong()
            }
            start += 1
        }
        return null
    }

    private fun firstDivergenceIndex(existing: List<SessionMessage>, runtime: List<SessionMessage>): Int {
        val minSize = minOf(existing.size, runtime.size)
        var index = 0
        while (index < minSize) {
            if (existing[index] != runtime[index]) {
                return index
            }
            index += 1
        }
        return minSize
    }

    private fun loadWindowMessages(
        sessionId: String,
        agentId: String,
        agentMeta: AgentFileMeta,
    ): LoadedWindow {
        val start = agentMeta.activeStartSeq.coerceAtLeast(0)
        val end = agentMeta.nextSeq.coerceAtLeast(start)
        if (start == end) {
            return LoadedWindow(messages = emptyList(), complete = true)
        }

        val messages = mutableListOf<SessionMessage>()
        var seq = start
        while (seq < end) {
            val messageFile = getAgentMessageFile(
                sessionId = sessionId,
                agentId = agentId,
                seq = seq,
            )
            if (!messageFile.isFile) {
                return LoadedWindow(messages = messages, complete = false)
            }
            val decoded = runCatching {
                json.decodeFromString(AgentMessage.serializer(), messageFile.readText())
            }.getOrNull()
            if (decoded == null) {
                return LoadedWindow(messages = messages, complete = false)
            }
            messages += decoded
            seq += 1
        }
        return LoadedWindow(messages = messages, complete = true)
    }

    private fun writeMessages(
        sessionId: String,
        agentId: String,
        startSeq: Long,
        messages: List<SessionMessage>,
    ) {
        if (messages.isEmpty()) {
            return
        }
        val messagesDir = getAgentMessagesDirectory(sessionId = sessionId, agentId = agentId)
        messagesDir.mkdirs()
        messages.forEachIndexed { index, message ->
            val seq = startSeq + index
            val messageFile = getAgentMessageFile(
                sessionId = sessionId,
                agentId = agentId,
                seq = seq,
            )
            messageFile.writeText(
                text = json.encodeToString(AgentMessage.serializer(), message),
            )
        }
    }

    private fun deleteMessageRange(
        sessionId: String,
        agentId: String,
        startInclusive: Long,
        endExclusive: Long,
    ) {
        var seq = startInclusive
        while (seq < endExclusive) {
            getAgentMessageFile(
                sessionId = sessionId,
                agentId = agentId,
                seq = seq,
            ).delete()
            seq += 1
        }
    }

    private fun cleanupStaleAgentDirectories(sessionId: String, validAgentIds: Set<String>) {
        val agentsDir = getAgentsDirectory(sessionId = sessionId)
        if (!agentsDir.isDirectory) {
            return
        }
        val validNames = validAgentIds.map { agentId -> encodeAgentId(agentId) }.toSet()
        agentsDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name !in validNames }
            ?.forEach { directory -> directory.deleteRecursively() }
    }

    private fun readSessionMeta(sessionId: String): SessionFileMeta? {
        val file = getSessionMetaFile(sessionId = sessionId)
        if (!file.isFile) {
            return null
        }
        return runCatching {
            json.decodeFromString(SessionFileMeta.serializer(), file.readText())
        }.getOrNull()
    }

    private fun writeSessionMeta(sessionId: String, meta: SessionFileMeta) {
        val file = getSessionMetaFile(sessionId = sessionId)
        file.parentFile?.mkdirs()
        file.writeText(
            text = json.encodeToString(SessionFileMeta.serializer(), meta),
        )
    }

    private fun readAgentMeta(sessionId: String, agentId: String): AgentFileMeta? {
        val file = getAgentMetaFile(sessionId = sessionId, agentId = agentId)
        if (!file.isFile) {
            return null
        }
        return runCatching {
            json.decodeFromString(AgentFileMeta.serializer(), file.readText())
        }.getOrNull()
    }

    private fun writeAgentMeta(sessionId: String, meta: AgentFileMeta) {
        val file = getAgentMetaFile(sessionId = sessionId, agentId = meta.agentId)
        file.parentFile?.mkdirs()
        file.writeText(
            text = json.encodeToString(AgentFileMeta.serializer(), meta),
        )
    }

    private fun readAllAgentMetas(sessionId: String): List<AgentFileMeta> {
        val agentsDir = getAgentsDirectory(sessionId = sessionId)
        if (!agentsDir.isDirectory) {
            return emptyList()
        }
        return agentsDir.listFiles()
            ?.filter { file -> file.isDirectory }
            ?.mapNotNull { directory ->
                val metaFile = File(directory, AGENT_META_FILE_NAME)
                if (!metaFile.isFile) {
                    null
                } else {
                    runCatching {
                        json.decodeFromString(AgentFileMeta.serializer(), metaFile.readText())
                    }.getOrNull()
                }
            }
            ?: emptyList()
    }

    private fun readMetadataRows(): List<SessionMetadataCsvRow> {
        if (!metadataCsvFile.exists()) {
            return emptyList()
        }
        val content = metadataCsvFile.readText().trim()
        if (content.isBlank()) {
            return emptyList()
        }
        return decodeCurrentMetadataRows(content)
            ?: decodeLegacyMetadataRows(content)
            ?: emptyList()
    }

    private fun decodeCurrentMetadataRows(content: String): List<SessionMetadataCsvRow>? {
        return runCatching {
            CSVFormat.decodeFromString(
                deserializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                string = content,
            )
        }.getOrNull()
    }

    private fun decodeLegacyMetadataRows(content: String): List<SessionMetadataCsvRow>? {
        return runCatching {
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
        }.getOrNull()
    }

    private fun emptySessionConfig(): SessionConfig {
        return SessionConfig(
            preferredModel = null,
            systemPrompt = null,
            workDir = null,
            maxIterations = null,
            temperature = null,
            customValues = null,
        )
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

    private fun normalizeAgentState(state: AgentState): AgentState {
        return if (state == AgentState.Running) {
            AgentState.Suspended
        } else {
            state
        }
    }

    private fun mainAgentId(sessionId: String): String {
        return "main-$sessionId"
    }

    private fun defaultAgentMeta(
        agentId: String,
        kind: AgentKind,
        config: AgentConfig,
    ): AgentFileMeta {
        return AgentFileMeta(
            agentId = agentId,
            kind = kind,
            state = AgentState.Suspended,
            config = config,
            activeStartSeq = 0,
            nextSeq = 0,
            result = null,
            completed = false,
        )
    }

    private fun clampToInt(value: Long): Int {
        return value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun getSessionDirectory(sessionId: String): File {
        return File(sessionDirRoot, sessionId)
    }

    private fun getSessionMetaFile(sessionId: String): File {
        return File(getSessionDirectory(sessionId), SESSION_META_FILE_NAME)
    }

    private fun getAgentsDirectory(sessionId: String): File {
        return File(getSessionDirectory(sessionId), AGENTS_DIR_NAME)
    }

    private fun getAgentDirectory(sessionId: String, agentId: String): File {
        return File(getAgentsDirectory(sessionId), encodeAgentId(agentId))
    }

    private fun getAgentMetaFile(sessionId: String, agentId: String): File {
        return File(getAgentDirectory(sessionId, agentId), AGENT_META_FILE_NAME)
    }

    private fun getAgentMessagesDirectory(sessionId: String, agentId: String): File {
        return File(getAgentDirectory(sessionId, agentId), MESSAGES_DIR_NAME)
    }

    private fun getAgentMessageFile(sessionId: String, agentId: String, seq: Long): File {
        return File(getAgentMessagesDirectory(sessionId, agentId), "$seq.json")
    }

    private fun encodeAgentId(agentId: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(agentId.toByteArray(Charsets.UTF_8))
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

    @Serializable
    private data class SessionFileMeta(
        val config: SessionConfig,
    )

    @Serializable
    private data class AgentFileMeta(
        val agentId: String,
        val kind: AgentKind,
        val state: AgentState,
        val config: AgentConfig,
        val activeStartSeq: Long,
        val nextSeq: Long,
        val result: String?,
        val completed: Boolean,
    )

    @Serializable
    private enum class AgentKind {
        MAIN,
        SUBAGENT,
    }

    private data class LoadedWindow(
        val messages: List<SessionMessage>,
        val complete: Boolean,
    )

    private companion object {
        private const val SESSION_META_FILE_NAME: String = "meta.json"
        private const val AGENTS_DIR_NAME: String = "agents"
        private const val AGENT_META_FILE_NAME: String = "meta.json"
        private const val MESSAGES_DIR_NAME: String = "messages"
    }
}
