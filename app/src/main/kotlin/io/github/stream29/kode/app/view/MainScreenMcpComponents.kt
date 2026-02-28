package io.github.stream29.kode.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.util.parseKeyValueLines
import io.github.stream29.kode.app.viewmodel.mcp.McpUiState
import io.github.stream29.kode.app.viewmodel.mcp.McpViewModel
import io.github.stream29.kode.config.api.McpServerConfig
import io.github.stream29.kode.config.api.McpTransportType
import io.github.stream29.kode.config.api.supportsBrowserOAuth
import io.github.stream29.kode.ui.bridge.mcp.McpHealthStatus
import io.github.stream29.kode.ui.bridge.mcp.McpTestResult
import io.github.stream29.kode.ui.bridge.mcp.McpTestStatus
import io.github.stream29.kode.ui.bridge.mcp.McpToolParameterSummary

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
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
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
                                    colors = ButtonDefaults.filledTonalButtonColors(
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

@Composable
private fun McpToolsDialog(
    serverName: String,
    result: McpTestResult?,
    onDismiss: () -> Unit,
) {
    if (result == null) {
        return
    }

    val tools = result.tools
    var expandedTools by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MCP Tools · $serverName") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (result.message.isNotBlank()) {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (tools.isEmpty()) {
                    Text(
                        text = "No tools returned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = tools,
                            key = { tool -> tool.name },
                        ) { tool ->
                            val expanded = expandedTools.contains(tool.name)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedTools = if (expanded) {
                                            expandedTools - tool.name
                                        } else {
                                            expandedTools + tool.name
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                    )
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (tool.description.isNotBlank()) {
                                        Text(
                                            text = tool.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    ToolParameterSection(
                                        title = "Required Parameters",
                                        parameters = tool.requiredParameters,
                                    )
                                    ToolParameterSection(
                                        title = "Optional Parameters",
                                        parameters = tool.optionalParameters,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ToolParameterSection(
    title: String,
    parameters: List<McpToolParameterSummary>,
) {
    if (parameters.isEmpty()) {
        return
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parameters.forEach { param ->
            Text(
                text = "${param.name} (${param.type})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (param.description.isNotBlank()) {
                Text(
                    text = param.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun McpHealthBadge(status: McpHealthStatus) {
    val color = when (status) {
        McpHealthStatus.Healthy -> MaterialTheme.colorScheme.primary
        McpHealthStatus.Unhealthy -> MaterialTheme.colorScheme.error
        McpHealthStatus.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        McpHealthStatus.Unknown -> MaterialTheme.colorScheme.outline
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, McpServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var transportType by remember { mutableStateOf(McpTransportType.Stdio) }
    var urlOrCommand by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = transportType.configValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transport") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(text = { Text("stdio") }, onClick = {
                            transportType = McpTransportType.Stdio
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text("http") }, onClick = {
                            transportType = McpTransportType.Http
                            expanded = false
                        })
                    }
                }

                OutlinedTextField(
                    value = urlOrCommand,
                    onValueChange = { urlOrCommand = it },
                    label = { Text(if (transportType.usesUrlTransport()) "URL" else "Command") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (transportType.usesCommandProcess()) {
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text("Args (space separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = env,
                        onValueChange = { env = it },
                        label = { Text("Env (KEY=VALUE, per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                    )
                } else {
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        label = { Text("Headers (Key:Value, per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                    )
                    OutlinedTextField(
                        value = auth,
                        onValueChange = { auth = it },
                        label = { Text("Auth (e.g. oauth)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val server = when (transportType) {
                        McpTransportType.Http,
                        McpTransportType.Sse,
                            -> {
                            if (transportType == McpTransportType.Http) {
                                McpServerConfig.Http(
                                    url = urlOrCommand.takeIf { it.isNotBlank() },
                                    headers = parseKeyValueLines(headers, separator = ":"),
                                    auth = auth.takeIf { it.isNotBlank() },
                                )
                            } else {
                                McpServerConfig.Sse(
                                    url = urlOrCommand.takeIf { it.isNotBlank() },
                                    headers = parseKeyValueLines(headers, separator = ":"),
                                    auth = auth.takeIf { it.isNotBlank() },
                                )
                            }
                        }

                        McpTransportType.Stdio,
                        McpTransportType.Unsupported,
                            -> {
                            McpServerConfig.Stdio(
                                command = urlOrCommand.takeIf { it.isNotBlank() },
                                args = args.split(" ").filter { it.isNotBlank() },
                                env = parseKeyValueLines(env, separator = "="),
                            )
                        }
                    }
                    if (name.isNotBlank()) {
                        onConfirm(name, server)
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
