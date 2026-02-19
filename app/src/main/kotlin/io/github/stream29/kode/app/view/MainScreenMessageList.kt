package io.github.stream29.kode.app.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.ui.core.components.message.MessageBubble
import io.github.stream29.kode.ui.core.components.message.SystemMessage
import io.github.stream29.kode.ui.core.message.collapsedPreviewUi
import io.github.stream29.kode.ui.core.message.collapsedTitleUi
import io.github.stream29.kode.ui.core.message.isSystemRoleUi
import io.github.stream29.kode.ui.core.message.isUiError
import io.github.stream29.kode.ui.core.message.isUiToolCallLike
import io.github.stream29.kode.ui.core.message.isUserRoleUi
import io.github.stream29.kode.ui.core.message.shouldExpandByDefaultUi

@Composable
internal fun MessageList(
    messages: List<SessionMessage>,
    onForkFromMessage: (Int) -> Unit,
    messageAlignment: String,
    messageMaxWidthRatio: Float,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Start a conversation by typing below 👇",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = messages,
                    key = { index, message -> "${message.id}-$index" },
                ) { index, message ->
                    val defaultExpanded = remember(
                        message.id,
                        message.isUiToolCallLike(),
                        message.isUiError(),
                    ) {
                        message.shouldExpandByDefaultUi()
                    }
                    var expanded by rememberSaveable(message.id) { mutableStateOf(defaultExpanded) }

                    if (!expanded) {
                        CollapsedMessageRow(
                            message = message,
                            onExpand = { expanded = true },
                        )
                        return@itemsIndexed
                    }

                    if (message.isSystemRoleUi()) {
                        SystemMessage(content = message.collapsedPreviewUi(maxLength = Int.MAX_VALUE))
                    } else {
                        MessageBubble(
                            message = message,
                            isCurrentUser = message.isUserRoleUi(),
                            messageAlignment = messageAlignment,
                            messageMaxWidthRatio = messageMaxWidthRatio,
                            onForkFromHere = {
                                onForkFromMessage(index)
                            },
                        )
                    }

                    if (!defaultExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { expanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Collapse")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedMessageRow(
    message: SessionMessage,
    onExpand: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.collapsedTitleUi(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message.collapsedPreviewUi(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
