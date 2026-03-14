package io.github.stream29.kode.session.core

import io.github.stream29.kode.agent.model.SubAgent
import io.github.stream29.kode.session.core.model.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

public fun interface SessionSubAgentCoordinatorFactory {
    public fun create(
        requireRuntime: suspend (String) -> SessionState,
        persist: suspend (SessionState) -> Unit,
        clock: Clock,
        softStopRequestedSessions: MutableSet<String>,
        subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>,
    ): SessionSubAgentCoordinator
}

public val DefaultSessionSubAgentCoordinatorFactory: SessionSubAgentCoordinatorFactory =
    SessionSubAgentCoordinatorFactory { requireRuntime, persist, clock, softStopRequestedSessions, subAgentJobs ->
        SessionSubAgentCoordinator(
            requireRuntime = requireRuntime,
            persist = persist,
            clock = clock,
            softStopRequestedSessions = softStopRequestedSessions,
            subAgentJobs = subAgentJobs,
        )
    }

public class SessionSubAgentCoordinator(
    private val requireRuntime: suspend (String) -> SessionState,
    private val persist: suspend (SessionState) -> Unit,
    private val clock: Clock,
    private val softStopRequestedSessions: MutableSet<String>,
    private val subAgentJobs: ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>,
) {
    public fun requestSoftStop(sessionId: String, runtime: SessionState) {
        requestSoftStop(
            sessionId = sessionId,
            runtime = runtime,
            subAgentJobs = subAgentJobs,
        )
    }

    public fun forceStop(sessionId: String, runtime: SessionState) {
        forceStop(
            sessionId = sessionId,
            runtime = runtime,
            subAgentJobs = subAgentJobs,
            softStopRequestedSessions = softStopRequestedSessions,
        )
    }

    public suspend fun completeSubAgentResult(sessionId: String, agentId: String, result: String): Boolean {
        return finishSubAgent(
            sessionId = sessionId,
            agentId = agentId,
            resultText = result,
        )
    }

    public suspend fun cancelSubAgent(sessionId: String, agentId: String, reason: String): Boolean {
        return finishSubAgent(
            sessionId = sessionId,
            agentId = agentId,
            resultText = reason,
        )
    }

    public suspend fun killSubAgent(sessionId: String, agentId: String): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            val subAgent = runtime.findSubAgent(agentId)
            if (subAgent == null || subAgent.result.isCompleted) {
                false
            } else {
                cancelAndUnregisterSubAgentJob(
                    sessionId = sessionId,
                    agentId = agentId,
                    reason = "Killed by parent agent",
                    subAgentJobs = subAgentJobs,
                )
                val changed = runtime.killSubAgentMutation(
                    agentId = agentId,
                    now = clock.now(),
                )
                if (!changed) {
                    return false
                }
                clearSoftStopRequestIfSuspended(
                    sessionId = sessionId,
                    state = runtime.metadata.value.state,
                    softStopRequestedSessions = softStopRequestedSessions,
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
            runtime.listActiveSubAgentIds()
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun listActiveAgentIds(sessionId: String): List<String> {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.listActiveAgentIds(sessionId = sessionId)
        } finally {
            runtime.mutex.unlock()
        }
    }

    public suspend fun pollSubAgentResult(sessionId: String, agentId: String): SessionManager.SubAgentPollResult {
        val subAgent = loadSubAgent(sessionId = sessionId, agentId = agentId)
            ?: return missingSubAgentPollResult(error = SUBAGENT_NOT_FOUND_ERROR)

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
    ): SessionManager.SubAgentPollResult {
        val subAgent = loadSubAgent(sessionId = sessionId, agentId = agentId)
            ?: return missingSubAgentPollResult(error = SUBAGENT_NOT_FOUND_ERROR)

        return runCatching {
            withTimeout(timeoutSeconds * 1000L) {
                subAgent.result.await()
            }
        }.fold(
            onSuccess = { value -> completedSubAgentPollResult(value) },
            onFailure = { throwable ->
                if (throwable is TimeoutCancellationException) {
                    pendingSubAgentPollResult(error = SUBAGENT_TIMEOUT_ERROR)
                } else {
                    failedSubAgentPollResult(throwable)
                }
            },
        )
    }

    public fun registerSubAgentJob(sessionId: String, agentId: String, job: Job) {
        val sessionJobs = subAgentJobs.getOrPut(sessionId) { ConcurrentHashMap() }
        sessionJobs[agentId] = job
    }

    public fun unregisterSubAgentJob(sessionId: String, agentId: String) {
        subAgentJobs[sessionId]?.remove(agentId)
    }

    public fun cancelSessionJobs(sessionId: String, reason: String) {
        subAgentJobs.remove(sessionId)?.values?.forEach { job ->
            job.cancel(reason)
        }
    }

    private suspend fun finishSubAgent(
        sessionId: String,
        agentId: String,
        resultText: String,
    ): Boolean {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            val subAgent = runtime.findSubAgent(agentId)
            if (subAgent == null || subAgent.result.isCompleted) {
                false
            } else {
                cancelAndUnregisterSubAgentJob(
                    sessionId = sessionId,
                    agentId = agentId,
                    reason = "Subagent finished",
                    subAgentJobs = subAgentJobs,
                )
                val changed = runtime.finishSubAgentMutation(
                    agentId = agentId,
                    resultText = resultText,
                    now = clock.now(),
                )
                if (!changed) {
                    return false
                }
                clearSoftStopRequestIfSuspended(
                    sessionId = sessionId,
                    state = runtime.metadata.value.state,
                    softStopRequestedSessions = softStopRequestedSessions,
                )
                persist(runtime)
                true
            }
        } finally {
            runtime.mutex.unlock()
        }
    }

    private suspend fun loadSubAgent(sessionId: String, agentId: String): SubAgent? {
        val runtime = requireRuntime(sessionId)
        runtime.mutex.lock()
        return try {
            runtime.findSubAgent(agentId = agentId)
        } finally {
            runtime.mutex.unlock()
        }
    }

    private companion object {
        private const val SUBAGENT_NOT_FOUND_ERROR: String = "Subagent not found"
        private const val SUBAGENT_TIMEOUT_ERROR: String = "timeout"
    }
}
