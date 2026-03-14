package io.github.stream29.kode.agent.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable
public enum class AgentState {
    Running,
    Suspended,
}

@Serializable
public data class AgentConfig(
    val systemPrompt: String?,
    val taskDescription: String?,
    val expectedResult: String?,
    val canInteractWithUser: Boolean,
)

public data class Agent(
    val state: MutableStateFlow<AgentState>,
    val config: MutableStateFlow<AgentConfig>,
    val messages: MutableStateFlow<PersistentList<SessionMessage>>,
    val todoState: MutableStateFlow<List<TodoItem>> = MutableStateFlow(emptyList()),
) {
    public fun todoMetadataFlow(): MutableStateFlow<List<TodoItem>> {
        return todoState
    }

    public fun readTodoFromMetadata(): List<TodoItem> {
        return todoState.value.toList()
    }

    public fun writeTodoToMetadata(todos: List<TodoItem>): Boolean {
        val normalizedTodos = todos.toList()
        if (todoState.value == normalizedTodos) {
            return false
        }
        todoState.value = normalizedTodos
        return true
    }
}

public data class SubAgent(
    val delegate: Agent,
    val result: CompletableDeferred<String>,
)
