package io.github.stream29.kode.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.util.parseKeyValueLines
import io.github.stream29.kode.config.api.McpServerConfig
import io.github.stream29.kode.config.api.McpTransportType
import io.github.stream29.kode.ui.core.mcp.McpHealthStatus
import io.github.stream29.kode.ui.core.mcp.McpTestResult
import io.github.stream29.kode.ui.core.mcp.McpToolParameterSummary

@Composable
internal fun McpToolsDialog(
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
internal fun McpHealthBadge(status: McpHealthStatus) {
    val color = when (status) {
        McpHealthStatus.Healthy -> MaterialTheme.colorScheme.primary
        McpHealthStatus.Unhealthy -> MaterialTheme.colorScheme.error
        McpHealthStatus.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        McpHealthStatus.Unknown -> MaterialTheme.colorScheme.outline
    }

    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
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
internal fun McpServerDialog(
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
            androidx.compose.material3.Button(
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
