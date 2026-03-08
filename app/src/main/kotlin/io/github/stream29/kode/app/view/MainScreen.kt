package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.github.stream29.kode.app.viewmodel.MainViewModel

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
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                AppNavigationRail(
                    state = state,
                    currentPage = chromeUi.currentPage,
                    modifier = Modifier.fillMaxHeight(),
                )

                VerticalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
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
