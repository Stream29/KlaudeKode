package io.github.stream29.kode.ui.components.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

public typealias TodoNode = Any

public data class TodoUiNode(
    val node: TodoNode,
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
                key = { node -> node.id },
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
    node: RenderTodoNode,
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

        if (node.hasChildren) {
            IconButton(
                onClick = { onToggleExpand(node.id) },
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
            onCheckedChange = { onToggleComplete(node.id) },
        )

        Text(
            text = node.path,
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

private data class SidebarTodoNode(
    val id: String,
    val path: String,
    val completed: Boolean,
    val parentId: String?,
    val expanded: Boolean,
    val level: Int,
    val originalIndex: Int,
)

private data class RenderTodoNode(
    val id: String,
    val path: String,
    val completed: Boolean,
    val expanded: Boolean,
    val level: Int,
    val hasChildren: Boolean,
)

private fun buildVisibleNodes(rootNodes: List<TodoUiNode>): List<RenderTodoNode> {
    if (rootNodes.isEmpty()) {
        return emptyList()
    }

    val usedIds = mutableSetOf<String>()
    val sidebarNodes = rootNodes.mapIndexed { index, uiNode ->
        toSidebarNode(uiNode = uiNode, index = index, usedIds = usedIds)
    }
    val nodeById = sidebarNodes.associateBy { node -> node.id }
    val childrenByParent = sidebarNodes
        .groupBy { node ->
            val parentId = node.parentId
            if (parentId != null && nodeById[parentId] == null) {
                null
            } else {
                parentId
            }
        }
        .mapValues { entry ->
            entry.value.sortedBy { node -> node.originalIndex }
        }

    val roots = childrenByParent[null].orEmpty().ifEmpty {
        sidebarNodes.sortedBy { node -> node.originalIndex }
    }
    val visibleNodes = mutableListOf<RenderTodoNode>()
    val visited = mutableSetOf<String>()

    fun appendNode(node: SidebarTodoNode, inheritedLevel: Int) {
        if (!visited.add(node.id)) {
            return
        }

        val children = childrenByParent[node.id].orEmpty()
        val currentLevel = max(node.level, inheritedLevel)
        visibleNodes += RenderTodoNode(
            id = node.id,
            path = node.path,
            completed = node.completed,
            expanded = node.expanded,
            level = currentLevel,
            hasChildren = children.isNotEmpty(),
        )

        if (!node.expanded) {
            return
        }

        children.forEach { child ->
            appendNode(node = child, inheritedLevel = currentLevel + 1)
        }
    }

    roots.forEach { root ->
        appendNode(node = root, inheritedLevel = root.level)
    }


    return visibleNodes
}

private fun toSidebarNode(
    uiNode: TodoUiNode,
    index: Int,
    usedIds: MutableSet<String>,
): SidebarTodoNode {
    val node = uiNode.node
    val rawId = resolveStringMethod(node = node, methodName = "getId").ifBlank { "todo-$index" }
    val uniqueId = ensureUniqueId(rawId = rawId, index = index, usedIds = usedIds)
    val fallbackPath = resolveStringMethod(node = node, methodName = "getText").ifBlank { uniqueId }

    return SidebarTodoNode(
        id = uniqueId,
        path = normalizePath(path = uiNode.path, fallback = fallbackPath),
        completed = resolveCompleted(node = node),
        parentId = resolveParentId(node = node),
        expanded = uiNode.expanded,
        level = uiNode.level,
        originalIndex = index,
    )
}

private fun normalizePath(path: String, fallback: String): String {
    val normalized = path.split(':')
        .map { segment -> segment.trim() }
        .filter { segment -> segment.isNotEmpty() }
        .joinToString(separator = ":")

    return if (normalized.isNotEmpty()) {
        normalized
    } else {
        fallback
    }
}

private fun ensureUniqueId(
    rawId: String,
    index: Int,
    usedIds: MutableSet<String>,
): String {
    if (usedIds.add(rawId)) {
        return rawId
    }

    var suffix = 1
    while (true) {
        val candidateId = "$rawId-$index-$suffix"
        if (usedIds.add(candidateId)) {
            return candidateId
        }
        suffix += 1
    }
}

private fun resolveStringMethod(node: Any, methodName: String): String {
    return runCatching {
        node.javaClass.getMethod(methodName).invoke(node) as? String
    }.getOrNull().orEmpty().trim()
}

private fun resolveParentId(node: Any): String? {
    return runCatching {
        node.javaClass.getMethod("getParentId").invoke(node) as? String
    }.getOrNull()?.trim()?.takeIf { value -> value.isNotEmpty() }
}

private fun resolveCompleted(node: Any): Boolean {
    return runCatching {
        node.javaClass.getMethod("getCompleted").invoke(node) as? Boolean
    }.getOrNull() == true
}
