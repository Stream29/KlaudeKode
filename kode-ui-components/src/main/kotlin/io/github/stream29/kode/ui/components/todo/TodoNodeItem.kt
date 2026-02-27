package io.github.stream29.kode.ui.components.todo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

@Composable
public fun TodoNodeItem(
    node: TodoUiNode,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    val hasChildren = remember(node.node) {
        resolveHasChildren(node = node.node)
    }
    val completed = remember(node.node) {
        resolveCompleted(node = node.node)
    }
    val displayText = node.path.ifBlank {
        resolveStringMethod(node = node.node, methodName = "getText").ifBlank { "Todo" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width((node.level * 16).dp))

        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
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
            checked = completed,
            onCheckedChange = { onToggleComplete() },
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (completed) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp),
        )
    }
}

private fun resolveHasChildren(node: Any): Boolean {
    resolveBooleanMethod(node = node, methodName = "getHasChildren")?.let { hasChildren ->
        return hasChildren
    }
    resolveBooleanMethod(node = node, methodName = "hasChildren")?.let { hasChildren ->
        return hasChildren
    }

    val childrenValue = runCatching {
        node.javaClass.getMethod("getChildren").invoke(node)
    }.getOrNull()

    return when (childrenValue) {
        is Collection<*> -> childrenValue.isNotEmpty()
        is Array<*> -> childrenValue.isNotEmpty()
        else -> false
    }
}

private fun resolveCompleted(node: Any): Boolean {
    resolveBooleanMethod(node = node, methodName = "getCompleted")?.let { completed ->
        return completed
    }
    resolveBooleanMethod(node = node, methodName = "isCompleted")?.let { completed ->
        return completed
    }
    return false
}

private fun resolveBooleanMethod(node: Any, methodName: String): Boolean? {
    return runCatching {
        node.javaClass.getMethod(methodName).invoke(node) as? Boolean
    }.getOrNull()
}

private fun resolveStringMethod(node: Any, methodName: String): String {
    return runCatching {
        node.javaClass.getMethod(methodName).invoke(node) as? String
    }.getOrNull().orEmpty().trim()
}
