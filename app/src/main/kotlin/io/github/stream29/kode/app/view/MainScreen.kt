package io.github.stream29.kode.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.model.MessageItem
import io.github.stream29.kode.app.view.components.MessageBubble
import io.github.stream29.kode.app.view.components.SystemMessage
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.session.core.model.MessageRole

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
public fun MainScreen(state: MainViewModel) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = md_theme_dark_primary,
            onPrimary = md_theme_dark_onPrimary,
            primaryContainer = md_theme_dark_primaryContainer,
            onPrimaryContainer = md_theme_dark_onPrimaryContainer,
            secondary = md_theme_dark_secondary,
            onSecondary = md_theme_dark_onSecondary,
            secondaryContainer = md_theme_dark_secondaryContainer,
            onSecondaryContainer = md_theme_dark_onSecondaryContainer,
            tertiary = md_theme_dark_tertiary,
            onTertiary = md_theme_dark_onTertiary,
            tertiaryContainer = md_theme_dark_tertiaryContainer,
            onTertiaryContainer = md_theme_dark_onTertiaryContainer,
            error = md_theme_dark_error,
            errorContainer = md_theme_dark_errorContainer,
            onError = md_theme_dark_onError,
            onErrorContainer = md_theme_dark_onErrorContainer,
            background = md_theme_dark_background,
            onBackground = md_theme_dark_onBackground,
            surface = md_theme_dark_surface,
            onSurface = md_theme_dark_onSurface,
            surfaceVariant = md_theme_dark_surfaceVariant,
            onSurfaceVariant = md_theme_dark_onSurfaceVariant,
            outline = md_theme_dark_outline,
            inverseOnSurface = md_theme_dark_inverseOnSurface,
            inverseSurface = md_theme_dark_inverseSurface,
            inversePrimary = md_theme_dark_inversePrimary,
            surfaceTint = md_theme_dark_surfaceTint,
            outlineVariant = md_theme_dark_outlineVariant,
            scrim = md_theme_dark_scrim,
        )
    ) {
        LaunchedEffect(state.showSessionManager) {
            if (state.showSessionManager) {
                state.loadSessionList()
            }
        }
        
        if (state.showSessionManager) {
            SessionManagerDialog(
                viewModel = state,
                onDismiss = { state.showSessionManager = false }
            )
        }
        
        if (state.showConfigEditor) {
            ConfigEditorDialog(
                viewModel = state,
                onDismiss = { state.showConfigEditor = false }
            )
        }
        
        if (state.showSettings) {
            SettingsDialog(
                viewModel = state,
                onDismiss = { state.showSettings = false }
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                TopAppBar(state = state)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SessionControls(state = state)

                Spacer(modifier = Modifier.height(8.dp))

                MessageList(
                    messages = state.messages,
                    onForkFromMessage = { index ->
                        state.forkFromMessage(index)
                    },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                InputSection(state = state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(state: MainViewModel) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "🤖 Kode Agent",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            FilledTonalIconButton(
                onClick = { state.showSessionManager = true }
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Sessions"
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledTonalIconButton(
                onClick = { state.showSettings = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputSection(state: MainViewModel) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.taskInput,
                onValueChange = { state.taskInput = it },
                label = {
                    Text(
                        if (state.isWaitingForInput) "Enter response..." 
                        else "What would you like me to do?"
                    )
                },
                placeholder = {
                    Text(
                        if (state.isWaitingForInput) "Type your response..."
                        else "e.g., Read and explain the README file"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && 
                            keyEvent.isCtrlPressed && 
                            keyEvent.key == Key.Enter) {
                            if (state.isWaitingForInput) {
                                state.submitInput()
                            } else {
                                state.runTask()
                            }
                            true
                        } else {
                            false
                        }
                    },
                enabled = !state.isRunning || state.isWaitingForInput,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Icon(
                        imageVector = if (state.isWaitingForInput) 
                            Icons.Default.Chat else Icons.Default.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            val isInputValid = state.taskInput.isNotBlank()
            val canClick = if (state.isWaitingForInput) 
                isInputValid 
            else 
                (!state.isRunning && isInputValid)

            FilledIconButton(
                onClick = {
                    if (state.isWaitingForInput) {
                        state.submitInput()
                    } else {
                        state.runTask()
                    }
                },
                enabled = canClick,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canClick) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canClick) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = when {
                        state.isWaitingForInput -> Icons.Default.Check
                        state.isRunning -> Icons.Default.HourglassEmpty
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        state.isWaitingForInput -> "Send"
                        state.isRunning -> "Running"
                        else -> "Run"
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionControls(state: MainViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { state.createNewSession() },
            enabled = !state.isRunning,
            label = { Text("New Session") },
            leadingIcon = {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        
        AssistChip(
            onClick = { state.continueCurrentSession() },
            enabled = !state.isRunning && state.currentSessionId != null,
            label = { Text("Continue") },
            leadingIcon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
        
        state.currentSessionId?.let { sessionId ->
            SuggestionChip(
                onClick = { },
                label = { 
                    Text(
                        "Session: ${sessionId.take(8)}...",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageItem>,
    onForkFromMessage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Start a conversation by typing below 👇",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    when (message.role) {
                        MessageRole.SYSTEM -> {
                            SystemMessage(content = message.content)
                        }
                        else -> {
                            MessageBubble(
                                message = message,
                                isCurrentUser = message.role == MessageRole.USER,
                                onForkFromHere = if (index > 0 && message.role == MessageRole.ASSISTANT) {
                                    { onForkFromMessage(index) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

// Material 3 Color Scheme - Expressive Dark Theme
private val md_theme_dark_primary = Color(0xFFD0BCFF)
private val md_theme_dark_onPrimary = Color(0xFF381E72)
private val md_theme_dark_primaryContainer = Color(0xFF4F378B)
private val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
private val md_theme_dark_secondary = Color(0xFFCCC2DC)
private val md_theme_dark_onSecondary = Color(0xFF332D41)
private val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
private val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
private val md_theme_dark_tertiary = Color(0xFFEFB8C8)
private val md_theme_dark_onTertiary = Color(0xFF492532)
private val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
private val md_theme_dark_error = Color(0xFFF2B8B5)
private val md_theme_dark_errorContainer = Color(0xFF8C1D18)
private val md_theme_dark_onError = Color(0xFF601410)
private val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
private val md_theme_dark_background = Color(0xFF1C1B1F)
private val md_theme_dark_onBackground = Color(0xFFE6E1E5)
private val md_theme_dark_surface = Color(0xFF1C1B1F)
private val md_theme_dark_onSurface = Color(0xFFE6E1E5)
private val md_theme_dark_surfaceVariant = Color(0xFF49454F)
private val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
private val md_theme_dark_outline = Color(0xFF938F99)
private val md_theme_dark_inverseOnSurface = Color(0xFF1C1B1F)
private val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
private val md_theme_dark_inversePrimary = Color(0xFF6750A4)
private val md_theme_dark_surfaceTint = Color(0xFFD0BCFF)
private val md_theme_dark_outlineVariant = Color(0xFF49454F)
private val md_theme_dark_scrim = Color(0xFF000000)
