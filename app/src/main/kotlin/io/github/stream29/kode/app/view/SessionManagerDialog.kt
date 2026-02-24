package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.app.viewmodel.SessionsPageUiState
import io.github.stream29.kode.app.viewmodel.SessionUiState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionManagerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val ui by viewModel.sessionsPageUiState.collectAsStateWithLifecycle()
    val sessionUi by viewModel.sessionUiState.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Session Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            SessionManagerContent(
                viewModel = viewModel,
                ui = ui,
                sessionUi = sessionUi,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { viewModel.loadSessionList() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
public fun SessionManagerContent(
    viewModel: MainViewModel,
    ui: SessionsPageUiState,
    sessionUi: SessionUiState,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = ui.sessionSearchQuery,
                onValueChange = { viewModel.updateSessionSearchQuery(it) },
                label = { Text("Search") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilledTonalButton(
                onClick = { viewModel.importSession() }
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sessionUi.currentSessionWorkDir,
            onValueChange = {},
            label = { Text("Session directory") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { viewModel.openSessionDirDialog() },
            enabled = sessionUi.currentSessionId != null
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ui.sessionStatusFilter == SessionStatusFilter.ACTIVE,
                    onClick = { viewModel.updateSessionStatusFilter(SessionStatusFilter.ACTIVE) },
                    label = { Text("Active") }
                )
                FilterChip(
                    selected = ui.sessionStatusFilter == SessionStatusFilter.ARCHIVED,
                    onClick = { viewModel.updateSessionStatusFilter(SessionStatusFilter.ARCHIVED) },
                    label = { Text("Archived") }
                )
                FilterChip(
                    selected = ui.sessionStatusFilter == SessionStatusFilter.ALL,
                    onClick = { viewModel.updateSessionStatusFilter(SessionStatusFilter.ALL) },
                    label = { Text("All") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (ui.sessionSummaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "No sessions yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = ui.sessionSummaries,
                    key = { session -> session.id },
                ) { session ->
                    SessionCard(
                        session = session,
                        isCurrent = session.id == sessionUi.currentSessionId,
                        onSwitch = { viewModel.switchToSession(session.id) },
                        onFork = { viewModel.forkSession(session.id) },
                        onExport = { viewModel.exportSession(session.id) },
                        onRestore = { viewModel.restoreSession(session.id) },
                        onDelete = { viewModel.deleteSession(session.id) },
                        onArchive = { viewModel.archiveSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionSummary,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onFork: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    val statusColor = session.status.statusColor(colorScheme = MaterialTheme.colorScheme)
    val statusIcon = session.status.statusIcon()

    val dateStr = formatSessionTime(session)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )

                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    if (isCurrent) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Active") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                if (session.hasForks) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("🍴")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ID: ${session.id.take(8)}... • ${session.messageCount} messages • $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onSwitch,
                    enabled = !isCurrent,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = "Switch",
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onFork,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Fork",
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onExport,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export",
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (session.status.showRestoreAction()) {
                    FilledTonalIconButton(
                        onClick = onRestore,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Unarchive,
                            contentDescription = "Restore",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = onArchive,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Archive",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatSessionTime(session: SessionSummary): String {
    val dateFormat = session.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateFormat.date} ${dateFormat.hour.toString().padStart(2, '0')}:${
        dateFormat.minute.toString().padStart(2, '0')
    }"
}

private fun SessionStatus.statusColor(colorScheme: ColorScheme): Color {
    return when (this) {
        SessionStatus.ACTIVE -> colorScheme.primary
        SessionStatus.ARCHIVED -> colorScheme.tertiary
        SessionStatus.DELETED -> colorScheme.error
    }
}

private fun SessionStatus.statusIcon(): ImageVector {
    return when (this) {
        SessionStatus.ACTIVE -> Icons.Default.Circle
        SessionStatus.ARCHIVED -> Icons.Default.Inventory2
        SessionStatus.DELETED -> Icons.Default.Delete
    }
}

private fun SessionStatus.showRestoreAction(): Boolean {
    return this == SessionStatus.ARCHIVED
}
