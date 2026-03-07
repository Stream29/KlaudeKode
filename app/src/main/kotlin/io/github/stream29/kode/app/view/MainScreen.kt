package io.github.stream29.kode.app.view

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.github.stream29.kode.app.util.formatModelDisplayName
import io.github.stream29.kode.app.viewmodel.*
import io.github.stream29.kode.app.viewmodel.acp.AcpViewModel
import io.github.stream29.kode.app.viewmodel.chat.ChatViewModel
import io.github.stream29.kode.app.viewmodel.config.ConfigViewModel
import io.github.stream29.kode.app.viewmodel.info.InfoViewModel
import io.github.stream29.kode.app.viewmodel.mcp.McpViewModel
import io.github.stream29.kode.app.viewmodel.sessions.SessionsViewModel
import io.github.stream29.kode.app.viewmodel.sessions.SessionsUiState
import io.github.stream29.kode.app.viewmodel.models.ModelsViewModel
import io.github.stream29.kode.app.viewmodel.terminal.TerminalViewModel
import io.github.stream29.kode.app.viewmodel.tools.ToolsViewModel
import io.github.stream29.kode.app.viewmodel.web.WebViewModel
import io.github.stream29.kode.ui.components.todo.TodoSidebar
import io.github.stream29.kode.ui.core.preferences.SendKeyModePreference
import io.github.stream29.kode.ui.components.todo.TodoUiNode as SidebarTodoUiNode
import io.github.stream29.kode.ui.components.todo.TodoUiState as SidebarTodoUiState
import io.github.stream29.kode.ui.core.todo.TodoUiNode as CoreTodoUiNode
import io.github.stream29.kode.ui.core.todo.TodoUiState as CoreTodoUiState

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
public fun MainScreen(state: MainViewModel) {
    val chromeUi by state.mainChromeUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val nextToast = chromeUi.toasts.firstOrNull()
    LaunchedEffect(nextToast?.id) {
        val toast = nextToast ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = toast.message,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        state.consumeToast(toast.id)
    }

    val colorScheme = remember(chromeUi.uiTheme) {
        if (chromeUi.uiTheme == "light") {
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
    }

    MaterialTheme(colorScheme = colorScheme) {
        val navBackStack = remember { mutableStateListOf<NavKey>(chromeUi.currentPage) }
        val dialogSceneStrategy = remember { DialogSceneStrategy<NavKey>() }

        LaunchedEffect(chromeUi.currentPage) {
            val root = navBackStack.firstOrNull()
            if (root != chromeUi.currentPage) {
                navBackStack.clear()
                navBackStack.add(chromeUi.currentPage)
            }
        }

        val navEntryProvider = entryProvider {
            entry<AppPage> { key ->
                when (key) {
                    AppPage.Chat -> ChatRoute(state = state)
                    AppPage.Sessions -> SessionsRoute(state = state)
                    AppPage.Models -> ModelsRoute(state = state)
                    AppPage.Settings -> SettingsRoute(state = state)
                    AppPage.Tools -> ToolsRoute(state = state)
                    AppPage.Mcp -> McpRoute(state = state)
                    AppPage.Acp -> AcpRoute(state = state)
                    AppPage.Terminal -> TerminalRoute(state = state)
                    AppPage.Web -> WebRoute(state = state)
                    AppPage.Info -> InfoRoute(state = state)
                }
            }
            entry<SessionManagerDialogRoute>(metadata = DialogSceneStrategy.dialog()) {
                SessionManagerDialog(
                    viewModel = state,
                    onDismiss = {
                        navBackStack.removeDialogRoute(SessionManagerDialogRoute)
                    },
                )
            }
        }

        Scaffold(
            topBar = {
                AppTopBar(
                    state = state,
                    currentPage = chromeUi.currentPage,
                    onOpenSessionManager = {
                        navBackStack.upsertDialogRoute(
                            route = SessionManagerDialogRoute,
                            predicate = { entry -> entry == SessionManagerDialogRoute },
                        )
                    },
                )
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
                    currentPage = chromeUi.currentPage,
                    modifier = Modifier.fillMaxHeight()
                )

                VerticalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    NavDisplay(
                        backStack = navBackStack,
                        onBack = {
                            when (navBackStack.lastOrNull()) {
                                SessionManagerDialogRoute -> {
                                    navBackStack.removeLastOrNull()
                                }

                                else -> Unit
                            }
                        },
                        sceneStrategy = dialogSceneStrategy,
                        entryProvider = navEntryProvider,
                    )
                }
            }
        }

    }
}

private fun SnapshotStateList<NavKey>.removeDialogRoute(route: NavKey) {
    if (lastOrNull() == route) {
        removeLastOrNull()
        return
    }
    removeAll { entry -> entry == route }
}

private fun SnapshotStateList<NavKey>.upsertDialogRoute(
    route: NavKey,
    predicate: (NavKey) -> Boolean,
) {
    val existingIndex = indexOfLast { entry -> predicate(entry) }
    if (existingIndex >= 0) {
        removeAt(existingIndex)
    }
    add(route)
}

@Composable
private fun ChatRoute(state: MainViewModel) {
    val chatViewModel: ChatViewModel = org.koin.compose.koinInject()
    val sessionsViewModel: SessionsViewModel = org.koin.compose.koinInject()
    val modelsViewModel: ModelsViewModel = org.koin.compose.koinInject()
    val configViewModel: ConfigViewModel = org.koin.compose.koinInject()
    val chatUi by chatViewModel.uiState.collectAsStateWithLifecycle()
    val sessionsUi by sessionsViewModel.uiState.collectAsStateWithLifecycle()
    val modelsUi by modelsViewModel.uiState.collectAsStateWithLifecycle()
    val configUi by configViewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId by state.currentSessionIdFlow.collectAsStateWithLifecycle()
    val activePresetName by state.activePresetNameFlow.collectAsStateWithLifecycle()
    val agentPresets by state.agentPresetsFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sessionsViewModel.loadSessionList()
    }

    ChatPage(
        state = state,
        chatViewModel = chatViewModel,
        stopMode = chatUi.stopMode,
        sessionUi = SessionUiState(
            messages = chatUi.messages,
            currentSessionId = currentSessionId,
            currentSessionWorkDir = chatUi.currentSessionWorkDir,
            isRunning = chatUi.isRunning,
            isWaitingForInput = chatUi.isWaitingForInput,
            currentTask = chatUi.currentTask,
            todoState = chatUi.todoState,
            isGeneratingSessionTitle = chatUi.isGeneratingSessionTitle,
            taskInput = chatUi.taskInput,
        ),
        ui = ChatPageUiState(
            sessionSummaries = sessionsUi.sessionSummaries,
            messageAlignment = configUi.messageAlignment,
            messageMaxWidthRatio = configUi.messageMaxWidthRatio,
            sendKeyMode = configUi.sendKeyMode,
            agentPresets = agentPresets,
            activePresetName = activePresetName,
            models = modelsUi.models,
            auths = modelsUi.auths,
            activeModelId = state.activeModelIdFlow.value,
        ),
    )
}

@Composable
private fun SessionsRoute(state: MainViewModel) {
    val sessionsViewModel: SessionsViewModel = org.koin.compose.koinInject()
    val chatViewModel: ChatViewModel = org.koin.compose.koinInject()
    val sessionsUi by sessionsViewModel.uiState.collectAsStateWithLifecycle()
    val chatUi by chatViewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId by state.currentSessionIdFlow.collectAsStateWithLifecycle()

    SessionsPage(
        state = state,
        sessionsViewModel = sessionsViewModel,
        sessionUi = SessionUiState(
            messages = chatUi.messages,
            currentSessionId = currentSessionId,
            currentSessionWorkDir = chatUi.currentSessionWorkDir,
            isRunning = chatUi.isRunning,
            isWaitingForInput = chatUi.isWaitingForInput,
            currentTask = chatUi.currentTask,
            todoState = chatUi.todoState,
            isGeneratingSessionTitle = chatUi.isGeneratingSessionTitle,
            taskInput = chatUi.taskInput,
        ),
        ui = sessionsUi,
    )
}

@Composable
private fun ModelsRoute(state: MainViewModel) {
    val ui by state.appUiState.collectAsStateWithLifecycle()
    ModelsPage(state = state, ui = ui)
}

@Composable
private fun SettingsRoute(state: MainViewModel) {
    val ui by state.appUiState.collectAsStateWithLifecycle()
    SettingsPage(state = state, ui = ui)
}

@Composable
private fun ToolsRoute(state: MainViewModel) {
    val toolsViewModel: ToolsViewModel = org.koin.compose.koinInject()
    val ui by toolsViewModel.uiState.collectAsStateWithLifecycle()
    ToolsPage(viewModel = toolsViewModel, ui = ui)
}

@Composable
private fun McpRoute(state: MainViewModel) {
    val mcpViewModel: McpViewModel = org.koin.compose.koinInject()
    val ui by mcpViewModel.uiState.collectAsStateWithLifecycle()
    McpPage(viewModel = mcpViewModel, ui = ui)
}

@Composable
private fun AcpRoute(state: MainViewModel) {
    val acpViewModel: AcpViewModel = org.koin.compose.koinInject()
    val ui by acpViewModel.uiState.collectAsStateWithLifecycle()
    AcpPage(viewModel = acpViewModel, ui = ui)
}

@Composable
private fun TerminalRoute(state: MainViewModel) {
    val terminalViewModel: TerminalViewModel = org.koin.compose.koinInject()
    val ui by terminalViewModel.uiState.collectAsStateWithLifecycle()
    TerminalPage(viewModel = terminalViewModel, ui = ui)
}

@Composable
private fun WebRoute(state: MainViewModel) {
    val webViewModel: WebViewModel = org.koin.compose.koinInject()
    val ui by webViewModel.uiState.collectAsStateWithLifecycle()
    WebPage(viewModel = webViewModel, ui = ui)
}

@Composable
private fun InfoRoute(state: MainViewModel) {
    val infoViewModel: InfoViewModel = org.koin.compose.koinInject()
    val ui by infoViewModel.uiState.collectAsStateWithLifecycle()
    InfoPage(viewModel = infoViewModel, ui = ui)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: MainViewModel, currentPage: AppPage, onOpenSessionManager: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Kode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentPage.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = { state.navigateToPage(page = AppPage.Sessions) }
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Sessions"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = {
                    onOpenSessionManager()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Session Manager"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = { state.navigateToPage(page = AppPage.Models) }
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
    currentPage: AppPage,
    modifier: Modifier
) {
    NavigationRail(modifier = modifier) {
        AppPage.entries.forEach { page ->
            NavigationRailItem(
                selected = currentPage == page,
                onClick = { state.navigateToPage(page = page) },
                icon = {
                    Icon(imageVector = page.icon, contentDescription = page.title)
                },
                label = { Text(text = page.title) }
            )
        }
    }
}

@Composable
private fun ChatPage(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    stopMode: StopMode,
    sessionUi: SessionUiState,
    ui: ChatPageUiState
) {
    val onForkFromMessage = remember(chatViewModel) {
        { index: Int -> chatViewModel.forkFromMessage(index) }
    }

    var isTodoSidebarCollapsed by rememberSaveable(sessionUi.currentSessionId) {
        mutableStateOf(true)
    }

    val sidebarTodoState = remember(sessionUi.todoState) {
        sessionUi.todoState.toSidebarTodoUiState()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        SessionControls(state = state, chatViewModel = chatViewModel, sessionUi = sessionUi, ui = ui)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MessageList(
                messages = sessionUi.messages,
                onForkFromMessage = onForkFromMessage,
                messageAlignment = ui.messageAlignment,
                messageMaxWidthRatio = ui.messageMaxWidthRatio,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .align(Alignment.Start)
                        .animateContentSize(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .clickable { isTodoSidebarCollapsed = !isTodoSidebarCollapsed }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Todo List",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isTodoSidebarCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTodoSidebarCollapsed) "Expand Todo List" else "Collapse Todo List",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isTodoSidebarCollapsed) {
                            HorizontalDivider()
                            TodoSidebar(
                                todoState = sidebarTodoState,
                                onToggleExpand = chatViewModel::toggleTodoExpand,
                                onToggleComplete = { _ -> },
                                modifier = Modifier.heightIn(max = 300.dp)
                            )
                        }
                    }
                }

                InputSection(
                    state = state,
                    chatViewModel = chatViewModel,
                    stopMode = stopMode,
                    sessionUi = sessionUi,
                    sendKeyMode = ui.sendKeyMode,
                )
            }
        }
    }
}

@Composable
private fun SessionsPage(
    state: MainViewModel,
    sessionsViewModel: SessionsViewModel,
    sessionUi: SessionUiState,
    ui: SessionsUiState
) {
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
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SessionManagerContent(
            viewModel = sessionsViewModel,
            ui = ui,
            sessionUi = sessionUi,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ModelsPage(state: MainViewModel, ui: AppUiState) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

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
            onTabSelected = { tab -> selectedTab = tab },
            tabs = tabs,
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
private fun ToolsPage(viewModel: ToolsViewModel, ui: ToolsPageUiState) {
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
                                viewModel.setToolEnabled(tool.key, enabled)
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
                    TextButton(onClick = { viewModel.clearToolLogs() }) {
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
                        itemsIndexed(
                            items = ui.toolLogs,
                            key = { index, _ -> index },
                        ) { _, log ->
                            Text(
                                text = log,
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
private fun AcpPage(viewModel: AcpViewModel, ui: AcpPageUiState) {
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
                    onValueChange = { value -> viewModel.updateHost(value) },
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
                            viewModel.updatePort(parsed)
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
                        onClick = { viewModel.startAcpServer() },
                        enabled = !ui.acpRunning
                    ) {
                        Text("Start")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.stopAcpServer() },
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
                        itemsIndexed(
                            items = ui.acpLogs,
                            key = { index, _ -> index },
                        ) { _, log ->
                            Text(
                                text = log,
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
private fun TerminalPage(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
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
            ShellPanel(viewModel = viewModel, ui = ui)
        } else {
            ScriptPanel(viewModel = viewModel, ui = ui)
        }
    }
}

@Composable
private fun WebPage(viewModel: WebViewModel, ui: WebPageUiState) {
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
                onValueChange = { value -> viewModel.updateWebUrl(value) },
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            FilledTonalButton(
                onClick = { viewModel.fetchWebContent() },
                enabled = !ui.webLoading
            ) {
                Text("Fetch")
            }
            FilledTonalButton(
                onClick = { viewModel.openWebInBrowser() }
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
private fun InfoPage(viewModel: InfoViewModel, ui: InfoPageUiState) {
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
                InfoRow(
                    label = "Config",
                    value = io.github.stream29.kode.config.fs.FileSystemLocations.configFile.absolutePath
                )
                InfoRow(label = "Preset spec", value = ui.presetSpecPath.ifBlank { "Not found" })
                InfoRow(
                    label = "Skills",
                    value = if (ui.skillsPreview.isEmpty()) "None" else ui.skillsPreview.size.toString()
                )
                InfoRow(label = "Models", value = ui.modelsCount.toString())
                InfoRow(label = "Auth Providers", value = ui.authCount.toString())
                InfoRow(label = "MCP Servers", value = ui.mcpServerCount.toString())
                InfoRow(
                    label = "Disabled Tools",
                    value = if (ui.disabledTools.isEmpty()) "None" else ui.disabledTools.joinToString(", ")
                )
                InfoRow(label = "ACP Running", value = ui.acpRunning.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.exportLogs() }) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Logs")
            }

            FilledTonalButton(onClick = { viewModel.refreshPresetAndSkillsPreview() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preset File",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (ui.presetSpecPreview.isBlank()) {
                    Text(
                        text = "No preset file found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = ui.presetSpecPreview.take(800),
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
private fun ShellPanel(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.terminalCommand,
            onValueChange = { value -> viewModel.updateTerminalCommand(value) },
            label = { Text("Shell command") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { viewModel.runShellCommand() },
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
private fun ScriptPanel(viewModel: TerminalViewModel, ui: TerminalPageUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.scriptContent,
            onValueChange = { value -> viewModel.updateScriptContent(value) },
            label = { Text("KTS Script") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            singleLine = false,
            maxLines = 12
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { viewModel.runScript() },
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

private data class ToolItem(
    val key: String,
    val title: String,
    val description: String
)

private fun CoreTodoUiState.toSidebarTodoUiState(): SidebarTodoUiState {
    fun mapNode(coreNode: CoreTodoUiNode): SidebarTodoUiNode {
        return SidebarTodoUiNode(
            name = coreNode.name,
            isCompleted = coreNode.isCompleted,
            subtasks = coreNode.subtasks.map { mapNode(it) },
            path = coreNode.path,
            expanded = coreNode.expanded,
            level = coreNode.level,
        )
    }

    return SidebarTodoUiState(
        rootNodes = rootNodes.map { node -> mapNode(node) },
        allExpanded = allExpanded,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    stopMode: StopMode,
    sessionUi: SessionUiState,
    sendKeyMode: String,
) {
    var localTaskInput by rememberSaveable(sessionUi.currentSessionId) {
        mutableStateOf(sessionUi.taskInput)
    }

    LaunchedEffect(sessionUi.currentSessionId) {
        localTaskInput = sessionUi.taskInput
    }

    LaunchedEffect(sessionUi.taskInput, sessionUi.isRunning) {
        if (sessionUi.taskInput.isBlank() && !sessionUi.isRunning) {
            localTaskInput = ""
        }
    }

    val normalizedSendKeyMode = SendKeyModePreference.fromValue(sendKeyMode)
    val hasActiveSession = sessionUi.currentSessionId != null
    val isForceStopAction = stopMode == StopMode.SafeRequested || stopMode == StopMode.ForceStop

    fun submitDraftInput() {
        chatViewModel.submitInput(localTaskInput)
        localTaskInput = ""
    }

    fun shouldHandleSubmitShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
            return false
        }
        return normalizedSendKeyMode.shouldSubmitShortcut(
            isCtrlPressed = keyEvent.isCtrlPressed,
            isMetaPressed = keyEvent.isMetaPressed,
            isShiftPressed = keyEvent.isShiftPressed,
        )
    }

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
                value = localTaskInput,
                onValueChange = { localTaskInput = it },
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
                    .onPreviewKeyEvent { keyEvent ->
                        if (!shouldHandleSubmitShortcut(keyEvent)) {
                            return@onPreviewKeyEvent false
                        }
                        val canSubmitFromKeyboard = when {
                            sessionUi.isWaitingForInput -> true
                            sessionUi.isRunning -> false
                            else -> localTaskInput.isNotBlank() || hasActiveSession
                        }
                        if (canSubmitFromKeyboard) {
                            if (localTaskInput.isBlank()) {
                                chatViewModel.continueCurrentSession()
                            } else {
                                submitDraftInput()
                            }
                        }
                        true
                    },
                enabled = true,
                singleLine = false,
                minLines = 1,
                maxLines = 8,
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


            val isInputValid = localTaskInput.isNotBlank()
            val canClick = when {
                sessionUi.isWaitingForInput -> true
                sessionUi.isRunning -> true
                else -> isInputValid || hasActiveSession
            }

            FilledIconButton(
                onClick = {
                    if (sessionUi.isWaitingForInput) {
                        submitDraftInput()
                    } else if (sessionUi.isRunning) {
                        chatViewModel.stopRun(kill = isForceStopAction)
                    } else {
                        submitDraftInput()
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
                        sessionUi.isRunning && isForceStopAction -> Icons.Default.Close
                        sessionUi.isRunning -> Icons.Default.Stop
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        sessionUi.isWaitingForInput -> "Send"
                        sessionUi.isRunning && isForceStopAction -> "Force Stop"
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
private fun SessionControls(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    sessionUi: SessionUiState,
    ui: ChatPageUiState
) {
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

        SessionQuickSwitch(state = state, chatViewModel = chatViewModel, sessionUi = sessionUi, ui = ui)

        PresetQuickSwitch(state = state, ui = ui)
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
            }
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
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            state.switchToSession(session.id)
                            expanded = false
                        }
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
private fun PresetQuickSwitch(state: MainViewModel, ui: ChatPageUiState) {
    val presets = ui.agentPresets
    var expanded by remember { mutableStateOf(false) }
    val activeName = ui.activePresetName
    val activePreset = presets.firstOrNull { it.name == activeName }
    val displayName = activePreset?.name ?: activeName.ifBlank { "build" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = "Preset: $displayName",
            onValueChange = {},
            readOnly = true,
            label = { Text("Preset") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .widthIn(min = 180.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(preset.name)
                            Text(
                                preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        state.selectPreset(preset.name, persist = true)
                        expanded = false
                    }
                )
            }
        }
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
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        return
    }

    val activeModel = models.find { it.id == ui.activeModelId } ?: models.first()
    val displayName = formatModelDisplayName(activeModel, auths)

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
                            Text(formatModelDisplayName(model, auths))
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
