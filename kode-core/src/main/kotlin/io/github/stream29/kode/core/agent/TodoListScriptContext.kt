package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public interface TodoListScriptContext : ScriptContext {
    public val todoStateFlow: StateFlow<List<TodoNode>>

    public fun list(): List<TodoNode>
    public fun clear()
    public fun reset(todos: List<TodoNode>)
    public fun edit(vararg path: String, update: (TodoNode) -> TodoNode)

    public fun listTodoItems(): List<TodoNode> = list()
    public fun clearTodoItems() {
        clear()
    }

    public fun resetTodoItems(items: List<TodoNode>) {
        reset(items)
    }

    public fun editTodoItem(vararg path: String, update: (TodoNode) -> TodoNode) {
        edit(*path, update = update)
    }

    public fun getTodoList(): List<TodoNode> = list()

    public fun updateTodoList(todos: List<TodoNode>) {
        reset(todos)
    }

    public fun updateTodoNode(vararg path: String, update: (TodoNode) -> TodoNode) {
        edit(*path, update = update)
    }
}

public class TodoListScriptContextImpl(
    initialTodos: List<TodoNode> = emptyList(),
    activeFlow: MutableStateFlow<List<TodoNode>>? = null,
) : TodoListScriptContext {
    private val _todoStateFlow: MutableStateFlow<List<TodoNode>> = activeFlow ?: MutableStateFlow(initialTodos)
    override val todoStateFlow: StateFlow<List<TodoNode>> = _todoStateFlow.asStateFlow()

    override val defaultImports: List<String> = listOf(
        TodoNode::class.qualifiedName!!,
        TODO_ITEM_QUALIFIED_NAME,
    )

    override val systemPromptInjection: String = """
        ### Todo List API
        - **Policy**: Use todo list for any complex task that can be further decomposed. Small, atomic tasks do not require todo entries.
        - **Data structure**: `TodoNode` (alias `TodoItem`) is predefined and auto-imported.
        - `list(): List<TodoNode>`: Get current todo tree.
        - `clear()`: Remove all todo items.
        - `reset(todos: List<TodoNode>)`: Replace entire todo tree.
        - `edit(vararg path: String, update: (TodoNode) -> TodoNode)`: Update one node by name path. For example: `edit("Task", "Subtask") { it.copy(isCompleted = true) }`.
        - Compatibility aliases are available: `listTodoItems/clearTodoItems/resetTodoItems/editTodoItem`, `getTodoList/updateTodoList/updateTodoNode`.
    """.trimIndent()

    override fun list(): List<TodoNode> {
        return _todoStateFlow.value.toList()
    }

    override fun clear() {
        reset(emptyList())
    }

    override fun reset(todos: List<TodoNode>) {
        _todoStateFlow.value = todos
    }

    override fun edit(vararg path: String, update: (TodoNode) -> TodoNode) {
        if (path.isEmpty()) throw IllegalArgumentException("Path cannot be empty")
        if (path.any { name -> name.isBlank() }) throw IllegalArgumentException("Path cannot contain blank names")

        fun updateNodeRecursively(
            nodes: List<TodoNode>,
            currentPath: List<String>
        ): List<TodoNode> {
            val targetName = currentPath.first()
            val isLast = currentPath.size == 1

            var found = false
            val newNodes = nodes.map { node ->
                if (node.name == targetName) {
                    found = true
                    if (isLast) {
                        update(node)
                    } else {
                        node.copy(subtasks = updateNodeRecursively(node.subtasks, currentPath.drop(1)))
                    }
                } else {
                    node
                }
            }
            if (!found) {
                throw IllegalArgumentException("Todo node not found: $targetName")
            }
            return newNodes
        }

        val newTodos = updateNodeRecursively(_todoStateFlow.value, path.toList())
        reset(newTodos)
    }

    private companion object {
        private const val TODO_ITEM_QUALIFIED_NAME: String =
            "io.github.stream29.kode.session.core.model.TodoItem"
    }
}
