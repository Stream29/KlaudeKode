package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TodoListScriptContextTest {

    @Test
    fun `updateTodoNode should update root level node`() {
        val initialTodos = listOf(
            TodoNode("Task 1", false),
            TodoNode("Task 2", false)
        )
        val context = TodoListScriptContextImpl(initialTodos)

        context.updateTodoNode("Task 1") { it.copy(isCompleted = true) }

        val todos = context.getTodoList()
        assertEquals(2, todos.size)
        assertTrue(todos[0].isCompleted)
        assertFalse(todos[1].isCompleted)
    }

    @Test
    fun `updateTodoNode should update nested node`() {
        val initialTodos = listOf(
            TodoNode(
                name = "Task 1",
                isCompleted = false,
                subtasks = listOf(
                    TodoNode("Subtask 1.1", false),
                    TodoNode("Subtask 1.2", false)
                )
            )
        )
        val context = TodoListScriptContextImpl(initialTodos)

        context.updateTodoNode("Task 1", "Subtask 1.2") { it.copy(isCompleted = true) }

        val todos = context.getTodoList()
        assertEquals(1, todos.size)
        assertFalse(todos[0].isCompleted)
        val subtasks = todos[0].subtasks
        assertEquals(2, subtasks.size)
        assertFalse(subtasks[0].isCompleted)
        assertTrue(subtasks[1].isCompleted)
    }

    @Test
    fun `updateTodoNode should throw IllegalArgumentException for non-existent node`() {
        val initialTodos = listOf(TodoNode("Task 1", false))
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.updateTodoNode("Task 2") { it.copy(isCompleted = true) }
        }
        assertEquals("Todo node not found: Task 2", exception.message)
    }

    @Test
    fun `updateTodoNode should throw IllegalArgumentException for non-existent nested node`() {
        val initialTodos = listOf(
            TodoNode(
                name = "Task 1",
                isCompleted = false,
                subtasks = listOf(TodoNode("Subtask 1.1", false))
            )
        )
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.updateTodoNode("Task 1", "Subtask 1.2") { it.copy(isCompleted = true) }
        }
        assertEquals("Todo node not found: Subtask 1.2", exception.message)
    }

    @Test
    fun `updateTodoNode should throw IllegalArgumentException for empty path`() {
        val context = TodoListScriptContextImpl()

        val exception = assertThrows<IllegalArgumentException> {
            context.updateTodoNode { it.copy(isCompleted = true) }
        }
        assertEquals("Path cannot be empty", exception.message)
    }
}
