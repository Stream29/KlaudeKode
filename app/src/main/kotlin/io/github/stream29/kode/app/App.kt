package io.github.stream29.kode.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.stream29.kode.app.di.appModule
import io.github.stream29.kode.app.view.MainScreen
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.fs.FileSystemConfigFactory
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

public fun main() {
    val startupConfigError = validateStartupConfig()
    if (startupConfigError != null) {
        reportStartupConfigError(error = startupConfigError)
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Koog Code Agent",
            state = WindowState(width = 1400.dp, height = 900.dp)
        ) {
            KoinApplication(application = { modules(appModule) }) {
                val appState: MainViewModel = koinInject()
                MainScreen(appState)
            }
        }
    }
}

private fun validateStartupConfig(): Throwable? {
    return runCatching {
        val configManager = FileSystemConfigFactory.createDefault()
        runBlocking {
            configManager.load()
        }
    }.exceptionOrNull()
}

private fun reportStartupConfigError(error: Throwable) {
    val rootCause = error.rootCause()
    val primaryMessage = error.message?.takeIf { message -> message.isNotBlank() }
        ?: "Unknown configuration error"
    val rootMessage = rootCause.message?.takeIf { message -> message.isNotBlank() }

    val details = buildString {
        append("Kode startup aborted: invalid config.\n")
        append(primaryMessage)
        if (!rootMessage.isNullOrBlank() && rootMessage != primaryMessage) {
            append("\nRoot cause: ")
            append(rootMessage)
        }
        append("\nPlease fix ~/.kode/config.yaml and relaunch.")
    }

    System.err.println(details)
}

private fun Throwable.rootCause(): Throwable {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}
