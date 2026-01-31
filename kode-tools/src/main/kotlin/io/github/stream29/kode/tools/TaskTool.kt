package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Task tool for spawning sub-agents.
 * Based on kimi-cli's Task tool for parallel sub-agent execution.
 */
@Suppress("unused")
@LLMDescription(
    "Create and manage sub-agents (tasks) that can work in parallel. " +
    "Use this to delegate work to specialized sub-agents that can run concurrently."
)
public class TaskTool public constructor(
    private val messageHandler: MessageHandler,
    private val agentFactory: AgentFactory,
    private val logger: (String) -> Unit = { println(it) }
) : ToolSet {

    private val tasks = ConcurrentHashMap<Long, TaskInfo>()
    private val idGenerator = AtomicLong(1)

    @Tool
    @LLMDescription(
        "Create a new sub-agent (task) to perform work in parallel. " +
        "The sub-agent will run independently and can be monitored or awaited. " +
        "Use this to delegate specific tasks to specialized workers."
    )
    public suspend fun createTask(
        @LLMDescription("A clear, specific task description for the sub-agent")
        task: String,
        @LLMDescription("Optional name/identifier for this task (for your reference)")
        taskName: String? = null
    ): TaskCreationResult {
        val taskId = idGenerator.getAndIncrement()
        val name = taskName ?: "Task-$taskId"

        logger("🚀 Creating sub-agent task #$taskId: $name")
        messageHandler.addMessageToUser("🚀 Starting sub-agent: $name")

        // Create a coroutine scope for this task
        val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Create a deferred to hold the result
        val deferred = taskScope.async {
            try {
                // Create a sub-agent using the factory
                val subAgent = agentFactory.createAgent()
                
                // Run the task
                logger("▶️ Task #$taskId running: $task")
                val result = subAgent.run(task)
                
                logger("✅ Task #$taskId completed")
                TaskResult.success(result)
            } catch (e: Exception) {
                logger("❌ Task #$taskId failed: ${e.message}")
                TaskResult.failure(e.message ?: "Unknown error")
            }
        }

        // Store task info
        val taskInfo = TaskInfo(
            id = taskId,
            name = name,
            description = task,
            status = TaskStatus.RUNNING,
            deferred = deferred,
            scope = taskScope
        )
        tasks[taskId] = taskInfo

        return TaskCreationResult(
            success = true,
            taskId = taskId,
            name = name,
            message = "Created task #$taskId: $name"
        )
    }

    @Tool
    @LLMDescription(
        "Wait for a task to complete and return its result. " +
        "This blocks until the sub-agent finishes its work."
    )
    public suspend fun awaitTask(
        @LLMDescription("The ID of the task to wait for")
        taskId: Long,
        @LLMDescription("Timeout in seconds (default 300 = 5 minutes)")
        timeout: Int = 300
    ): TaskAwaitResult {
        val taskInfo = tasks[taskId]
            ?: return TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.NOT_FOUND,
                result = null,
                message = "Task #$taskId not found"
            )

        logger("⏳ Waiting for task #$taskId (${taskInfo.name})...")

        return try {
            val result = withTimeout(timeout * 1000L) {
                taskInfo.deferred.await()
            }

            // Update status
            val updatedInfo = taskInfo.copy(status = TaskStatus.COMPLETED)
            tasks[taskId] = updatedInfo

            logger("✅ Task #$taskId completed")

            TaskAwaitResult(
                success = result.success,
                taskId = taskId,
                status = TaskStatus.COMPLETED,
                result = result.output,
                message = result.output ?: result.error ?: "Task completed"
            )

        } catch (e: TimeoutCancellationException) {
            logger("⏱️ Task #$taskId timed out after ${timeout}s")
            TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.TIMEOUT,
                result = null,
                message = "Task timed out after ${timeout} seconds"
            )
        } catch (e: Exception) {
            logger("❌ Task #$taskId failed: ${e.message}")
            TaskAwaitResult(
                success = false,
                taskId = taskId,
                status = TaskStatus.FAILED,
                result = null,
                message = "Task failed: ${e.message}"
            )
        }
    }

    @Tool
    @LLMDescription(
        "Create multiple tasks and wait for all to complete. " +
        "Use this to parallelize work across multiple sub-agents."
    )
    public suspend fun createAndAwaitTasks(
        @LLMDescription("List of task descriptions, one per task")
        tasks: List<String>,
        @LLMDescription("Timeout in seconds for all tasks (default 600 = 10 minutes)")
        timeout: Int = 600
    ): BatchTaskResult {
        if (tasks.isEmpty()) {
            return BatchTaskResult(
                success = false,
                results = emptyList(),
                message = "No tasks provided"
            )
        }

        logger("🚀 Creating ${tasks.size} parallel tasks")
        messageHandler.addMessageToUser("🚀 Starting ${tasks.size} parallel sub-agents")

        // Create all tasks
        val taskIds = tasks.mapIndexed { index, task ->
            createTask(task, "Parallel-${index + 1}").taskId
        }

        // Wait for all tasks with timeout
        return try {
            val results = withTimeout(timeout * 1000L) {
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
                message = message
            )

        } catch (e: TimeoutCancellationException) {
            logger("⏱️ Batch tasks timed out after ${timeout}s")
            BatchTaskResult(
                success = false,
                results = emptyList(),
                message = "Batch tasks timed out after ${timeout} seconds"
            )
        }
    }

    @Tool
    @LLMDescription(
        "Get the status of a task without waiting for it to complete. " +
        "Use this to check if a sub-agent is still running or has finished."
    )
    public fun getTaskStatus(
        @LLMDescription("The ID of the task to check")
        taskId: Long
    ): TaskStatusResult {
        val taskInfo = tasks[taskId]
            ?: return TaskStatusResult(
                taskId = taskId,
                status = TaskStatus.NOT_FOUND,
                name = null,
                description = null,
                message = "Task #$taskId not found"
            )

        val isActive = taskInfo.deferred.isActive
        val isCompleted = taskInfo.deferred.isCompleted
        val status = when {
            isCompleted -> TaskStatus.COMPLETED
            isActive -> TaskStatus.RUNNING
            else -> TaskStatus.FAILED
        }

        return TaskStatusResult(
            taskId = taskId,
            status = status,
            name = taskInfo.name,
            description = taskInfo.description,
            message = "Task #$taskId (${taskInfo.name}) is ${status.name.lowercase()}"
        )
    }

    @Tool
    @LLMDescription(
        "Cancel a running task. " +
        "Use this to stop a sub-agent that is no longer needed."
    )
    public fun cancelTask(
        @LLMDescription("The ID of the task to cancel")
        taskId: Long
    ): TaskOperationResult {
        val taskInfo = tasks[taskId]
            ?: return TaskOperationResult(
                success = false,
                taskId = taskId,
                message = "Task #$taskId not found"
            )

        taskInfo.scope.cancel("Cancelled by user")
        tasks.remove(taskId)

        logger("🛑 Cancelled task #$taskId (${taskInfo.name})")
        messageHandler.addMessageToUser("🛑 Cancelled task: ${taskInfo.name}")

        return TaskOperationResult(
            success = true,
            taskId = taskId,
            message = "Cancelled task #$taskId: ${taskInfo.name}"
        )
    }

    @Tool
    @LLMDescription("List all tasks and their current status")
    public fun listTasks(): TaskListResult {
        val taskList = tasks.values.map { task ->
            TaskStatusResult(
                taskId = task.id,
                status = when {
                    task.deferred.isCompleted -> TaskStatus.COMPLETED
                    task.deferred.isActive -> TaskStatus.RUNNING
                    else -> TaskStatus.FAILED
                },
                name = task.name,
                description = task.description,
                message = "Task #${task.id}: ${task.name}"
            )
        }.sortedBy { it.taskId }

        return TaskListResult(
            success = true,
            tasks = taskList,
            message = "Found ${taskList.size} tasks"
        )
    }
}

/**
 * Factory interface for creating agents.
 */
public interface AgentFactory {
    public fun createAgent(): SimpleAgent
}

/**
 * Simple agent interface for sub-tasks.
 */
public interface SimpleAgent {
    public suspend fun run(task: String): String
}

/**
 * Internal task information
 */
private data class TaskInfo(
    val id: Long,
    val name: String,
    val description: String,
    val status: TaskStatus,
    val deferred: Deferred<TaskResult>,
    val scope: CoroutineScope
)

/**
 * Task execution result
 */
private data class TaskResult(
    val success: Boolean,
    val output: String?,
    val error: String?
) {
    public companion object {
        public fun success(output: String): TaskResult = TaskResult(true, output, null)
        public fun failure(error: String): TaskResult = TaskResult(false, null, error)
    }
}

/**
 * Task status
 */
@Serializable
public enum class TaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    NOT_FOUND
}

/**
 * Task creation result
 */
@Serializable
public data class TaskCreationResult(
    val success: Boolean,
    val taskId: Long,
    val name: String,
    val message: String
)

/**
 * Task await result
 */
@Serializable
public data class TaskAwaitResult(
    val success: Boolean,
    val taskId: Long,
    val status: TaskStatus,
    val result: String?,
    val message: String
)

/**
 * Task status result
 */
@Serializable
public data class TaskStatusResult(
    val taskId: Long,
    val status: TaskStatus,
    val name: String?,
    val description: String?,
    val message: String
)

/**
 * Task operation result
 */
@Serializable
public data class TaskOperationResult(
    val success: Boolean,
    val taskId: Long,
    val message: String
)

/**
 * Task list result
 */
@Serializable
public data class TaskListResult(
    val success: Boolean,
    val tasks: List<TaskStatusResult>,
    val message: String
)

/**
 * Batch task result
 */
@Serializable
public data class BatchTaskResult(
    val success: Boolean,
    val results: List<TaskAwaitResult>,
    val message: String
) {
    override fun toString(): String = buildString {
        appendLine(message)
        if (results.isNotEmpty()) {
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("Task ${index + 1} (#${result.taskId}): ${result.status}")
                result.result?.let { appendLine("  Result: ${it.take(200)}${if (it.length > 200) "..." else ""}") }
            }
        }
    }
}
