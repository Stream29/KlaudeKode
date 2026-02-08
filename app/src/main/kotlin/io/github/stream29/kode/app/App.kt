package io.github.stream29.kode.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.stream29.kode.app.di.appModule
import io.github.stream29.kode.app.view.MainScreen
import io.github.stream29.kode.app.viewmodel.MainViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

public fun main(): Unit = application {
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
