package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Todo management tool for tracking tasks.
 * Based on kimi-cli's todo tool implementation.
 */
@Suppress("unused")
@LLMDescription("Manage a todo list for tracking tasks and their progress")
public class TodoTool public constructor(
    private val messageHandler: MessageHandler,
    private val logger: (String) -> Unit = { println(it) },
) : ToolSet {

    private val todos = ConcurrentHashMap<Long, TodoItem>()
    private val idGenerator = AtomicLong(1)

    init {
        logger("📝 Todo tool initialized")
    }

    @Tool
    @LLMDescription(
        "Add a new todo item to the list. " +
        "Use this to track tasks that need to be completed."
    )
    public fun addTodo(
        @LLMDescription("The title/description of the todo item")
        title: String,
        @LLMDescription("Optional parent todo ID for subtasks")
        parentId: Long? = null,
    ): TodoOperationResult {
        val id = idGenerator.getAndIncrement()
        val todo = TodoItem(id = id, title = title, status = TodoStatus.PENDING, parentId = parentId)
        todos[id] = todo

        val parentInfo = parentId?.let { " (parent: #$it)" } ?: ""
        logger("✅ Added todo #$id: $title$parentInfo")
        messageHandler.addMessageToUser("📝 Added todo #$id: $title")

        return TodoOperationResult(
            success = true,
            message = "Added todo #$id: $title",
            todo = todo,
        )
    }

    @Tool
    @LLMDescription(
        "Update the status of a todo item. " +
        "Use this to mark tasks as in progress or completed."
    )
    public fun updateTodoStatus(
        @LLMDescription("The ID of the todo item to update")
        id: Long,
        @LLMDescription("The new status: 'pending', 'in_progress', or 'done'")
        status: String,
    ): TodoOperationResult {
        val todoStatus = parseTodoStatus(status) ?: return TodoOperationResult(
            success = false,
            message = "Invalid status: $status. Use 'pending', 'in_progress', or 'done'",
            todo = null,
        )

        val todo = todos[id] ?: return todoNotFoundResult(id)

        val updatedTodo = todo.copy(status = todoStatus)
        todos[id] = updatedTodo

        val statusLabel = todoStatus.name.lowercase()
        val statusEmoji = todoStatus.emoji()

        logger("$statusEmoji Updated todo #$id to $statusLabel: ${todo.title}")
        messageHandler.addMessageToUser("$statusEmoji Updated todo #$id: ${todo.title}")

        return TodoOperationResult(
            success = true,
            message = "Updated todo #$id to $statusLabel",
            todo = updatedTodo,
        )
    }

    @Tool
    @LLMDescription("Remove a todo item from the list")
    public fun removeTodo(
        @LLMDescription("The ID of the todo item to remove")
        id: Long,
    ): TodoOperationResult {
        val todo = todos.remove(id) ?: return todoNotFoundResult(id)

        logger("🗑️ Removed todo #$id: ${todo.title}")
        messageHandler.addMessageToUser("🗑️ Removed todo #$id: ${todo.title}")

        return TodoOperationResult(
            success = true,
            message = "Removed todo #$id: ${todo.title}",
            todo = null,
        )
    }

    @Tool
    @LLMDescription(
        "Get the current todo list with all items and their statuses. " +
        "Use this to check progress on tasks."
    )
    public fun getTodoList(): TodoListResult {
        val allTodos = todos.values.sortedBy { it.id }

        if (allTodos.isEmpty()) {
            return TodoListResult(
                success = true,
                message = "No todos yet. Use addTodo to create tasks.",
                todos = emptyList(),
                summary = EMPTY_SUMMARY,
            )
        }

        val summary = buildSummary(allTodos)

        return TodoListResult(
            success = true,
            message = "Found ${allTodos.size} todos",
            todos = allTodos,
            summary = summary,
        )
    }

    @Tool
    @LLMDescription(
        "Mark all pending todos as done. " +
        "Use this when all tasks have been completed."
    )
    public fun completeAllTodos(): TodoListResult {
        todos.values.filter { it.status != TodoStatus.DONE }.forEach { todo ->
            todos[todo.id] = todo.copy(status = TodoStatus.DONE)
        }

        logger("✅ Marked all todos as done")
        messageHandler.addMessageToUser("✅ All todos marked as done")

        return getTodoList()
    }

    @Tool
    @LLMDescription(
        "Clear all completed todos from the list. " +
        "Use this to clean up after tasks are finished."
    )
    public fun clearCompletedTodos(): TodoListResult {
        val completed = todos.values.filter { it.status == TodoStatus.DONE }
        completed.forEach { todos.remove(it.id) }

        logger("🧹 Cleared ${completed.size} completed todos")
        messageHandler.addMessageToUser("🧹 Cleared ${completed.size} completed todos")

        return getTodoList()
    }

    private fun parseTodoStatus(status: String): TodoStatus? {
        return runCatching { TodoStatus.valueOf(status.trim().uppercase()) }.getOrNull()
    }

    private fun todoNotFoundResult(id: Long): TodoOperationResult {
        return TodoOperationResult(
            success = false,
            message = "Todo #$id not found",
            todo = null,
        )
    }

    private fun buildSummary(todos: List<TodoItem>): TodoSummary {
        return TodoSummary(
            total = todos.size,
            pending = todos.count { it.status == TodoStatus.PENDING },
            inProgress = todos.count { it.status == TodoStatus.IN_PROGRESS },
            done = todos.count { it.status == TodoStatus.DONE },
        )
    }

    private companion object {
        val EMPTY_SUMMARY: TodoSummary = TodoSummary(total = 0, pending = 0, inProgress = 0, done = 0)
    }
}

/**
 * Status of a todo item
 */
@Serializable
public enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
}

/**
 * A todo item
 */
@Serializable
public data class TodoItem(
    val id: Long,
    val title: String,
    val status: TodoStatus,
    val parentId: Long? = null,
)

/**
 * Result of a todo operation
 */
@Serializable
public data class TodoOperationResult(
    val success: Boolean,
    val message: String,
    val todo: TodoItem?,
)

/**
 * Summary of todos by status
 */
@Serializable
public data class TodoSummary(
    val total: Int,
    val pending: Int,
    val inProgress: Int = 0,
    val done: Int,
)

/**
 * Result of getting the todo list
 */
@Serializable
public data class TodoListResult(
    val success: Boolean,
    val message: String,
    val todos: List<TodoItem>,
    val summary: TodoSummary
) {
    override fun toString(): String = buildString {
        appendLine(message)
        appendLine("Summary: ${summary.done}/${summary.total} done, ${summary.inProgress} in progress, ${summary.pending} pending")
        if (todos.isNotEmpty()) {
            appendLine()
            appendLine("Todos:")
            todos.forEach { todo ->
                val statusEmoji = todo.status.emoji()
                val indent = if (todo.parentId != null) "  " else ""
                appendLine("$indent$statusEmoji #${todo.id}: ${todo.title}")
            }
        }
    }
}

private fun TodoStatus.emoji(): String {
    return when (this) {
        TodoStatus.PENDING -> "⏳"
        TodoStatus.IN_PROGRESS -> "🔧"
        TodoStatus.DONE -> "✅"
    }
}
