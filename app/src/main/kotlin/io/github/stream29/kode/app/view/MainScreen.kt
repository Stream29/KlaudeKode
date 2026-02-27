package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
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
import io.github.stream29.kode.ui.core.preferences.SendKeyModePreference
import io.github.stream29.kode.ui.components.todo.TodoSidebar
import io.github.stream29.kode.ui.components.todo.TodoUiNode as SidebarTodoUiNode
import io.github.stream29.kode.ui.components.todo.TodoUiState as SidebarTodoUiState
import io.github.stream29.kode.ui.core.todo.TodoUiState as CoreTodoUiState
import io.github.stream29.kode.app.util.formatModelDisplayName
import io.github.stream29.kode.app.viewmodel.AcpPageUiState
import io.github.stream29.kode.app.viewmodel.AppUiState
import io.github.stream29.kode.app.viewmodel.ChatPageUiState
import io.github.stream29.kode.app.viewmodel.InfoPageUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.app.viewmodel.SessionUiState
import io.github.stream29.kode.app.viewmodel.SessionsPageUiState
import io.github.stream29.kode.app.viewmodel.TerminalPageUiState
import io.github.stream29.kode.app.viewmodel.ToolsPageUiState
import io.github.stream29.kode.app.viewmodel.WebPageUiState

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
public fun MainScreen(state: MainViewModel) {
    val chromeUi by state.mainChromeUiState.collectAsStateWithLifecycle()
    val appUi by state.appUiState.collectAsStateWithLifecycle()
    val sessionUi by state.sessionUiState.collectAsStateWithLifecycle()
    val configEditorUi by state.configEditorUiState.collectAsStateWithLifecycle()
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
            entry<ConfigEditorDialogRoute>(metadata = DialogSceneStrategy.dialog()) {
                ConfigEditorDialog(
                    ui = configEditorUi,
                    onConfigTextChange = { value -> state.configText = value },
                    onSave = { state.saveConfig() },
                    onDismiss = {
                        state.showConfigEditor = false
                        navBackStack.removeDialogRoute(ConfigEditorDialogRoute)
                    },
                )
            }
            entry<SessionManagerDialogRoute>(metadata = DialogSceneStrategy.dialog()) {
                SessionManagerDialog(
                    viewModel = state,
                    onDismiss = {
                        state.requestCloseSessionManagerDialog()
                        navBackStack.removeDialogRoute(SessionManagerDialogRoute)
                    },
                )
            }
            entry<AddModelDialogRoute>(metadata = DialogSceneStrategy.dialog()) { key ->
                AddModelDialogDestination(
                    viewModel = state,
                    ui = appUi,
                    preselectedAuthId = key.preselectedAuthId,
                    onDismiss = {
                        navBackStack.removeDialogRoute(key)
                    },
                )
            }
            entry<EditModelDialogRoute>(metadata = DialogSceneStrategy.dialog()) { key ->
                EditModelDialogDestination(
                    viewModel = state,
                    ui = appUi,
                    modelId = key.modelId,
                    onDismiss = {
                        navBackStack.removeDialogRoute(key)
                    },
                )
            }
            entry<AddAuthDialogRoute>(metadata = DialogSceneStrategy.dialog()) { key ->
                AddAuthDialogDestination(
                    viewModel = state,
                    ui = appUi,
                    onDismiss = {
                        navBackStack.removeDialogRoute(key)
                    },
                )
            }
            entry<EditAuthDialogRoute>(metadata = DialogSceneStrategy.dialog()) { key ->
                EditAuthDialogDestination(
                    viewModel = state,
                    ui = appUi,
                    authId = key.authId,
                    onDismiss = {
                        navBackStack.removeDialogRoute(key)
                    },
                )
            }
            entry<DeleteAuthConfirmDialogRoute>(metadata = DialogSceneStrategy.dialog()) { key ->
                DeleteAuthConfirmDialogDestination(
                    viewModel = state,
                    ui = appUi,
                    authId = key.authId,
                    onDismiss = {
                        navBackStack.removeDialogRoute(key)
                    },
                )
            }
            entry<NewSessionDirDialogRoute>(metadata = DialogSceneStrategy.dialog()) {
                NewSessionDirDialog(
                    value = sessionUi.newSessionDirInput,
                    onValueChange = { value -> state.newSessionDirInput = value },
                    onConfirm = { state.confirmNewSessionDir() },
                    onDismiss = {
                        state.cancelNewSessionDir()
                        navBackStack.removeDialogRoute(NewSessionDirDialogRoute)
                    },
                )
            }
            entry<EditSessionDirDialogRoute>(metadata = DialogSceneStrategy.dialog()) {
                EditSessionDirDialog(
                    value = sessionUi.sessionDirDraft,
                    onValueChange = { value -> state.sessionDirDraft = value },
                    onConfirm = { state.confirmSessionDirDialog() },
                    onDismiss = {
                        state.cancelSessionDirDialog()
                        navBackStack.removeDialogRoute(EditSessionDirDialogRoute)
                    },
                )
            }
        }

        LaunchedEffect(chromeUi.currentPage) {
            if (chromeUi.currentPage == AppPage.Sessions) {
                state.loadSessionList()
            }
            val activeDialogRoutes = navBackStack.filter { entry ->
                when (entry) {
                    ConfigEditorDialogRoute -> chromeUi.showConfigEditor
                    SessionManagerDialogRoute -> true
                    is AddModelDialogRoute,
                    is EditModelDialogRoute,
                    is AddAuthDialogRoute,
                    is EditAuthDialogRoute,
                    is DeleteAuthConfirmDialogRoute,
                        -> chromeUi.currentPage == AppPage.Models

                    NewSessionDirDialogRoute -> sessionUi.showNewSessionDialog
                    EditSessionDirDialogRoute -> sessionUi.showSessionDirDialog

                    else -> false
                }
            }
            navBackStack.clear()
            navBackStack.add(chromeUi.currentPage)
            navBackStack.addAll(activeDialogRoutes)
        }

        LaunchedEffect(chromeUi.showConfigEditor) {
            val hasRoute = navBackStack.any { entry -> entry == ConfigEditorDialogRoute }
            if (chromeUi.showConfigEditor && !hasRoute) {
                navBackStack.add(ConfigEditorDialogRoute)
            }
            if (!chromeUi.showConfigEditor && hasRoute) {
                navBackStack.removeAll { entry -> entry == ConfigEditorDialogRoute }
            }
        }

        LaunchedEffect(appUi.overlayDialogRequests.openSessionManagerDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openSessionManagerDialogRequest ?: return@LaunchedEffect
            val hasRoute = navBackStack.any { entry -> entry == SessionManagerDialogRoute }
            if (!hasRoute) {
                navBackStack.add(SessionManagerDialogRoute)
            }
            state.consumeOpenSessionManagerDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(appUi.overlayDialogRequests.closeSessionManagerDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.closeSessionManagerDialogRequest ?: return@LaunchedEffect
            navBackStack.removeAll { entry -> entry == SessionManagerDialogRoute }
            state.consumeCloseSessionManagerDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(sessionUi.showNewSessionDialog) {
            val hasRoute = navBackStack.any { entry -> entry == NewSessionDirDialogRoute }
            if (sessionUi.showNewSessionDialog && !hasRoute) {
                navBackStack.add(NewSessionDirDialogRoute)
            }
            if (!sessionUi.showNewSessionDialog && hasRoute) {
                navBackStack.removeAll { entry -> entry == NewSessionDirDialogRoute }
            }
        }

        LaunchedEffect(sessionUi.showSessionDirDialog) {
            val hasRoute = navBackStack.any { entry -> entry == EditSessionDirDialogRoute }
            if (sessionUi.showSessionDirDialog && !hasRoute) {
                navBackStack.add(EditSessionDirDialogRoute)
            }
            if (!sessionUi.showSessionDirDialog && hasRoute) {
                navBackStack.removeAll { entry -> entry == EditSessionDirDialogRoute }
            }
        }

        LaunchedEffect(appUi.overlayDialogRequests.openAddModelDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openAddModelDialogRequest ?: return@LaunchedEffect
            if (navBackStack.none { entry -> entry == AppPage.Models }) {
                navBackStack.clear()
                navBackStack.add(AppPage.Models)
            }
            navBackStack.upsertDialogRoute(
                route = AddModelDialogRoute(
                    preselectedAuthId = request.preselectedAuthId,
                    requestNonce = request.requestNonce,
                ),
                predicate = { entry -> entry is AddModelDialogRoute },
            )
            state.consumeOpenAddModelDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(appUi.overlayDialogRequests.openEditModelDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openEditModelDialogRequest ?: return@LaunchedEffect
            if (navBackStack.none { entry -> entry == AppPage.Models }) {
                navBackStack.clear()
                navBackStack.add(AppPage.Models)
            }
            navBackStack.upsertDialogRoute(
                route = EditModelDialogRoute(
                    modelId = request.modelId,
                    requestNonce = request.requestNonce,
                ),
                predicate = { entry -> entry is EditModelDialogRoute },
            )
            state.consumeOpenEditModelDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(appUi.overlayDialogRequests.openAddAuthDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openAddAuthDialogRequest ?: return@LaunchedEffect
            if (navBackStack.none { entry -> entry == AppPage.Models }) {
                navBackStack.clear()
                navBackStack.add(AppPage.Models)
            }
            navBackStack.upsertDialogRoute(
                route = AddAuthDialogRoute(requestNonce = request.requestNonce),
                predicate = { entry -> entry is AddAuthDialogRoute },
            )
            state.consumeOpenAddAuthDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(appUi.overlayDialogRequests.openEditAuthDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openEditAuthDialogRequest ?: return@LaunchedEffect
            if (navBackStack.none { entry -> entry == AppPage.Models }) {
                navBackStack.clear()
                navBackStack.add(AppPage.Models)
            }
            navBackStack.upsertDialogRoute(
                route = EditAuthDialogRoute(
                    authId = request.authId,
                    requestNonce = request.requestNonce,
                ),
                predicate = { entry -> entry is EditAuthDialogRoute },
            )
            state.consumeOpenEditAuthDialogRequest(requestNonce = request.requestNonce)
        }

        LaunchedEffect(appUi.overlayDialogRequests.openDeleteAuthDialogRequest?.requestNonce) {
            val request = appUi.overlayDialogRequests.openDeleteAuthDialogRequest ?: return@LaunchedEffect
            if (navBackStack.none { entry -> entry == AppPage.Models }) {
                navBackStack.clear()
                navBackStack.add(AppPage.Models)
            }
            navBackStack.upsertDialogRoute(
                route = DeleteAuthConfirmDialogRoute(
                    authId = request.authId,
                    requestNonce = request.requestNonce,
                ),
                predicate = { entry -> entry is DeleteAuthConfirmDialogRoute },
            )
            state.consumeOpenDeleteAuthDialogRequest(requestNonce = request.requestNonce)
        }

        Scaffold(
            topBar = {
                AppTopBar(state = state, currentPage = chromeUi.currentPage)
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
                                ConfigEditorDialogRoute -> {
                                    state.showConfigEditor = false
                                    navBackStack.removeLastOrNull()
                                }

                                SessionManagerDialogRoute -> {
                                    state.requestCloseSessionManagerDialog()
                                    navBackStack.removeLastOrNull()
                                }

                                is AddModelDialogRoute,
                                is EditModelDialogRoute,
                                is AddAuthDialogRoute,
                                is EditAuthDialogRoute,
                                is DeleteAuthConfirmDialogRoute,
                                    -> {
                                    navBackStack.removeLastOrNull()
                                }

                                NewSessionDirDialogRoute -> {
                                    state.cancelNewSessionDir()
                                    navBackStack.removeLastOrNull()
                                }

                                EditSessionDirDialogRoute -> {
                                    state.cancelSessionDirDialog()
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
    val chatUi by state.chatPageUiState.collectAsStateWithLifecycle()
    val sessionUi by state.sessionUiState.collectAsStateWithLifecycle()
    ChatPage(state = state, sessionUi = sessionUi, ui = chatUi)
}

@Composable
private fun SessionsRoute(state: MainViewModel) {
    val sessionsUi by state.sessionsPageUiState.collectAsStateWithLifecycle()
    val sessionUi by state.sessionUiState.collectAsStateWithLifecycle()
    SessionsPage(state = state, sessionUi = sessionUi, ui = sessionsUi)
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
    val ui by state.toolsPageUiState.collectAsStateWithLifecycle()
    ToolsPage(state = state, ui = ui)
}

@Composable
private fun McpRoute(state: MainViewModel) {
    val ui by state.mcpPageUiState.collectAsStateWithLifecycle()
    McpPage(state = state, ui = ui)
}

@Composable
private fun AcpRoute(state: MainViewModel) {
    val ui by state.acpPageUiState.collectAsStateWithLifecycle()
    AcpPage(state = state, ui = ui)
}

@Composable
private fun TerminalRoute(state: MainViewModel) {
    val ui by state.terminalPageUiState.collectAsStateWithLifecycle()
    TerminalPage(state = state, ui = ui)
}

@Composable
private fun WebRoute(state: MainViewModel) {
    val ui by state.webPageUiState.collectAsStateWithLifecycle()
    WebPage(state = state, ui = ui)
}

@Composable
private fun InfoRoute(state: MainViewModel) {
    val ui by state.infoPageUiState.collectAsStateWithLifecycle()
    InfoPage(state = state, ui = ui)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(state: MainViewModel, currentPage: AppPage) {
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
                    state.loadSessionList()
                    state.requestOpenSessionManagerDialog()
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
private fun ChatPage(state: MainViewModel, sessionUi: SessionUiState, ui: ChatPageUiState) {
    LaunchedEffect(Unit) {
        state.loadSessionList()
        state.restoreLastSessionIfNeeded()
    }
    val onForkFromMessage = remember(state) {
        { index: Int -> state.forkFromMessage(index) }
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
        SessionControls(state = state, sessionUi = sessionUi, ui = ui)

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
                Row(
                    modifier = Modifier
                        .padding(bottom = if (isTodoSidebarCollapsed) 8.dp else 4.dp)
                        .clickable { isTodoSidebarCollapsed = !isTodoSidebarCollapsed }
                        .padding(4.dp),
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

                AnimatedVisibility(
                    visible = !isTodoSidebarCollapsed,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                    ) {
                        TodoSidebar(
                            todoState = sidebarTodoState,
                            onToggleExpand = state::toggleTodoExpand,
                            onToggleComplete = { _ -> },
                        )
                    }
                }

                InputSection(
                    state = state,
                    sessionUi = sessionUi,
                    sendKeyMode = ui.sendKeyMode,
                )
            }
        }
    }
}

@Composable
private fun SessionsPage(state: MainViewModel, sessionUi: SessionUiState, ui: SessionsPageUiState) {
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
                TextButton(
                    onClick = { state.openSessionDirDialog() },
                    enabled = !sessionUi.isRunning && !sessionUi.isWaitingForInput,
                ) {
                    Text("Edit")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SessionManagerContent(
            viewModel = state,
            ui = ui,
            sessionUi = sessionUi,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ModelsPage(state: MainViewModel, ui: AppUiState) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    val selectedTab = ui.modelsPageSelectedTab

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
            onTabSelected = { state.modelsPageSelectedTab = it },
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
private fun ToolsPage(state: MainViewModel, ui: ToolsPageUiState) {
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
private fun AcpPage(state: MainViewModel, ui: AcpPageUiState) {
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
private fun TerminalPage(state: MainViewModel, ui: TerminalPageUiState) {
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
private fun WebPage(state: MainViewModel, ui: WebPageUiState) {
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
private fun InfoPage(state: MainViewModel, ui: InfoPageUiState) {
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
            FilledTonalButton(onClick = { state.exportLogs() }) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Logs")
            }

            FilledTonalButton(onClick = { state.refreshPresetAndSkillsPreview() }) {
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
                if (ui.presetSpecPreview.isBlank()) {
                    Text(
                        text = "No AGENTS.md found",
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
private fun ShellPanel(state: MainViewModel, ui: TerminalPageUiState) {
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
private fun ScriptPanel(state: MainViewModel, ui: TerminalPageUiState) {
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

private data class ToolItem(
    val key: String,
    val title: String,
    val description: String
)

private fun CoreTodoUiState.toSidebarTodoUiState(): SidebarTodoUiState {
    return SidebarTodoUiState(
        rootNodes = rootNodes.map { node ->
            SidebarTodoUiNode(
                node = node.node,
                path = node.path,
                expanded = node.expanded,
                level = node.level,
            )
        },
        allExpanded = allExpanded,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(
    state: MainViewModel,
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

    fun submitDraftInput() {
        state.continueFromInput(localTaskInput)
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
                                state.continueCurrentSession()
                            } else {
                                submitDraftInput()
                            }
                        }
                        true
                    },
                enabled = !sessionUi.isRunning || sessionUi.isWaitingForInput,
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
                    if (sessionUi.isRunning) {
                        state.stopCurrentSession()
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
private fun SessionControls(state: MainViewModel, sessionUi: SessionUiState, ui: ChatPageUiState) {
    val canEditWorkDir = sessionUi.currentSessionId != null && !sessionUi.isRunning && !sessionUi.isWaitingForInput

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
            onClick = { state.openSessionDirDialog() },
            enabled = canEditWorkDir,
            label = { Text("Work Dir") },
            leadingIcon = {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )

        SessionQuickSwitch(state = state, sessionUi = sessionUi, ui = ui)

        PresetQuickSwitch(state = state, ui = ui)
        ModelQuickSwitch(state = state, ui = ui)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionQuickSwitch(state: MainViewModel, sessionUi: SessionUiState, ui: ChatPageUiState) {
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
                onClick = { state.regenerateCurrentSessionTitle() },
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
                        state.updateCurrentSessionTitle(titleDraft)
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
