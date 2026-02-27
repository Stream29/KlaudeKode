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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
// import io.github.stream29.kode.ui.core.todo.TodoUiNode

@Composable
public fun TodoNodeItem(
    node: TodoUiNode,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    val hasChildren = node.subtasks.isNotEmpty()
    val completed = node.isCompleted
    val displayText = node.name.ifBlank { "Todo" }

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
