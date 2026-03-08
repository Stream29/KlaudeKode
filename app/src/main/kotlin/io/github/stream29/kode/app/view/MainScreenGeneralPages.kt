package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.viewmodel.*
import io.github.stream29.kode.app.viewmodel.sessions.SessionsUiState
import io.github.stream29.kode.app.viewmodel.sessions.SessionsViewModel
import io.github.stream29.kode.app.viewmodel.tools.ToolsViewModel

@Composable
internal fun SessionsPage(
    state: MainViewModel,
    sessionsViewModel: SessionsViewModel,
    sessionUi: SessionUiState,
    ui: SessionsUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (sessionUi.currentSessionId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session dir: ${sessionUi.currentSessionWorkDir.ifBlank { "(unset)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SessionManagerContent(
            viewModel = sessionsViewModel,
            ui = ui,
            sessionUi = sessionUi,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun ModelsPage(state: MainViewModel, ui: AppUiState) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsContent(
            viewModel = state,
            ui = ui,
            modifier = Modifier.fillMaxSize(),
            selectedTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            tabs = tabs,
        )
    }
}

@Composable
internal fun SettingsPage(state: MainViewModel, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppSettingsContent(viewModel = state, ui = ui)
    }
}

@Composable
internal fun ToolsPage(viewModel: ToolsViewModel, ui: ToolsPageUiState) {
    val toolItems = listOf(
        ToolItem(key = "file", title = "File (read/list)", description = "Read and list files"),
        ToolItem(key = "file-edit", title = "File edit", description = "Edit files"),
        ToolItem(key = "web", title = "Web", description = "Fetch web content"),
        ToolItem(key = "search", title = "Search", description = "Glob and grep files"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Enable or disable tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val fileDisabled = ui.disabledTools.contains("file")
                toolItems.forEach { tool ->
                    val isEnabled = when (tool.key) {
                        "file-edit" -> !fileDisabled && !ui.disabledTools.contains("file-edit")
                        else -> !ui.disabledTools.contains(tool.key)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tool.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setToolEnabled(tool.key, enabled)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tool Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearToolLogs() }) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (ui.toolLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No tool logs yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = ui.toolLogs,
                            key = { index, _ -> index },
                        ) { _, log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewSessionDirDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a working directory for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Session directory") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun EditSessionDirDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Session Directory") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Session directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class ToolItem(
    val key: String,
    val title: String,
    val description: String,
)
