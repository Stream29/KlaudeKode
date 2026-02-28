package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public interface TodoListScriptContext : ScriptContext {
    public val todoStateFlow: StateFlow<List<TodoNode>>
    public fun getTodoList(): List<TodoNode>
    public fun updateTodoList(todos: List<TodoNode>)
    public fun updateTodoNode(vararg path: String, update: (TodoNode) -> TodoNode)
}

public class TodoListScriptContextImpl(
    initialTodos: List<TodoNode> = emptyList(),
    activeFlow: MutableStateFlow<List<TodoNode>>? = null,
) : TodoListScriptContext {
    private val _todoStateFlow: MutableStateFlow<List<TodoNode>> = activeFlow ?: MutableStateFlow(initialTodos)
    override val todoStateFlow: StateFlow<List<TodoNode>> = _todoStateFlow.asStateFlow()

    override val defaultImports: List<String> = listOf(TodoNode::class.qualifiedName!!)

    override val systemPromptInjection: String = """
        ### Todo List API
        - **Policy**: Use todo list for any complex task that can be further decomposed. Small, atomic tasks do not require todo entries.
        - **Data structure**: `data class TodoNode(val name: String, val isCompleted: Boolean, val subtasks: List<TodoNode> = emptyList())` This is predefined. You can use it without import.
        - `getTodoList(): List<TodoNode>`: Get current state of the todo tree.
        - `updateTodoList(todos: List<TodoNode>)`: Replace the entire todo list state.
        - `updateTodoNode(vararg path: String, update: (TodoNode) -> TodoNode)`: Update a specific node by its path (node names). For example: `updateTodoNode("Task", "Subtask") { it.copy(isCompleted = true) }`.
    """.trimIndent()

    override fun getTodoList(): List<TodoNode> {
        return _todoStateFlow.value
    }

    override fun updateTodoList(todos: List<TodoNode>) {
        _todoStateFlow.value = todos
    }

    override fun updateTodoNode(vararg path: String, update: (TodoNode) -> TodoNode) {
        if (path.isEmpty()) throw IllegalArgumentException("Path cannot be empty")

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
        updateTodoList(newTodos)
    }
}
