package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.util.formatModelDisplayName
import io.github.stream29.kode.app.viewmodel.ChatPageUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.app.viewmodel.SessionUiState
import io.github.stream29.kode.app.viewmodel.chat.ChatViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionControls(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    sessionUi: SessionUiState,
    ui: ChatPageUiState,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { state.createNewSession() },
            enabled = true,
            label = { Text("New Session") },
            leadingIcon = {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )

        SessionQuickSwitch(state = state, chatViewModel = chatViewModel, sessionUi = sessionUi, ui = ui)
        ModelQuickSwitch(state = state, ui = ui)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionQuickSwitch(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    sessionUi: SessionUiState,
    ui: ChatPageUiState,
) {
    val sessions = ui.sessionSummaries
    val activeId = sessionUi.currentSessionId
    val activeSession = sessions.firstOrNull { it.id == activeId }
    val displayName = activeSession?.title?.takeIf { it.isNotBlank() }
        ?: activeId?.let { "Session ${it.take(8)}" }
        ?: "No session"
    var expanded by remember { mutableStateOf(false) }
    val showEditDialogState = rememberSaveable(activeId) { mutableStateOf(false) }
    var titleDraft by rememberSaveable(activeId) { mutableStateOf(activeSession?.title.orEmpty()) }
    val enabled = sessions.isNotEmpty()
    val hasActiveSession = activeId != null
    val titleGenerating = sessionUi.isGeneratingSessionTitle

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                if (enabled) {
                    expanded = !expanded
                }
            },
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Session") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .widthIn(min = 260.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                sessions.forEach { session ->
                    val title = session.title.takeIf { it.isNotBlank() }
                        ?: "Session ${session.id.take(8)}"
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(title)
                                Text(
                                    "${session.messageCount} messages · ${session.id.take(8)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            state.switchToSession(session.id)
                            expanded = false
                        },
                    )
                }
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(if (titleGenerating) "Generating title..." else "Refresh title")
                }
            },
            state = rememberTooltipState(),
        ) {
            FilledTonalIconButton(
                onClick = { chatViewModel.regenerateCurrentSessionTitle() },
                enabled = hasActiveSession && !titleGenerating,
                modifier = Modifier.size(36.dp),
            ) {
                if (titleGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh title",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text("Edit title") } },
            state = rememberTooltipState(),
        ) {
            FilledTonalIconButton(
                onClick = {
                    titleDraft = activeSession?.title.orEmpty()
                    showEditDialogState.value = true
                },
                enabled = hasActiveSession,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit title",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showEditDialogState.value) {
        AlertDialog(
            onDismissRequest = { showEditDialogState.value = false },
            title = { Text("Edit session title") },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Title") },
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        chatViewModel.updateCurrentSessionTitle(titleDraft)
                        showEditDialogState.value = false
                    },
                    enabled = titleDraft.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialogState.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelQuickSwitch(state: MainViewModel, ui: ChatPageUiState) {
    val models = ui.models
    val auths = ui.auths
    var expanded by remember { mutableStateOf(false) }

    if (models.isEmpty()) {
        AssistChip(
            onClick = { state.navigateToPage(page = AppPage.Models) },
            label = { Text("Model: Not configured") },
            leadingIcon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        return
    }

    val activeModel = models.find { it.id == ui.activeModelId } ?: models.first()
    val displayName = formatModelDisplayName(activeModel, auths)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .widthIn(min = 220.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(formatModelDisplayName(model, auths))
                            Text(
                                "ID: ${model.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        state.switchModel(model.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
