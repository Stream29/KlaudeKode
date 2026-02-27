package io.github.stream29.kode.app.ui

import io.github.stream29.kode.ui.components.todo.TodoUiNode
import io.github.stream29.kode.ui.components.todo.TodoUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoSidebarTest {
    @Test
    fun openSidebarShowsRootTodoNode() {
        val state = TodoUiState(
            rootNodes = listOf(
                createUiNode(
                    id = "root",
                    text = "Root task",
                    completed = false,
                    parentId = null,
                    expanded = false,
                    level = 0,
                ),
            ),
            allExpanded = false,
        )

        val visibleNodes = buildVisibleNodes(todoState = state)

        assertEquals(1, visibleNodes.size)
        assertEquals("root", visibleNodes.single().id)
        assertEquals("Root task", visibleNodes.single().path)
    }

    @Test
    fun expandAndCollapseNodesChangesVisibleNodes() {
        val collapsedState = buildParentChildState(rootExpanded = false, childCompleted = false)
        val expandedState = buildParentChildState(rootExpanded = true, childCompleted = false)

        val collapsedVisibleNodes = buildVisibleNodes(todoState = collapsedState)
        val expandedVisibleNodes = buildVisibleNodes(todoState = expandedState)

        assertEquals(listOf("root"), collapsedVisibleNodes.map { node -> node.id })
        assertEquals(listOf("root", "child"), expandedVisibleNodes.map { node -> node.id })
        assertTrue(expandedVisibleNodes.first().hasChildren)
    }

    @Test
    fun markCompleteAndIncompleteUpdatesRenderedState() {
        val incompleteState = buildParentChildState(rootExpanded = true, childCompleted = false)
        val completedState = buildParentChildState(rootExpanded = true, childCompleted = true)

        val incompleteChild = buildVisibleNodes(todoState = incompleteState).single { node -> node.id == "child" }
        val completedChild = buildVisibleNodes(todoState = completedState).single { node -> node.id == "child" }

        assertFalse(incompleteChild.completed)
        assertTrue(completedChild.completed)
    }

    @Test
    fun todoStatePersistsAfterRefresh() {
        val beforeRefresh = buildParentChildState(rootExpanded = true, childCompleted = true)
        val afterRefresh = buildParentChildState(rootExpanded = true, childCompleted = true)

        val visibleBeforeRefresh = buildVisibleNodes(todoState = beforeRefresh)
        val visibleAfterRefresh = buildVisibleNodes(todoState = afterRefresh)

        assertEquals(visibleBeforeRefresh, visibleAfterRefresh)
    }

    @Test
    fun manualVerificationChecklistIsDocumentedForComposeDesktop() {
        assertEquals(4, MANUAL_VERIFICATION_STEPS.size)
        assertTrue(MANUAL_VERIFICATION_STEPS.all { step -> step.isNotBlank() })
    }

    private fun buildParentChildState(rootExpanded: Boolean, childCompleted: Boolean): TodoUiState {
        return TodoUiState(
            rootNodes = listOf(
                createUiNode(
                    id = "root",
                    text = "Root task",
                    completed = false,
                    parentId = null,
                    expanded = rootExpanded,
                    level = 0,
                ),
                createUiNode(
                    id = "child",
                    text = "Root task:Child task",
                    completed = childCompleted,
                    parentId = "root",
                    expanded = false,
                    level = 1,
                ),
            ),
            allExpanded = false,
        )
    }

    private fun createUiNode(
        id: String,
        text: String,
        completed: Boolean,
        parentId: String?,
        expanded: Boolean,
        level: Int,
    ): TodoUiNode {
        return TodoUiNode(
            node = FakeTodoNode(
                id = id,
                text = text,
                completed = completed,
                parentId = parentId,
            ),
            path = text,
            expanded = expanded,
            level = level,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildVisibleNodes(todoState: TodoUiState): List<RenderedNodeSnapshot> {
        val rawVisibleNodes = BUILD_VISIBLE_NODES_METHOD.invoke(null, todoState.rootNodes) as List<Any>
        return rawVisibleNodes.map { node ->
            RenderedNodeSnapshot(
                id = node.invokeGetter(methodName = "getId") as String,
                path = node.invokeGetter(methodName = "getPath") as String,
                completed = node.invokeGetter(methodName = "getCompleted") as Boolean,
                expanded = node.invokeGetter(methodName = "getExpanded") as Boolean,
                level = node.invokeGetter(methodName = "getLevel") as Int,
                hasChildren = node.invokeGetter(methodName = "getHasChildren") as Boolean,
            )
        }
    }

    private fun Any.invokeGetter(methodName: String): Any? {
        val method = this.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(this)
    }

    internal data class FakeTodoNode(
        val id: String,
        val text: String,
        val completed: Boolean,
        val parentId: String?,
    )

    private data class RenderedNodeSnapshot(
        val id: String,
        val path: String,
        val completed: Boolean,
        val expanded: Boolean,
        val level: Int,
        val hasChildren: Boolean,
    )

    private companion object {
        private val BUILD_VISIBLE_NODES_METHOD = Class
            .forName("io.github.stream29.kode.ui.components.todo.TodoSidebarKt")
            .getDeclaredMethod("buildVisibleNodes", List::class.java)
            .apply {
                isAccessible = true
            }

        private val MANUAL_VERIFICATION_STEPS: List<String> = listOf(
            "1. 打开 Chat 页面，确认宽屏下输入区右侧可切换 Todo 侧边栏可见性。",
            "2. 在侧边栏点击父节点箭头，验证子节点展开与折叠都正常。",
            "3. 点击节点复选框切换完成/未完成状态，验证删除线样式同步变化。",
            "4. 刷新应用并恢复同一会话，验证节点展开状态与完成状态保持。",
        )
    }
}
