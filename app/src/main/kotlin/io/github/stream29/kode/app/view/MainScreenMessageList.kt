package io.github.stream29.kode.app.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.model.SessionMessage
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.ui.core.components.message.MessageBubble
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val scriptArgsJson: Json = Json { ignoreUnknownKeys = true }

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
                    text = "Start a conversation by typing below 👇",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ElevatedCard
        }

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
                when (message) {
                    is UserMessage -> {
                        MessageBubble(
                            message = message,
                            isCurrentUser = true,
                            messageAlignment = messageAlignment,
                            messageMaxWidthRatio = messageMaxWidthRatio,
                            onForkFromHere = { onForkFromMessage(index) },
                        )
                    }

                    is AgentScript -> {
                        ScriptMessageBlock(
                            scriptMessage = message,
                            sourceIndex = index,
                            onForkFromMessage = onForkFromMessage,
                            messageAlignment = messageAlignment,
                            messageMaxWidthRatio = messageMaxWidthRatio,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptMessageBlock(
    scriptMessage: AgentScript,
    sourceIndex: Int,
    onForkFromMessage: (Int) -> Unit,
    messageAlignment: String,
    messageMaxWidthRatio: Float,
) {
    var expanded by rememberSaveable(scriptMessage.id) { mutableStateOf(false) }
    val scriptBody = remember(scriptMessage.id, scriptMessage.scriptStdout) { extractScriptBody(scriptMessage) }
    val scriptResult = remember(scriptMessage.id, scriptMessage.scriptReturnValue, scriptMessage.error) {
        extractScriptResult(scriptMessage)
    }
    val collapsedPreview = remember(scriptBody) {
        scriptBody.replace(Regex("\\s+"), " ").trim().ifBlank { "(empty script)" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Script Preview",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = scriptMessage.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!expanded) {
                    Text(
                        text = collapsedPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "Script",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    SelectionContainer {
                        Text(
                            text = scriptBody.ifBlank { "(empty script)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    SelectionContainer {
                        Text(
                            text = scriptResult,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        scriptMessage.outputList.forEachIndexed { outputIndex, output ->
            val outputMessage = scriptMessage.copy(
                id = "${scriptMessage.id}#output-$outputIndex",
                status = AgentScriptStatus.COMPLETED,
                scriptReturnValue = output,
                scriptStdout = "",
                error = null,
                outputList = listOf(output),
            )
            MessageBubble(
                message = outputMessage,
                isCurrentUser = false,
                messageAlignment = messageAlignment,
                messageMaxWidthRatio = messageMaxWidthRatio,
                onForkFromHere = { onForkFromMessage(sourceIndex) },
            )
        }
    }
}

private fun extractScriptBody(message: AgentScript): String {
    val rawArgs = message.scriptStdout
    if (rawArgs.isBlank()) {
        return ""
    }
    return runCatching {
        val json = scriptArgsJson.parseToJsonElement(rawArgs).jsonObject
        json["script"]?.jsonPrimitive?.contentOrNull ?: rawArgs
    }.getOrDefault(rawArgs)
}

private fun extractScriptResult(message: AgentScript): String {
    if (message.status == AgentScriptStatus.PENDING_INPUT) {
        return "(waiting for user input)"
    }
    val resultBody = message.scriptReturnValue?.takeIf { it.isNotBlank() } ?: "(no result)"
    val errorLine = message.error?.takeIf { it.isNotBlank() }
    return if (errorLine == null) {
        resultBody
    } else {
        "Error: $errorLine\n$resultBody"
    }
}
