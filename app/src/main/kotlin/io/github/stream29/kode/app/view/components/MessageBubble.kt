@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.hoverable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.model.extractToolCallArgumentsText
import io.github.stream29.kode.app.model.extractToolName
import io.github.stream29.kode.app.model.extractToolResultText
import io.github.stream29.kode.app.model.isUiError
import io.github.stream29.kode.app.model.isUiToolCallLike
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.SessionMessage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MessageBubble(
    message: SessionMessage,
    isCurrentUser: Boolean = false,
    onForkFromHere: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isTool = message.isUiToolCallLike()
    val toolName = message.extractToolName()
    val toolDetailText = when (message.role) {
        MessageRole.TOOL_CALL -> message.extractToolCallArgumentsText()
        MessageRole.TOOL_RESULT -> message.extractToolResultText()
        else -> null
    }
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by hoverInteractionSource.collectIsHoveredAsState()
    var metaPinned by rememberSaveable(message.id) { mutableStateOf(false) }
    val showMeta = isHovered || metaPinned
    var detailsExpanded by rememberSaveable("${message.id}-details") {
        mutableStateOf(message.role == MessageRole.TOOL_CALL || message.role == MessageRole.TOOL_RESULT)
    }
    val clickInteractionSource = remember { MutableInteractionSource() }
    
    val backgroundColor = when {
        message.isUiError() -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isAssistant -> MaterialTheme.colorScheme.secondaryContainer
        isTool -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        message.isUiError() -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isAssistant -> MaterialTheme.colorScheme.onSecondaryContainer
        isTool -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (showMeta) {
            Text(
                text = when (message.role) {
                    MessageRole.USER -> "You"
                    MessageRole.ASSISTANT -> "Assistant"
                    MessageRole.SYSTEM -> "System"
                    MessageRole.TOOL_CALL -> "Tool Call"
                    MessageRole.TOOL_RESULT -> "Tool Result"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Message bubble
        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .widthIn(max = 600.dp)
                .hoverable(interactionSource = hoverInteractionSource)
                .clickable(
                    interactionSource = clickInteractionSource,
                    indication = null,
                ) {
                    metaPinned = !metaPinned
                },
            colors = CardDefaults.elevatedCardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Tool name if applicable
                if (showMeta && isTool && toolName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
                
                // Message content
                MessageContent(
                    message = message,
                    contentColor = contentColor
                )

                if (toolDetailText != null) {
                    ToolMessageDetails(
                        role = message.role,
                        details = toolDetailText,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded },
                        contentColor = contentColor,
                    )
                }

                if (showMeta) {
                    // Timestamp and actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val timeFormat = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            text = "${timeFormat.hour.toString().padStart(2, '0')}:${timeFormat.minute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onForkFromHere != null) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text("Fork from this message")
                                        }
                                    },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(
                                        onClick = onForkFromHere,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                            contentDescription = "Fork",
                                            modifier = Modifier.size(16.dp),
                                            tint = contentColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Copy message")
                                    }
                                },
                                state = rememberTooltipState()
                            ) {
                                IconButton(
                                    onClick = { copyToClipboard(message.content) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp),
                                        tint = contentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val selection = StringSelection(text)
    clipboard.setContents(selection, selection)
}

@Composable
private fun ToolMessageDetails(
    role: MessageRole,
    details: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val label = when (role) {
        MessageRole.TOOL_CALL -> "Parameters"
        MessageRole.TOOL_RESULT -> "Result"
        else -> "Details"
    }

    TextButton(
        onClick = onToggle,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (expanded) "Hide $label" else "Show $label")
    }

    if (!expanded) {
        return
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        SelectionContainer {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun MessageContent(
    message: SessionMessage,
    contentColor: androidx.compose.ui.graphics.Color
) {
    val text = message.content
    if (text.contains("```") && message.role == MessageRole.ASSISTANT) {
        MarkdownCodeBlocks(
            content = text,
            contentColor = contentColor
        )
        return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
        fontFamily = if (message.role == MessageRole.TOOL_RESULT) {
            FontFamily.Monospace
        } else {
            null
        }
    )
}

@Composable
private fun MarkdownCodeBlocks(
    content: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    val parts = content.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            val isCode = index % 2 == 1
            if (isCode) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = part.trim(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (part.isNotBlank()) {
                Text(
                    text = part.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
public fun SystemMessage(
    content: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
