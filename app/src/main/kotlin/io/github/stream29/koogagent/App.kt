package io.github.stream29.koogagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import io.github.stream29.koogagent.view.MainScreen

fun main() = application {
    val configResult = runCatching { ConfigLoader.load() }

    Window(onCloseRequest = ::exitApplication, title = "Koog Code Agent") {
        if (configResult.isSuccess) {
            val appState = remember { AppState(configResult.getOrThrow().llm.apiKey) }
            MainScreen(appState)
        } else {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Error loading configuration:\n${configResult.exceptionOrNull()?.message}")
                }
            }
        }
    }
}