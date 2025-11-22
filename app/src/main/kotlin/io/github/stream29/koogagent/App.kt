package io.github.stream29.koogagent

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.stream29.koogagent.view.MainScreen
import io.github.stream29.koogagent.viewmodel.MainViewModel

public fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = "Koog Code Agent") {
        val appState = remember { MainViewModel() }
        MainScreen(appState)
    }
}