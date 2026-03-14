package io.github.stream29.kode.session.core.model

import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.agent.model.Agent
import io.github.stream29.kode.agent.model.AgentConfig
import io.github.stream29.kode.agent.model.AgentState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentTodoMetadataContractTest {
    @Test
    fun todoMetadataFlowReflectsAgentMetadataUpdates() {
        val agent = testAgent()

        val changed = agent.writeTodoToMetadata(
            listOf(TodoItem(name = "flow", completed = true)),
        )

        assertTrue(changed)
        assertEquals("flow", agent.todoMetadataFlow().value.single().name)
        assertTrue(agent.todoMetadataFlow().value.single().completed)
    }

    @Test
    fun readTodoFromMetadataReturnsSnapshot() {
        val agent = testAgent(
            todos = listOf(TodoItem(name = "root", completed = false)),
        )

        val fromMetadata = agent.readTodoFromMetadata()

        assertEquals(1, fromMetadata.size)
        assertEquals("root", fromMetadata.single().name)
    }

    @Test
    fun writeTodoToMetadataReturnsTrueOnlyWhenChanged() {
        val initial = listOf(TodoItem(name = "same", completed = false))
        val agent = testAgent(todos = initial)

        val unchanged = agent.writeTodoToMetadata(initial)
        val changed = agent.writeTodoToMetadata(
            listOf(TodoItem(name = "changed", completed = true)),
        )

        assertFalse(unchanged)
        assertTrue(changed)
        assertEquals("changed", agent.readTodoFromMetadata().single().name)
        assertTrue(agent.readTodoFromMetadata().single().completed)
    }

    @Test
    fun writeTodoToMetadataDefensivelyCopiesInputList() {
        val mutableSource = mutableListOf(TodoItem(name = "copied", completed = false))
        val agent = testAgent()

        val written = agent.writeTodoToMetadata(mutableSource)
        mutableSource.clear()

        assertTrue(written)
        assertEquals(1, agent.readTodoFromMetadata().size)
        assertEquals("copied", agent.readTodoFromMetadata().single().name)
    }

    private fun testAgent(todos: List<TodoItem> = emptyList()): Agent {
        return Agent(
            state = MutableStateFlow(AgentState.Suspended),
            config = MutableStateFlow(
                AgentConfig(
                    systemPrompt = null,
                    taskDescription = null,
                    expectedResult = null,
                    canInteractWithUser = true,
                ),
            ),
            messages = MutableStateFlow(persistentListOf()),
            todoState = MutableStateFlow(todos),
        )
    }
}
