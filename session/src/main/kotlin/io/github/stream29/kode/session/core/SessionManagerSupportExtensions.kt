package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import io.github.stream29.kode.agent.model.Agent
import io.github.stream29.kode.agent.model.AgentState
import io.github.stream29.kode.agent.tool.ToolNames
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.SessionState
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun cancelAndUnregisterSubAgentJob(
    sessionId: String,
    agentId: String,
    reason: String,
    subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>,
) {
    subAgentJobs[sessionId]?.remove(agentId)?.cancel(reason)
}

internal fun SessionState.resolveAgentForSession(
    sessionId: String,
    agentId: String?,
): Agent {
    val mainAgentId = mainAgentId(sessionId)
    if (agentId == null || agentId == mainAgentId) {
        return agent.value
    }
    return subagents.value[agentId]?.delegate
        ?: throw IllegalArgumentException("Agent not found: $agentId")
}

internal fun requestSoftStop(
    sessionId: String,
    runtime: SessionState,
    subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>,
) {
    runtime.runJob.value?.cancel("Soft stop requested by user")
    subAgentJobs[sessionId]?.values?.forEach { job ->
        job.cancel("Soft stop requested by user")
    }
}

internal fun forceStop(
    sessionId: String,
    runtime: SessionState,
    subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>,
    softStopRequestedSessions: MutableSet<String>,
) {
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
    clearSoftStopRequest(sessionId = sessionId, softStopRequestedSessions = softStopRequestedSessions)
}

internal fun normalizeStoppedRuntime(runtime: SessionState) {
    runtime.runJob.value = null
    runtime.agent.value.state.value = AgentState.Suspended
}

internal fun markSoftStopRequested(sessionId: String, softStopRequestedSessions: MutableSet<String>) {
    softStopRequestedSessions.add(sessionId)
}

internal fun isSoftStopRequested(sessionId: String, softStopRequestedSessions: MutableSet<String>): Boolean {
    return softStopRequestedSessions.contains(sessionId)
}

internal fun clearSoftStopRequest(sessionId: String, softStopRequestedSessions: MutableSet<String>) {
    softStopRequestedSessions.remove(sessionId)
}

internal fun clearSoftStopRequestIfSuspended(
    sessionId: String,
    state: SessionRunState,
    softStopRequestedSessions: MutableSet<String>,
) {
    if (state == SessionRunState.Suspended) {
        clearSoftStopRequest(sessionId = sessionId, softStopRequestedSessions = softStopRequestedSessions)
    }
}

internal fun normalizeTrailingPendingScript(agent: Agent): Boolean {
    if (agent.trailingPendingScript() == null) {
        return false
    }
    agent.messages.value = agent.messages.value.dropLast(1).toPersistentList()
    return true
}

internal fun missingSubAgentPollResult(error: String): SessionManager.SubAgentPollResult {
    return SessionManager.SubAgentPollResult.Missing(error = error)
}

internal fun pendingSubAgentPollResult(error: String? = null): SessionManager.SubAgentPollResult {
    return SessionManager.SubAgentPollResult.Pending(error = error)
}

internal fun completedSubAgentPollResult(value: String): SessionManager.SubAgentPollResult {
    return SessionManager.SubAgentPollResult.Completed(result = value)
}

internal fun failedSubAgentPollResult(throwable: Throwable): SessionManager.SubAgentPollResult {
    return SessionManager.SubAgentPollResult.Failed(error = throwable.message)
}

internal fun assertScriptOnlyKoogMessages(koogMessages: List<Message>) {
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

@OptIn(ExperimentalUuidApi::class)
internal fun generateSessionManagerId(): String {
    return Uuid.random().toString()
}
