package io.github.stream29.kode.app.di

import io.github.stream29.kode.app.service.DefaultWebToolsProvider
import io.github.stream29.kode.app.service.WebToolsProvider
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import io.github.stream29.kode.core.agent.SessionAwareAgentFactoryProvider
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.SessionRepository
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.session.core.storage.SessionStorage
import org.koin.core.module.Module
import org.koin.dsl.module

public val configModule: Module = module {
    single<ConfigManager> {
        FileSystemConfigFactory.createDefault()
    }
}

public val sessionModule: Module = module {
    single<FileSessionStorage> {
        FileSessionStorage()
    }
    single<SessionStorage> {
        get<FileSessionStorage>()
    }
    single<SessionRepository> {
        get<FileSessionStorage>()
    }
    single {
        SessionManager(repository = get())
    }
}

public val coreModule: Module = module {
    single {
        HookManager.empty()
    }
    single<WebToolsProvider> {
        DefaultWebToolsProvider()
    }
    single {
        SessionAwareAgentFactoryProvider(
            sessionManager = get(),
            hookManager = get(),
        )
    }
}

public val viewModelModule: Module = module {
    single {
        MainViewModel(
            configManager = get(),
            sessionManager = get(),
            agentFactoryProvider = get(),
            webToolsProvider = get(),
        )
    }
}

public val appModule: Module = module {
    includes(configModule, sessionModule, coreModule, viewModelModule)
}
