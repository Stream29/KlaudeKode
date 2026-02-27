package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoApiTest {
    @Test
    fun todoManagerUpdatesStateCorrectly() {
        val manager = TodoManager()

        val newNodes = listOf(
            TodoNode(
                name = "Root",
                isCompleted = false,
                subtasks = listOf(
                    TodoNode(
                        name = "Child",
                        isCompleted = true,
                        subtasks = emptyList()
                    )
                )
            )
        )

        manager.updateNodes(newNodes)

        val allNodes = manager.listAllNodes()
        assertEquals(1, allNodes.size)

        val root = allNodes.first()
        assertEquals("Root", root.name)
        assertFalse(root.isCompleted)
        assertEquals(1, root.subtasks.size)

        val child = root.subtasks.first()
        assertEquals("Child", child.name)
        assertTrue(child.isCompleted)
        assertEquals(0, child.subtasks.size)
    }
}
