package io.github.stream29.kode.app.view

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.viewmodel.*
import io.github.stream29.kode.app.viewmodel.chat.ChatViewModel
import io.github.stream29.kode.ui.components.todo.TodoSidebar
import io.github.stream29.kode.ui.components.todo.TodoUiNode as SidebarTodoUiNode
import io.github.stream29.kode.ui.components.todo.TodoUiState as SidebarTodoUiState
import io.github.stream29.kode.ui.core.preferences.SendKeyModePreference
import io.github.stream29.kode.ui.core.todo.TodoUiNode as CoreTodoUiNode
import io.github.stream29.kode.ui.core.todo.TodoUiState as CoreTodoUiState

@Composable
internal fun ChatPage(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    stopMode: StopMode,
    sessionUi: SessionUiState,
    ui: ChatPageUiState,
) {
    val onForkFromMessage = remember(chatViewModel) {
        { index: Int -> chatViewModel.forkFromMessage(index) }
    }

    var isTodoSidebarCollapsed by rememberSaveable(sessionUi.currentSessionId) {
        mutableStateOf(true)
    }

    val sidebarTodoState = remember(sessionUi.todoState) {
        sessionUi.todoState.toSidebarTodoUiState()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        SessionControls(state = state, chatViewModel = chatViewModel, sessionUi = sessionUi, ui = ui)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            MessageList(
                messages = sessionUi.messages,
                onForkFromMessage = onForkFromMessage,
                messageAlignment = ui.messageAlignment,
                messageMaxWidthRatio = ui.messageMaxWidthRatio,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .align(Alignment.Start)
                        .animateContentSize(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .clickable { isTodoSidebarCollapsed = !isTodoSidebarCollapsed }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Todo List",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = if (isTodoSidebarCollapsed) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (isTodoSidebarCollapsed) {
                                    "Expand Todo List"
                                } else {
                                    "Collapse Todo List"
                                },
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (!isTodoSidebarCollapsed) {
                            HorizontalDivider()
                            TodoSidebar(
                                todoState = sidebarTodoState,
                                onToggleExpand = chatViewModel::toggleTodoExpand,
                                onToggleComplete = { _ -> },
                                modifier = Modifier.heightIn(max = 300.dp),
                            )
                        }
                    }
                }

                InputSection(
                    state = state,
                    chatViewModel = chatViewModel,
                    stopMode = stopMode,
                    sessionUi = sessionUi,
                    sendKeyMode = ui.sendKeyMode,
                )
            }
        }
    }
}

private fun CoreTodoUiState.toSidebarTodoUiState(): SidebarTodoUiState {
    fun mapNode(coreNode: CoreTodoUiNode): SidebarTodoUiNode {
        return SidebarTodoUiNode(
            name = coreNode.name,
            completed = coreNode.completed,
            subItems = coreNode.subItems.map { mapNode(it) },
            path = coreNode.path,
            expanded = coreNode.expanded,
            level = coreNode.level,
        )
    }

    return SidebarTodoUiState(
        rootNodes = rootNodes.map { node -> mapNode(node) },
        allExpanded = allExpanded,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(
    state: MainViewModel,
    chatViewModel: ChatViewModel,
    stopMode: StopMode,
    sessionUi: SessionUiState,
    sendKeyMode: String,
) {
    var localTaskInput by rememberSaveable(sessionUi.currentSessionId) {
        mutableStateOf(sessionUi.taskInput)
    }

    LaunchedEffect(sessionUi.currentSessionId) {
        localTaskInput = sessionUi.taskInput
    }

    LaunchedEffect(sessionUi.taskInput, sessionUi.isRunning) {
        if (sessionUi.taskInput.isBlank() && !sessionUi.isRunning) {
            localTaskInput = ""
        }
    }

    val normalizedSendKeyMode = SendKeyModePreference.fromValue(sendKeyMode)
    val hasActiveSession = sessionUi.currentSessionId != null
    val isForceStopAction = stopMode == StopMode.SafeRequested || stopMode == StopMode.ForceStop

    fun submitDraftInput() {
        chatViewModel.submitInput(localTaskInput)
        localTaskInput = ""
    }

    fun shouldHandleSubmitShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
            return false
        }
        return normalizedSendKeyMode.shouldSubmitShortcut(
            isCtrlPressed = keyEvent.isCtrlPressed,
            isMetaPressed = keyEvent.isMetaPressed,
            isShiftPressed = keyEvent.isShiftPressed,
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localTaskInput,
                onValueChange = { localTaskInput = it },
                label = {
                    Text(
                        if (sessionUi.isWaitingForInput) "Enter response..."
                        else "What would you like me to do?",
                    )
                },
                placeholder = {
                    Text(
                        if (sessionUi.isWaitingForInput) "Type your response..."
                        else "e.g., Read and explain the README file",
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (!shouldHandleSubmitShortcut(keyEvent)) {
                            return@onPreviewKeyEvent false
                        }
                        val canSubmitFromKeyboard = when {
                            sessionUi.isWaitingForInput -> true
                            sessionUi.isRunning -> false
                            else -> localTaskInput.isNotBlank() || hasActiveSession
                        }
                        if (canSubmitFromKeyboard) {
                            if (localTaskInput.isBlank()) {
                                chatViewModel.continueCurrentSession()
                            } else {
                                submitDraftInput()
                            }
                        }
                        true
                    },
                enabled = true,
                singleLine = false,
                minLines = 1,
                maxLines = 8,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Icon(
                        imageVector = if (sessionUi.isWaitingForInput) {
                            Icons.AutoMirrored.Filled.Chat
                        } else {
                            Icons.AutoMirrored.Filled.Send
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )

            Spacer(modifier = Modifier.width(12.dp))

            val isInputValid = localTaskInput.isNotBlank()
            val canClick = when {
                sessionUi.isWaitingForInput -> true
                sessionUi.isRunning -> true
                else -> isInputValid || hasActiveSession
            }

            FilledIconButton(
                onClick = {
                    if (sessionUi.isWaitingForInput) {
                        submitDraftInput()
                    } else if (sessionUi.isRunning) {
                        chatViewModel.stopRun(kill = isForceStopAction)
                    } else {
                        submitDraftInput()
                    }
                },
                enabled = canClick,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canClick) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (canClick) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(
                    imageVector = when {
                        sessionUi.isWaitingForInput -> Icons.Default.Check
                        sessionUi.isRunning && isForceStopAction -> Icons.Default.Close
                        sessionUi.isRunning -> Icons.Default.Stop
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        sessionUi.isWaitingForInput -> "Send"
                        sessionUi.isRunning && isForceStopAction -> "Force Stop"
                        sessionUi.isRunning -> "Stop"
                        else -> "Run"
                    },
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
