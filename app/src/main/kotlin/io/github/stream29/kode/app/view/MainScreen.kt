package io.github.stream29.kode.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.model.MessageItem
import io.github.stream29.kode.app.view.components.MessageBubble
import io.github.stream29.kode.app.view.components.SystemMessage
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.ui.core.ToolApprovalDecision

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
public fun MainScreen(state: MainViewModel) {
    val colorScheme = if (state.uiTheme == "light") {
        lightColorScheme()
    } else {
        darkColorScheme(
            primary = md_theme_dark_primary,
            onPrimary = md_theme_dark_onPrimary,
            primaryContainer = md_theme_dark_primaryContainer,
            onPrimaryContainer = md_theme_dark_onPrimaryContainer,
            secondary = md_theme_dark_secondary,
            onSecondary = md_theme_dark_onSecondary,
            secondaryContainer = md_theme_dark_secondaryContainer,
            onSecondaryContainer = md_theme_dark_onSecondaryContainer,
            tertiary = md_theme_dark_tertiary,
            onTertiary = md_theme_dark_onTertiary,
            tertiaryContainer = md_theme_dark_tertiaryContainer,
            onTertiaryContainer = md_theme_dark_onTertiaryContainer,
            error = md_theme_dark_error,
            errorContainer = md_theme_dark_errorContainer,
            onError = md_theme_dark_onError,
            onErrorContainer = md_theme_dark_onErrorContainer,
            background = md_theme_dark_background,
            onBackground = md_theme_dark_onBackground,
            surface = md_theme_dark_surface,
            onSurface = md_theme_dark_onSurface,
            surfaceVariant = md_theme_dark_surfaceVariant,
            onSurfaceVariant = md_theme_dark_onSurfaceVariant,
            outline = md_theme_dark_outline,
            inverseOnSurface = md_theme_dark_inverseOnSurface,
            inverseSurface = md_theme_dark_inverseSurface,
            inversePrimary = md_theme_dark_inversePrimary,
            surfaceTint = md_theme_dark_surfaceTint,
            outlineVariant = md_theme_dark_outlineVariant,
            scrim = md_theme_dark_scrim,
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        LaunchedEffect(state.currentPage) {
            if (state.currentPage == AppPage.Sessions) {
                state.loadSessionList()
            }
        }

        if (state.showConfigEditor) {
            ConfigEditorDialog(
                viewModel = state,
                onDismiss = { state.showConfigEditor = false }
            )
        }

        Scaffold(
            topBar = {
                AppTopBar(state = state)
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AppNavigationRail(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )

                VerticalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    when (state.currentPage) {
                        AppPage.Chat -> ChatPage(state = state)
                        AppPage.Sessions -> SessionsPage(state = state)
                        AppPage.Settings -> SettingsPage(state = state)
                        AppPage.Tools -> ToolsPage(state = state)
                        AppPage.Mcp -> McpPage(state = state)
                        AppPage.Acp -> AcpPage(state = state)
                        AppPage.Terminal -> TerminalPage(state = state)
                        AppPage.Web -> WebPage(state = state)
                        AppPage.Info -> InfoPage(state = state)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: MainViewModel) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Kode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.currentPage.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = { state.currentPage = AppPage.Sessions }
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Sessions"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = { state.currentPage = AppPage.Settings }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun AppNavigationRail(
    state: MainViewModel,
    modifier: Modifier
) {
    val pages = listOf(
        AppPage.Chat,
        AppPage.Sessions,
        AppPage.Settings,
        AppPage.Tools,
        AppPage.Mcp,
        AppPage.Acp,
        AppPage.Terminal,
        AppPage.Web,
        AppPage.Info,
    )

    NavigationRail(modifier = modifier) {
        pages.forEach { page ->
            NavigationRailItem(
                selected = state.currentPage == page,
                onClick = { state.currentPage = page },
                icon = {
                    Icon(imageVector = page.icon, contentDescription = page.title)
                },
                label = { Text(text = page.title) }
            )
        }
    }
}

@Composable
private fun ChatPage(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        ApprovalControls(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        if (state.pendingApprovals.isNotEmpty()) {
            ApprovalPanel(state = state)
            Spacer(modifier = Modifier.height(12.dp))
        }

        SessionControls(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        MessageList(
            messages = state.messages,
            streamingMessage = state.streamingMessage,
            onForkFromMessage = { index ->
                state.forkFromMessage(index)
            },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        InputSection(state = state)
    }
}

@Composable
private fun SessionsPage(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        SessionManagerContent(
            viewModel = state,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SettingsPage(state: MainViewModel) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsContent(
            viewModel = state,
            modifier = Modifier.fillMaxSize(),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            tabs = tabs
        )
    }
}

@Composable
private fun ToolsPage(state: MainViewModel) {
    val toolItems = listOf(
        ToolItem(key = "file", title = "File (read/list)", description = "Read and list files"),
        ToolItem(key = "file-edit", title = "File edit", description = "Edit files"),
        ToolItem(key = "communication", title = "Communication", description = "Ask for input / messages"),
        ToolItem(key = "shell", title = "Shell", description = "Execute shell commands"),
        ToolItem(key = "web", title = "Web", description = "Fetch web content"),
        ToolItem(key = "search", title = "Search", description = "Glob and grep files"),
        ToolItem(key = "todo", title = "Todo", description = "Task lists"),
        ToolItem(key = "think", title = "Think", description = "Planning/analysis tool"),
        ToolItem(key = "task", title = "Task", description = "Parallel sub-agents"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Enable or disable tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                val fileDisabled = state.disabledTools.contains("file")
                toolItems.forEach { tool ->
                    val isEnabled = when (tool.key) {
                        "file-edit" -> !fileDisabled && !state.disabledTools.contains("file-edit")
                        else -> !state.disabledTools.contains(tool.key)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tool.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                state.setToolEnabled(tool.key, enabled)
                            }
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tool Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { state.clearToolLogs() }) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.toolLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No tool logs yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.toolLogs.size) { index ->
                            Text(
                                text = state.toolLogs[index],
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpPage(state: MainViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var timeoutText by remember { mutableStateOf(state.mcpToolTimeoutMs.toString()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MCP Servers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { input ->
                    timeoutText = input
                    val parsed = input.toIntOrNull()
                    if (parsed != null) {
                        state.mcpToolTimeoutMs = parsed
                    }
                },
                label = { Text("Tool call timeout (ms)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { state.saveMcpSettings() }) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.mcpServers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No MCP servers configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.mcpServers.entries.toList()) { entry ->
                    val name = entry.key
                    val server = entry.value
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Transport: ${server.transport}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (server.url != null) {
                                Text(
                                    text = "URL: ${server.url}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (server.command != null) {
                                Text(
                                    text = "Command: ${server.command} ${server.args.joinToString(" ")}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(onClick = { state.testMcpServer(name) }) {
                                    Text("Test")
                                }
                                if (server.transport == "http" && server.auth == "oauth") {
                                    FilledTonalButton(onClick = { state.authMcpServer(name) }) {
                                        Text("Auth")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { state.removeMcpServer(name) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
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

    if (showAddDialog) {
        McpServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, config ->
                state.addMcpServer(name, config)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AcpPage(state: MainViewModel) {
    var portText by remember { mutableStateOf(state.acpPort.toString()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ACP Server",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.acpHost,
                    onValueChange = { state.acpHost = it },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input
                        val parsed = input.toIntOrNull()
                        if (parsed != null) {
                            state.acpPort = parsed
                        }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { state.startAcpServer() },
                        enabled = !state.acpRunning
                    ) {
                        Text("Start")
                    }
                    FilledTonalButton(
                        onClick = { state.stopAcpServer() },
                        enabled = state.acpRunning
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
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.acpLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No logs yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.acpLogs.size) { index ->
                            Text(
                                text = state.acpLogs[index],
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalPage(state: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Shell", "KTS")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Terminal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            ShellPanel(state = state)
        } else {
            ScriptPanel(state = state)
        }
    }
}

@Composable
private fun WebPage(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Web",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.webUrl,
                onValueChange = { state.webUrl = it },
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilledTonalButton(
                onClick = { state.fetchWebContent() },
                enabled = !state.webLoading
            ) {
                Text("Fetch")
            }
            FilledTonalButton(
                onClick = { state.openWebInBrowser() }
            ) {
                Text("Open")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                text = state.webContent,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoPage(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(label = "OS", value = System.getProperty("os.name") + " " + System.getProperty("os.version"))
                InfoRow(label = "Java", value = System.getProperty("java.version"))
                InfoRow(label = "User", value = System.getProperty("user.name"))
                InfoRow(label = "Config", value = io.github.stream29.kode.config.fs.FileSystemLocations.configFile.absolutePath)
                InfoRow(label = "Agent spec", value = state.agentSpecPath.ifBlank { "Not found" })
                InfoRow(label = "Skills", value = if (state.skillsPreview.isEmpty()) "None" else state.skillsPreview.size.toString())
                InfoRow(label = "Models", value = state.models.size.toString())
                InfoRow(label = "Auth Providers", value = state.auths.size.toString())
                InfoRow(label = "MCP Servers", value = state.mcpServers.size.toString())
                InfoRow(label = "Disabled Tools", value = if (state.disabledTools.isEmpty()) "None" else state.disabledTools.joinToString(", "))
                InfoRow(label = "ACP Running", value = state.acpRunning.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { state.exportLogs() }) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Logs")
            }

            FilledTonalButton(onClick = { state.refreshAgentAndSkillsPreview() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AGENTS.md",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.agentSpecPreview.isBlank()) {
                    Text(
                        text = "No AGENTS.md found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = state.agentSpecPreview.take(800),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.skillsPreview.isEmpty()) {
                    Text(
                        text = "No skills discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.skillsPreview.take(20).forEach { skill ->
                        Text(text = "• $skill", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.skillsPreview.size > 20) {
                        Text(
                            text = "…and ${state.skillsPreview.size - 20} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ShellPanel(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.terminalCommand,
            onValueChange = { state.terminalCommand = it },
            label = { Text("Shell command") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { state.runShellCommand() },
                enabled = !state.terminalRunning
            ) {
                Text("Run")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                text = state.terminalOutput,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScriptPanel(state: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.scriptContent,
            onValueChange = { state.scriptContent = it },
            label = { Text("KTS Script") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            singleLine = false,
            maxLines = 12
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { state.runScript() },
            enabled = !state.scriptRunning
        ) {
            Text("Run Script")
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = state.scriptOutput,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, io.github.stream29.kode.config.api.McpServerConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("stdio") }
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
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = transport,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transport") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("stdio") }, onClick = {
                            transport = "stdio"
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text("http") }, onClick = {
                            transport = "http"
                            expanded = false
                        })
                    }
                }

                OutlinedTextField(
                    value = urlOrCommand,
                    onValueChange = { urlOrCommand = it },
                    label = { Text(if (transport == "http") "URL" else "Command") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (transport == "stdio") {
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text("Args (space separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = env,
                        onValueChange = { env = it },
                        label = { Text("Env (KEY=VALUE, per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                } else {
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        label = { Text("Headers (Key:Value, per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = auth,
                        onValueChange = { auth = it },
                        label = { Text("Auth (e.g. oauth)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val server = if (transport == "http") {
                        io.github.stream29.kode.config.api.McpServerConfig(
                            transport = "http",
                            url = urlOrCommand.takeIf { it.isNotBlank() },
                            headers = parseKeyValueLines(headers, separator = ":"),
                            auth = auth.takeIf { it.isNotBlank() }
                        )
                    } else {
                        io.github.stream29.kode.config.api.McpServerConfig(
                            transport = "stdio",
                            command = urlOrCommand.takeIf { it.isNotBlank() },
                            args = args.split(" ").filter { it.isNotBlank() },
                            env = parseKeyValueLines(env, separator = "=")
                        )
                    }
                    if (name.isNotBlank()) {
                        onConfirm(name, server)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseKeyValueLines(input: String, separator: String): Map<String, String> {
    if (input.isBlank()) {
        return emptyMap()
    }
    return input.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains(separator) }
        .associate {
            val parts = it.split(separator, limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}

private data class ToolItem(
    val key: String,
    val title: String,
    val description: String
)

@Composable
private fun PlaceholderPage(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "$title page is under construction",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(state: MainViewModel) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.taskInput,
                onValueChange = { state.taskInput = it },
                label = {
                    Text(
                        if (state.isWaitingForInput) "Enter response..." 
                        else "What would you like me to do?"
                    )
                },
                placeholder = {
                    Text(
                        if (state.isWaitingForInput) "Type your response..."
                        else "e.g., Read and explain the README file"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && 
                            keyEvent.isCtrlPressed && 
                            keyEvent.key == Key.Enter) {
                            if (state.isWaitingForInput) {
                                state.submitInput()
                            } else {
                                state.runTask()
                            }
                            true
                        } else {
                            false
                        }
                    },
                enabled = !state.isRunning || state.isWaitingForInput,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Icon(
                        imageVector = if (state.isWaitingForInput) 
                            Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            val isInputValid = state.taskInput.isNotBlank()
            val canClick = if (state.isWaitingForInput) 
                isInputValid 
            else 
                (!state.isRunning && isInputValid)

            FilledIconButton(
                onClick = {
                    if (state.isWaitingForInput) {
                        state.submitInput()
                    } else {
                        state.runTask()
                    }
                },
                enabled = canClick,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canClick) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canClick) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = when {
                        state.isWaitingForInput -> Icons.Default.Check
                        state.isRunning -> Icons.Default.HourglassEmpty
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        state.isWaitingForInput -> "Send"
                        state.isRunning -> "Running"
                        else -> "Run"
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionControls(state: MainViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { state.createNewSession() },
            enabled = !state.isRunning,
            label = { Text("New Session") },
            leadingIcon = {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        
        AssistChip(
            onClick = { state.continueCurrentSession() },
            enabled = !state.isRunning && state.currentSessionId != null,
            label = { Text("Continue") },
            leadingIcon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        
        state.currentSessionId?.let { sessionId ->
            SuggestionChip(
                onClick = { },
                label = { 
                    Text(
                        "Session: ${sessionId.take(8)}...",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        AgentQuickSwitch(state = state)
        ModelQuickSwitch(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentQuickSwitch(state: MainViewModel) {
    val profiles = state.agentProfiles
    var expanded by remember { mutableStateOf(false) }
    val activeName = state.activeAgentProfileName
    val activeProfile = profiles.firstOrNull { it.name == activeName }
    val displayName = activeProfile?.name ?: if (activeName.isBlank()) "build" else activeName

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "Agent: ${displayName}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Agent") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .widthIn(min = 180.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(profile.name)
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        state.selectAgentProfile(profile.name, persist = true)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelQuickSwitch(state: MainViewModel) {
    val models = state.models
    val auths = state.auths
    var expanded by remember { mutableStateOf(false) }

    if (models.isEmpty()) {
        AssistChip(
            onClick = { state.currentPage = AppPage.Settings },
            label = { Text("Model: Not configured") },
            leadingIcon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        return
    }

    val activeModel = models.find { it.id == state.activeModelId } ?: models.first()
    val displayName = getModelDisplayName(activeModel, auths)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .widthIn(min = 220.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(getModelDisplayName(model, auths))
                            Text(
                                "ID: ${model.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        state.switchModel(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getModelDisplayName(
    model: io.github.stream29.kode.config.api.LlmModelConfig,
    auths: List<io.github.stream29.kode.config.api.LlmAuthConfig>
): String {
    val auth = auths.find { it.id == model.authId }
    val provider = auth?.provider ?: "Unknown"
    val name = model.displayName ?: model.model
    return "$provider - $name"
}

@Composable
private fun MessageList(
    messages: List<MessageItem>,
    streamingMessage: MessageItem?,
    onForkFromMessage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMessages = if (streamingMessage == null) {
        messages
    } else {
        messages + streamingMessage
    }
    val listState = rememberLazyListState()
    
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }
    
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        if (displayMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Start a conversation by typing below 👇",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayMessages) { index, message ->
                    when (message.role) {
                        MessageRole.SYSTEM -> {
                            SystemMessage(content = message.content)
                        }
                        else -> {
                            MessageBubble(
                                message = message,
                                isCurrentUser = message.role == MessageRole.USER,
                                onForkFromHere = { onForkFromMessage(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalControls(state: MainViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Tool Approvals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Require confirmation before running tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "YOLO",
                    style = MaterialTheme.typography.labelLarge
                )
                Switch(
                    checked = state.yoloEnabled,
                    onCheckedChange = { state.yoloEnabled = it }
                )
            }
        }
    }
}

@Composable
private fun ApprovalPanel(state: MainViewModel) {
    val request = state.pendingApprovals.firstOrNull() ?: return

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Approval required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Tool: ${request.toolName}",
                style = MaterialTheme.typography.bodyMedium
            )

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = request.arguments,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        state.approvePendingRequest(
                            requestId = request.id,
                            decision = ToolApprovalDecision.Approve
                        )
                    }
                ) {
                    Text("Approve")
                }
                FilledTonalButton(
                    onClick = {
                        state.approvePendingRequest(
                            requestId = request.id,
                            decision = ToolApprovalDecision.ApproveForSession
                        )
                    }
                ) {
                    Text("Approve for Session")
                }
                FilledTonalButton(
                    onClick = {
                        state.approvePendingRequest(
                            requestId = request.id,
                            decision = ToolApprovalDecision.Reject
                        )
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

// Material 3 Color Scheme - Expressive Dark Theme
private val md_theme_dark_primary = Color(0xFFD0BCFF)
private val md_theme_dark_onPrimary = Color(0xFF381E72)
private val md_theme_dark_primaryContainer = Color(0xFF4F378B)
private val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
private val md_theme_dark_secondary = Color(0xFFCCC2DC)
private val md_theme_dark_onSecondary = Color(0xFF332D41)
private val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
private val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
private val md_theme_dark_tertiary = Color(0xFFEFB8C8)
private val md_theme_dark_onTertiary = Color(0xFF492532)
private val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
private val md_theme_dark_error = Color(0xFFF2B8B5)
private val md_theme_dark_errorContainer = Color(0xFF8C1D18)
private val md_theme_dark_onError = Color(0xFF601410)
private val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
private val md_theme_dark_background = Color(0xFF1C1B1F)
private val md_theme_dark_onBackground = Color(0xFFE6E1E5)
private val md_theme_dark_surface = Color(0xFF1C1B1F)
private val md_theme_dark_onSurface = Color(0xFFE6E1E5)
private val md_theme_dark_surfaceVariant = Color(0xFF49454F)
private val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
private val md_theme_dark_outline = Color(0xFF938F99)
private val md_theme_dark_inverseOnSurface = Color(0xFF1C1B1F)
private val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
private val md_theme_dark_inversePrimary = Color(0xFF6750A4)
private val md_theme_dark_surfaceTint = Color(0xFFD0BCFF)
private val md_theme_dark_outlineVariant = Color(0xFF49454F)
private val md_theme_dark_scrim = Color(0xFF000000)
