package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.github.stream29.kode.session.core.model.AgentConfig
import io.github.stream29.kode.session.core.model.AgentScript
import io.github.stream29.kode.session.core.model.AgentScriptStatus
import io.github.stream29.kode.session.core.model.AgentState
import io.github.stream29.kode.session.core.model.Agent
import io.github.stream29.kode.session.core.model.SessionSnapshot
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionConfiguration
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.model.SubAgent
import io.github.stream29.kode.session.core.model.UserMessage
import io.github.stream29.kode.session.core.tool.ToolNames
import io.github.stream29.kode.session.core.model.toSessionSnapshot
import io.github.stream29.kode.session.core.model.toSessionState
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.querySessionSummaries
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.datetime.toDeprecatedInstant
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class SessionManager(
    private val repository: SessionRepository,
    private val clock: Clock = Clock.System,
) {
    private val sessionFactory: SessionFactory = SessionFactory(repository)
    private val subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>> = ConcurrentHashMap()

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
        val sessionId = generateId()
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
        sessionFactory.put(runtime)
        return runtime.toSessionSnapshot()
    }

    public suspend fun createConversationSession(
        title: String,
        systemPrompt: String,
        preferredModel: String?,
        preferredModelId: String,
        workDir: String?,
    ): SessionSnapshot {
        val configuration = SessionConfiguration(
            preferredModel = preferredModel,
            systemPrompt = systemPrompt,
            workDir = workDir,
            maxIterations = null,
            temperature = null,
            customValues = mapOf(SESSION_CONFIG_MODEL_ID_KEY to preferredModelId),
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
            runtime.runJob.value = ownerJob
            runtime.metadata.value = runtime.metadata.value.copy(
                state = SessionRunState.Running,
                updatedAt = clock.now(),
            )
            runtime.agent.value.state.value = AgentState.Running
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
            runtime.runJob.value = null
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.metadata.value = runtime.metadata.value.copy(
                state = computeSessionState(runtime),
                updatedAt = clock.now(),
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun stopRun(sessionId: String): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.runJob.value?.cancel("Stopped by user")
            subAgentJobs[sessionId]?.values?.forEach { job ->
                job.cancel("Stopped by user")
            }
            runtime.subagents.value.forEach { (_, subAgent) ->
                subAgent.delegate.state.value = AgentState.Suspended
                if (!subAgent.result.isCompleted) {
                    subAgent.result.complete("Cancelled by user")
                }
            }
            val rolledBackPending = normalizeTrailingPendingScript(runtime.agent.value)
            runtime.runJob.value = null
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.metadata.value = runtime.metadata.value.copy(
                state = SessionRunState.Suspended,
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
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
            val targetAgent = resolveAgent(runtime, sessionId, agentId)
            targetAgent.messages.value = targetAgent.messages.value.add(message)
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
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
                id = generateId(),
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
            val targetAgent = resolveAgent(runtime, sessionId, agentId)
            val pendingScript = targetAgent.trailingPendingScript()
            if (pendingScript != null) {
                throw IllegalStateException(
                    "Script-only violation: pending script '${pendingScript.scriptId}' blocks continue; resolve pending-input state first"
                )
            }
            if (input.isBlank()) {
                return
            }

            val now = clock.now()
            targetAgent.messages.value = targetAgent.messages.value.add(
                UserMessage(
                    id = generateId(),
                    content = input,
                    timestamp = now,
                    koogMessages = listOf(
                        Message.User(
                            content = input,
                            metaInfo = RequestMetaInfo(timestamp = now.toDeprecatedInstant()),
                        )
                    ),
                    metadata = null,
                )
            )
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = now,
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
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
                id = generateId(),
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

    public suspend fun createCheckpoint(sessionId: String, label: String?): SessionCheckpoint {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val checkpoint = SessionCheckpoint(
                checkpointId = generateId(),
                sessionId = sessionId,
                createdAt = clock.now(),
                messageCount = runtime.agent.value.messages.value.size,
                messages = runtime.agent.value.messages.value,
                version = runtime.metadata.value.version,
                label = label,
                isTombstone = false,
            )
            runtime.checkpoints.value = runtime.checkpoints.value.add(checkpoint)
            runtime.metadata.value = runtime.metadata.value.copy(updatedAt = clock.now())
            persist(runtime)
            return checkpoint
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun revertToCheckpoint(sessionId: String, checkpointId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val checkpoint = runtime.checkpoints.value.firstOrNull { item -> item.checkpointId == checkpointId }
                ?: throw IllegalArgumentException("Checkpoint not found: $checkpointId")

            runtime.agent.value.messages.value = checkpoint.messages.toPersistentList()
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = checkpoint.version + 1,
                state = SessionRunState.Suspended,
                messageCount = runtime.agent.value.messages.value.size,
            )
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.runJob.value = null
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun revertToLatestCheckpoint(sessionId: String): SessionSnapshot? {
        val checkpoint = getLatestCheckpoint(sessionId) ?: return null
        return revertToCheckpoint(sessionId, checkpoint.checkpointId)
    }

    public suspend fun forkSession(
        parentSessionId: String,
        atMessageId: String?,
        newTitle: String?,
    ): SessionSnapshot {
        val parent = requireRuntime(parentSessionId)
        parent.mutex.lock()
        try {
            val parentMessages = parent.agent.value.messages.value
            val messages = if (atMessageId != null) {
                val index = parentMessages.indexOfFirst { item -> item.id == atMessageId }
                if (index < 0) {
                    throw IllegalArgumentException("Message not found: $atMessageId")
                }
                parentMessages.take(index + 1)
            } else {
                parentMessages
            }

            val now = clock.now()
            val childId = generateId()
            val child = SessionSnapshot(
                id = childId,
                title = newTitle ?: "${parent.metadata.value.title} (Fork)",
                createdAt = now,
                updatedAt = now,
                messages = messages,
                status = SessionStatus.ACTIVE,
                parentSessionId = parentSessionId,
                forkedFromMessageId = atMessageId,
                version = 1L,
                configuration = parent.config.value,
                tags = parent.metadata.value.tags,
                childSessionIds = emptyList(),
                runtimeState = SessionRunState.Suspended,
            ).toSessionState()

            parent.metadata.value = parent.metadata.value.copy(
                childSessionIds = parent.metadata.value.childSessionIds + childId,
                updatedAt = now,
            )

            persist(parent)
            persist(child)
            sessionFactory.put(child)
            return child.toSessionSnapshot()
        } finally {
            parent.mutex.unlock()
        }
    }

    public suspend fun duplicateSession(sessionId: String, newTitle: String?): SessionSnapshot {
        val original = requireRuntime(sessionId)
        original.mutex.lock()
        try {
            if (original.metadata.value.state != SessionRunState.Suspended) {
                throw IllegalStateException("Only suspended sessions can be duplicated")
            }
            val now = clock.now()
            val duplicatedId = generateId()
            val duplicated = SessionSnapshot(
                id = duplicatedId,
                title = newTitle ?: "${original.metadata.value.title} (Copy)",
                createdAt = now,
                updatedAt = now,
                messages = original.agent.value.messages.value.map { message -> message.copyWithNewId(generateId()) },
                status = SessionStatus.ACTIVE,
                parentSessionId = null,
                forkedFromMessageId = null,
                version = 1L,
                configuration = original.config.value,
                tags = original.metadata.value.tags,
                childSessionIds = emptyList(),
                runtimeState = SessionRunState.Suspended,
            ).toSessionState()
            persist(duplicated)
            sessionFactory.put(duplicated)
            return duplicated.toSessionSnapshot()
        } finally {
            original.mutex.unlock()
        }
    }

    public suspend fun archiveSession(sessionId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                status = SessionStatus.ARCHIVED,
                state = SessionRunState.Suspended,
                updatedAt = clock.now(),
            )
            runtime.runJob.value = null
            runtime.agent.value.state.value = AgentState.Suspended
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
            runtime.metadata.value = runtime.metadata.value.copy(
                status = SessionStatus.ACTIVE,
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun deleteSession(sessionId: String, hardDelete: Boolean) {
        if (hardDelete) {
            subAgentJobs.remove(sessionId)?.values?.forEach { job ->
                job.cancel("Session hard deleted")
            }
            repository.removeSession(sessionId)
            sessionFactory.evict(sessionId)
            return
        }

        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                status = SessionStatus.DELETED,
                state = SessionRunState.Suspended,
                updatedAt = clock.now(),
            )
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.runJob.value = null
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun listSessions(filter: SessionFilter?): List<SessionSummary> {
        val metadata = repository.listSessions()
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
            id = generateId(),
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
        sessionFactory.put(runtime)
        return runtime.toSessionSnapshot()
    }

    public suspend fun updateTitle(sessionId: String, newTitle: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                title = newTitle,
                updatedAt = clock.now(),
            )
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
            runtime.config.value = configuration
            runtime.agent.value.config.value = runtime.agent.value.config.value.copy(
                systemPrompt = configuration.systemPrompt,
            )
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
            )
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
            if (runtime.metadata.value.state != SessionRunState.Suspended) {
                throw IllegalStateException("Session work directory can only be changed while suspended")
            }

            if (runtime.config.value.workDir == workDir) {
                return runtime.toSessionSnapshot()
            }

            runtime.config.value = runtime.config.value.copy(workDir = workDir)
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
            )
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
            runtime.metadata.value = runtime.metadata.value.copy(
                tags = (runtime.metadata.value.tags + tags).distinct(),
                updatedAt = clock.now(),
            )
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
            runtime.metadata.value = runtime.metadata.value.copy(
                tags = runtime.metadata.value.tags - tags.toSet(),
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toSessionSnapshot()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getCheckpoints(sessionId: String): List<SessionCheckpoint> {
        val runtime = requireRuntime(sessionId)
        return runtime.checkpoints.value
    }

    public suspend fun getLatestCheckpoint(sessionId: String): SessionCheckpoint? {
        return getCheckpoints(sessionId).maxByOrNull { checkpoint -> checkpoint.version }
    }

    public suspend fun deleteCheckpoint(sessionId: String, checkpointId: String) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.checkpoints.value = runtime.checkpoints.value
                .filterNot { checkpoint -> checkpoint.checkpointId == checkpointId }
                .toPersistentList()
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun clearMessages(sessionId: String): SessionSnapshot {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.agent.value.messages.value = kotlinx.collections.immutable.persistentListOf()
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = 0,
            )
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
            resolveAgent(runtime, sessionId, agentId).config.value
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getAgentMessages(sessionId: String, agentId: String?): List<SessionMessage> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            resolveAgent(runtime, sessionId, agentId).messages.value
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
            val agent = resolveAgent(runtime, sessionId, agentId)
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
            val agent = resolveAgent(runtime, sessionId, agentId)
            if (agent.trailingPendingScript() == null) {
                return false
            }

            agent.messages.value = agent.messages.value.dropLast(1).toPersistentList()
            runtime.runJob.value = null
            if (isMainAgent(sessionId, agentId)) {
                runtime.agent.value.state.value = AgentState.Suspended
            }
            updateMetadataAfterPendingScriptRollback(runtime)
            persist(runtime)
            return true
        } finally {
            runtime.mutex.unlock()
        }
    }

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    public suspend fun createSubAgent(
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
        return finishSubAgent(sessionId = sessionId, agentId = agentId, resultText = result)
    }

    public suspend fun cancelSubAgent(sessionId: String, agentId: String, reason: String): Boolean {
        return finishSubAgent(sessionId = sessionId, agentId = agentId, resultText = reason)
    }

    private suspend fun finishSubAgent(
        sessionId: String,
        agentId: String,
        resultText: String,
    ): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            val subAgent = runtime.subagents.value[agentId]
            if (subAgent == null) {
                false
            } else {
                if (!subAgent.result.isCompleted) {
                    subAgent.result.complete(resultText)
                }
                subAgent.delegate.state.value = AgentState.Suspended
                runtime.metadata.value = runtime.metadata.value.copy(
                    state = computeSessionState(runtime),
                    updatedAt = clock.now(),
                    version = runtime.metadata.value.version + 1,
                )
                persist(runtime)
                true
            }
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun killSubAgent(sessionId: String, agentId: String): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            val subAgent = runtime.subagents.value[agentId]
            if (subAgent == null) {
                false
            } else {
                subAgentJobs[sessionId]?.remove(agentId)?.cancel("Killed by parent agent")
                if (!subAgent.result.isCompleted) {
                    subAgent.result.complete("Killed by parent agent")
                }
                runtime.subagents.value = runtime.subagents.value.remove(agentId)
                runtime.metadata.value = runtime.metadata.value.copy(
                    state = computeSessionState(runtime),
                    updatedAt = clock.now(),
                    version = runtime.metadata.value.version + 1,
                )
                persist(runtime)
                true
            }
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun listActiveSubAgentIds(sessionId: String): List<String> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.subagents.value.entries
                .filter { (_, subAgent) ->
                    subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
                }
                .map { (agentId, _) -> agentId }
                .sorted()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun listActiveAgentIds(sessionId: String): List<String> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            val activeIds = mutableListOf<String>()
            val mainRunning =
                runtime.agent.value.state.value == AgentState.Running && (runtime.runJob.value?.isActive == true)
            if (mainRunning) {
                activeIds += mainAgentId(sessionId)
            }
            runtime.subagents.value.entries
                .filter { (_, subAgent) ->
                    subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
                }
                .mapTo(activeIds) { (agentId, _) -> agentId }
            activeIds.sorted()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun pollSubAgentResult(sessionId: String, agentId: String): SubAgentPollResult {
        val subAgent = loadSubAgent(sessionId = sessionId, agentId = agentId)
            ?: return missingSubAgentPollResult()

        if (!subAgent.result.isCompleted) {
            return pendingSubAgentPollResult()
        }

        return runCatching {
            subAgent.result.await()
        }.fold(
            onSuccess = { value -> completedSubAgentPollResult(value) },
            onFailure = { throwable -> failedSubAgentPollResult(throwable) },
        )
    }

    public suspend fun awaitSubAgentResult(
        sessionId: String,
        agentId: String,
        timeoutSeconds: Int,
    ): SubAgentPollResult {
        val subAgent = loadSubAgent(sessionId = sessionId, agentId = agentId)
            ?: return missingSubAgentPollResult()

        return runCatching {
            withTimeout(timeoutSeconds * 1000L) {
                subAgent.result.await()
            }
        }.fold(
            onSuccess = { value -> completedSubAgentPollResult(value) },
            onFailure = { throwable ->
                if (throwable is kotlinx.coroutines.TimeoutCancellationException) {
                    pendingSubAgentPollResult(error = SUBAGENT_TIMEOUT_ERROR)
                } else {
                    failedSubAgentPollResult(throwable)
                }
            },
        )
    }

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    public suspend fun injectReceiveAgentMessage(
        sessionId: String,
        targetAgentId: String,
        fromAgentId: String,
        message: String,
    ): Boolean {
        throw IllegalStateException("receiveAgentMessage is disabled in strict script-only runtime")
    }

    public fun registerSubAgentJob(sessionId: String, agentId: String, job: Job) {
        val sessionJobs = subAgentJobs.getOrPut(sessionId) { ConcurrentHashMap() }
        sessionJobs[agentId] = job
    }

    public fun unregisterSubAgentJob(sessionId: String, agentId: String) {
        subAgentJobs[sessionId]?.remove(agentId)
    }

    private fun resolveAgent(runtime: SessionState, sessionId: String, agentId: String?): Agent {
        if (isMainAgent(sessionId, agentId)) {
            return runtime.agent.value
        }
        val normalized = requireNotNull(agentId) { "agentId is required" }
        return runtime.subagents.value[normalized]?.delegate
            ?: throw IllegalArgumentException("Agent not found: $normalized")
    }

    private fun isMainAgent(sessionId: String, agentId: String?): Boolean {
        if (agentId == null) {
            return true
        }
        return agentId == mainAgentId(sessionId)
    }

    private fun mainAgentId(sessionId: String): String {
        return "main-$sessionId"
    }

    private fun computeSessionState(runtime: SessionState): SessionRunState {
        val mainRunning =
            runtime.agent.value.state.value == AgentState.Running && (runtime.runJob.value?.isActive == true)
        val subRunning = runtime.subagents.value.any { (_, subAgent) ->
            subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
        }
        return if (mainRunning || subRunning) {
            SessionRunState.Running
        } else {
            SessionRunState.Suspended
        }
    }

    private suspend fun loadSubAgent(sessionId: String, agentId: String): SubAgent? {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.subagents.value[agentId]
        } finally {
            runtime.mutex.unlock()
        }
    }

    private fun updateMetadataAfterPendingScriptRollback(runtime: SessionState) {
        runtime.metadata.value = runtime.metadata.value.copy(
            state = computeSessionState(runtime),
            updatedAt = clock.now(),
            version = runtime.metadata.value.version + 1,
            messageCount = runtime.agent.value.messages.value.size,
        )
    }

    private fun normalizeTrailingPendingScript(agent: Agent): Boolean {
        if (agent.trailingPendingScript() == null) {
            return false
        }
        agent.messages.value = agent.messages.value.dropLast(1).toPersistentList()
        return true
    }

    private fun missingSubAgentPollResult(): SubAgentPollResult {
        return SubAgentPollResult.Missing()
    }

    private fun pendingSubAgentPollResult(error: String? = null): SubAgentPollResult {
        return SubAgentPollResult.Pending(error = error)
    }

    private fun completedSubAgentPollResult(value: String): SubAgentPollResult {
        return SubAgentPollResult.Completed(result = value)
    }

    private fun failedSubAgentPollResult(throwable: Throwable): SubAgentPollResult {
        return SubAgentPollResult.Failed(error = throwable.message)
    }

    private fun assertScriptOnlyKoogMessages(koogMessages: List<Message>) {
        if (koogMessages.isEmpty()) {
            throw IllegalStateException("Script-only violation: AgentScript.koogMessages must not be empty")
        }
        val nonScriptTool = koogMessages
            .filterIsInstance<Message.Tool>()
            .firstOrNull { tool -> tool.tool != ToolNames.EXECUTE_KOTLIN_SCRIPT }
        if (nonScriptTool != null) {
            throw IllegalStateException(
                "Script-only violation: tool '${nonScriptTool.tool}' is not allowed in AgentScript.koogMessages"
            )
        }
    }

    private fun SessionMessage.copyWithNewId(newId: String): SessionMessage {
        return when (this) {
            is UserMessage -> copy(id = newId)
            is AgentScript -> copy(id = newId)
        }
    }

    private fun Agent.trailingPendingScript(): AgentScript? {
        val trailing = messages.value.lastOrNull() ?: return null
        if (trailing !is AgentScript || trailing.status != AgentScriptStatus.PENDING_INPUT) {
            return null
        }
        return trailing
    }

    private suspend fun persist(runtime: SessionState) {
        repository.persistSession(runtime.metadata.value.id, runtime)
    }

    private suspend fun loadRuntime(sessionId: String): SessionState? {
        return runCatching {
            sessionFactory.loadSession(sessionId)
        }.getOrNull()
    }

    private suspend fun requireRuntime(sessionId: String): SessionState {
        return sessionFactory.loadSession(sessionId)
    }

    private fun generateId(): String {
        return Uuid.random().toString()
    }

    private fun <E> List<E>.toPersistentList() = persistentListOf<E>().addAll(this)

    public suspend fun getAgentTodo(sessionId: String, agentId: String): List<io.github.stream29.kode.session.core.model.TodoNode> {
        val state = getSessionState(sessionId)
        if (state != null) {
            val agent = resolveAgent(state, sessionId, agentId)
            return agent.todoState.value
        }
        return repository.readAgentTodo(sessionId, agentId) ?: emptyList()
    }

    public suspend fun updateAgentTodo(sessionId: String, agentId: String, todos: List<io.github.stream29.kode.session.core.model.TodoNode>) {
        val state = getSessionState(sessionId)
        if (state != null) {
            val agent = resolveAgent(state, sessionId, agentId)
            agent.todoState.value = todos
        }
        repository.writeAgentTodo(sessionId, agentId, todos)
        if (state != null) {
            persist(state)
        }
    }

    private companion object {
        private const val SESSION_CONFIG_MODEL_ID_KEY: String = "preferred_model_id"
        private const val SUBAGENT_NOT_FOUND_ERROR: String = "Subagent not found"
        private const val SUBAGENT_TIMEOUT_ERROR: String = "timeout"
    }
}
