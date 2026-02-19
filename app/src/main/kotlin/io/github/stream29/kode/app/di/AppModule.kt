package io.github.stream29.kode.app.di

import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import io.github.stream29.kode.config.fs.FileSystemLocations
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.oauth.core.DefaultOAuthAuthCodePkceClient
import io.github.stream29.kode.oauth.core.DefaultOAuthCredentialManager
import io.github.stream29.kode.oauth.core.DefaultOAuthDeviceFlowClient
import io.github.stream29.kode.oauth.core.FileOAuthTokenStore
import io.github.stream29.kode.oauth.core.OAuthAuthCodePkceClient
import io.github.stream29.kode.oauth.core.OAuthCredentialManager
import io.github.stream29.kode.oauth.core.OAuthDeviceFlowClient
import io.github.stream29.kode.oauth.core.OAuthTokenStore
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.SessionRepository
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
    single {
        SessionManager(repository = get())
    }
}

public val coreModule: Module = module {
    single {
        HookManager.empty()
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
            hookManager = get(),
            oauthCredentialManager = get(),
        )
    }
}

public val appModule: Module = module {
    includes(configModule, sessionModule, coreModule, viewModelModule)
}
