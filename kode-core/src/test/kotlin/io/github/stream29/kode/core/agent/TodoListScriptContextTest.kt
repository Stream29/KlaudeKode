package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TodoListScriptContextTest {

    @Test
    fun `edit should update root level node`() {
        val initialTodos = listOf(
            TodoNode("Task 1", false),
            TodoNode("Task 2", false)
        )
        val context = TodoListScriptContextImpl(initialTodos)

        context.edit("Task 1") { it.copy(isCompleted = true) }

        val todos = context.list()
        assertEquals(2, todos.size)
        assertTrue(todos[0].isCompleted)
        assertFalse(todos[1].isCompleted)
    }

    @Test
    fun `edit should update nested node`() {
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

        context.edit("Task 1", "Subtask 1.2") { it.copy(isCompleted = true) }

        val todos = context.list()
        assertEquals(1, todos.size)
        assertFalse(todos[0].isCompleted)
        val subtasks = todos[0].subtasks
        assertEquals(2, subtasks.size)
        assertFalse(subtasks[0].isCompleted)
        assertTrue(subtasks[1].isCompleted)
    }

    @Test
    fun `edit should throw IllegalArgumentException for non-existent node`() {
        val initialTodos = listOf(TodoNode("Task 1", false))
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.edit("Task 2") { it.copy(isCompleted = true) }
        }
        assertEquals("Todo node not found: Task 2", exception.message)
    }

    @Test
    fun `edit should throw IllegalArgumentException for non-existent nested node`() {
        val initialTodos = listOf(
            TodoNode(
                name = "Task 1",
                isCompleted = false,
                subtasks = listOf(TodoNode("Subtask 1.1", false))
            )
        )
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.edit("Task 1", "Subtask 1.2") { it.copy(isCompleted = true) }
        }
        assertEquals("Todo node not found: Subtask 1.2", exception.message)
    }

    @Test
    fun `edit should throw IllegalArgumentException for empty path`() {
        val context = TodoListScriptContextImpl()

        val exception = assertThrows<IllegalArgumentException> {
            context.edit { it.copy(isCompleted = true) }
        }
        assertEquals("Path cannot be empty", exception.message)
    }

    @Test
    fun `edit should throw IllegalArgumentException for blank path segment`() {
        val context = TodoListScriptContextImpl()

        val exception = assertThrows<IllegalArgumentException> {
            context.edit(" ") { it.copy(isCompleted = true) }
        }
        assertEquals("Path cannot contain blank names", exception.message)
    }

    @Test
    fun `clear should empty todo list`() {
        val context = TodoListScriptContextImpl(
            initialTodos = listOf(TodoNode(name = "Task", isCompleted = false)),
        )

        context.clear()

        assertTrue(context.list().isEmpty())
    }

    @Test
    fun `legacy aliases should remain compatible`() {
        val context = TodoListScriptContextImpl()

        context.resetTodoItems(
            listOf(TodoNode(name = "Legacy Task", isCompleted = false))
        )
        context.updateTodoNode("Legacy Task") { it.copy(isCompleted = true) }

        val todosFromLegacyGet = context.getTodoList()
        val todosFromSpecAlias = context.listTodoItems()
        assertEquals(1, todosFromLegacyGet.size)
        assertTrue(todosFromLegacyGet.single().isCompleted)
        assertEquals(todosFromLegacyGet, todosFromSpecAlias)

        context.clearTodoItems()
        assertTrue(context.list().isEmpty())
    }
}
