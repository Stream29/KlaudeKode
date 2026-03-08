package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.viewmodel.mcp.McpUiState
import io.github.stream29.kode.app.viewmodel.mcp.McpViewModel
import io.github.stream29.kode.config.api.supportsBrowserOAuth
import io.github.stream29.kode.ui.bridge.mcp.McpHealthStatus
import io.github.stream29.kode.ui.bridge.mcp.McpTestResult
import io.github.stream29.kode.ui.bridge.mcp.McpTestStatus

@Composable
internal fun McpPage(viewModel: McpViewModel, ui: McpUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var timeoutText by remember { mutableStateOf(ui.mcpToolTimeoutMs.toString()) }
    var pendingDialogServer by remember { mutableStateOf<String?>(null) }
    var toolDialogResult by remember { mutableStateOf<McpTestResult?>(null) }
    var toolDialogTitle by remember { mutableStateOf("") }
    val closeAddDialog = {
        showAddDialog = false
    }
    val dismissToolsDialog = {
        toolDialogResult = null
    }

    LaunchedEffect(ui.mcpTestResults, pendingDialogServer) {
        val target = pendingDialogServer ?: return@LaunchedEffect
        val result = ui.mcpTestResults[target] ?: return@LaunchedEffect
        if (result.status == McpTestStatus.Success) {
            toolDialogTitle = target
            toolDialogResult = result
        }
        pendingDialogServer = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MCP Servers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            FilledTonalButton(onClick = { showAddDialog = true }) {
                androidx.compose.material3.Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { input ->
                    timeoutText = input
                    val parsed = input.toIntOrNull()
                    if (parsed != null) {
                        viewModel.updateMcpToolTimeoutMs(parsed)
                    }
                },
                label = { Text("Tool call timeout (ms)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (ui.mcpServers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No MCP servers configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = ui.mcpServers.entries.toList(),
                    key = { entry -> entry.key },
                ) { entry ->
                    val name = entry.key
                    val server = entry.value
                    val inFlight = ui.mcpTestsInFlight.contains(name)
                    val health = ui.mcpHealthResults[name]
                    val healthStatus = health?.status ?: McpHealthStatus.Unknown
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                McpHealthBadge(status = healthStatus)
                            }
                            Text(
                                text = "Transport: ${server.transport}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (server.url != null) {
                                Text(
                                    text = "URL: ${server.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (server.command != null) {
                                Text(
                                    text = "Command: ${server.command} ${server.args.joinToString(" ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            if (healthStatus == McpHealthStatus.Unhealthy) {
                                val message = health?.message.orEmpty()
                                if (message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        pendingDialogServer = name
                                        toolDialogResult = null
                                        viewModel.clearMcpTestResult(name)
                                        viewModel.testMcpServer(name)
                                    },
                                    enabled = !inFlight,
                                ) {
                                    Text(if (inFlight) "Testing..." else "Test")
                                }
                                if (server.supportsBrowserOAuth()) {
                                    FilledTonalButton(onClick = { viewModel.authMcpServer(name) }) {
                                        Text("Auth")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.removeMcpServer(name) },
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (toolDialogResult != null) {
        McpToolsDialog(
            serverName = toolDialogTitle,
            result = toolDialogResult,
            onDismiss = dismissToolsDialog,
        )
    }

    if (showAddDialog) {
        McpServerDialog(
            onDismiss = closeAddDialog,
            onConfirm = { name, config ->
                viewModel.addMcpServer(name, config)
                closeAddDialog()
            },
        )
    }
}
