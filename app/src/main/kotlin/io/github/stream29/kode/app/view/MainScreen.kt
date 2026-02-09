package io.github.stream29.kode.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stream29.kode.app.model.extractToolCallArgumentsText
import io.github.stream29.kode.app.model.extractToolCallPrimaryTextArg
import io.github.stream29.kode.app.model.extractToolName
import io.github.stream29.kode.app.model.extractToolResultText
import io.github.stream29.kode.app.model.isAssistantToolPlan
import io.github.stream29.kode.app.model.isAwaitUserInputToolCall
import io.github.stream29.kode.app.model.isAwaitUserInputToolResult
import io.github.stream29.kode.app.model.isSayToUserToolCall
import io.github.stream29.kode.app.model.isSayToUserToolResult
import io.github.stream29.kode.app.model.isUiError
import io.github.stream29.kode.app.model.isUiToolCallLike
import io.github.stream29.kode.app.view.components.MessageBubble
import io.github.stream29.kode.app.view.components.SystemMessage
import io.github.stream29.kode.app.viewmodel.AppUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.app.viewmodel.SessionUiState
import io.github.stream29.kode.session.core.model.MessageRole
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.ui.core.ToolApprovalDecision

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
public fun MainScreen(state: MainViewModel) {
    val ui by state.appUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val nextToast = ui.toasts.firstOrNull()
    LaunchedEffect(nextToast?.id) {
        val toast = nextToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = toast.message,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        state.consumeToast(toast.id)
    }

    val colorScheme = if (ui.uiTheme == "light") {
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
        LaunchedEffect(ui.currentPage) {
            if (ui.currentPage == AppPage.Sessions) {
                state.loadSessionList()
            }
        }

        if (ui.showConfigEditor) {
            ConfigEditorDialog(
                viewModel = state,
                onDismiss = { state.showConfigEditor = false }
            )
        }

        Scaffold(
            topBar = {
                AppTopBar(state = state, ui = ui)
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
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
                    ui = ui,
                    modifier = Modifier.fillMaxHeight()
                )

                VerticalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    when (ui.currentPage) {
                        AppPage.Chat -> ChatPage(state = state, sessionUi = ui.session, ui = ui)
                        AppPage.Sessions -> SessionsPage(state = state, sessionUi = ui.session, ui = ui)
                        AppPage.Models -> ModelsPage(state = state, ui = ui)
                        AppPage.Settings -> SettingsPage(state = state, ui = ui)
                        AppPage.Tools -> ToolsPage(state = state, ui = ui)
                        AppPage.Mcp -> McpPage(state = state, ui = ui)
                        AppPage.Acp -> AcpPage(state = state, ui = ui)
                        AppPage.Terminal -> TerminalPage(state = state, ui = ui)
                        AppPage.Web -> WebPage(state = state, ui = ui)
                        AppPage.Info -> InfoPage(state = state, ui = ui)
                    }
                }
            }
        }

        if (ui.session.showNewSessionDialog) {
            NewSessionDirDialog(
                value = ui.session.newSessionDirInput,
                onValueChange = { state.newSessionDirInput = it },
                onConfirm = { state.confirmNewSessionDir() },
                onDismiss = { state.cancelNewSessionDir() }
            )
        }

        if (ui.session.showSessionDirDialog) {
            EditSessionDirDialog(
                value = ui.session.sessionDirDraft,
                onValueChange = { state.sessionDirDraft = it },
                onConfirm = { state.confirmSessionDirDialog() },
                onDismiss = { state.cancelSessionDirDialog() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: MainViewModel, ui: AppUiState) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Kode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ui.currentPage.title,
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
                onClick = { state.currentPage = AppPage.Models }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Models"
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
    ui: AppUiState,
    modifier: Modifier
) {
    val pages = listOf(
        AppPage.Chat,
        AppPage.Sessions,
        AppPage.Models,
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
                selected = ui.currentPage == page,
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
private fun ChatPage(state: MainViewModel, sessionUi: SessionUiState, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        ApprovalControls(state = state, ui = ui)

        Spacer(modifier = Modifier.height(8.dp))

        if (ui.pendingApprovals.isNotEmpty()) {
            ApprovalPanel(state = state, ui = ui)
            Spacer(modifier = Modifier.height(12.dp))
        }

        SessionControls(state = state, sessionUi = sessionUi, ui = ui)

        Spacer(modifier = Modifier.height(8.dp))

        MessageList(
            messages = sessionUi.messages,
            onForkFromMessage = { index ->
                state.forkFromMessage(index)
            },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        InputSection(state = state, sessionUi = sessionUi)
    }
}

@Composable
private fun SessionsPage(state: MainViewModel, sessionUi: SessionUiState, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (sessionUi.currentSessionId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session dir: ${sessionUi.currentSessionWorkDir.ifBlank { "(unset)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { state.openSessionDirDialog() }) {
                    Text("Edit")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SessionManagerContent(
            viewModel = state,
            ui = ui,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ModelsPage(state: MainViewModel, ui: AppUiState) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsContent(
            viewModel = state,
            ui = ui,
            modifier = Modifier.fillMaxSize(),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            tabs = tabs
        )
    }
}

@Composable
private fun SettingsPage(state: MainViewModel, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppSettingsContent(viewModel = state, ui = ui)
    }
}

@Composable
private fun ToolsPage(state: MainViewModel, ui: AppUiState) {
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

                val fileDisabled = ui.disabledTools.contains("file")
                toolItems.forEach { tool ->
                    val isEnabled = when (tool.key) {
                        "file-edit" -> !fileDisabled && !ui.disabledTools.contains("file-edit")
                        else -> !ui.disabledTools.contains(tool.key)
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

                if (ui.toolLogs.isEmpty()) {
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
                        items(ui.toolLogs.size) { index ->
                            Text(
                                text = ui.toolLogs[index],
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
@Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
private fun McpPage(state: MainViewModel, ui: AppUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var timeoutText by remember { mutableStateOf(ui.mcpToolTimeoutMs.toString()) }
    var pendingDialogServer by remember { mutableStateOf<String?>(null) }
    var toolDialogResult by remember { mutableStateOf<MainViewModel.McpTestResult?>(null) }
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
        if (result.status == MainViewModel.McpTestStatus.Success) {
            toolDialogTitle = target
            toolDialogResult = result
        }
        pendingDialogServer = null
    }

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
            
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (ui.mcpServers.isEmpty()) {
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
                items(ui.mcpServers.entries.toList()) { entry ->
                    val name = entry.key
                    val server = entry.value
                    val inFlight = ui.mcpTestsInFlight.contains(name)
                    val health = ui.mcpHealthResults[name]
                    val healthStatus = health?.status ?: MainViewModel.McpHealthStatus.Unknown
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                McpHealthBadge(status = healthStatus)
                            }
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

                            if (healthStatus == MainViewModel.McpHealthStatus.Unhealthy) {
                                val message = health?.message.orEmpty()
                                if (message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        pendingDialogServer = name
                                        toolDialogResult = null
                                        state.clearMcpTestResult(name)
                                        state.testMcpServer(name)
                                    },
                                    enabled = !inFlight
                                ) {
                                    Text(if (inFlight) "Testing..." else "Test")
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
                state.addMcpServer(name, config)
                closeAddDialog()
            }
        )
    }
}

@Composable
private fun NewSessionDirDialog(
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Session directory") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
        }
    )
}

@Composable
private fun EditSessionDirDialog(
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
                singleLine = true
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
        }
    )
}

@Composable
private fun McpToolsDialog(
    serverName: String,
    result: MainViewModel.McpTestResult?,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (tools.isEmpty()) {
                    Text(
                        text = "No tools returned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tools) { tool ->
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
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (tool.description.isNotBlank()) {
                                        Text(
                                            text = tool.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    ToolParameterSection(
                                        title = "Required Parameters",
                                        parameters = tool.requiredParameters
                                    )
                                    ToolParameterSection(
                                        title = "Optional Parameters",
                                        parameters = tool.optionalParameters
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
        }
    )
}

@Composable
private fun ToolParameterSection(
    title: String,
    parameters: List<MainViewModel.McpToolParameterSummary>,
) {
    if (parameters.isEmpty()) {
        return
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parameters.forEach { param ->
            Text(
                text = "${param.name} (${param.type})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (param.description.isNotBlank()) {
                Text(
                    text = param.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun McpHealthBadge(status: MainViewModel.McpHealthStatus) {
    val (label, color) = when (status) {
        MainViewModel.McpHealthStatus.Healthy -> "Healthy" to MaterialTheme.colorScheme.primary
        MainViewModel.McpHealthStatus.Unhealthy -> "Unhealthy" to MaterialTheme.colorScheme.error
        MainViewModel.McpHealthStatus.Checking -> "Checking" to MaterialTheme.colorScheme.onSurfaceVariant
        MainViewModel.McpHealthStatus.Unknown -> "Unknown" to MaterialTheme.colorScheme.outline
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun AcpPage(state: MainViewModel, ui: AppUiState) {
    var portText by remember { mutableStateOf(ui.acpPort.toString()) }
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
                    value = ui.acpHost,
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
                        enabled = !ui.acpRunning
                    ) {
                        Text("Start")
                    }
                    FilledTonalButton(
                        onClick = { state.stopAcpServer() },
                        enabled = ui.acpRunning
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
                if (ui.acpLogs.isEmpty()) {
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
                        items(ui.acpLogs.size) { index ->
                            Text(
                                text = ui.acpLogs[index],
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
private fun TerminalPage(state: MainViewModel, ui: AppUiState) {
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
            ShellPanel(state = state, ui = ui)
        } else {
            ScriptPanel(state = state, ui = ui)
        }
    }
}

@Composable
private fun WebPage(state: MainViewModel, ui: AppUiState) {
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
                value = ui.webUrl,
                onValueChange = { state.webUrl = it },
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilledTonalButton(
                onClick = { state.fetchWebContent() },
                enabled = !ui.webLoading
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
                text = ui.webContent,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoPage(state: MainViewModel, ui: AppUiState) {
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
                InfoRow(label = "Agent spec", value = ui.agentSpecPath.ifBlank { "Not found" })
                InfoRow(label = "Skills", value = if (ui.skillsPreview.isEmpty()) "None" else ui.skillsPreview.size.toString())
                InfoRow(label = "Models", value = ui.models.size.toString())
                InfoRow(label = "Auth Providers", value = ui.auths.size.toString())
                InfoRow(label = "MCP Servers", value = ui.mcpServers.size.toString())
                InfoRow(label = "Disabled Tools", value = if (ui.disabledTools.isEmpty()) "None" else ui.disabledTools.joinToString(", "))
                InfoRow(label = "ACP Running", value = ui.acpRunning.toString())
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
                if (ui.agentSpecPreview.isBlank()) {
                    Text(
                        text = "No AGENTS.md found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = ui.agentSpecPreview.take(800),
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
                if (ui.skillsPreview.isEmpty()) {
                    Text(
                        text = "No skills discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ui.skillsPreview.take(20).forEach { skill ->
                        Text(text = "• $skill", style = MaterialTheme.typography.bodySmall)
                    }
                    if (ui.skillsPreview.size > 20) {
                        Text(
                            text = "…and ${ui.skillsPreview.size - 20} more",
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
private fun ShellPanel(state: MainViewModel, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.terminalCommand,
            onValueChange = { state.terminalCommand = it },
            label = { Text("Shell command") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { state.runShellCommand() },
                enabled = !ui.terminalRunning
            ) {
                Text("Run")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                text = ui.terminalOutput,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScriptPanel(state: MainViewModel, ui: AppUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.scriptContent,
            onValueChange = { state.scriptContent = it },
            label = { Text("KTS Script") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            singleLine = false,
            maxLines = 12
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { state.runScript() },
            enabled = !ui.scriptRunning
        ) {
            Text("Run Script")
        }
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = ui.scriptOutput,
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(state: MainViewModel, sessionUi: SessionUiState) {
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
                value = sessionUi.taskInput,
                onValueChange = { state.taskInput = it },
                label = {
                    Text(
                        if (sessionUi.isWaitingForInput) "Enter response..." 
                        else "What would you like me to do?"
                    )
                },
                placeholder = {
                    Text(
                        if (sessionUi.isWaitingForInput) "Type your response..."
                        else "e.g., Read and explain the README file"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && 
                            keyEvent.isCtrlPressed && 
                            keyEvent.key == Key.Enter) {
                            if (sessionUi.isWaitingForInput) {
                                state.submitInput()
                            } else {
                                state.runTask()
                            }
                            true
                        } else {
                            false
                        }
                    },
                enabled = !sessionUi.isRunning || sessionUi.isWaitingForInput,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Icon(
                        imageVector = if (sessionUi.isWaitingForInput) 
                            Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            val isInputValid = sessionUi.taskInput.isNotBlank()
            val canClick = when {
                sessionUi.isWaitingForInput -> isInputValid
                sessionUi.isRunning -> true
                else -> isInputValid
            }

            FilledIconButton(
                onClick = {
                    if (sessionUi.isWaitingForInput) {
                        state.submitInput()
                    } else if (sessionUi.isRunning) {
                        state.stopCurrentSession()
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
                        sessionUi.isWaitingForInput -> Icons.Default.Check
                        sessionUi.isRunning -> Icons.Default.Stop
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        sessionUi.isWaitingForInput -> "Send"
                        sessionUi.isRunning -> "Stop"
                        else -> "Run"
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionControls(state: MainViewModel, sessionUi: SessionUiState, ui: AppUiState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { state.createNewSession() },
            enabled = true,
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
            enabled = !sessionUi.isRunning && sessionUi.currentSessionId != null,
            label = { Text("Continue") },
            leadingIcon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        
        sessionUi.currentSessionId?.let { sessionId ->
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

        AgentQuickSwitch(state = state, ui = ui)
        ModelQuickSwitch(state = state, ui = ui)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
private fun AgentQuickSwitch(state: MainViewModel, ui: AppUiState) {
    val profiles = ui.agentProfiles
    var expanded by remember { mutableStateOf(false) }
    val activeName = ui.activeAgentProfileName
    val activeProfile = profiles.firstOrNull { it.name == activeName }
    val displayName = activeProfile?.name ?: activeName.ifBlank { "build" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = "Agent: $displayName",
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
@Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
private fun ModelQuickSwitch(state: MainViewModel, ui: AppUiState) {
    val models = ui.models
    val auths = ui.auths
    var expanded by remember { mutableStateOf(false) }

    if (models.isEmpty()) {
        AssistChip(
            onClick = { state.currentPage = AppPage.Models },
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

    val activeModel = models.find { it.id == ui.activeModelId } ?: models.first()
    val displayName = getModelDisplayName(activeModel, auths)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
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

private data class MessageListItem(
    val message: SessionMessage,
    val sourceIndex: Int,
)

private fun buildMessageListItems(messages: List<SessionMessage>): List<MessageListItem> {
    return messages.mapIndexedNotNull { index, message ->
        when {
            message.isSayToUserToolResult() -> null
            message.isSayToUserToolCall() || message.isAwaitUserInputToolCall() -> {
                val projectedContent = message.extractToolCallPrimaryTextArg()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: message.content
                MessageListItem(
                    message = message.copy(
                        role = MessageRole.ASSISTANT,
                        content = projectedContent,
                    ),
                    sourceIndex = index,
                )
            }
            message.isAwaitUserInputToolResult() -> {
                val projectedContent = message.extractToolResultText()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: message.content
                MessageListItem(
                    message = message.copy(
                        role = MessageRole.USER,
                        content = projectedContent,
                    ),
                    sourceIndex = index,
                )
            }
            else -> MessageListItem(message = message, sourceIndex = index)
        }
    }
}

@Composable
private fun MessageList(
    messages: List<SessionMessage>,
    onForkFromMessage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(messages) {
        buildMessageListItems(messages)
    }
    val listState = rememberLazyListState()
    
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.size - 1)
        }
    }
    
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        if (items.isEmpty()) {
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
                itemsIndexed(items, key = { _, item -> item.message.id }) { _, item ->
                    val message = item.message
                    val sourceMessage = messages.getOrNull(item.sourceIndex)
                    val defaultExpanded = remember(
                        message.id,
                        message.role,
                        message.isUiToolCallLike(),
                        message.isUiError(),
                    ) {
                        shouldExpandByDefault(message)
                    }
                    var expanded by rememberSaveable(message.id) { mutableStateOf(defaultExpanded) }

                    if (!expanded) {
                        CollapsedMessageRow(
                            message = message,
                            onExpand = { expanded = true },
                        )
                        return@itemsIndexed
                    }

                    when (message.role) {
                        MessageRole.SYSTEM -> SystemMessage(content = message.content)
                        else -> MessageBubble(
                            message = message,
                            isCurrentUser = message.role == MessageRole.USER,
                            onForkFromHere = if (sourceMessage?.role == MessageRole.SYSTEM) {
                                null
                            } else {
                                { onForkFromMessage(item.sourceIndex) }
                            }
                        )
                    }

                    if (!defaultExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { expanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Collapse")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedMessageRow(
    message: SessionMessage,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collapsedMessageTitle(message),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = collapsedMessagePreview(message),
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

private fun shouldExpandByDefault(message: SessionMessage): Boolean {
    return when (message.role) {
        MessageRole.USER -> true
        MessageRole.ASSISTANT -> !message.isUiToolCallLike() && !message.isUiError()
        MessageRole.SYSTEM,
        MessageRole.TOOL_CALL,
        MessageRole.TOOL_RESULT,
        -> false
    }
}

private fun collapsedMessageTitle(message: SessionMessage): String {
    val toolSuffix = message.extractToolName()?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    return when (message.role) {
        MessageRole.SYSTEM -> "System message"
        MessageRole.TOOL_CALL -> "Tool call$toolSuffix"
        MessageRole.TOOL_RESULT -> if (message.isUiError()) {
            "Tool error$toolSuffix"
        } else {
            "Tool result$toolSuffix"
        }
        MessageRole.ASSISTANT -> if (message.isAssistantToolPlan()) {
            "Assistant tool plan"
        } else {
            "Assistant message"
        }
        MessageRole.USER -> "User message"
    }
}

private fun collapsedMessagePreview(message: SessionMessage): String {
    val content = when (message.role) {
        MessageRole.TOOL_CALL -> message.extractToolCallArgumentsText() ?: message.content
        MessageRole.TOOL_RESULT -> message.extractToolResultText() ?: message.content
        else -> message.content
    }
    val normalized = content.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) {
        return "(empty)"
    }
    return if (normalized.length > 120) {
        normalized.take(120) + "..."
    } else {
        normalized
    }
}

@Composable
private fun ApprovalControls(state: MainViewModel, ui: AppUiState) {
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
                    checked = ui.yoloEnabled,
                    onCheckedChange = { state.yoloEnabled = it }
                )
            }
        }
    }
}

@Composable
private fun ApprovalPanel(state: MainViewModel, ui: AppUiState) {
    val pending = ui.pendingApprovals.firstOrNull() ?: return
    val request = pending.request

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

            Text(
                text = "Session: ${pending.sessionId.take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
