package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Suppress("unused")
@LLMDescription(
    "Create and manage sub-agents (tasks) that can work in parallel. " +
        "Use this to delegate work to specialized sub-agents that can run concurrently."
)
public class TaskTool public constructor(
    private val messageHandler: MessageHandler,
    private val agentFactory: AgentFactory,
    private val logger: (String) -> Unit = { println(it) },
    private val sessionManager: SessionManager? = null,
    private val ownerSessionId: String? = null,
    private val ownerAgentId: String? = null,
) : ToolSet {

    private fun createTask(
        @LLMDescription("A clear, specific task description for the sub-agent")
        task: String,
        @LLMDescription("Optional name/identifier for this task (for your reference)")
        taskName: String? = null,
    ): TaskCreationResult {
        val taskId = GLOBAL_ID_GENERATOR.getAndIncrement()
        val name = taskName ?: "Task-$taskId"

        logger("🚀 Creating sub-agent task #$taskId: $name")
        messageHandler.addMessageToUser("🚀 Starting sub-agent: $name")

        val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val deferred = taskScope.async {
            try {
                val subAgent = agentFactory.createAgent()
                logger("▶️ Task #$taskId running: $task")
                val result = subAgent.run(task)
                logger("✅ Task #$taskId completed")
                TaskResult.success(result)
            } catch (e: Exception) {
                logger("❌ Task #$taskId failed: ${e.message}")
                TaskResult.failure(e.message ?: "Unknown error")
            }
        }

        GLOBAL_TASKS[taskId] = TaskInfo(
            id = taskId,
            name = name,
            description = task,
            deferred = deferred,
            scope = taskScope,
            agentId = null,
            sessionId = null,
        )

        return TaskCreationResult(
            success = true,
            taskId = taskId,
            name = name,
            message = "Created task #$taskId: $name",
        )
    }

    @Tool(customName = "fork_subagent")
    @LLMDescription("Fork a subagent that inherits parent context for delegated parallel work.")
    public suspend fun forkSubagent(
        @LLMDescription("Atomic task description for the subagent")
        taskDescription: String,
        @LLMDescription("Expected result format and completion criteria")
        expectedResult: String,
    ): AgentCreationResult {
        return createAgent(
            mode = "fork",
            taskDescription = taskDescription,
            expectedResult = expectedResult,
        )
    }

    @Tool(customName = "spawn_subagent")
    @LLMDescription("Spawn a fresh subagent without inheriting parent context.")
    public suspend fun spawnSubagent(
        @LLMDescription("Atomic task description for the subagent")
        taskDescription: String,
        @LLMDescription("Expected result format and completion criteria")
        expectedResult: String,
    ): AgentCreationResult {
        return createAgent(
            mode = "spawn",
            taskDescription = taskDescription,
            expectedResult = expectedResult,
        )
    }

    private suspend fun createAgent(
        mode: String,
        taskDescription: String,
        expectedResult: String,
    ): AgentCreationResult {
        val manager = sessionManager
        val sessionId = ownerSessionId
        if (manager == null || sessionId.isNullOrBlank()) {
            return AgentCreationResult(
                success = false,
                agentId = "",
                mode = mode,
                message = "Subagent creation is unavailable in current context.",
            )
        }

        val normalizedMode = mode.trim().lowercase()
        if (normalizedMode != "fork" && normalizedMode != "spawn") {
            return AgentCreationResult(
                success = false,
                agentId = "",
                mode = normalizedMode,
                message = "Invalid mode '$mode'. Use 'fork' or 'spawn'.",
            )
        }

        val taskId = GLOBAL_ID_GENERATOR.getAndIncrement()
        val agentId = "agent-$taskId"
        val parentAgentId = ownerAgentId ?: mainAgentId(sessionId)
        val taskName = "SubAgent-$normalizedMode-${System.currentTimeMillis()}"

        manager.createSubAgent(
            sessionId = sessionId,
            agentId = agentId,
            parentAgentId = parentAgentId,
            mode = normalizedMode,
            taskDescription = taskDescription,
            expectedResult = expectedResult,
        )

        logger("🚀 Creating subagent $agentId ($normalizedMode)")
        messageHandler.addMessageToUser("🚀 Starting sub-agent: $agentId")

        val parentContext = currentCoroutineContext()
        val taskScope = CoroutineScope(parentContext + Dispatchers.IO)
        val deferred = taskScope.async {
            val currentJob = requireNotNull(currentCoroutineContext()[Job]) { "Subagent requires job context" }
            manager.registerSubAgentJob(sessionId, agentId, currentJob)
            try {
                val result = agentFactory.runSubAgent(
                    sessionId = sessionId,
                    agentId = agentId,
                    parentAgentId = parentAgentId,
                    mode = normalizedMode,
                    taskDescription = taskDescription,
                    expectedResult = expectedResult,
                )
                manager.completeSubAgentResult(sessionId, agentId, result)
                TaskResult.success(result)
            } catch (e: CancellationException) {
                manager.cancelSubAgent(sessionId, agentId, "Cancelled by parent agent")
                throw e
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                manager.completeSubAgentResult(sessionId, agentId, "Subagent failed: $error")
                TaskResult.failure(error)
            } finally {
                manager.unregisterSubAgentJob(sessionId, agentId)
            }
        }

        GLOBAL_TASKS[taskId] = TaskInfo(
            id = taskId,
            name = taskName,
            description = "$normalizedMode: $taskDescription",
            deferred = deferred,
            scope = taskScope,
            agentId = agentId,
            sessionId = sessionId,
        )
        GLOBAL_AGENT_TASK_IDS[agentId] = taskId
        GLOBAL_AGENT_SESSION_IDS[agentId] = sessionId

        return AgentCreationResult(
            success = true,
            agentId = agentId,
            mode = normalizedMode,
            message = "Created subagent $agentId",
        )
    }

    @Tool
    @LLMDescription(
        "Poll subagent result without blocking. " +
            "Returns pending if the subagent has not finished."
    )
    public suspend fun pollAgentResult(
        @LLMDescription("Target agent ID")
        agentId: String,
    ): AgentPollResult {
        val taskInfo = when (val resolvedTask = resolveAgentTask(agentId)) {
            is ResolvedAgentTask.Missing -> {
                return missingAgentPollResult(agentId = agentId, reason = resolvedTask.reason)
            }

            is ResolvedAgentTask.Found -> resolvedTask.taskInfo
        }

        val sessionId = taskInfo.sessionId
        val manager = sessionManager
        if (manager != null && sessionId != null) {
            val polled = manager.pollSubAgentResult(sessionId, agentId)
            return fromSessionPollResult(
                agentId = agentId,
                pollStatus = polled.status,
                result = polled.result,
                error = polled.error,
                timeoutAsPending = false,
            )
        }

        if (!taskInfo.deferred.isCompleted) {
            return agentPendingPollResult(agentId = agentId, success = true, message = "pending")
        }

        val outcome = runCatching { taskInfo.deferred.await() }
            .getOrElse { throwable -> TaskResult.failure(throwable.message ?: "Subagent failed") }

        return createAgentPollResult(
            success = outcome.success,
            status = if (outcome.success) "completed" else "failed",
            agentId = agentId,
            result = outcome.output,
            message = outcome.output ?: outcome.error ?: "completed",
        )
    }

    @Tool
    @LLMDescription("Wait for subagent completion with timeout in seconds")
    public suspend fun awaitAgentResult(
        @LLMDescription("Target agent ID")
        agentId: String,
        @LLMDescription("Timeout in seconds")
        timeout: Int = 300,
    ): AgentPollResult {
        val foundTask = when (val resolvedTask = resolveAgentTask(agentId)) {
            is ResolvedAgentTask.Missing -> {
                return missingAgentPollResult(agentId = agentId, reason = resolvedTask.reason)
            }

            is ResolvedAgentTask.Found -> resolvedTask
        }
        val taskId = foundTask.taskId
        val taskInfo = foundTask.taskInfo

        val sessionId = taskInfo.sessionId
        val manager = sessionManager
        if (manager != null && sessionId != null) {
            val awaited = manager.awaitSubAgentResult(sessionId, agentId, timeout)
            return fromSessionPollResult(
                agentId = agentId,
                pollStatus = awaited.status,
                result = awaited.result,
                error = awaited.error,
                timeoutAsPending = true,
            )
        }

        val awaitResult = awaitTask(taskId, timeout)
        return createAgentPollResult(
            success = awaitResult.success,
            status = awaitResult.status.toAgentStatus(),
            agentId = agentId,
            result = awaitResult.result,
            message = awaitResult.message,
        )
    }

    @Tool
    @LLMDescription("Kill a running subagent and remove it")
    public suspend fun killAgent(
        @LLMDescription("Target agent ID")
        agentId: String,
    ): AgentOperationResult {
        val taskId = GLOBAL_AGENT_TASK_IDS.remove(agentId)
            ?: return AgentOperationResult(
                success = false,
                agentId = agentId,
                message = "Unknown agentId: $agentId",
            )

        val sessionId = GLOBAL_AGENT_SESSION_IDS.remove(agentId)
        cancelTask(taskId)

        val manager = sessionManager
        if (manager != null && sessionId != null) {
            manager.killSubAgent(sessionId, agentId)
        }

        return AgentOperationResult(
            success = true,
            agentId = agentId,
            message = "Killed subagent $agentId",
        )
    }

    @Tool
    @LLMDescription("List all currently running agents")
    public suspend fun listActiveAgents(): ActiveAgentsResult {
        val manager = sessionManager
        val sessionId = ownerSessionId
        if (manager != null && !sessionId.isNullOrBlank()) {
            val active = manager.listActiveAgentIds(sessionId)
            return ActiveAgentsResult(
                success = true,
                agents = active,
                message = "${active.size} active agents",
            )
        }

        val active = GLOBAL_AGENT_TASK_IDS.entries
            .filter { (_, taskId) -> GLOBAL_TASKS[taskId]?.deferred?.isActive == true }
            .map { (agentId, _) -> agentId }
            .sorted()
        return ActiveAgentsResult(
            success = true,
            agents = active,
            message = "${active.size} active agents",
        )
    }

    @Tool
    @LLMDescription(
        "Send message to another agent. " +
            "It injects receiveAgentMessage into target agent history immediately."
    )
    public suspend fun sayToAgent(
        @LLMDescription("Target agent ID")
        agentId: String,
        @LLMDescription("Message payload")
        message: String,
    ): AgentOperationResult {
        val manager = sessionManager
        val sessionId = ownerSessionId
        if (manager == null || sessionId.isNullOrBlank()) {
            return AgentOperationResult(
                success = false,
                agentId = agentId,
                message = "Agent communication unavailable in current context.",
            )
        }

        val fromAgentId = ownerAgentId ?: mainAgentId(sessionId)
        val injected = manager.injectReceiveAgentMessage(
            sessionId = sessionId,
            targetAgentId = agentId,
            fromAgentId = fromAgentId,
            message = message,
        )
        if (!injected) {
            return AgentOperationResult(
                success = false,
                agentId = agentId,
                message = "Target agent is already dead.",
            )
        }

        return AgentOperationResult(
            success = true,
            agentId = agentId,
            message = "Injected receiveAgentMessage to $agentId",
        )
    }

    @Tool
    @LLMDescription("Return result from subagent and mark it completed")
    public suspend fun returnAgentResult(
        @LLMDescription("Result text")
        result: String,
    ): String {
        val manager = sessionManager
        val sessionId = ownerSessionId
        val agentId = ownerAgentId
        if (manager != null && !sessionId.isNullOrBlank() && !agentId.isNullOrBlank()) {
            manager.completeSubAgentResult(sessionId, agentId, result)
        }
        return result
    }

    private fun lookupAgentTask(agentId: String): AgentTaskLookup? {
        val taskId = GLOBAL_AGENT_TASK_IDS[agentId] ?: return null
        val taskInfo = GLOBAL_TASKS[taskId]
        return AgentTaskLookup(taskId = taskId, taskInfo = taskInfo)
    }

    private fun resolveAgentTask(agentId: String): ResolvedAgentTask {
        val lookup = lookupAgentTask(agentId)
            ?: return ResolvedAgentTask.Missing(reason = AgentTaskMissingReason.Unknown)
        val taskInfo = lookup.taskInfo
            ?: return ResolvedAgentTask.Missing(reason = AgentTaskMissingReason.Cleaned)
        return ResolvedAgentTask.Found(taskId = lookup.taskId, taskInfo = taskInfo)
    }

    private fun missingAgentPollResult(agentId: String, reason: AgentTaskMissingReason): AgentPollResult {
        val message = when (reason) {
            AgentTaskMissingReason.Unknown -> "Unknown agentId: $agentId"
            AgentTaskMissingReason.Cleaned -> "Agent already cleaned: $agentId"
        }
        return createAgentPollResult(
            success = false,
            status = "missing",
            agentId = agentId,
            result = null,
            message = message,
        )
    }

    private fun fromSessionPollResult(
        agentId: String,
        pollStatus: SessionManager.SubAgentPollStatus,
        result: String?,
        error: String?,
        timeoutAsPending: Boolean,
    ): AgentPollResult {
        return when (pollStatus) {
            SessionManager.SubAgentPollStatus.Pending -> {
                if (!timeoutAsPending) {
                    agentPendingPollResult(
                        agentId = agentId,
                        success = true,
                        message = "pending",
                    )
                } else if (error == "timeout") {
                    createAgentPollResult(
                        success = false,
                        status = "timeout",
                        agentId = agentId,
                        result = null,
                        message = error,
                    )
                } else {
                    agentPendingPollResult(
                        agentId = agentId,
                        success = false,
                        message = error ?: "pending",
                    )
                }
            }

            SessionManager.SubAgentPollStatus.Completed -> createAgentPollResult(
                success = true,
                status = "completed",
                agentId = agentId,
                result = result,
                message = result ?: "completed",
            )

            SessionManager.SubAgentPollStatus.Failed -> createAgentPollResult(
                success = false,
                status = "failed",
                agentId = agentId,
                result = null,
                message = error ?: "failed",
            )

            SessionManager.SubAgentPollStatus.Missing -> createAgentPollResult(
                success = false,
                status = "missing",
                agentId = agentId,
                result = null,
                message = "Target agent is already dead.",
            )
        }
    }

    private fun agentPendingPollResult(agentId: String, success: Boolean, message: String): AgentPollResult {
        return createAgentPollResult(
            success = success,
            status = "pending",
            agentId = agentId,
            result = null,
            message = message,
        )
    }

    private fun createAgentPollResult(
        success: Boolean,
        status: String,
        agentId: String,
        result: String?,
        message: String,
    ): AgentPollResult {
        return AgentPollResult(
            success = success,
            status = status,
            agentId = agentId,
            result = result,
            message = message,
        )
    }

    private suspend fun awaitTask(
        @LLMDescription("The ID of the task to wait for")
        taskId: Long,
        @LLMDescription("Timeout in seconds (default 300 = 5 minutes)")
        timeout: Int = 300,
    ): TaskAwaitResult {
        val taskInfo = GLOBAL_TASKS[taskId]
            ?: return TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.NOT_FOUND,
                result = null,
                message = "Task #$taskId not found",
            )

        logger("⏳ Waiting for task #$taskId (${taskInfo.name})...")

        return try {
            val result = withTimeout(timeout * MILLIS_PER_SECOND) {
                taskInfo.deferred.await()
            }
            logger("✅ Task #$taskId completed")

            TaskAwaitResult(
                success = result.success,
                taskId = taskId,
                status = TaskStatus.COMPLETED,
                result = result.output,
                message = result.output ?: result.error ?: "Task completed",
            )
        } catch (_: TimeoutCancellationException) {
            logger("⏱️ Task #$taskId timed out after ${timeout}s")
            TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.TIMEOUT,
                result = null,
                message = "Task timed out after $timeout seconds",
            )
        } catch (e: Exception) {
            logger("❌ Task #$taskId failed: ${e.message}")
            TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.FAILED,
                result = null,
                message = "Task failed: ${e.message}",
            )
        }
    }

    private suspend fun createAndAwaitTasks(
        @LLMDescription("List of task descriptions, one per task")
        tasks: List<String>,
        @LLMDescription("Timeout in seconds for all tasks (default 600 = 10 minutes)")
        timeout: Int = 600,
    ): BatchTaskResult {
        if (tasks.isEmpty()) {
            return BatchTaskResult(
                success = false,
                results = emptyList(),
                message = "No tasks provided",
            )
        }

        logger("🚀 Creating ${tasks.size} parallel tasks")
        messageHandler.addMessageToUser("🚀 Starting ${tasks.size} parallel sub-agents")

        val taskIds = tasks.mapIndexed { index, task ->
            createTask(task, "Parallel-${index + 1}").taskId
        }

        return try {
            val results = withTimeout(timeout * MILLIS_PER_SECOND) {
                taskIds.map { taskId ->
                    async { awaitTask(taskId, timeout) }
                }.awaitAll()
            }

            val successCount = results.count { it.success }
            val message = "Completed ${results.size} tasks: $successCount successful, ${results.size - successCount} failed"
            logger("✅ $message")
            messageHandler.addMessageToUser("✅ $message")

            BatchTaskResult(
                success = successCount == results.size,
                results = results,
                message = message,
            )
        } catch (_: TimeoutCancellationException) {
            logger("⏱️ Batch tasks timed out after ${timeout}s")
            BatchTaskResult(
                success = false,
                results = emptyList(),
                message = "Batch tasks timed out after $timeout seconds",
            )
        }
    }

    private fun getTaskStatus(
        @LLMDescription("The ID of the task to check")
        taskId: Long,
    ): TaskStatusResult {
        val taskInfo = GLOBAL_TASKS[taskId]
            ?: return TaskStatusResult(
                taskId = taskId,
                status = TaskStatus.NOT_FOUND,
                name = null,
                description = null,
                message = "Task #$taskId not found",
            )

        val status = taskInfo.runtimeStatus()
        return TaskStatusResult(
            taskId = taskId,
            status = status,
            name = taskInfo.name,
            description = taskInfo.description,
            message = "Task #$taskId (${taskInfo.name}) is ${status.name.lowercase()}",
        )
    }

    private fun cancelTask(
        @LLMDescription("The ID of the task to cancel")
        taskId: Long,
    ): TaskOperationResult {
        val taskInfo = GLOBAL_TASKS[taskId]
            ?: return TaskOperationResult(
                success = false,
                taskId = taskId,
                message = "Task #$taskId not found",
            )

        taskInfo.scope.cancel("Cancelled by user")
        GLOBAL_TASKS.remove(taskId)

        taskInfo.agentId?.let { agentId ->
            GLOBAL_AGENT_TASK_IDS.remove(agentId)
            GLOBAL_AGENT_SESSION_IDS.remove(agentId)
        }

        logger("🛑 Cancelled task #$taskId (${taskInfo.name})")
        messageHandler.addMessageToUser("🛑 Cancelled task: ${taskInfo.name}")

        return TaskOperationResult(
            success = true,
            taskId = taskId,
            message = "Cancelled task #$taskId: ${taskInfo.name}",
        )
    }

    private fun listTasks(): TaskListResult {
        val taskList = GLOBAL_TASKS.values.map { task ->
            TaskStatusResult(
                taskId = task.id,
                status = task.runtimeStatus(),
                name = task.name,
                description = task.description,
                message = "Task #${task.id}: ${task.name}",
            )
        }.sortedBy { it.taskId }

        return TaskListResult(
            success = true,
            tasks = taskList,
            message = "Found ${taskList.size} tasks",
        )
    }

    private fun mainAgentId(sessionId: String): String {
        return "main-$sessionId"
    }

    private fun TaskInfo.runtimeStatus(): TaskStatus {
        return when {
            deferred.isCompleted -> TaskStatus.COMPLETED
            deferred.isActive -> TaskStatus.RUNNING
            else -> TaskStatus.FAILED
        }
    }

    private fun TaskStatus.toAgentStatus(): String {
        return when (this) {
            TaskStatus.TIMEOUT -> "timeout"
            TaskStatus.FAILED -> "failed"
            TaskStatus.COMPLETED -> "completed"
            else -> "pending"
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND: Long = 1000L
        val GLOBAL_TASKS: ConcurrentHashMap<Long, TaskInfo> = ConcurrentHashMap()
        val GLOBAL_AGENT_TASK_IDS: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
        val GLOBAL_AGENT_SESSION_IDS: ConcurrentHashMap<String, String> = ConcurrentHashMap()
        val GLOBAL_ID_GENERATOR: AtomicLong = AtomicLong(1)
    }
}

public interface AgentFactory {
    public fun createAgent(): SimpleAgent

    public suspend fun runSubAgent(
        sessionId: String,
        agentId: String,
        parentAgentId: String,
        mode: String,
        taskDescription: String,
        expectedResult: String,
    ): String
}

public interface SimpleAgent {
    public suspend fun run(task: String): String
}

private data class TaskInfo(
    val id: Long,
    val name: String,
    val description: String,
    val deferred: Deferred<TaskResult>,
    val scope: CoroutineScope,
    val agentId: String?,
    val sessionId: String?,
)

private data class TaskResult(
    val success: Boolean,
    val output: String?,
    val error: String?,
) {
    companion object {
        fun success(output: String): TaskResult = TaskResult(success = true, output = output, error = null)

        fun failure(error: String): TaskResult = TaskResult(success = false, output = null, error = error)
    }
}

private data class AgentTaskLookup(
    val taskId: Long,
    val taskInfo: TaskInfo?,
)

private sealed interface ResolvedAgentTask {
    data class Found(val taskId: Long, val taskInfo: TaskInfo) : ResolvedAgentTask
    data class Missing(val reason: AgentTaskMissingReason) : ResolvedAgentTask
}

private enum class AgentTaskMissingReason {
    Unknown,
    Cleaned,
}

@Serializable
public enum class TaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    NOT_FOUND,
}

@Serializable
public data class TaskCreationResult(
    val success: Boolean,
    val taskId: Long,
    val name: String,
    val message: String,
)

@Serializable
public data class TaskAwaitResult(
    val success: Boolean,
    val taskId: Long,
    val status: TaskStatus,
    val result: String?,
    val message: String,
)

@Serializable
public data class TaskStatusResult(
    val taskId: Long,
    val status: TaskStatus,
    val name: String?,
    val description: String?,
    val message: String,
)

@Serializable
public data class TaskOperationResult(
    val success: Boolean,
    val taskId: Long,
    val message: String,
)

@Serializable
public data class TaskListResult(
    val success: Boolean,
    val tasks: List<TaskStatusResult>,
    val message: String,
)

@Serializable
public data class BatchTaskResult(
    val success: Boolean,
    val results: List<TaskAwaitResult>,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine(message)
        if (results.isNotEmpty()) {
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("Task ${index + 1} (#${result.taskId}): ${result.status}")
                result.result?.let { value ->
                    appendLine("  Result: ${value.take(200)}${if (value.length > 200) "..." else ""}")
                }
            }
        }
    }
}

@Serializable
public data class AgentCreationResult(
    val success: Boolean,
    val agentId: String,
    val mode: String,
    val message: String,
)

@Serializable
public data class AgentPollResult(
    val success: Boolean,
    val status: String,
    val agentId: String,
    val result: String?,
    val message: String,
)

@Serializable
public data class AgentOperationResult(
    val success: Boolean,
    val agentId: String,
    val message: String,
)

@Serializable
public data class ActiveAgentsResult(
    val success: Boolean,
    val agents: List<String>,
    val message: String,
)
