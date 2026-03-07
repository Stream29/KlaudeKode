package io.github.stream29.kode.session.core.storage

import app.softwork.serialization.csv.CSVFormat
import io.github.stream29.kode.agent.model.*
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.*
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
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
public class FileSessionStorage(
    dataDir: File = FileSystemLocations.dataDir,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) : SessionStorage, SessionRepository {

    private val rootStorageRepository: RootSessionFileRepository = RootSessionFileRepository(dataDir = dataDir)
    private val sessionDirRoot: File = rootStorageRepository.sessionsDirectory
    private val sessionIndexFile: File = rootStorageRepository.sessionIndexFile
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

    override suspend fun loadSession(id: String): SessionState {
        return withContext(Dispatchers.IO) {
            rwMutex.withLock {
                val metadataRow = readMetadataRows().firstOrNull { row -> row.id == id }
                    ?: throw IllegalArgumentException("Session not found: $id")
                val metadata = metadataRow.toMetadata()
                val sessionMeta = readSessionMeta(sessionId = id)
                    ?: throw IllegalStateException("Session meta missing: $id")
                val storedAgentMetas = readAllAgentMetas(sessionId = id)

                val mainAgentId = mainAgentId(sessionId = id)
                val mainAgentMeta = storedAgentMetas
                    .firstOrNull { item -> item.kind == AgentKind.MAIN && item.agentId == mainAgentId }
                    ?: throw IllegalStateException("Main agent meta missing for session: $id")
                val mainAgentTodo = readAgentTodo(sessionId = id, agentId = mainAgentMeta.agentId)

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
                    val subAgentTodo = readAgentTodo(sessionId = id, agentId = subMeta.agentId)
                    val subAgent = SubAgent(
                        delegate = Agent(
                            state = MutableStateFlow(normalizeAgentState(subMeta.state)),
                            config = MutableStateFlow(subMeta.config),
                            messages = MutableStateFlow(subMessages),
                            todoState = MutableStateFlow(subAgentTodo),
                        ),
                        result = deferred,
                    )
                    subagentMap = subagentMap.put(subMeta.agentId, subAgent)
                }

                val normalizedSessionState = if (metadata.state == SessionRunState.Running) {
                    SessionRunState.Suspended
                } else {
                    metadata.state
                }
                val totalMainMessageCount = clampToInt(mainAgentMeta.nextSeq)

                SessionState(
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
                            todoState = MutableStateFlow(mainAgentTodo),
                        )
                    ),
                    subagents = MutableStateFlow(subagentMap),
                    runJob = MutableStateFlow(null),
                    mutex = Mutex(),
                )
            }
        }
    }

    override suspend fun persistSession(id: String, session: SessionState) {
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
                    todos = mainAgentValue.todoState.value,
                    result = null,
                    completed = false,
                )

                val subagentIds = mutableSetOf(mainAgentId)
                session.subagents.value.forEach { (agentId, subAgent) ->
                    val completed = subAgent.result.isCompleted
                    val resultText = if (completed) {
                        subAgent.result.await()
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
                        todos = subAgent.delegate.todoState.value,
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

    override suspend fun saveSession(session: SessionSnapshot) {
        persistSession(session.id, session.toSessionState())
    }

    override suspend fun getSession(sessionId: String): SessionSnapshot? {
        return try {
            loadSession(sessionId).toSessionSnapshot()
        } catch (error: IllegalArgumentException) {
            if (error.message == "Session not found: $sessionId") {
                null
            } else {
                throw error
            }
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

    private fun persistAgent(
        sessionId: String,
        agentId: String,
        kind: AgentKind,
        state: AgentState,
        config: AgentConfig,
        messages: List<SessionMessage>,
        todos: List<TodoItem>,
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

        val (baseMetaForReconcile, existingWindow) = loadWindowForPersist(
            sessionId = sessionId,
            agentId = agentId,
            baseMeta = baseMeta,
        )

        val reconciledMeta = reconcileAgentWindow(
            sessionId = sessionId,
            agentId = agentId,
            existingMeta = baseMetaForReconcile,
            existingWindow = existingWindow,
            runtimeMessages = messages,
        )

        val reconciledWithTodo = reconciledMeta.copy(todo = todos)
        writeAgentMeta(sessionId = sessionId, meta = reconciledWithTodo)
        return reconciledWithTodo
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

    private fun loadWindowForPersist(
        sessionId: String,
        agentId: String,
        baseMeta: AgentFileMeta,
    ): Pair<AgentFileMeta, LoadedWindow> {
        return runCatching {
            baseMeta to loadWindowMessages(
                sessionId = sessionId,
                agentId = agentId,
                agentMeta = baseMeta,
            )
        }.getOrElse { error ->
            if (error !is IllegalStateException || !error.isMissingMessageFileFailure()) {
                throw error
            }
            val normalizedMeta = baseMeta.copy(activeStartSeq = baseMeta.nextSeq.coerceAtLeast(baseMeta.activeStartSeq))
            normalizedMeta to LoadedWindow(messages = emptyList())
        }
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

    private fun IllegalStateException.isMissingMessageFileFailure(): Boolean {
        return message.orEmpty().startsWith("Session message file missing:")
    }

    private fun loadWindowMessages(
        sessionId: String,
        agentId: String,
        agentMeta: AgentFileMeta,
    ): LoadedWindow {
        val start = agentMeta.activeStartSeq.coerceAtLeast(0)
        val end = agentMeta.nextSeq.coerceAtLeast(start)
        if (start == end) {
            return LoadedWindow(messages = emptyList())
        }

        val messages = mutableListOf<SessionMessage>()
        var seq = start
        while (seq < end) {
            val messageFile = getAgentMessageFileForRead(
                sessionId = sessionId,
                agentId = agentId,
                seq = seq,
            ) ?: throw IllegalStateException(
                "Session message file missing: sessionId=$sessionId, agentId=$agentId, seq=$seq"
            )
            val decoded = try {
                json.decodeFromString(AgentMessage.serializer(), messageFile.readText())
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Failed to decode session message: sessionId=$sessionId, agentId=$agentId, seq=$seq",
                    error,
                )
            }
            messages += decoded
            seq += 1
        }
        return LoadedWindow(messages = messages)
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
        val sessionRepository = getSessionFileRepository(sessionId = sessionId)
        val subAgentsDir = sessionRepository.subAgentsDirectory
        if (!subAgentsDir.isDirectory) {
            return
        }
        val mainAgentId = mainAgentId(sessionId = sessionId)
        val validNames = validAgentIds
            .filter { agentId -> agentId != mainAgentId }
            .map { agentId -> encodeAgentId(agentId) }
            .toSet()
        subAgentsDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name !in validNames }
            ?.forEach { directory -> directory.deleteRecursively() }
    }

    private fun readSessionMeta(sessionId: String): SessionFileMeta? {
        val file = getSessionMetaFileForRead(sessionId = sessionId)
        if (file == null) {
            return null
        }
        return try {
            json.decodeFromString(SessionFileMeta.serializer(), file.readText())
        } catch (error: Exception) {
            throw IllegalStateException("Failed to decode session meta: sessionId=$sessionId", error)
        }
    }

    private fun writeSessionMeta(sessionId: String, meta: SessionFileMeta) {
        val file = getSessionMetaFile(sessionId = sessionId)
        file.parentFile?.mkdirs()
        file.writeText(
            text = json.encodeToString(SessionFileMeta.serializer(), meta),
        )
    }

    private fun readAgentMeta(sessionId: String, agentId: String): AgentFileMeta? {
        val file = getAgentMetaFileForRead(
            sessionId = sessionId,
            agentId = agentId,
        )
        if (file == null) {
            return null
        }
        val decoded = try {
            json.decodeFromString(AgentFileMeta.serializer(), file.readText())
        } catch (error: Exception) {
            throw IllegalStateException(
                "Failed to decode agent meta: sessionId=$sessionId, agentId=$agentId",
                error,
            )
        }
        return validateCanonicalAgentMeta(
            sessionId = sessionId,
            expectedAgentId = agentId,
            expectedKind = inferAgentKind(sessionId = sessionId, agentId = agentId),
            meta = decoded,
        )
    }

    override suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoItem> =
        withContext(Dispatchers.IO) {
            val meta = readAgentMeta(sessionId = sessionId, agentId = agentId)
                ?: throw IllegalStateException(
                    "Agent metadata missing: sessionId=$sessionId, agentId=$agentId"
                )
            return@withContext meta.todo
        }

    private fun writeAgentMeta(sessionId: String, meta: AgentFileMeta) {
        val file = getAgentMetaFile(sessionId = sessionId, agentId = meta.agentId)
        file.parentFile?.mkdirs()
        file.writeText(
            text = json.encodeToString(AgentFileMeta.serializer(), meta),
        )
    }

    override suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoItem>): Unit =
        withContext(Dispatchers.IO) {
            writeAgentTodoSync(sessionId, agentId, todos)
        }

    private fun writeAgentTodoSync(sessionId: String, agentId: String, todos: List<TodoItem>) {
        val currentMeta = readAgentMeta(sessionId = sessionId, agentId = agentId)
            ?: throw IllegalStateException(
                "Agent metadata missing: sessionId=$sessionId, agentId=$agentId"
            )
        writeAgentMeta(
            sessionId = sessionId,
            meta = currentMeta.copy(
                todo = todos,
            ),
        )
    }

    private fun readAllAgentMetas(sessionId: String): List<AgentFileMeta> {
        val sessionRepository = getSessionFileRepository(sessionId = sessionId)
        val directories = sessionRepository.listAgentDirectoriesForRead()
        val distinctByAgentId = linkedMapOf<String, AgentFileMeta>()
        directories.forEach { directory ->
            val metadataFile = File(directory, AGENT_METADATA_FILE_NAME)
            if (!metadataFile.isFile) {
                return@forEach
            }
            val decoded = try {
                json.decodeFromString(AgentFileMeta.serializer(), metadataFile.readText())
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Failed to decode agent meta file: sessionId=$sessionId, path=${metadataFile.absolutePath}",
                    error,
                )
            }
            val expectedIdentity = resolveExpectedAgentIdentity(
                sessionId = sessionId,
                sessionRepository = sessionRepository,
                directory = directory,
            )
            val canonicalMeta = validateCanonicalAgentMeta(
                sessionId = sessionId,
                expectedAgentId = expectedIdentity.agentId,
                expectedKind = expectedIdentity.kind,
                meta = decoded,
            )
            distinctByAgentId.putIfAbsent(expectedIdentity.agentId, canonicalMeta)
        }
        return distinctByAgentId.values.toList()
    }

    private fun validateCanonicalAgentMeta(
        sessionId: String,
        expectedAgentId: String,
        expectedKind: AgentKind,
        meta: AgentFileMeta,
    ): AgentFileMeta {
        if (meta.agentId != expectedAgentId) {
            throw IllegalStateException(
                "Canonical agent metadata required: sessionId=$sessionId, expectedAgentId=$expectedAgentId, actualAgentId=${meta.agentId}"
            )
        }
        if (meta.kind != expectedKind) {
            throw IllegalStateException(
                "Canonical agent metadata required: sessionId=$sessionId, agentId=$expectedAgentId, expectedKind=$expectedKind, actualKind=${meta.kind}"
            )
        }
        return meta
    }

    private fun resolveExpectedAgentIdentity(
        sessionId: String,
        sessionRepository: SessionFileRepository,
        directory: File,
    ): ExpectedAgentIdentity {
        val normalizedDirectory = directory.absoluteFile
        val mainAgentDirectory = sessionRepository.mainAgentDirectory.absoluteFile
        if (normalizedDirectory == mainAgentDirectory) {
            return ExpectedAgentIdentity(
                agentId = mainAgentId(sessionId = sessionId),
                kind = AgentKind.MAIN,
            )
        }

        val subAgentsDirectory = sessionRepository.subAgentsDirectory.absoluteFile
        if (normalizedDirectory.parentFile?.absoluteFile == subAgentsDirectory) {
            val decodedAgentId = decodeAgentId(encodedAgentId = normalizedDirectory.name)
            return ExpectedAgentIdentity(
                agentId = decodedAgentId,
                kind = AgentKind.SUBAGENT,
            )
        }

        throw IllegalStateException(
            "Unexpected agent directory: sessionId=$sessionId, path=${directory.absolutePath}"
        )
    }

    private fun readMetadataRows(): List<SessionMetadataCsvRow> {
        val sourceFile = rootStorageRepository.resolveSessionIndexFileForRead()
        if (sourceFile == null) {
            return emptyList()
        }
        val content = sourceFile.readText().trim()
        if (content.isBlank()) {
            return emptyList()
        }
        return decodeCurrentMetadataRows(
            content = content,
            sourceFile = sourceFile,
        )
    }

    private fun decodeCurrentMetadataRows(
        content: String,
        sourceFile: File,
    ): List<SessionMetadataCsvRow> {
        return try {
            CSVFormat.decodeFromString(
                deserializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                string = content,
            )
        } catch (error: Exception) {
            throw IllegalStateException("Failed to decode session metadata csv: ${sourceFile.absolutePath}", error)
        }
    }

    private fun writeMetadataRows(rows: List<SessionMetadataCsvRow>) {
        sessionIndexFile.parentFile?.mkdirs()
        val serialized = if (rows.isEmpty()) {
            ""
        } else {
            CSVFormat.encodeToString(
                serializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                value = rows,
            )
        }
        sessionIndexFile.writeText(serialized)
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

    private fun inferAgentKind(sessionId: String, agentId: String): AgentKind {
        return if (agentId == mainAgentId(sessionId = sessionId)) {
            AgentKind.MAIN
        } else {
            AgentKind.SUBAGENT
        }
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
            todo = emptyList(),
        )
    }

    private fun clampToInt(value: Long): Int {
        return value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun getSessionFileRepository(sessionId: String): SessionFileRepository {
        return rootStorageRepository.session(sessionId = sessionId)
    }

    private fun getAgentFileRepository(sessionId: String, agentId: String): AgentFileRepository {
        val sessionRepository = getSessionFileRepository(sessionId = sessionId)
        return if (agentId == mainAgentId(sessionId = sessionId)) {
            sessionRepository.mainAgent()
        } else {
            sessionRepository.subAgent(encodedSubAgentId = encodeAgentId(agentId))
        }
    }

    private fun getSessionDirectory(sessionId: String): File {
        return getSessionFileRepository(sessionId = sessionId).sessionDirectory
    }

    private fun getSessionMetaFile(sessionId: String): File {
        return getSessionFileRepository(sessionId = sessionId).metadataFile
    }

    private fun getSessionMetaFileForRead(sessionId: String): File? {
        return getSessionFileRepository(sessionId = sessionId).resolveMetadataFileForRead()
    }

    private fun getAgentMetaFile(sessionId: String, agentId: String): File {
        return getAgentFileRepository(
            sessionId = sessionId,
            agentId = agentId,
        ).metadataFile
    }

    private fun getAgentMetaFileForRead(sessionId: String, agentId: String): File? {
        return getAgentFileRepository(
            sessionId = sessionId,
            agentId = agentId,
        ).resolveMetadataFileForRead()
    }

    private fun getAgentMessagesDirectory(sessionId: String, agentId: String): File {
        return getAgentFileRepository(
            sessionId = sessionId,
            agentId = agentId,
        ).messagesDirectory
    }

    private fun getAgentMessageFile(sessionId: String, agentId: String, seq: Long): File {
        return getAgentFileRepository(
            sessionId = sessionId,
            agentId = agentId,
        ).messageFileForWrite(seq = seq)
    }

    private fun getAgentMessageFileForRead(sessionId: String, agentId: String, seq: Long): File? {
        return getAgentFileRepository(
            sessionId = sessionId,
            agentId = agentId,
        ).messageFileForRead(seq = seq)
    }

    private fun encodeAgentId(agentId: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(agentId.toByteArray(Charsets.UTF_8))
    }

    private fun decodeAgentId(encodedAgentId: String): String {
        val normalized = when (encodedAgentId.length % 4) {
            0 -> encodedAgentId
            2 -> "$encodedAgentId=="
            3 -> "$encodedAgentId="
            else -> throw IllegalStateException("Invalid encoded agent id format: $encodedAgentId")
        }
        return try {
            val bytes = Base64.getUrlDecoder().decode(normalized)
            bytes.toString(Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Invalid encoded agent id payload: $encodedAgentId", error)
        }
    }

    private fun <E> List<E>.toPersistentList(): PersistentList<E> {
        return persistentListOf<E>().addAll(this)
    }

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
        val todo: List<TodoItem>,
    )

    @Serializable
    private enum class AgentKind {
        MAIN,
        SUBAGENT,
    }

    private data class LoadedWindow(
        val messages: List<SessionMessage>,
    )

    private data class ExpectedAgentIdentity(
        val agentId: String,
        val kind: AgentKind,
    )
}
