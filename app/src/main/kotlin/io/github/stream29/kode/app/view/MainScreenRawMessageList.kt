package io.github.stream29.kode.app.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.session.core.model.AgentMessage
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.ui.core.message.collapsedPreviewUi
import io.github.stream29.kode.ui.core.message.messageTypeNameUi
import kotlinx.serialization.json.Json

private val rawMessageJsonCodec: Json = Json {
    prettyPrint = true
    encodeDefaults = true
}

@Composable
internal fun RawMessageList(
    messages: List<SessionMessage>,
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
                    text = "No raw messages yet",
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
                    key = { index, message -> "raw-${message.id}-$index" },
                ) { index, message ->
                    val rawJsonText = remember(message) { message.toRawJsonText() }
                    val collapsedPreview = remember(message) { message.collapsedPreviewUi() }
                    var expanded by rememberSaveable("raw-${message.id}") { mutableStateOf(false) }

                    if (!expanded) {
                        RawMessageCollapsedRow(
                            index = index,
                            message = message,
                            previewText = collapsedPreview,
                            onExpand = { expanded = true },
                        )
                    } else {
                        RawMessageExpandedRow(
                            index = index,
                            message = message,
                            rawJsonText = rawJsonText,
                            onCollapse = { expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RawMessageCollapsedRow(
    index: Int,
    message: SessionMessage,
    previewText: String,
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RawMessageHeader(
                    index = index,
                    message = message,
                    modifier = Modifier.fillMaxWidth(),
                    singleLineTimestamp = true,
                )
                Text(
                    text = previewText,
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

@Composable
private fun RawMessageExpandedRow(
    index: Int,
    message: SessionMessage,
    rawJsonText: String,
    onCollapse: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RawMessageHeader(
                index = index,
                message = message,
                modifier = Modifier.fillMaxWidth(),
                singleLineTimestamp = false,
            )
            SelectionContainer {
                Text(
                    text = rawJsonText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            TextButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Collapse")
            }
        }
    }
}

@Composable
private fun RawMessageHeader(
    index: Int,
    message: SessionMessage,
    modifier: Modifier,
    singleLineTimestamp: Boolean,
) {
    Column(modifier = modifier) {
        Text(
            text = "#${index + 1} ${message.messageTypeNameUi()}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "id=${message.id} · ts=${message.timestamp}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (singleLineTimestamp) 1 else Int.MAX_VALUE,
            overflow = if (singleLineTimestamp) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

private fun SessionMessage.toRawJsonText(): String {
    return runCatching {
        rawMessageJsonCodec.encodeToString(AgentMessage.serializer(), this)
    }.getOrElse { error ->
        "{\n  \"serializationError\": \"${error.message ?: "unknown"}\",\n  \"id\": \"$id\",\n  \"timestamp\": \"$timestamp\"\n}"
    }
}
