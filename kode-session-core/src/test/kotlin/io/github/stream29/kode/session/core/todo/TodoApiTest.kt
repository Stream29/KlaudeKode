package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoApiTest {
    @Test
    fun scriptStyleTodoApiCompletesWorkflow() {
        val api = TodoApiHarness(initialNodes = emptyList())

        val rootId = api.todoAdd(parentId = null, text = "Root")
        val childId = api.todoAdd(parentId = rootId, text = "Child")

        api.todoUpdate(id = childId, text = "Child Updated", newChildren = null)
        api.todoComplete(id = childId, completed = true)

        val allNodes = api.todoList()
        assertEquals(2, allNodes.size)

        val root = allNodes.single { node -> node.id == rootId }
        assertEquals("Root", root.text)
        assertNull(root.parentId)
        assertFalse(root.completed)

        val child = allNodes.single { node -> node.id == childId }
        assertEquals("Child Updated", child.text)
        assertEquals(rootId, child.parentId)
        assertTrue(child.completed)

        assertEquals("Root:Child Updated", api.todoGetPath(id = childId))

        api.todoRemove(id = childId)
        val afterRemove = api.todoList()
        assertEquals(listOf(rootId), afterRemove.map { node -> node.id })
    }

    @Test
    fun todoUpdateWithNewChildrenRebuildsSubtree() {
        val api = TodoApiHarness(initialNodes = emptyList())

        val rootId = api.todoAdd(parentId = null, text = "Root")
        val oldChildId = api.todoAdd(parentId = rootId, text = "Old Child")
        val oldGrandchildId = api.todoAdd(parentId = oldChildId, text = "Old Grandchild")

        val newChild = TodoNode(
            id = "new-child-id",
            text = "New Child",
            completed = false,
            parentId = rootId,
            metadata = null,
        )
        val newGrandchild = TodoNode(
            id = "new-grandchild-id",
            text = "New Grandchild",
            completed = false,
            parentId = newChild.id,
            metadata = null,
        )

        api.todoUpdate(
            id = rootId,
            text = "Root Rebuilt",
            newChildren = listOf(newChild, newGrandchild),
        )

        val allNodes = api.todoList()
        assertEquals(3, allNodes.size)

        val rebuiltRoot = allNodes.single { node -> node.id == rootId }
        assertEquals("Root Rebuilt", rebuiltRoot.text)
        assertNull(rebuiltRoot.parentId)

        assertNotNull(allNodes.singleOrNull { node -> node.id == newChild.id })
        assertNotNull(allNodes.singleOrNull { node -> node.id == newGrandchild.id })
        assertNull(allNodes.singleOrNull { node -> node.id == oldChildId })
        assertNull(allNodes.singleOrNull { node -> node.id == oldGrandchildId })

        assertEquals("Root Rebuilt:New Child:New Grandchild", api.todoGetPath(id = newGrandchild.id))
    }

    private class TodoApiHarness(
        initialNodes: List<TodoNode>,
    ) {
        private var todoManager: TodoManager = TodoManager(initialNodes = initialNodes)

        fun todoAdd(parentId: String?, text: String): String {
            val createdNode = todoManager.addNode(parentId = parentId, text = text)
            return createdNode.id
        }

        fun todoUpdate(id: String, text: String, newChildren: List<TodoNode>?) {
            if (newChildren == null) {
                todoManager.updateNode(id = id, text = text)
                return
            }
            todoManager.rebuildSubtree(id = id, newText = text, newChildren = newChildren)
        }

        fun todoComplete(id: String, completed: Boolean) {
            val currentNodes = todoManager.listAllNodes()
            require(currentNodes.any { node -> node.id == id }) { "Todo node not found: $id" }

            val updatedNodes = currentNodes.map { node ->
                if (node.id == id) {
                    node.copy(completed = completed)
                } else {
                    node
                }
            }
            todoManager = TodoManager(initialNodes = updatedNodes)
        }

        fun todoRemove(id: String) {
            todoManager.removeNode(id = id)
        }

        fun todoList(): List<TodoNode> {
            return todoManager.listAllNodes()
        }

        fun todoGetPath(id: String): String? {
            return todoManager.getPath(id = id)
        }
    }
}
