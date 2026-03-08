package io.github.stream29.kode.session.core

import io.github.stream29.kode.agent.model.Agent
import io.github.stream29.kode.agent.model.SubAgent
import io.github.stream29.kode.session.core.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Clock

public fun interface SessionPersistenceObserverCoordinatorFactory {
    public fun create(
        persistencePort: SessionPersistencePort,
        clock: Clock,
    ): SessionPersistenceObserverCoordinator
}

public val DefaultSessionPersistenceObserverCoordinatorFactory: SessionPersistenceObserverCoordinatorFactory =
    SessionPersistenceObserverCoordinatorFactory { persistencePort, clock ->
        SessionPersistenceObserverCoordinator(
            persistencePort = persistencePort,
            clock = clock,
        )
    }

public class SessionPersistenceObserverCoordinator(
    private val persistencePort: SessionPersistencePort,
    private val clock: Clock,
) {
    private val persistenceObserverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionPersistenceObserverJobs: MutableMap<String, Job> = java.util.concurrent.ConcurrentHashMap()
    private val sessionLastPersistedDigests: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    public suspend fun persist(runtime: SessionState) {
        val sessionId = runtime.metadata.value.id
        persistencePort.persistSession(sessionId, runtime)
        sessionLastPersistedDigests[sessionId] = computePersistenceDigest(runtime)
    }

    public fun ensureSessionPersistenceObserver(sessionId: String, runtime: SessionState) {
        if (sessionPersistenceObserverJobs.containsKey(sessionId)) {
            return
        }
        sessionLastPersistedDigests.putIfAbsent(sessionId, computePersistenceDigest(runtime))
        val observerJob = persistenceObserverScope.launch {
            data class SubAgentObserver(
                val subAgent: SubAgent,
                val jobs: List<Job>,
            )

            var mainAgentObservers: List<Job> = emptyList()
            val observedSubAgents: MutableMap<String, SubAgentObserver> = linkedMapOf()

            fun restartMainAgentObservers(agent: Agent) {
                mainAgentObservers.forEach { job ->
                    job.cancel("Main agent observer rewired")
                }
                mainAgentObservers = listOf(
                    launch {
                        agent.state.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        agent.config.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        agent.messages.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        agent.todoMetadataFlow().collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                )
            }

            fun buildSubAgentObservers(subAgent: SubAgent): List<Job> {
                return listOf(
                    launch {
                        subAgent.delegate.state.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        subAgent.delegate.config.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        subAgent.delegate.messages.collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        subAgent.delegate.todoMetadataFlow().collect {
                            maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                        }
                    },
                    launch {
                        runCatching { subAgent.result.await() }
                        maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                    },
                )
            }

            fun reconcileSubAgentObservers(subagents: Map<String, SubAgent>) {
                val removedIds = observedSubAgents.keys.filter { id -> id !in subagents.keys }
                removedIds.forEach { id ->
                    observedSubAgents.remove(id)?.jobs?.forEach { job ->
                        job.cancel("Subagent observer removed")
                    }
                }

                subagents.forEach { (id, subAgent) ->
                    val existing = observedSubAgents[id]
                    if (existing == null || existing.subAgent !== subAgent) {
                        existing?.jobs?.forEach { job ->
                            job.cancel("Subagent observer replaced")
                        }
                        observedSubAgents[id] = SubAgentObserver(
                            subAgent = subAgent,
                            jobs = buildSubAgentObservers(subAgent = subAgent),
                        )
                    }
                }
            }

            launch {
                runtime.metadata.collect {
                    maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                }
            }
            launch {
                runtime.config.collect {
                    maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                }
            }
            launch {
                runtime.agent.collect { agent ->
                    restartMainAgentObservers(agent = agent)
                    maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                }
            }
            launch {
                runtime.subagents.collect { subagents ->
                    reconcileSubAgentObservers(subagents = subagents)
                    maybePersistObservedMutation(sessionId = sessionId, runtime = runtime)
                }
            }

            awaitCancellation()
        }
        val previous = sessionPersistenceObserverJobs.putIfAbsent(sessionId, observerJob)
        if (previous != null) {
            observerJob.cancel("Session persistence observer already registered")
        }
    }

    public fun cancelSessionPersistenceObserver(sessionId: String, reason: String) {
        sessionPersistenceObserverJobs.remove(sessionId)?.cancel(reason)
        sessionLastPersistedDigests.remove(sessionId)
    }

    private suspend fun maybePersistObservedMutation(
        sessionId: String,
        runtime: SessionState,
    ) {
        runtime.mutex.lock()
        try {
            val currentDigest = computePersistenceDigest(runtime)
            if (sessionLastPersistedDigests[sessionId] == currentDigest) {
                return
            }
            val metadata = runtime.metadata.value
            runtime.metadata.value = metadata.copy(
                updatedAt = clock.now(),
                version = metadata.version + 1,
                messageCount = runtime.agent.value.messages.value.size,
            )
            persist(runtime)
        } finally {
            runtime.mutex.unlock()
        }
    }

    private fun computePersistenceDigest(runtime: SessionState): Int {
        var digest = 1
        digest = 31 * digest + runtime.metadata.value.hashCode()
        digest = 31 * digest + runtime.config.value.hashCode()

        val mainAgent = runtime.agent.value
        digest = 31 * digest + mainAgent.state.value.hashCode()
        digest = 31 * digest + mainAgent.config.value.hashCode()
        digest = 31 * digest + mainAgent.messages.value.hashCode()
        digest = 31 * digest + mainAgent.readTodoFromMetadata().hashCode()

        val subagents = runtime.subagents.value.entries.sortedBy { entry -> entry.key }
        subagents.forEach { (id, subAgent) ->
            digest = 31 * digest + id.hashCode()
            digest = 31 * digest + subAgent.delegate.state.value.hashCode()
            digest = 31 * digest + subAgent.delegate.config.value.hashCode()
            digest = 31 * digest + subAgent.delegate.messages.value.hashCode()
            digest = 31 * digest + subAgent.delegate.readTodoFromMetadata().hashCode()
            digest = 31 * digest + subAgent.result.isCompleted.hashCode()
        }
        return digest
    }
}
