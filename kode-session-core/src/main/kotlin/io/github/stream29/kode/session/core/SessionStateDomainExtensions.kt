package io.github.stream29.kode.session.core

import io.github.stream29.kode.agent.model.Agent
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentState
import io.github.stream29.kode.agent.model.SessionMessage
import io.github.stream29.kode.agent.model.SubAgent
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.agent.model.trailingPendingInputScriptOrNull
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.SessionSnapshot
import io.github.stream29.kode.session.core.model.SessionState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionConfiguration
import io.github.stream29.kode.session.core.model.toSessionState
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.datetime.toDeprecatedInstant
import kotlin.time.Instant

internal fun mainAgentId(sessionId: String): String {
    return "main-$sessionId"
}

internal fun isMainAgent(sessionId: String, agentId: String?): Boolean {
    if (agentId == null) {
        return true
    }
    return agentId == mainAgentId(sessionId)
}

internal fun SessionState.computeSessionState(): SessionRunState {
    val mainRunning =
        agent.value.state.value == AgentState.Running && runJob.value != null
    if (mainRunning) {
        return SessionRunState.Running
    }
    return computeSessionStateWhenMainSuspended()
}

internal fun SessionState.computeSessionStateWhenMainSuspended(): SessionRunState {
    val subRunning = subagents.value.any { (_, subAgent) ->
        subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
    }
    return if (subRunning) {
        SessionRunState.Running
    } else {
        SessionRunState.Suspended
    }
}

internal fun SessionMessage.copyWithNewId(newId: String): SessionMessage {
    return when (this) {
        is UserMessage -> copy(id = newId)
        is AgentScript -> copy(id = newId)
    }
}

internal fun Agent.trailingPendingScript(): AgentScript? {
    return messages.value.trailingPendingInputScriptOrNull()
}

internal fun SessionState.applyBeginRunMutation(ownerJob: Job, now: Instant): Boolean {
    val currentOwner = runJob.value
    if (
        currentOwner != null &&
        currentOwner != ownerJob &&
        metadata.value.state == SessionRunState.Running
    ) {
        throw IllegalStateException("Session run is already owned by another active job")
    }
    val alreadyRunningByOwner =
        currentOwner == ownerJob &&
                metadata.value.state == SessionRunState.Running &&
                agent.value.state.value == AgentState.Running
    if (alreadyRunningByOwner) {
        return false
    }

    runJob.value = ownerJob
    metadata.value = metadata.value.copy(
        state = SessionRunState.Running,
        updatedAt = now,
    )
    agent.value.state.value = AgentState.Running
    return true
}

internal data class SuspendMainMutationResult(
    val changed: Boolean,
    val targetState: SessionRunState,
)

internal fun SessionState.applySuspendMainMutation(now: Instant): SuspendMainMutationResult {
    val targetState = computeSessionStateWhenMainSuspended()
    val alreadySuspended =
        runJob.value == null &&
                agent.value.state.value == AgentState.Suspended &&
                metadata.value.state == targetState
    if (alreadySuspended) {
        return SuspendMainMutationResult(
            changed = false,
            targetState = targetState,
        )
    }

    runJob.value = null
    agent.value.state.value = AgentState.Suspended
    metadata.value = metadata.value.copy(
        state = targetState,
        updatedAt = now,
    )
    return SuspendMainMutationResult(
        changed = true,
        targetState = targetState,
    )
}

internal fun SessionState.appendMessageMutation(targetAgent: Agent, message: SessionMessage, now: Instant) {
    targetAgent.messages.value = targetAgent.messages.value.add(message)
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
        messageCount = agent.value.messages.value.size,
    )
}

internal fun SessionState.applyStopMetadataMutation(now: Instant): SessionRunState {
    val targetState = computeSessionState()
    metadata.value = metadata.value.copy(
        state = targetState,
        updatedAt = now,
        version = metadata.value.version + 1,
        messageCount = agent.value.messages.value.size,
    )
    return targetState
}

internal fun SessionState.applyPendingRollbackMetadataMutation(now: Instant) {
    metadata.value = metadata.value.copy(
        state = computeSessionState(),
        updatedAt = now,
        version = metadata.value.version + 1,
        messageCount = agent.value.messages.value.size,
    )
}

internal fun SessionState.applyContinuationInputMutation(
    sessionId: String,
    agentId: String?,
    input: String,
    now: Instant,
): Boolean {
    if (metadata.value.state != SessionRunState.Suspended) {
        throw IllegalStateException("Session continuation requires a suspended session")
    }
    val targetAgent = resolveAgentForSession(sessionId = sessionId, agentId = agentId)
    val pendingScript = targetAgent.trailingPendingScript()
    if (pendingScript != null) {
        throw IllegalStateException(
            "Script-only violation: pending script '${pendingScript.scriptId}' blocks continue; resolve pending-input state first"
        )
    }
    if (input.isBlank()) {
        return false
    }

    targetAgent.messages.value = targetAgent.messages.value.add(
        UserMessage(
            id = generateSessionManagerId(),
            content = input,
            timestamp = now,
            koogMessages = listOf(
                ai.koog.prompt.message.Message.User(
                    content = input,
                    metaInfo = ai.koog.prompt.message.RequestMetaInfo(timestamp = now.toDeprecatedInstant()),
                )
            ),
            metadata = null,
        )
    )
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
        messageCount = agent.value.messages.value.size,
    )
    return true
}

internal fun SessionState.rollbackTrailingPendingScriptMutation(sessionId: String, agentId: String?, now: Instant): Boolean {
    val targetAgent = resolveAgentForSession(sessionId = sessionId, agentId = agentId)
    if (targetAgent.trailingPendingScript() == null) {
        return false
    }

    targetAgent.messages.value = targetAgent.messages.value.dropLast(1).toPersistentList()
    runJob.value = null
    if (isMainAgent(sessionId = sessionId, agentId = agentId)) {
        agent.value.state.value = AgentState.Suspended
    }
    applyPendingRollbackMetadataMutation(now = now)
    return true
}

internal fun SessionState.findSubAgent(agentId: String): SubAgent? {
    return subagents.value[agentId]
}

internal fun SessionState.finishSubAgentMutation(agentId: String, resultText: String, now: Instant): Boolean {
    val subAgent = subagents.value[agentId]
    if (subAgent == null || subAgent.result.isCompleted) {
        return false
    }

    subAgent.result.complete(resultText)
    subAgent.delegate.state.value = AgentState.Suspended
    metadata.value = metadata.value.copy(
        state = computeSessionState(),
        updatedAt = now,
        version = metadata.value.version + 1,
    )
    return true
}

internal fun SessionState.killSubAgentMutation(agentId: String, now: Instant): Boolean {
    val subAgent = subagents.value[agentId]
    if (subAgent == null || subAgent.result.isCompleted) {
        return false
    }

    subAgent.result.complete("Killed by parent agent")
    subagents.value = subagents.value.remove(agentId)
    metadata.value = metadata.value.copy(
        state = computeSessionState(),
        updatedAt = now,
        version = metadata.value.version + 1,
    )
    return true
}

internal fun SessionState.listActiveSubAgentIds(): List<String> {
    return subagents.value.entries
        .filter { (_, subAgent) ->
            subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
        }
        .map { (agentId, _) -> agentId }
        .sorted()
}

internal fun SessionState.listActiveAgentIds(sessionId: String): List<String> {
    val activeIds = mutableListOf<String>()
    val mainRunning =
        agent.value.state.value == AgentState.Running && runJob.value != null
    if (mainRunning) {
        activeIds += mainAgentId(sessionId)
    }
    subagents.value.entries
        .filter { (_, subAgent) ->
            subAgent.delegate.state.value == AgentState.Running && !subAgent.result.isCompleted
        }
        .mapTo(activeIds) { (agentId, _) -> agentId }
    return activeIds.sorted()
}

internal fun SessionState.forkSessionMutation(
    parentSessionId: String,
    atMessageId: String?,
    newTitle: String?,
    childId: String,
    now: Instant,
): SessionState {
    if (metadata.value.state != SessionRunState.Suspended) {
        throw IllegalStateException("Only suspended sessions can be forked")
    }
    val parentMessages = agent.value.messages.value
    val messages = if (atMessageId != null) {
        val index = parentMessages.indexOfFirst { item -> item.id == atMessageId }
        if (index < 0) {
            throw IllegalArgumentException("Message not found: $atMessageId")
        }
        parentMessages.take(index + 1)
    } else {
        parentMessages
    }

    val child = SessionSnapshot(
        id = childId,
        title = newTitle ?: "${metadata.value.title} (Fork)",
        createdAt = now,
        updatedAt = now,
        messages = messages,
        status = SessionStatus.ACTIVE,
        parentSessionId = parentSessionId,
        forkedFromMessageId = atMessageId,
        version = 1L,
        configuration = config.value,
        tags = metadata.value.tags,
        childSessionIds = emptyList(),
        runtimeState = SessionRunState.Suspended,
    ).toSessionState()

    metadata.value = metadata.value.copy(
        childSessionIds = metadata.value.childSessionIds + childId,
        updatedAt = now,
    )

    return child
}

internal fun SessionState.duplicateSessionMutation(
    newTitle: String?,
    duplicatedId: String,
    now: Instant,
    messageIdGenerator: () -> String,
): SessionState {
    if (metadata.value.state != SessionRunState.Suspended) {
        throw IllegalStateException("Only suspended sessions can be duplicated")
    }

    return SessionSnapshot(
        id = duplicatedId,
        title = newTitle ?: "${metadata.value.title} (Copy)",
        createdAt = now,
        updatedAt = now,
        messages = agent.value.messages.value.map { message -> message.copyWithNewId(messageIdGenerator()) },
        status = SessionStatus.ACTIVE,
        parentSessionId = null,
        forkedFromMessageId = null,
        version = 1L,
        configuration = config.value,
        tags = metadata.value.tags,
        childSessionIds = emptyList(),
        runtimeState = SessionRunState.Suspended,
    ).toSessionState()
}

internal fun SessionState.archiveSessionMutation(now: Instant) {
    metadata.value = metadata.value.copy(
        status = SessionStatus.ARCHIVED,
        state = SessionRunState.Suspended,
        updatedAt = now,
    )
    runJob.value = null
    agent.value.state.value = AgentState.Suspended
}

internal fun SessionState.restoreSessionMutation(now: Instant) {
    metadata.value = metadata.value.copy(
        status = SessionStatus.ACTIVE,
        updatedAt = now,
    )
}

internal fun SessionState.softDeleteSessionMutation(now: Instant) {
    metadata.value = metadata.value.copy(
        status = SessionStatus.DELETED,
        state = SessionRunState.Suspended,
        updatedAt = now,
    )
    agent.value.state.value = AgentState.Suspended
    runJob.value = null
}

internal fun SessionState.applyTitleMutation(newTitle: String, now: Instant) {
    metadata.value = metadata.value.copy(
        title = newTitle,
        updatedAt = now,
    )
}

internal fun SessionState.applyConfigurationMutation(configuration: SessionConfiguration, now: Instant) {
    config.value = configuration
    agent.value.config.value = agent.value.config.value.copy(
        systemPrompt = configuration.systemPrompt,
    )
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
    )
}

internal fun SessionState.applyWorkDirMutation(workDir: String?, now: Instant): Boolean {
    if (metadata.value.state != SessionRunState.Suspended) {
        throw IllegalStateException("Session work directory can only be changed while suspended")
    }
    if (config.value.workDir == workDir) {
        return false
    }
    config.value = config.value.copy(workDir = workDir)
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
    )
    return true
}

internal fun SessionState.addTagsMutation(tags: List<String>, now: Instant) {
    metadata.value = metadata.value.copy(
        tags = (metadata.value.tags + tags).distinct(),
        updatedAt = now,
    )
}

internal fun SessionState.removeTagsMutation(tags: List<String>, now: Instant) {
    metadata.value = metadata.value.copy(
        tags = metadata.value.tags - tags.toSet(),
        updatedAt = now,
    )
}

internal fun SessionState.clearMessagesMutation(now: Instant) {
    agent.value.messages.value = persistentListOf()
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
        messageCount = 0,
    )
}

internal fun SessionState.updateAgentTodoMutation(
    sessionId: String,
    agentId: String,
    todos: List<io.github.stream29.kode.agent.model.TodoItem>,
    now: Instant,
): Boolean {
    val targetAgent = resolveAgentForSession(sessionId = sessionId, agentId = agentId)
    if (targetAgent.readTodoFromMetadata() == todos) {
        return false
    }
    if (!targetAgent.writeTodoToMetadata(todos)) {
        return false
    }
    metadata.value = metadata.value.copy(
        updatedAt = now,
        version = metadata.value.version + 1,
    )
    return true
}
