package io.github.stream29.kode.tools.scripting

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.todo.TodoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


public interface ScriptContext {
    public val systemPromptInjection: String

    public fun sayToUser(message: String)

    public fun consumeOutputList(): List<String>

    public fun suspendForUserInput()

    public fun consumeAwaitForUserInputSignal(): Boolean
    public fun getTodoList(): List<TodoNode>
    public fun updateTodoList(todos: List<TodoNode>)
}

public class DefaultScriptContext(
    initialTodos: List<TodoNode> = emptyList(),
) : ScriptContext {
    @OptIn(ExperimentalAtomicApi::class)
    private val awaitForUserInput: AtomicBoolean = AtomicBoolean(false)
    private val outputLock: Any = Any()
    private val outputList: MutableList<String> = mutableListOf()
    private var todoManager: TodoManager = TodoManager(initialNodes = initialTodos)
    private val todoStateFlow: MutableStateFlow<List<TodoNode>> = MutableStateFlow(todoManager.listAllNodes())

    override val systemPromptInjection: String = DEFAULT_SYSTEM_PROMPT_INJECTION

    override fun sayToUser(message: String) {
        synchronized(outputLock) {
            outputList.add(message)
        }
    }

    override fun consumeOutputList(): List<String> {
        synchronized(outputLock) {
            val snapshot = outputList.toList()
            outputList.clear()
            return snapshot
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun suspendForUserInput() {
        awaitForUserInput.compareAndSet(expectedValue = false, newValue = true)
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun consumeAwaitForUserInputSignal(): Boolean {
        return awaitForUserInput.exchange(newValue = false)
    }
    public fun getTodoStateFlow(): StateFlow<List<TodoNode>> {
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


    public companion object {
        public val DEFAULT_SYSTEM_PROMPT_INJECTION: String = """
            ## Script receiver API (implicit receiver = DefaultScriptContext):

            You can call methods on `DefaultScriptContext` in your script without `this` reference.
            Getting the receiver instance by referencing `this` is also acceptable.

            ### `sayToUser(text: String)`
            - Append one user-visible output entry.
            - Each call corresponds to one UI message entry.
            - May be written in markdown with mermaid.

            ### `suspendForUserInput()`
            - You must call `suspendForUserInput()` to finish your output. Otherwise, you will be forced to continue.
            - Runtime behavior: the run enters pending-input and resumes after the user provides input.
            - Do not call consumeAwaitForUserInputSignal(); it is runtime-internal.
            - You can do other work in script and call this method at the end of the script.
            ### Todo List API
            - **Policy**: Use todo list for any complex task that can be further decomposed. Small, atomic tasks do not require todo entries.
            - `getTodoList(): List<TodoNode>`: Get current state of the todo tree.
            - `updateTodoList(todos: List<TodoNode>)`: Replace the entire todo list state.
        """.trimIndent()
    }
}
