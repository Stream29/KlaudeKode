package io.github.stream29.kode.app.view.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
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
import io.github.stream29.kode.app.model.MessageAlignmentPreference
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
    messageAlignment: String,
    messageMaxWidthRatio: Float,
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
    val canToggleDetails = toolDetailText != null
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by hoverInteractionSource.collectIsHoveredAsState()
    var detailsExpanded by rememberSaveable("${message.id}-details") {
        mutableStateOf(message.role == MessageRole.TOOL_CALL || message.role == MessageRole.TOOL_RESULT)
    }
    val clickInteractionSource = remember { MutableInteractionSource() }
    val timeFormat = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val timeText = "${timeFormat.hour.toString().padStart(2, '0')}:${timeFormat.minute.toString().padStart(2, '0')}"
    val copyText = toolDetailText?.takeIf { it.isNotBlank() } ?: message.content
    val alignmentMode = MessageAlignmentPreference.fromValue(messageAlignment)
    val bubbleAlignment = if (alignmentMode.userAlignsToEnd() && isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }
    val maxWidthRatio = messageMaxWidthRatio.coerceIn(0.5f, 1f)

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * maxWidthRatio
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = bubbleAlignment,
        ) {
            // Message bubble
            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .widthIn(max = maxBubbleWidth)
                    .hoverable(interactionSource = hoverInteractionSource)
                    .then(
                        if (canToggleDetails) {
                            Modifier.clickable(
                                interactionSource = clickInteractionSource,
                                indication = null,
                            ) {
                                detailsExpanded = !detailsExpanded
                            }
                        } else {
                            Modifier
                        }
                    ),
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
                Box(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MessageContent(
                            message = message,
                            contentColor = contentColor,
                            containerColor = backgroundColor,
                        )

                        if (toolDetailText != null && detailsExpanded) {
                            ToolMessageDetails(
                                role = message.role,
                                details = toolDetailText,
                                contentColor = contentColor,
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.matchParentSize(),
                    ) {
                        DisableSelection {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isTool && toolName != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 2.dp, top = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = contentColor.copy(alpha = 0.85f)
                                        )
                                        Text(
                                            text = toolName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = contentColor.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.7f),
                                    )
                                    if (canToggleDetails) {
                                        Icon(
                                            imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = contentColor.copy(alpha = 0.75f),
                                        )
                                    }
                                    if (onForkFromHere != null) {
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above
                                            ),
                                            tooltip = {
                                                PlainTooltip { Text("Fork from this message") }
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
                                            PlainTooltip { Text("Copy message") }
                                        },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(
                                            onClick = { copyToClipboard(copyText) },
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
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val label = when (role) {
        MessageRole.TOOL_CALL -> "Parameters"
        MessageRole.TOOL_RESULT -> "Result"
        else -> "Details"
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            SelectionContainer {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: SessionMessage,
    contentColor: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    val text = message.content
    if (shouldRenderMarkdownMessage(message = message, content = text)) {
        MarkdownMessageContent(
            markdown = text,
            textColor = contentColor,
            containerColor = containerColor,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    SelectionContainer {
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
}

private val blockMarkdownRegex: Regex = Regex("""(?m)^\s{0,3}(#{1,6}\s|[-*+]\s|\d+\.\s|>|```)""")
private val inlineMarkdownRegex: Regex = Regex("""(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\([^\)]+\))""")
private val mermaidFenceRegex: Regex = Regex("""(?is)```\s*mermaid\b""")

private fun shouldRenderMarkdownMessage(message: SessionMessage, content: String): Boolean {
    if (content.isBlank()) {
        return false
    }
    if (message.role == MessageRole.TOOL_CALL || message.role == MessageRole.TOOL_RESULT) {
        return false
    }
    if (mermaidFenceRegex.containsMatchIn(content)) {
        return true
    }
    if (blockMarkdownRegex.containsMatchIn(content)) {
        return true
    }
    return inlineMarkdownRegex.containsMatchIn(content)
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
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
