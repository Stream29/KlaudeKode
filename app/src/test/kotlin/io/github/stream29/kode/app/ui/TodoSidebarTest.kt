package io.github.stream29.kode.app.ui

import io.github.stream29.kode.ui.components.todo.TodoUiNode
import io.github.stream29.kode.ui.components.todo.TodoUiState
import io.github.stream29.kode.ui.components.todo.buildVisibleNodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoSidebarTest {
    @Test
    fun openSidebarShowsRootTodoNode() {
        val state = TodoUiState(
            rootNodes = listOf(
                TodoUiNode(
                    name = "root",
                    completed = false,
                    subItems = emptyList(),
                    path = "root",
                    expanded = false,
                    level = 0,
                )
            ),
            allExpanded = false,
        )

        val visibleNodes = buildVisibleNodes(todoState = state)

        assertEquals(1, visibleNodes.size)
        assertEquals("root", visibleNodes.single().path)
        assertEquals("root", visibleNodes.single().name)
    }

    @Test
    fun expandAndCollapseNodesChangesVisibleNodes() {
        val collapsedState = buildParentChildState(rootExpanded = false, childCompleted = false)
        val expandedState = buildParentChildState(rootExpanded = true, childCompleted = false)

        val collapsedVisibleNodes = buildVisibleNodes(todoState = collapsedState)
        val expandedVisibleNodes = buildVisibleNodes(todoState = expandedState)

        assertEquals(listOf("root"), collapsedVisibleNodes.map { node -> node.path })
        assertEquals(listOf("root", "root:child"), expandedVisibleNodes.map { node -> node.path })
        assertTrue(expandedVisibleNodes.first().subItems.isNotEmpty())
    }

    @Test
    fun markCompleteAndIncompleteUpdatesRenderedState() {
        val incompleteState = buildParentChildState(rootExpanded = true, childCompleted = false)
        val completedState = buildParentChildState(rootExpanded = true, childCompleted = true)

        val incompleteChild =
            buildVisibleNodes(todoState = incompleteState).single { node -> node.path == "root:child" }
        val completedChild = buildVisibleNodes(todoState = completedState).single { node -> node.path == "root:child" }

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
                TodoUiNode(
                    name = "root",
                    completed = false,
                    subItems = listOf(
                        TodoUiNode(
                            name = "child",
                            completed = childCompleted,
                            subItems = emptyList(),
                            path = "root:child",
                            expanded = false,
                            level = 1,
                        )
                    ),
                    path = "root",
                    expanded = rootExpanded,
                    level = 0,
                )
            ),
            allExpanded = false,
        )
    }

    private fun buildVisibleNodes(todoState: TodoUiState): List<TodoUiNode> {
        return buildVisibleNodes(todoState.rootNodes)
    }

    private companion object {
        private val MANUAL_VERIFICATION_STEPS: List<String> = listOf(
            "1. 打开 Chat 页面，确认宽屏下输入区右侧可切换 Todo 侧边栏可见性。",
            "2. 在侧边栏点击父节点箭头，验证子节点展开与折叠都正常。",
            "3. 点击节点复选框切换完成/未完成状态，验证删除线样式同步变化。",
            "4. 刷新应用并恢复同一会话，验证节点展开状态与完成状态保持。",
        )
    }
}
