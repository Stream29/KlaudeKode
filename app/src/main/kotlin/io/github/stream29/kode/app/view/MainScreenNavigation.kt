package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.stream29.kode.app.viewmodel.*
import io.github.stream29.kode.app.viewmodel.acp.AcpViewModel
import io.github.stream29.kode.app.viewmodel.chat.ChatViewModel
import io.github.stream29.kode.app.viewmodel.config.ConfigViewModel
import io.github.stream29.kode.app.viewmodel.info.InfoViewModel
import io.github.stream29.kode.app.viewmodel.mcp.McpViewModel
import io.github.stream29.kode.app.viewmodel.models.ModelsViewModel
import io.github.stream29.kode.app.viewmodel.sessions.SessionsViewModel
import io.github.stream29.kode.app.viewmodel.terminal.TerminalViewModel
import io.github.stream29.kode.app.viewmodel.tools.ToolsViewModel
import io.github.stream29.kode.app.viewmodel.web.WebViewModel

internal fun SnapshotStateList<NavKey>.removeDialogRoute(route: NavKey) {
    if (lastOrNull() == route) {
        removeLastOrNull()
        return
    }
    removeAll { entry -> entry == route }
}

internal fun SnapshotStateList<NavKey>.upsertDialogRoute(
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
internal fun ChatRoute(state: MainViewModel) {
    val chatViewModel: ChatViewModel = org.koin.compose.koinInject()
    val sessionsViewModel: SessionsViewModel = org.koin.compose.koinInject()
    val modelsViewModel: ModelsViewModel = org.koin.compose.koinInject()
    val configViewModel: ConfigViewModel = org.koin.compose.koinInject()
    val chatUi by chatViewModel.uiState.collectAsStateWithLifecycle()
    val sessionsUi by sessionsViewModel.uiState.collectAsStateWithLifecycle()
    val modelsUi by modelsViewModel.uiState.collectAsStateWithLifecycle()
    val configUi by configViewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId by state.currentSessionIdFlow.collectAsStateWithLifecycle()

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
            models = modelsUi.models,
            auths = modelsUi.auths,
            activeModelId = state.activeModelIdFlow.value,
        ),
    )
}

@Composable
internal fun SessionsRoute(state: MainViewModel) {
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
internal fun ModelsRoute(state: MainViewModel) {
    val ui by state.appUiState.collectAsStateWithLifecycle()
    ModelsPage(state = state, ui = ui)
}

@Composable
internal fun SettingsRoute(state: MainViewModel) {
    val ui by state.appUiState.collectAsStateWithLifecycle()
    SettingsPage(state = state, ui = ui)
}

@Composable
internal fun ToolsRoute(state: MainViewModel) {
    val toolsViewModel: ToolsViewModel = org.koin.compose.koinInject()
    val ui by toolsViewModel.uiState.collectAsStateWithLifecycle()
    ToolsPage(viewModel = toolsViewModel, ui = ui)
}

@Composable
internal fun McpRoute(state: MainViewModel) {
    val mcpViewModel: McpViewModel = org.koin.compose.koinInject()
    val ui by mcpViewModel.uiState.collectAsStateWithLifecycle()
    McpPage(viewModel = mcpViewModel, ui = ui)
}

@Composable
internal fun AcpRoute(state: MainViewModel) {
    val acpViewModel: AcpViewModel = org.koin.compose.koinInject()
    val ui by acpViewModel.uiState.collectAsStateWithLifecycle()
    AcpPage(viewModel = acpViewModel, ui = ui)
}

@Composable
internal fun TerminalRoute(state: MainViewModel) {
    val terminalViewModel: TerminalViewModel = org.koin.compose.koinInject()
    val ui by terminalViewModel.uiState.collectAsStateWithLifecycle()
    TerminalPage(viewModel = terminalViewModel, ui = ui)
}

@Composable
internal fun WebRoute(state: MainViewModel) {
    val webViewModel: WebViewModel = org.koin.compose.koinInject()
    val ui by webViewModel.uiState.collectAsStateWithLifecycle()
    WebPage(viewModel = webViewModel, ui = ui)
}

@Composable
internal fun InfoRoute(state: MainViewModel) {
    val infoViewModel: InfoViewModel = org.koin.compose.koinInject()
    val ui by infoViewModel.uiState.collectAsStateWithLifecycle()
    InfoPage(viewModel = infoViewModel, ui = ui)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(state: MainViewModel, currentPage: AppPage, onOpenSessionManager: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Kode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = currentPage.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = { state.navigateToPage(page = AppPage.Sessions) },
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Sessions",
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = {
                    onOpenSessionManager()
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Session Manager",
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = { state.navigateToPage(page = AppPage.Models) },
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Models",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
internal fun AppNavigationRail(
    state: MainViewModel,
    currentPage: AppPage,
    modifier: Modifier,
) {
    NavigationRail(modifier = modifier) {
        AppPage.entries.forEach { page ->
            NavigationRailItem(
                selected = currentPage == page,
                onClick = { state.navigateToPage(page = page) },
                icon = {
                    Icon(imageVector = page.icon, contentDescription = page.title)
                },
                label = { Text(text = page.title) },
            )
        }
    }
}
