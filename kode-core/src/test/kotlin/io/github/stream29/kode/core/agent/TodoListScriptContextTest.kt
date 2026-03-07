package io.github.stream29.kode.core.agent

import io.github.stream29.kode.agent.model.TodoItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TodoListScriptContextTest {

    @Test
    fun `editTodoItem should update root level item`() {
        val initialTodos = listOf(
            TodoItem(name = "Task 1", completed = false),
            TodoItem(name = "Task 2", completed = false),
        )
        val context = TodoListScriptContextImpl(initialTodos)

        context.editTodoItem("Task 1") { it.copy(completed = true) }

        val todos = context.listTodoItems()
        assertEquals(2, todos.size)
        assertTrue(todos[0].completed)
        assertFalse(todos[1].completed)
    }

    @Test
    fun `editTodoItem should update nested item`() {
        val initialTodos = listOf(
            TodoItem(
                name = "Task 1",
                completed = false,
                subItems = listOf(
                    TodoItem(name = "Subtask 1.1", completed = false),
                    TodoItem(name = "Subtask 1.2", completed = false),
                ),
            ),
        )
        val context = TodoListScriptContextImpl(initialTodos)

        context.editTodoItem("Task 1", "Subtask 1.2") { it.copy(completed = true) }

        val todos = context.listTodoItems()
        assertEquals(1, todos.size)
        assertFalse(todos[0].completed)
        val subItems = todos[0].subItems
        assertEquals(2, subItems.size)
        assertFalse(subItems[0].completed)
        assertTrue(subItems[1].completed)
    }

    @Test
    fun `editTodoItem should throw IllegalArgumentException for non-existent item`() {
        val initialTodos = listOf(TodoItem(name = "Task 1", completed = false))
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.editTodoItem("Task 2") { it.copy(completed = true) }
        }
        assertEquals("Todo item not found: Task 2", exception.message)
    }

    @Test
    fun `editTodoItem should throw IllegalArgumentException for non-existent nested item`() {
        val initialTodos = listOf(
            TodoItem(
                name = "Task 1",
                completed = false,
                subItems = listOf(TodoItem(name = "Subtask 1.1", completed = false)),
            ),
        )
        val context = TodoListScriptContextImpl(initialTodos)

        val exception = assertThrows<IllegalArgumentException> {
            context.editTodoItem("Task 1", "Subtask 1.2") { it.copy(completed = true) }
        }
        assertEquals("Todo item not found: Subtask 1.2", exception.message)
    }

    @Test
    fun `editTodoItem should throw IllegalArgumentException for empty path`() {
        val context = TodoListScriptContextImpl()

        val exception = assertThrows<IllegalArgumentException> {
            context.editTodoItem(update = { it.copy(completed = true) })
        }
        assertEquals("Path cannot be empty", exception.message)
    }

    @Test
    fun `editTodoItem should throw IllegalArgumentException for blank path segment`() {
        val context = TodoListScriptContextImpl()

        val exception = assertThrows<IllegalArgumentException> {
            context.editTodoItem(" ") { it.copy(completed = true) }
        }
        assertEquals("Path cannot contain blank names", exception.message)
    }

    @Test
    fun `clearTodoItems should empty todo list`() {
        val context = TodoListScriptContextImpl(
            initialTodos = listOf(TodoItem(name = "Task", completed = false)),
        )

        context.clearTodoItems()

        assertTrue(context.listTodoItems().isEmpty())
    }

    @Test
    fun `convenience aliases should remain aligned`() {
        val context = TodoListScriptContextImpl()

        context.reset(
            listOf(TodoItem(name = "Legacy Task", completed = false)),
        )
        context.edit("Legacy Task") { it.copy(completed = true) }

        val todosFromAlias = context.list()
        val todosFromCanonical = context.listTodoItems()
        assertEquals(1, todosFromAlias.size)
        assertTrue(todosFromAlias.single().completed)
        assertEquals(todosFromAlias, todosFromCanonical)

        context.clear()
        assertTrue(context.listTodoItems().isEmpty())
    }
}
