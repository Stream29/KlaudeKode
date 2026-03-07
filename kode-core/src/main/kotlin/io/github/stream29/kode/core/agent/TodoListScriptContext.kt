package io.github.stream29.kode.core.agent

import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public interface TodoListScriptContext : ScriptContext {
    public val todoStateFlow: StateFlow<List<TodoItem>>

    public fun listTodoItems(): List<TodoItem>
    public fun clearTodoItems()
    public fun resetTodoItems(items: List<TodoItem>)
    public fun editTodoItem(vararg path: String, update: (TodoItem) -> TodoItem)

    public fun list(): List<TodoItem>
        = listTodoItems()

    public fun clear() {
        clearTodoItems()
    }

    public fun reset(todos: List<TodoItem>) {
        resetTodoItems(todos)
    }

    public fun edit(vararg path: String, update: (TodoItem) -> TodoItem) {
        editTodoItem(*path) { node ->
            update(node)
        }
    }
}

public class TodoListScriptContextImpl(
    initialTodos: List<TodoItem> = emptyList(),
    activeFlow: MutableStateFlow<List<TodoItem>>? = null,
) : TodoListScriptContext {
    private val _todoStateFlow: MutableStateFlow<List<TodoItem>> = activeFlow ?: MutableStateFlow(initialTodos)
    override val todoStateFlow: StateFlow<List<TodoItem>> = _todoStateFlow.asStateFlow()

    override val defaultImports: List<String> = listOf(
        TodoItem::class.qualifiedName!!,
    )

    override val systemPromptInjection: String = """
        ### Todo List API
        - **Policy**: Use todo list for any complex task that can be further decomposed. Small, atomic tasks do not require todo entries.
        - **Data structure**: `TodoItem(name: String, completed: Boolean = false, subItems: List<TodoItem> = emptyList())` is predefined and auto-imported.
        - `listTodoItems(): List<TodoItem>`: Get current todo tree.
        - `clearTodoItems()`: Remove all todo items.
        - `resetTodoItems(items: List<TodoItem>)`: Replace entire todo tree.
        - `editTodoItem(vararg path: String, update: (TodoItem) -> TodoItem)`: Update one node by name path. For example: `editTodoItem("Task", "Subtask") { it.copy(completed = true) }`.
        - Convenience aliases are available: `list/clear/reset/edit`.
        - Todo JSON fields use canonical names: `completed` and `subItems`.
    """.trimIndent()

    override fun listTodoItems(): List<TodoItem> {
        return _todoStateFlow.value.toList()
    }

    override fun clearTodoItems() {
        resetTodoItems(emptyList())
    }

    override fun resetTodoItems(items: List<TodoItem>) {
        _todoStateFlow.value = items
    }

    override fun editTodoItem(vararg path: String, update: (TodoItem) -> TodoItem) {
        if (path.isEmpty()) throw IllegalArgumentException("Path cannot be empty")
        if (path.any { name -> name.isBlank() }) throw IllegalArgumentException("Path cannot contain blank names")

        fun updateNodeRecursively(
            nodes: List<TodoItem>,
            currentPath: List<String>
        ): List<TodoItem> {
            val targetName = currentPath.first()
            val isLast = currentPath.size == 1

            var found = false
            val newNodes = nodes.map { node ->
                if (node.name == targetName) {
                    found = true
                    if (isLast) {
                        update(node)
                    } else {
                        node.copy(subItems = updateNodeRecursively(node.subItems, currentPath.drop(1)))
                    }
                } else {
                    node
                }
            }
            if (!found) {
                throw IllegalArgumentException("Todo item not found: $targetName")
            }
            return newNodes
        }

        val newTodos = updateNodeRecursively(_todoStateFlow.value, path.toList())
        resetTodoItems(newTodos)
    }
}
