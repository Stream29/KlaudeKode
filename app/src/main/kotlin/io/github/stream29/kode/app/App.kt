package io.github.stream29.kode.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.stream29.kode.app.view.MainScreen
import io.github.stream29.kode.app.viewmodel.MainViewModel

public fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = "Koog Code Agent") {
        val appState = remember { MainViewModel() }
        MainScreen(appState)
    }
}