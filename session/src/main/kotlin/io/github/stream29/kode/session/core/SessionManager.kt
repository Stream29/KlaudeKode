package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.github.stream29.kode.agent.model.*
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.session.core.model.*
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.querySessionSummaries
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

public class SessionManager(
    private val dependencies: SessionManagerDependencies,
    private val clock: Clock = Clock.System,
) {
    private val runtimeStore: SessionRuntimeStore = dependencies.runtimeStore
    private val persistencePort: SessionPersistencePort = dependencies.persistencePort
    private val persistenceObserverCoordinator = dependencies.observerCoordinatorFactory.create(
        persistencePort = persistencePort,
        clock = clock,
    )
    private val subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>> = ConcurrentHashMap()
    private val softStopRequestedSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val subAgentCoordinator = dependencies.subAgentCoordinatorFactory.create(
        requireRuntime = ::requireRuntime,
        persist = ::persist,
        clock = clock,
        softStopRequestedSessions = softStopRequestedSessions,
        subAgentJobs = subAgentJobs,
    )

    public sealed interface SubAgentPollResult {
        public data class Pending(
            val error: String? = null,
        ) : SubAgentPollResult

        public data class Completed(
            val result: String,
        ) : SubAgentPollResult

        public data class Missing(
            val error: String = SUBAGENT_NOT_FOUND_ERROR,
        ) : SubAgentPollResult

        public data class Failed(
            val error: String?,
        ) : SubAgentPollResult
    }

    public data class PendingScriptInfo(
        val messageId: String,
        val scriptId: String,
    )

    public suspend fun createSession(
        title: String,
        systemPrompt: String?,
        tags: List<String>,
        configuration: SessionConfiguration,
    ): SessionSnapshot {
        val sessionId = generateSessionManagerId()
        val now = clock.now()
        val base = SessionSnapshot(
            id = sessionId,
            title = title,
            createdAt = now,
            updatedAt = now,
            messages = emptyList(),
            status = SessionStatus.ACTIVE,
            parentSessionId = null,
            forkedFromMessageId = null,
            version = 1L,
            configuration = configuration.copy(systemPrompt = systemPrompt ?: configuration.systemPrompt),
            tags = tags,
            childSessionIds = emptyList(),
            runtimeState = SessionRunState.Suspended,
        )
        val runtime = base.toSessionState()
        runtime.agent.value.config.value = AgentConfig(
            systemPrompt = runtime.config.value.systemPrompt,
            taskDescription = null,
            expectedResult = null,
            canInteractWithUser = true,
        )
        persist(runtime)
        runtimeStore.put(runtime)
        ensureSessionPersistenceObserver(sessionId = sessionId, runtime = runtime)
        return runtime.toSessionSnapshot()
    }

    public suspend fun createConversationSession(
        title: String,
        systemPrompt: String,
        workDir: String?,
    ): SessionSnapshot {
        val configuration = SessionConfiguration(
            systemPrompt = systemPrompt,
            workDir = workDir,
            maxIterations = null,
            temperature = null,
            customValues = null,
        )
        return createSession(
            title = title,
            systemPrompt = systemPrompt,
            tags = emptyList(),
            configuration = configuration,
        )
    }

    public suspend fun getSession(sessionId: String): SessionSnapshot? {
        val runtime = loadRuntime(sessionId) ?: return null
        return runtime.toSessionSnapshot()
    }

    public suspend fun getSessionState(sessionId: String): SessionState? {
        return loadRuntime(sessionId)
    }

    public suspend fun beginRun(sessionId: String, ownerJob: Job) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val changed = runtime.applyBeginRunMutation(
                ownerJob = ownerJob,
                now = clock.now(),
            )
            if (!changed) {
                return
            }
            clearSoftStopRequest(
                sessionId = sessionId,
                softStopRequestedSessions = softStopRequestedSessions,
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun suspendForUserInput(sessionId: String) {
        suspendMainAgent(sessionId)
    }

    public suspend fun resumeRun(sessionId: String, ownerJob: Job) {
        beginRun(sessionId, ownerJob)
    }

    public suspend fun completeRun(sessionId: String) {
        suspendMainAgent(sessionId)
    }

    private suspend fun suspendMainAgent(sessionId: String) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val mutation = runtime.applySuspendMainMutation(now = clock.now())
            if (!mutation.changed) {
                val targetState = mutation.targetState
                if (targetState == SessionRunState.Suspended) {
                    clearSoftStopRequest(
                        sessionId = sessionId,
                        softStopRequestedSessions = softStopRequestedSessions,
                    )
                }
                return
            }
            val targetState = mutation.targetState
            if (targetState == SessionRunState.Suspended) {
                clearSoftStopRequest(
                    sessionId = sessionId,
                    softStopRequestedSessions = softStopRequestedSessions,
                )
            }
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun stopRun(sessionId: String): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val rolledBackPending = normalizeTrailingPendingScript(runtime.agent.value)
            val sessionRunning = runtime.computeSessionState() == SessionRunState.Running
            val softStopAlreadyRequested = isSoftStopRequested(
                sessionId = sessionId,
                softStopRequestedSessions = softStopRequestedSessions,
            )

            if (sessionRunning && !softStopAlreadyRequested) {
                subAgentCoordinator.requestSoftStop(
                    sessionId = sessionId,
                    runtime = runtime,
                )
                markSoftStopRequested(
                    sessionId = sessionId,
                    softStopRequestedSessions = softStopRequestedSessions,
                )
                if (rolledBackPending) {
                    updateMetadataAfterStopMutation(sessionId = sessionId, runtime = runtime)
                    persist(runtime)
                }
                return rolledBackPending
            }

            if (sessionRunning) {
                subAgentCoordinator.forceStop(
                    sessionId = sessionId,
                    runtime = runtime,
                )
            } else {
                normalizeStoppedRuntime(runtime)
                clearSoftStopRequest(
                    sessionId = sessionId,
                    softStopRequestedSessions = softStopRequestedSessions,
                )
            }

            updateMetadataAfterStopMutation(sessionId = sessionId, runtime = runtime)
            persist(runtime)
            return rolledBackPending
        } finally {
            runtime.mutex.unlock()
        }
    }

    private suspend fun addMessage(
        sessionId: String,
        agentId: String?,
        message: SessionMessage,
    ): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val targetAgent = runtime.resolveAgentForSession(sessionId = sessionId, agentId = agentId)
            runtime.appendMessageMutation(
                targetAgent = targetAgent,
                message = message,
                now = clock.now(),
            )
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addUserMessage(
        sessionId: String,
        content: String,
        agentId: String?,
    ): SessionSnapshot {
        val now = clock.now()
        return addMessage(
            sessionId = sessionId,
            agentId = agentId,
            message = UserMessage(
                id = generateSessionManagerId(),
                content = content,
                timestamp = now,
                koogMessages = listOf(
                    Message.User(
                        content = content,
                        metaInfo = RequestMetaInfo(timestamp = now.toDeprecatedInstant()),
                    )
                ),
                metadata = null,
            ),
        )
    }

    public suspend fun prepareConversationContinuation(
        sessionId: String,
        input: String,
        agentId: String?,
    ) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val changed = runtime.applyContinuationInputMutation(
                sessionId = sessionId,
                agentId = agentId,
                input = input,
                now = clock.now(),
            )
            if (!changed) {
                return
            }
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addAgentScriptMessage(
        sessionId: String,
        scriptId: String,
        status: AgentScriptStatus,
        scriptReturnValue: String?,
        scriptStdout: String,
        error: String?,
        outputList: List<String>,
        koogMessages: List<Message>,
        metadata: Map<String, String>?,
        agentId: String?,
    ): SessionSnapshot {
        assertScriptOnlyKoogMessages(koogMessages = koogMessages)
        return addMessage(
            sessionId = sessionId,
            agentId = agentId,
            message = AgentScript(
                id = generateSessionManagerId(),
                scriptId = scriptId,
                status = status,
                scriptReturnValue = scriptReturnValue,
                scriptStdout = scriptStdout,
                error = error,
                outputList = outputList,
                timestamp = clock.now(),
                koogMessages = koogMessages,
                metadata = metadata,
            ),
        )
    }

    public suspend fun forkSession(
        parentSessionId: String,
        atMessageId: String?,
        newTitle: String?,
    ): SessionSnapshot {
        val parent = requireRuntime(parentSessionId)
        parent.mutex.lock()
        try {
            val now = clock.now()
            val childId = generateSessionManagerId()
            val child = parent.forkSessionMutation(
                parentSessionId = parentSessionId,
                atMessageId = atMessageId,
                newTitle = newTitle,
                childId = childId,
                now = now,
            )

            persist(parent)
            persist(child)
            runtimeStore.put(child)
            ensureSessionPersistenceObserver(sessionId = childId, runtime = child)
            return child.toSessionSnapshot()
        } finally {
            parent.mutex.unlock()
        }
    }

    public suspend fun duplicateSession(sessionId: String, newTitle: String?): SessionSnapshot {
        val original = requireRuntime(sessionId)
        original.mutex.lock()
        try {
            val now = clock.now()
            val duplicatedId = generateSessionManagerId()
            val duplicated = original.duplicateSessionMutation(
                newTitle = newTitle,
                duplicatedId = duplicatedId,
                now = now,
                messageIdGenerator = ::generateSessionManagerId,
            )
            persist(duplicated)
            runtimeStore.put(duplicated)
            ensureSessionPersistenceObserver(sessionId = duplicatedId, runtime = duplicated)
            return duplicated.toSessionSnapshot()
        } finally {
            original.mutex.unlock()
        }
    }

    public suspend fun archiveSession(sessionId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.archiveSessionMutation(now = clock.now())
            clearSoftStopRequest(
                sessionId = sessionId,
                softStopRequestedSessions = softStopRequestedSessions,
            )
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun restoreSession(sessionId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.restoreSessionMutation(now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun deleteSession(sessionId: String, hardDelete: Boolean) {
        if (hardDelete) {
            subAgentCoordinator.cancelSessionJobs(sessionId = sessionId, reason = "Session hard deleted")
            cancelSessionPersistenceObserver(sessionId = sessionId, reason = "Session hard deleted")
            clearSoftStopRequest(
                sessionId = sessionId,
                softStopRequestedSessions = softStopRequestedSessions,
            )
            persistencePort.removeSession(sessionId)
            runtimeStore.evict(sessionId)
            return
        }

        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.softDeleteSessionMutation(now = clock.now())
            clearSoftStopRequest(
                sessionId = sessionId,
                softStopRequestedSessions = softStopRequestedSessions,
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun listSessions(filter: SessionFilter?): List<SessionSummary> {
        val metadata = persistencePort.listSessionMetadata()
        return querySessionSummaries(metadata, filter)
    }

    public suspend fun exportSession(sessionId: String, targetFile: File) {
        val session = getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(json.encodeToString(session))
    }

    public suspend fun importSession(sourceFile: File, newTitle: String?): SessionSnapshot {
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Import file not found: ${sourceFile.absolutePath}")
        }
        val json = Json {
            ignoreUnknownKeys = true
        }
        val imported = json.decodeFromString<SessionSnapshot>(sourceFile.readText())
        val now = clock.now()
        val normalized = imported.copy(
            id = generateSessionManagerId(),
            title = newTitle ?: imported.title,
            createdAt = now,
            updatedAt = now,
            status = SessionStatus.ACTIVE,
            parentSessionId = null,
            forkedFromMessageId = null,
            version = 1L,
            childSessionIds = emptyList(),
            runtimeState = SessionRunState.Suspended,
        )
        val runtime = normalized.toSessionState()
        persist(runtime)
        runtimeStore.put(runtime)
        return runtime.toSessionSnapshot()
    }

    public suspend fun updateTitle(sessionId: String, newTitle: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.applyTitleMutation(newTitle = newTitle, now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun updateConfiguration(
        sessionId: String,
        configuration: SessionConfiguration,
    ): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.applyConfigurationMutation(configuration = configuration, now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun updateSessionWorkDir(sessionId: String, workDir: String?): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val changed = runtime.applyWorkDirMutation(workDir = workDir, now = clock.now())
            if (!changed) {
                return runtime.toSessionSnapshot()
            }
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addTags(sessionId: String, tags: List<String>): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.addTagsMutation(tags = tags, now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun removeTags(sessionId: String, tags: List<String>): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.removeTagsMutation(tags = tags, now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun clearMessages(sessionId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.clearMessagesMutation(now = clock.now())
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getAgentConfig(sessionId: String, agentId: String?): AgentConfig {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.resolveAgentForSession(sessionId = sessionId, agentId = agentId).config.value
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getAgentMessages(sessionId: String, agentId: String?): List<SessionMessage> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.resolveAgentForSession(sessionId = sessionId, agentId = agentId).messages.value
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getTrailingPendingScript(
        sessionId: String,
        agentId: String?,
    ): PendingScriptInfo? {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val agent = runtime.resolveAgentForSession(sessionId = sessionId, agentId = agentId)
            val trailing = agent.trailingPendingScript() ?: return null
            return PendingScriptInfo(
                messageId = trailing.id,
                scriptId = trailing.scriptId,
            )
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun rollbackTrailingPendingScript(
        sessionId: String,
        agentId: String?,
    ): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val changed = runtime.rollbackTrailingPendingScriptMutation(
                sessionId = sessionId,
                agentId = agentId,
                now = clock.now(),
            )
            if (!changed) {
                return false
            }
            persist(runtime)
            return true
        } finally {
            runtime.mutex.unlock()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    public fun createSubAgent(
        sessionId: String,
        agentId: String,
        parentAgentId: String?,
        mode: String,
        taskDescription: String,
        expectedResult: String,
    ) {
        throw IllegalStateException("Subagent is disabled in strict script-only runtime")
    }

    public suspend fun completeSubAgentResult(sessionId: String, agentId: String, result: String): Boolean {
        return subAgentCoordinator.completeSubAgentResult(
            sessionId = sessionId,
            agentId = agentId,
            result = result,
        )
    }

    public suspend fun cancelSubAgent(sessionId: String, agentId: String, reason: String): Boolean {
        return subAgentCoordinator.cancelSubAgent(
            sessionId = sessionId,
            agentId = agentId,
            reason = reason,
        )
    }

    public suspend fun killSubAgent(sessionId: String, agentId: String): Boolean {
        return subAgentCoordinator.killSubAgent(
            sessionId = sessionId,
            agentId = agentId,
        )
    }

    public suspend fun listActiveSubAgentIds(sessionId: String): List<String> {
        return subAgentCoordinator.listActiveSubAgentIds(sessionId = sessionId)
    }

    public suspend fun listActiveAgentIds(sessionId: String): List<String> {
        return subAgentCoordinator.listActiveAgentIds(sessionId = sessionId)
    }

    public suspend fun pollSubAgentResult(sessionId: String, agentId: String): SubAgentPollResult {
        return subAgentCoordinator.pollSubAgentResult(
            sessionId = sessionId,
            agentId = agentId,
        )
    }

    public suspend fun awaitSubAgentResult(
        sessionId: String,
        agentId: String,
        timeoutSeconds: Int,
    ): SubAgentPollResult {
        return subAgentCoordinator.awaitSubAgentResult(
            sessionId = sessionId,
            agentId = agentId,
            timeoutSeconds = timeoutSeconds,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    public fun injectReceiveAgentMessage(
        sessionId: String,
        targetAgentId: String,
        fromAgentId: String,
        message: String,
    ): Boolean {
        throw IllegalStateException("receiveAgentMessage is disabled in strict script-only runtime")
    }

    public fun registerSubAgentJob(sessionId: String, agentId: String, job: Job) {
        subAgentCoordinator.registerSubAgentJob(
            sessionId = sessionId,
            agentId = agentId,
            job = job,
        )
    }

    public fun unregisterSubAgentJob(sessionId: String, agentId: String) {
        subAgentCoordinator.unregisterSubAgentJob(
            sessionId = sessionId,
            agentId = agentId,
        )
    }

    private fun updateMetadataAfterStopMutation(sessionId: String, runtime: SessionState) {
        val targetState = runtime.applyStopMetadataMutation(now = clock.now())
        clearSoftStopRequestIfSuspended(
            sessionId = sessionId,
            state = targetState,
            softStopRequestedSessions = softStopRequestedSessions,
        )
    }


    private suspend fun persist(runtime: SessionState) {
        persistenceObserverCoordinator.persist(runtime)
    }

    private suspend fun loadRuntime(sessionId: String): SessionState? {
        val runtime = runCatching {
            runtimeStore.loadSession(sessionId)
        }.getOrNull()
        if (runtime != null) {
            ensureSessionPersistenceObserver(sessionId = sessionId, runtime = runtime)
        }
        return runtime
    }

    private suspend fun requireRuntime(sessionId: String): SessionState {
        val runtime = runtimeStore.loadSession(sessionId)
        ensureSessionPersistenceObserver(sessionId = sessionId, runtime = runtime)
        return runtime
    }

    private fun ensureSessionPersistenceObserver(sessionId: String, runtime: SessionState) {
        persistenceObserverCoordinator.ensureSessionPersistenceObserver(
            sessionId = sessionId,
            runtime = runtime,
        )
    }

    private fun cancelSessionPersistenceObserver(sessionId: String, reason: String) {
        persistenceObserverCoordinator.cancelSessionPersistenceObserver(
            sessionId = sessionId,
            reason = reason,
        )
    }

    public suspend fun getAgentTodoStateFlow(
        sessionId: String,
        agentId: String
    ): MutableStateFlow<List<TodoItem>>? {
        val state = getSessionState(sessionId) ?: return null
        return state.resolveAgentForSession(sessionId = sessionId, agentId = agentId).todoMetadataFlow()
    }

    public suspend fun getAgentTodo(
        sessionId: String,
        agentId: String
    ): List<TodoItem> {
        val state = getSessionState(sessionId) ?: requireRuntime(sessionId)
        val agent = state.resolveAgentForSession(sessionId = sessionId, agentId = agentId)
        return agent.readTodoFromMetadata()
    }

    public suspend fun updateAgentTodo(
        sessionId: String,
        agentId: String,
        todos: List<TodoItem>
    ) {
        val state = getSessionState(sessionId) ?: requireRuntime(sessionId)

        state.mutex.lock()
        try {
            val changed = state.updateAgentTodoMutation(
                sessionId = sessionId,
                agentId = agentId,
                todos = todos,
                now = clock.now(),
            )
            if (!changed) {
                return
            }
            persist(state)
        } finally {
            state.mutex.unlock()
        }
    }

private companion object {
        private const val SUBAGENT_NOT_FOUND_ERROR: String = "Subagent not found"
    }
}
