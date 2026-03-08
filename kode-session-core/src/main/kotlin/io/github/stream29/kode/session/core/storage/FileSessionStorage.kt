package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.agent.model.*
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.model.*
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

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
    private val storageSupport = FileSessionStorageSupport(
        json = json,
        rootStorageRepository = rootStorageRepository,
        sessionIndexFile = sessionIndexFile,
    )
    private val rwMutex: Mutex = Mutex()

    init {
        dataDir.mkdirs()
        sessionDirRoot.mkdirs()
    }

    override suspend fun listSessions(): List<SessionMetadata> {
        return withContext(Dispatchers.IO) {
            rwMutex.withLock {
                storageSupport.readMetadataRows().map { row -> row.toMetadata() }
            }
        }
    }

    override suspend fun loadSession(id: String): SessionState {
        return withContext(Dispatchers.IO) {
            rwMutex.withLock {
                val metadataRow = storageSupport.readMetadataRows().firstOrNull { row -> row.id == id }
                    ?: throw IllegalArgumentException("Session not found: $id")
                val metadata = metadataRow.toMetadata()
                val sessionMeta = storageSupport.readSessionMeta(sessionId = id)
                    ?: throw IllegalStateException("Session meta missing: $id")
                val storedAgentMetas = storageSupport.readAllAgentMetas(sessionId = id)

                val mainAgentId = storageSupport.mainAgentId(sessionId = id)
                val mainAgentMeta = storedAgentMetas
                    .firstOrNull { item -> item.kind == AgentKind.MAIN && item.agentId == mainAgentId }
                    ?: throw IllegalStateException("Main agent meta missing for session: $id")
                val mainAgentTodo = readAgentTodo(sessionId = id, agentId = mainAgentMeta.agentId)

                val mainAgentMessages = storageSupport.loadWindowMessages(
                    sessionId = id,
                    agentId = mainAgentMeta.agentId,
                    agentMeta = mainAgentMeta,
                ).messages.toPersistentList()

                val subagentMetas = storedAgentMetas.filter { item ->
                    item.kind == AgentKind.SUBAGENT && item.agentId != mainAgentMeta.agentId
                }
                var subagentMap = persistentHashMapOf<String, SubAgent>()
                subagentMetas.forEach { subMeta ->
                    val subMessages = storageSupport.loadWindowMessages(
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
                            state = MutableStateFlow(storageSupport.normalizeAgentState(subMeta.state)),
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
                val totalMainMessageCount = storageSupport.clampToInt(mainAgentMeta.nextSeq)

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
                            state = MutableStateFlow(storageSupport.normalizeAgentState(mainAgentMeta.state)),
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
                val sessionFolder = storageSupport.getSessionDirectory(sessionId = id)
                sessionFolder.mkdirs()

                val mainAgentId = storageSupport.mainAgentId(sessionId = id)
                val mainAgentValue = session.agent.value
                val mainMeta = storageSupport.persistAgent(
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
                    storageSupport.persistAgent(
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

                storageSupport.cleanupStaleAgentDirectories(
                    sessionId = id,
                    validAgentIds = subagentIds,
                )

                storageSupport.writeSessionMeta(
                    sessionId = id,
                    meta = SessionFileMeta(config = session.config.value),
                )

                val metadata = session.metadata.value.copy(
                    messageCount = storageSupport.clampToInt(mainMeta.nextSeq),
                )
                session.metadata.value = metadata
                storageSupport.upsertMetadata(metadata)
            }
        }
    }

    override suspend fun removeSession(id: String) {
        withContext(Dispatchers.IO) {
            rwMutex.withLock {
                val filtered = storageSupport.readMetadataRows().filterNot { row -> row.id == id }
                storageSupport.writeMetadataRows(filtered)
                storageSupport.getSessionDirectory(id).deleteRecursively()
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

    override suspend fun readAgentTodo(sessionId: String, agentId: String): List<TodoItem> =
        withContext(Dispatchers.IO) {
            storageSupport.readAgentTodo(sessionId = sessionId, agentId = agentId)
        }

    override suspend fun writeAgentTodo(sessionId: String, agentId: String, todos: List<TodoItem>): Unit =
        withContext(Dispatchers.IO) {
            storageSupport.writeAgentTodoSync(sessionId = sessionId, agentId = agentId, todos = todos)
        }
}
