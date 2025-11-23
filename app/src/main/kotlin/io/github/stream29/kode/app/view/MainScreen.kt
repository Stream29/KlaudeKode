package io.github.stream29.kode.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.viewmodel.MainViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
public fun MainScreen(state: MainViewModel) {
    MaterialTheme(colors = darkColors()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("🤖 Koog Code Agent", style = MaterialTheme.typography.h5)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.taskInput,
                        onValueChange = { state.taskInput = it },
                        label = {
                            Text(if (state.isWaitingForInput) "Enter response..." else "Enter task")
                        },
                        modifier = Modifier.weight(1f).onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && 
                                keyEvent.isCtrlPressed && 
                                keyEvent.key == Key.Enter) {
                                // Trigger run or submit based on current state
                                if (state.isWaitingForInput) {
                                    state.submitInput()
                                } else {
                                    state.runTask()
                                }
                                true // Consume the event
                            } else {
                                false // Don't consume other events
                            }
                        },
                        enabled = !state.isRunning || state.isWaitingForInput,
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    val isInputValid = state.taskInput.isNotBlank()
                    val canClick = if (state.isWaitingForInput) isInputValid else (!state.isRunning && isInputValid)

                    Button(
                        onClick = {
                            if (state.isWaitingForInput) {
                                state.submitInput()
                            } else {
                                state.runTask()
                            }
                        },
                        enabled = canClick,
                        modifier = Modifier.height(56.dp) // Rough standard height
                    ) {
                        Text(
                            if (state.isWaitingForInput) "Send"
                            else if (state.isRunning) "Running..."
                            else "Run"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Output:", style = MaterialTheme.typography.subtitle1)

                SelectionContainer(modifier = Modifier.fillMaxSize().weight(1f)) {
                    Card(modifier = Modifier.fillMaxSize(), elevation = 4.dp) {
                        val scrollState = rememberScrollState()

                        // Auto-scroll to bottom when log changes
                        LaunchedEffect(state.outputLog) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Text(
                            text = state.outputLog,
                            modifier = Modifier.padding(8.dp).verticalScroll(scrollState),
                            style = MaterialTheme.typography.body2,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}