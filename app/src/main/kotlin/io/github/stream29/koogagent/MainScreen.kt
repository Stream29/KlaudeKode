package io.github.stream29.koogagent

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun MainScreen(state: AppState) {
    val scope = rememberCoroutineScope()

    MaterialTheme {
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
                    modifier = Modifier.weight(1f),
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
                            state.runTask(scope) 
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