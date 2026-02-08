package io.github.stream29.kode.session.core
import io.github.stream29.kode.session.core.model.AgentConfig
import io.github.stream29.kode.session.core.model.AgentState
import io.github.stream29.kode.session.core.model.Agent
import io.github.stream29.kode.session.core.model.ContentType
import io.github.stream29.kode.session.core.model.ConversationSession
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.Session
import io.github.stream29.kode.session.core.model.SessionCheckpoint
import io.github.stream29.kode.session.core.model.SessionConfiguration
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.model.SubAgent
import io.github.stream29.kode.session.core.model.ToolCallData
import io.github.stream29.kode.session.core.model.ToolResultData
import io.github.stream29.kode.session.core.model.toConversationSession
import io.github.stream29.kode.session.core.model.toSessionRuntime
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.querySessionSummaries
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

    public enum class SubAgentPollStatus {
        Pending,
        Completed,
        Missing,
        Failed,
    }

    public data class SubAgentPollResult(
        val status: SubAgentPollStatus,
        val result: String?,
        val error: String?,
    )

    public suspend fun createSession(
        title: String,
        systemPrompt: String?,
        tags: List<String>,
        configuration: SessionConfiguration,
    ): ConversationSession {
        val sessionId = generateId()
        val now = clock.now()
        val base = ConversationSession(
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
            runtimeState = SessionState.Suspended,
        )
        val runtime = base.toSessionRuntime()
        runtime.agent.value.config.value = AgentConfig(
            systemPrompt = runtime.config.value.systemPrompt,
            taskDescription = null,
            expectedResult = null,
            canInteractWithUser = true,
        )
        persist(runtime)
        sessionFactory.put(runtime)
        return runtime.toConversationSession()
    }

    public suspend fun getSession(sessionId: String): ConversationSession? {
        val runtime = loadRuntime(sessionId) ?: return null
        return runtime.toConversationSession()
    }

    public suspend fun getRuntimeSession(sessionId: String): Session? {
        return loadRuntime(sessionId)
    }

    public suspend fun beginRun(sessionId: String, ownerJob: Job) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.runJob.value = ownerJob
            runtime.metadata.value = runtime.metadata.value.copy(
                state = SessionState.Running,
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

    public suspend fun stopRun(sessionId: String) {
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
            runtime.runJob.value = null
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.metadata.value = runtime.metadata.value.copy(
                state = SessionState.Suspended,
                updatedAt = clock.now(),
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        structuredData: kotlinx.serialization.json.JsonElement?,
        contentType: ContentType,
        metadata: Map<String, String>?,
        agentId: String? = null,
    ): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val targetAgent = resolveAgent(runtime, sessionId, agentId)
            val message = SessionMessage(
                id = generateId(),
                role = role,
                content = content,
                structuredData = structuredData,
                contentType = contentType,
                timestamp = clock.now(),
                metadata = metadata,
            )
            targetAgent.messages.value = targetAgent.messages.value.add(message)
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
            persist(runtime)
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addUserMessage(
        sessionId: String,
        content: String,
        agentId: String? = null,
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content,
            structuredData = null,
            contentType = ContentType.TEXT,
            metadata = null,
            agentId = agentId,
        )
    }

    public suspend fun addAssistantMessage(
        sessionId: String,
        content: String,
        metadata: Map<String, String>?,
        agentId: String? = null,
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = content,
            structuredData = null,
            contentType = ContentType.TEXT,
            metadata = metadata,
            agentId = agentId,
        )
    }

    public suspend fun addToolCall(
        sessionId: String,
        toolCallData: ToolCallData,
        agentId: String? = null,
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.TOOL_CALL,
            content = "Calling tool: ${toolCallData.toolName}",
            structuredData = Json.encodeToJsonElement(
                ToolCallData.serializer(),
                toolCallData,
            ),
            contentType = ContentType.TOOL_CALL,
            metadata = null,
            agentId = agentId,
        )
    }

    public suspend fun addToolResult(
        sessionId: String,
        toolResultData: ToolResultData,
        agentId: String? = null,
    ): ConversationSession {
        return addMessage(
            sessionId = sessionId,
            role = MessageRole.TOOL_RESULT,
            content = if (toolResultData.isError) {
                "Error: ${toolResultData.errorMessage}"
            } else {
                "Tool result"
            },
            structuredData = Json.encodeToJsonElement(
                ToolResultData.serializer(),
                toolResultData,
            ),
            contentType = if (toolResultData.isError) ContentType.ERROR else ContentType.TOOL_RESULT,
            metadata = null,
            agentId = agentId,
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

    public suspend fun revertToCheckpoint(sessionId: String, checkpointId: String): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val checkpoint = runtime.checkpoints.value.firstOrNull { item -> item.checkpointId == checkpointId }
                ?: throw IllegalArgumentException("Checkpoint not found: $checkpointId")

            runtime.agent.value.messages.value = checkpoint.messages.toPersistentList()
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = checkpoint.version + 1,
                state = SessionState.Suspended,
                messageCount = runtime.agent.value.messages.value.size,
            )
            runtime.agent.value.state.value = AgentState.Suspended
            runtime.runJob.value = null
            persist(runtime)
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun revertToLatestCheckpoint(sessionId: String): ConversationSession? {
        val checkpoint = getLatestCheckpoint(sessionId) ?: return null
        return revertToCheckpoint(sessionId, checkpoint.checkpointId)
    }

    public suspend fun forkSession(
        parentSessionId: String,
        atMessageId: String?,
        newTitle: String?,
    ): ConversationSession {
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
            val child = ConversationSession(
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
                runtimeState = SessionState.Suspended,
            ).toSessionRuntime()

            parent.metadata.value = parent.metadata.value.copy(
                childSessionIds = parent.metadata.value.childSessionIds + childId,
                updatedAt = now,
            )

            persist(parent)
            persist(child)
            sessionFactory.put(child)
            return child.toConversationSession()
        } finally {
            parent.mutex.unlock()
        }
    }

    public suspend fun duplicateSession(sessionId: String, newTitle: String?): ConversationSession {
        val original = requireRuntime(sessionId)
        original.mutex.lock()
        try {
            if (original.metadata.value.state != SessionState.Suspended) {
                throw IllegalStateException("Only suspended sessions can be duplicated")
            }
            val now = clock.now()
            val duplicatedId = generateId()
            val duplicated = ConversationSession(
                id = duplicatedId,
                title = newTitle ?: "${original.metadata.value.title} (Copy)",
                createdAt = now,
                updatedAt = now,
                messages = original.agent.value.messages.value.map { message -> message.copy(id = generateId()) },
                status = SessionStatus.ACTIVE,
                parentSessionId = null,
                forkedFromMessageId = null,
                version = 1L,
                configuration = original.config.value,
                tags = original.metadata.value.tags,
                childSessionIds = emptyList(),
                runtimeState = SessionState.Suspended,
            ).toSessionRuntime()
            persist(duplicated)
            sessionFactory.put(duplicated)
            return duplicated.toConversationSession()
        } finally {
            original.mutex.unlock()
        }
    }

    public suspend fun archiveSession(sessionId: String): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                status = SessionStatus.ARCHIVED,
                state = SessionState.Suspended,
                updatedAt = clock.now(),
            )
            runtime.runJob.value = null
            runtime.agent.value.state.value = AgentState.Suspended
            persist(runtime)
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun restoreSession(sessionId: String): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                status = SessionStatus.ACTIVE,
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toConversationSession()
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
                state = SessionState.Suspended,
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

    public suspend fun importSession(sourceFile: File, newTitle: String?): ConversationSession {
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Import file not found: ${sourceFile.absolutePath}")
        }
        val json = Json {
            ignoreUnknownKeys = true
        }
        val imported = json.decodeFromString<ConversationSession>(sourceFile.readText())
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
            runtimeState = SessionState.Suspended,
        )
        val runtime = normalized.toSessionRuntime()
        persist(runtime)
        sessionFactory.put(runtime)
        return runtime.toConversationSession()
    }

    public suspend fun updateTitle(sessionId: String, newTitle: String): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                title = newTitle,
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun updateConfiguration(
        sessionId: String,
        configuration: SessionConfiguration,
    ): ConversationSession {
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
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun addTags(sessionId: String, tags: List<String>): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                tags = (runtime.metadata.value.tags + tags).distinct(),
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun removeTags(sessionId: String, tags: List<String>): ConversationSession {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            runtime.metadata.value = runtime.metadata.value.copy(
                tags = runtime.metadata.value.tags - tags.toSet(),
                updatedAt = clock.now(),
            )
            persist(runtime)
            return runtime.toConversationSession()
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

    public suspend fun clearMessages(sessionId: String): ConversationSession {
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
            return runtime.toConversationSession()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getAgentConfig(sessionId: String, agentId: String? = null): AgentConfig {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            resolveAgent(runtime, sessionId, agentId).config.value
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun getAgentMessages(sessionId: String, agentId: String? = null): List<SessionMessage> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            resolveAgent(runtime, sessionId, agentId).messages.value
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun createSubAgent(
        sessionId: String,
        agentId: String,
        parentAgentId: String?,
        mode: String,
        taskDescription: String,
        expectedResult: String,
    ) {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            if (runtime.subagents.value.containsKey(agentId)) {
                throw IllegalStateException("Subagent already exists: $agentId")
            }

            val normalizedMode = mode.lowercase()
            if (normalizedMode != "fork" && normalizedMode != "spawn") {
                throw IllegalArgumentException("Invalid subagent mode: $mode")
            }

            val parentAgent = resolveAgent(runtime, sessionId, parentAgentId)
            val parentMessages = parentAgent.messages.value
            val initialMessages = if (normalizedMode == "fork") {
                val toolCallId = generateId()
                val forkCall = SessionMessage(
                    id = generateId(),
                    role = MessageRole.TOOL_CALL,
                    content = "Calling tool: fork",
                    structuredData = Json.encodeToJsonElement(
                        ToolCallData.serializer(),
                        ToolCallData(
                            toolName = "fork",
                            toolCallId = toolCallId,
                            arguments = buildJsonObject { },
                            displayName = null,
                        ),
                    ),
                    contentType = ContentType.TOOL_CALL,
                    timestamp = clock.now(),
                    metadata = mapOf("injected" to "true"),
                )
                val forkResultText = "You are the forked subagent, Your task: $taskDescription Expected Result: $expectedResult"
                val forkResult = SessionMessage(
                    id = generateId(),
                    role = MessageRole.TOOL_RESULT,
                    content = forkResultText,
                    structuredData = Json.encodeToJsonElement(
                        ToolResultData.serializer(),
                        ToolResultData(
                            toolCallId = toolCallId,
                            toolName = "fork",
                            result = JsonPrimitive(forkResultText),
                            isError = false,
                            errorMessage = null,
                        ),
                    ),
                    contentType = ContentType.TOOL_RESULT,
                    timestamp = clock.now(),
                    metadata = mapOf("injected" to "true"),
                )
                parentMessages.add(forkCall).add(forkResult)
            } else {
                persistentListOf()
            }

            val injectedPrompt = buildString {
                parentAgent.config.value.systemPrompt?.let { prompt ->
                    if (prompt.isNotBlank()) {
                        append(prompt)
                        append("\n\n")
                    }
                }
                append("Subagent mode: ")
                append(normalizedMode)
                append("\nYour agentId: ")
                append(agentId)
                append("\nYour parentAgentId: ")
                append(parentAgentId ?: mainAgentId(sessionId))
                append("\nTask: ")
                append(taskDescription)
                append("\nExpected Result: ")
                append(expectedResult)
                append("\nRules: no await_user_input, no createAgent, finish by returnAgentResult.")
            }

            val subAgent = SubAgent(
                delegate = Agent(
                    state = MutableStateFlow(AgentState.Running),
                    config = MutableStateFlow(
                        parentAgent.config.value.copy(
                            systemPrompt = injectedPrompt,
                            taskDescription = taskDescription,
                            expectedResult = expectedResult,
                            canInteractWithUser = false,
                        )
                    ),
                    messages = MutableStateFlow(initialMessages),
                ),
                result = CompletableDeferred(),
            )

            runtime.subagents.value = runtime.subagents.value.put(agentId, subAgent)
            runtime.metadata.value = runtime.metadata.value.copy(
                state = SessionState.Running,
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
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
            val mainRunning = runtime.agent.value.state.value == AgentState.Running && (runtime.runJob.value?.isActive == true)
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
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        val subAgent = try {
            runtime.subagents.value[agentId]
        } finally {
            runtime.mutex.unlock()
        } ?: return SubAgentPollResult(
            status = SubAgentPollStatus.Missing,
            result = null,
            error = "Subagent not found",
        )

        if (!subAgent.result.isCompleted) {
            return SubAgentPollResult(
                status = SubAgentPollStatus.Pending,
                result = null,
                error = null,
            )
        }

        return runCatching {
            subAgent.result.await()
        }.fold(
            onSuccess = { value ->
                SubAgentPollResult(
                    status = SubAgentPollStatus.Completed,
                    result = value,
                    error = null,
                )
            },
            onFailure = { throwable ->
                SubAgentPollResult(
                    status = SubAgentPollStatus.Failed,
                    result = null,
                    error = throwable.message,
                )
            },
        )
    }

    public suspend fun awaitSubAgentResult(
        sessionId: String,
        agentId: String,
        timeoutSeconds: Int,
    ): SubAgentPollResult {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        val subAgent = try {
            runtime.subagents.value[agentId]
        } finally {
            runtime.mutex.unlock()
        } ?: return SubAgentPollResult(
            status = SubAgentPollStatus.Missing,
            result = null,
            error = "Subagent not found",
        )

        return runCatching {
            withTimeout(timeoutSeconds * 1000L) {
                subAgent.result.await()
            }
        }.fold(
            onSuccess = { value ->
                SubAgentPollResult(
                    status = SubAgentPollStatus.Completed,
                    result = value,
                    error = null,
                )
            },
            onFailure = { throwable ->
                if (throwable is kotlinx.coroutines.TimeoutCancellationException) {
                    SubAgentPollResult(
                        status = SubAgentPollStatus.Pending,
                        result = null,
                        error = "timeout",
                    )
                } else {
                    SubAgentPollResult(
                        status = SubAgentPollStatus.Failed,
                        result = null,
                        error = throwable.message,
                    )
                }
            },
        )
    }

    public suspend fun injectReceiveAgentMessage(
        sessionId: String,
        targetAgentId: String,
        fromAgentId: String,
        message: String,
    ): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        try {
            val targetAgent = if (isMainAgent(sessionId, targetAgentId)) {
                runtime.agent.value
            } else {
                val subAgent = runtime.subagents.value[targetAgentId] ?: return false
                if (subAgent.result.isCompleted || subAgent.delegate.state.value != AgentState.Running) {
                    return false
                }
                subAgent.delegate
            }

            val callId = generateId()
            val payload = buildJsonObject {
                put("agentId", JsonPrimitive(fromAgentId))
                put("message", JsonPrimitive(message))
            }
            val injectedCall = SessionMessage(
                id = generateId(),
                role = MessageRole.TOOL_CALL,
                content = "Calling tool: receiveAgentMessage",
                structuredData = Json.encodeToJsonElement(
                    ToolCallData.serializer(),
                    ToolCallData(
                        toolName = "receiveAgentMessage",
                        toolCallId = callId,
                        arguments = payload,
                        displayName = null,
                    ),
                ),
                contentType = ContentType.TOOL_CALL,
                timestamp = clock.now(),
                metadata = mapOf("injected" to "true"),
            )
            val injectedResult = SessionMessage(
                id = generateId(),
                role = MessageRole.TOOL_RESULT,
                content = "receiveAgentMessage",
                structuredData = Json.encodeToJsonElement(
                    ToolResultData.serializer(),
                    ToolResultData(
                        toolCallId = callId,
                        toolName = "receiveAgentMessage",
                        result = payload,
                        isError = false,
                        errorMessage = null,
                    ),
                ),
                contentType = ContentType.TOOL_RESULT,
                timestamp = clock.now(),
                metadata = mapOf("injected" to "true"),
            )

            targetAgent.messages.value = targetAgent.messages.value.add(injectedCall).add(injectedResult)
            runtime.metadata.value = runtime.metadata.value.copy(
                updatedAt = clock.now(),
                version = runtime.metadata.value.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
            persist(runtime)
            return true
        } finally {
            runtime.mutex.unlock()
        }
    }

    public fun registerSubAgentJob(sessionId: String, agentId: String, job: Job) {
        val sessionJobs = subAgentJobs.getOrPut(sessionId) { ConcurrentHashMap() }
        sessionJobs[agentId] = job
    }

    public fun unregisterSubAgentJob(sessionId: String, agentId: String) {
        subAgentJobs[sessionId]?.remove(agentId)
    }

    private fun resolveAgent(runtime: Session, sessionId: String, agentId: String?): Agent {
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

    private fun computeSessionState(runtime: Session): SessionState {
        val mainRunning = runtime.agent.value.state.value == AgentState.Running && (runtime.runJob.value?.isActive == true)
        val subRunning = runtime.subagents.value.any { (_, subAgent) ->
            subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
        }
        return if (mainRunning || subRunning) {
            SessionState.Running
        } else {
            SessionState.Suspended
        }
    }

    private suspend fun persist(runtime: Session) {
        repository.persistSession(runtime.metadata.value.id, runtime)
    }

    private suspend fun loadRuntime(sessionId: String): Session? {
        return runCatching {
            sessionFactory.loadSession(sessionId)
        }.getOrNull()
    }

    private suspend fun requireRuntime(sessionId: String): Session {
        return sessionFactory.loadSession(sessionId)
    }

    private fun generateId(): String {
        return Uuid.random().toString()
    }

    private fun <E> List<E>.toPersistentList() = persistentListOf<E>().addAll(this)

}
