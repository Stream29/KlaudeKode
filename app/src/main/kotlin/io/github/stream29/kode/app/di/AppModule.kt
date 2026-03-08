package io.github.stream29.kode.app.di

import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.core.agent.DefaultSessionExecutionRuntimeFactory
import io.github.stream29.kode.core.agent.SessionExecutionModelCatalog
import io.github.stream29.kode.core.agent.SessionExecutionModelCatalogPort
import io.github.stream29.kode.core.agent.SessionExecutionRuntimeFactory
import io.github.stream29.kode.oauth.core.*
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.SessionPersistenceObserverCoordinatorFactory
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.DefaultSessionPersistenceObserverCoordinatorFactory
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.session.core.storage.SessionStorage
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module

public val configModule: Module = module {
    single<ConfigManager> {
        FileSystemConfigFactory.createDefault()
    }
}

public val sessionModule: Module = module {
    single<FileSessionStorage> {
        val configManager = get<ConfigManager>()
        val configuredDataDir = runBlocking {
            configManager.load().storage.dataDir
        }
        FileSessionStorage(dataDir = FileSystemLocations.resolveDataDir(path = configuredDataDir))
    }
    single<SessionStorage> {
        get<FileSessionStorage>()
    }
    single<SessionRepository> {
        get<FileSessionStorage>()
    }
    single<SessionPersistenceObserverCoordinatorFactory> {
        DefaultSessionPersistenceObserverCoordinatorFactory
    }
    single {
        SessionManager(
            dependencies = get<SessionRepository>().toSessionManagerDependencies(
                observerCoordinatorFactory = get(),
            )
        )
    }
}

public val coreModule: Module = module {
    single<SessionExecutionModelCatalogPort> {
        val configManager = get<ConfigManager>()
        SessionExecutionModelCatalogPort {
            val config = configManager.load()
            SessionExecutionModelCatalog(
                auths = config.auths,
                models = config.models,
            )
        }
    }

    single<SessionExecutionRuntimeFactory> {
        DefaultSessionExecutionRuntimeFactory(
            sessionManager = get(),
            modelCatalogPort = get(),
        )
    }

    single<OAuthAuthCodePkceClient> {
        DefaultOAuthAuthCodePkceClient()
    }

    single<OAuthDeviceFlowClient> {
        DefaultOAuthDeviceFlowClient()
    }

    single<OAuthTokenStore> {
        val configManager = get<ConfigManager>()
        val configuredDataDir = runBlocking {
            configManager.load().storage.dataDir
        }
        val oauthDir = FileSystemLocations.resolveDataDir(path = configuredDataDir).resolve("oauth")
        FileOAuthTokenStore(baseDir = oauthDir)
    }

    single<OAuthCredentialManager> {
        DefaultOAuthCredentialManager(
            authCodePkceClient = get(),
            deviceFlowClient = get(),
            tokenStore = get(),
        )
    }
}

public val viewModelModule: Module = module {
    single {
        MainViewModel(
            configManager = get(),
            sessionManager = get(),
        )
    }

    factory {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.chat.ChatViewModel(
            currentSessionIdFlow = mainViewModel.currentSessionIdFlow,
            activeModelIdFlow = mainViewModel.activeModelIdFlow,
            sessionManager = get(),
            sessionExecutionRuntimeFactory = get(),
            onEventCallback = { event, sessionId -> mainViewModel.onEvent(event, sessionId) },
            onNotifyConfigChanged = { mainViewModel.onNotifyConfigChanged() },
        )
    }

    factory {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.sessions.SessionsViewModel(
            sessionManager = get(),
            configManager = get(),
            onSwitchToSession = { sessionId -> mainViewModel.switchToSession(sessionId) },
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
        )
    }

    factory {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.models.ModelsViewModel(
            configManager = get(),
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
            onNotifyConfigChanged = { mainViewModel.onNotifyConfigChanged() },
        )
    }

    single {
        io.github.stream29.kode.app.viewmodel.tools.ToolsViewModel()
    }

    single {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.mcp.McpViewModel(
            configManager = get(),
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
            onNotifyConfigChanged = { mainViewModel.onNotifyConfigChanged() },
            workingDirProvider = { java.io.File(".") },
        )
    }

    single {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.config.ConfigViewModel(
            configManager = get(),
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
            onNotifyConfigChanged = { mainViewModel.onNotifyConfigChanged() },
        )
    }

    single {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.auth.AuthViewModel(
            configManager = get(),
            oauthCredentialManager = get(),
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
            onNotifyConfigChanged = { mainViewModel.onNotifyConfigChanged() },
            openBrowser = { url ->
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url))
                } else {
                    mainViewModel.showToast("Desktop browser is not supported on this system")
                }
            },
        )
    }

    single {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.acp.AcpViewModel(
            onSystemMessage = { msg -> mainViewModel.showToast(msg) }
        )
    }

    single {
        io.github.stream29.kode.app.viewmodel.terminal.TerminalViewModel(
            defaultSessionDir = ".",
            currentSessionWorkDirProvider = { "." }
        )
    }

    single {
        val mainViewModel = get<MainViewModel>()
        io.github.stream29.kode.app.viewmodel.web.WebViewModel(
            onSystemMessage = { msg -> mainViewModel.showToast(msg) }
        )
    }

    single {
        val mainViewModel = get<MainViewModel>()
        val toolsViewModel = get<io.github.stream29.kode.app.viewmodel.tools.ToolsViewModel>()
        val acpViewModel = get<io.github.stream29.kode.app.viewmodel.acp.AcpViewModel>()
        val configManager = get<ConfigManager>()
        io.github.stream29.kode.app.viewmodel.info.InfoViewModel(
            onSystemMessage = { msg -> mainViewModel.showToast(msg) },
            appDataDirProvider = { runBlocking { io.github.stream29.kode.config.fs.FileSystemLocations.resolveDataDir(configManager.load().storage.dataDir) } },
            toolLogsProvider = { toolsViewModel.uiState.value.toolLogs },
            acpLogsProvider = { acpViewModel.uiState.value.acpLogs }
        )
    }
}

public val appModule: Module = module {
    includes(configModule, sessionModule, coreModule, viewModelModule)
}
