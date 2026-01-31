package io.github.stream29.kode.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.stream29.kode.app.view.MainScreen
import io.github.stream29.kode.app.viewmodel.MainViewModel

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

public fun main(): Unit = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Koog Code Agent",
        state = WindowState(width = 1400.dp, height = 900.dp)
    ) {
        val appState = remember { MainViewModel() }
        MainScreen(appState)
    }
}