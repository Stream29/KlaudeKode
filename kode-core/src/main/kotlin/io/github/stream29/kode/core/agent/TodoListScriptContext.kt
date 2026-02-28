package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.todo.TodoManager
import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public interface TodoListScriptContext : ScriptContext {
    public fun getTodoStateFlow(): StateFlow<List<TodoNode>>
    public fun getTodoList(): List<TodoNode>
    public fun updateTodoList(todos: List<TodoNode>)
}

public class TodoListScriptContextImpl(
    initialTodos: List<TodoNode> = emptyList(),
) : TodoListScriptContext {
    private var todoManager: TodoManager = TodoManager(initialNodes = initialTodos)
    private val todoStateFlow: MutableStateFlow<List<TodoNode>> = MutableStateFlow(todoManager.listAllNodes())

    override val systemPromptInjection: String = """
        ### Todo List API
        - **Policy**: Use todo list for any complex task that can be further decomposed. Small, atomic tasks do not require todo entries.
        - `getTodoList(): List<TodoNode>`: Get current state of the todo tree.
        - `updateTodoList(todos: List<TodoNode>)`: Replace the entire todo list state.
    """.trimIndent()

    override fun getTodoStateFlow(): StateFlow<List<TodoNode>> {
        return todoStateFlow.asStateFlow()
    }

    override fun getTodoList(): List<TodoNode> {
        return todoManager.listAllNodes()
    }

    override fun updateTodoList(todos: List<TodoNode>) {
        todoManager.updateNodes(todos)
        syncTodoStateFlow()
    }

    private fun syncTodoStateFlow() {
        todoStateFlow.value = todoManager.listAllNodes()
    }
}
