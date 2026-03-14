package io.github.stream29.kode.ui.components.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

public data class TodoUiNode(
    val name: String,
    val completed: Boolean,
    val subItems: List<TodoUiNode>,
    val path: String,
    val expanded: Boolean,
    val level: Int,
)

public data class TodoUiState(
    val rootNodes: List<TodoUiNode>,
    val allExpanded: Boolean,
)

@Composable
public fun TodoSidebar(
    todoState: TodoUiState,
    onToggleExpand: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleNodes = remember(todoState.rootNodes) {
        buildVisibleNodes(todoState.rootNodes)
    }

    LazyColumn(
        modifier = modifier.wrapContentWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (visibleNodes.isEmpty()) {
            item(key = "todo-sidebar-empty") {
                Text(
                    text = "No todos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        } else {
            items(
                items = visibleNodes,
                key = { node -> node.path },
            ) { node ->
                TodoSidebarNodeRow(
                    node = node,
                    onToggleExpand = onToggleExpand,
                    onToggleComplete = onToggleComplete,
                )
            }
        }
    }
}

@Composable
private fun TodoSidebarNodeRow(
    node: TodoUiNode,
    onToggleExpand: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(modifier = Modifier.width((node.level * 16).dp))

        if (node.subItems.isNotEmpty()) {
            IconButton(
                onClick = { onToggleExpand(node.path) },
            ) {
                Icon(
                    imageVector = if (node.expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (node.expanded) "Collapse todo" else "Expand todo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Checkbox(
            checked = node.completed,
            onCheckedChange = { onToggleComplete(node.path) },
        )

        Text(
            text = node.name.ifBlank { node.path.ifBlank { "Todo" } },
            style = MaterialTheme.typography.bodyMedium,
            color = if (node.completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (node.completed) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )

    }
}

public fun buildVisibleNodes(rootNodes: List<TodoUiNode>): List<TodoUiNode> {
    val visibleNodes = mutableListOf<TodoUiNode>()

    fun appendNode(node: TodoUiNode) {
        visibleNodes += node
        if (node.expanded) {
            node.subItems.forEach { child ->
                appendNode(child)
            }
        }
    }

    rootNodes.forEach { rootNode ->
        appendNode(rootNode)
    }

    return visibleNodes
}
