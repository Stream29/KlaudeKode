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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.viewmodel.*
import io.github.stream29.kode.app.viewmodel.acp.AcpViewModel
import io.github.stream29.kode.app.viewmodel.info.InfoViewModel
import io.github.stream29.kode.app.viewmodel.terminal.TerminalViewModel
import io.github.stream29.kode.app.viewmodel.web.WebViewModel

@Composable
internal fun AcpPage(viewModel: AcpViewModel, ui: AcpPageUiState) {
    var portText by remember { mutableStateOf(ui.acpPort.toString()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ACP Server",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.acpHost,
                    onValueChange = { value -> viewModel.updateHost(value) },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input
                        val parsed = input.toIntOrNull()
                        if (parsed != null) {
                            viewModel.updatePort(parsed)
                        }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.startAcpServer() },
                        enabled = !ui.acpRunning,
                    ) {
                        Text("Start")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.stopAcpServer() },
                        enabled = ui.acpRunning,
                    ) {
                        Text("Stop")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (ui.acpLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No logs yet",
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
                            items = ui.acpLogs,
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
internal fun TerminalPage(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Shell", "KTS")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Terminal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            ShellPanel(viewModel = viewModel, ui = ui)
        } else {
            ScriptPanel(viewModel = viewModel, ui = ui)
        }
    }
}

@Composable
internal fun WebPage(viewModel: WebViewModel, ui: WebPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Web",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ui.webUrl,
                onValueChange = { value -> viewModel.updateWebUrl(value) },
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            FilledTonalButton(
                onClick = { viewModel.fetchWebContent() },
                enabled = !ui.webLoading,
            ) {
                Text("Fetch")
            }
            FilledTonalButton(
                onClick = { viewModel.openWebInBrowser() },
            ) {
                Text("Open")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                text = ui.webContent,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun InfoPage(viewModel: InfoViewModel, ui: InfoPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(label = "OS", value = System.getProperty("os.name") + " " + System.getProperty("os.version"))
                InfoRow(label = "Java", value = System.getProperty("java.version"))
                InfoRow(label = "User", value = System.getProperty("user.name"))
                InfoRow(
                    label = "Config",
                    value = io.github.stream29.kode.config.fs.FileSystemLocations.configFile.absolutePath,
                )
                InfoRow(
                    label = "Skills",
                    value = if (ui.skillsPreview.isEmpty()) "None" else ui.skillsPreview.size.toString(),
                )
                InfoRow(label = "Models", value = ui.modelsCount.toString())
                InfoRow(label = "Auth Providers", value = ui.authCount.toString())
                InfoRow(label = "MCP Servers", value = ui.mcpServerCount.toString())
                InfoRow(
                    label = "Disabled Tools",
                    value = if (ui.disabledTools.isEmpty()) "None" else ui.disabledTools.joinToString(", "),
                )
                InfoRow(label = "ACP Running", value = ui.acpRunning.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.exportLogs() }) {
                androidx.compose.material3.Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Logs")
            }

            FilledTonalButton(onClick = { viewModel.refreshSkillsPreview() }) {
                androidx.compose.material3.Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (ui.skillsPreview.isEmpty()) {
                    Text(
                        text = "No skills discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ui.skillsPreview.take(20).forEach { skill ->
                        Text(text = "• $skill", style = MaterialTheme.typography.bodySmall)
                    }
                    if (ui.skillsPreview.size > 20) {
                        Text(
                            text = "…and ${ui.skillsPreview.size - 20} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ShellPanel(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.terminalCommand,
            onValueChange = { value -> viewModel.updateTerminalCommand(value) },
            label = { Text("Shell command") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { viewModel.runShellCommand() },
                enabled = !ui.terminalRunning,
            ) {
                Text("Run")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                text = ui.terminalOutput,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScriptPanel(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.scriptContent,
            onValueChange = { value -> viewModel.updateScriptContent(value) },
            label = { Text("KTS Script") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            singleLine = false,
            maxLines = 12,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { viewModel.runScript() },
            enabled = !ui.scriptRunning,
        ) {
            Text("Run Script")
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = ui.scriptOutput,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
