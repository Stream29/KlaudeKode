package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stream29.kode.app.util.formatModelDisplayName
import io.github.stream29.kode.app.viewmodel.AppUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel

@Composable
public fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Settings") },
        text = {
            SettingsContent(
                viewModel = viewModel,
                ui = viewModel.appUiState.value,
                selectedTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab },
                tabs = tabs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
public fun SettingsContent(
    viewModel: MainViewModel,
    ui: AppUiState,
    modifier: Modifier,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>,
) {
    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { onTabSelected(index) },
                    text = { Text(title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> ModelsTab(viewModel = viewModel, ui = ui)
            1 -> AuthTab(ui = ui)
            else -> PreferencesTab(ui = ui)
        }
    }
}

@Composable
private fun ModelsTab(viewModel: MainViewModel, ui: AppUiState) {
    val authById = remember(ui.auths) { ui.auths.associateBy { auth -> auth.id } }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Configured Models (${ui.models.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (ui.models.isEmpty()) {
            Text(
                text = "No models configured",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = ui.models, key = { model -> model.id }) { model ->
                val isActive = model.id == ui.activeModelId
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = formatModelDisplayName(model = model, auths = ui.auths),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "id: ${model.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val authLabel = authById[model.authId]?.name ?: model.authId
                        Text(
                            text = "auth: $authLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isActive) "Active" else "Inactive",
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (!isActive) {
                                AssistChip(
                                    onClick = { viewModel.switchModel(model.id) },
                                    label = { Text("Set Active") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTab(ui: AppUiState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Auth Providers (${ui.auths.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (ui.auths.isEmpty()) {
            Text(
                text = "No auth providers configured",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = ui.auths, key = { auth -> auth.id }) { auth ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = auth.name ?: auth.id,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "provider: ${auth.providerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "id: ${auth.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesTab(ui: AppUiState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        PreferenceRow(label = "Theme", value = ui.uiTheme)
        PreferenceRow(label = "Message Alignment", value = ui.messageAlignment)
        PreferenceRow(label = "Message Width", value = ui.messageMaxWidthRatio.toString())
        PreferenceRow(label = "Send Key", value = ui.sendKeyMode)
        PreferenceRow(label = "Last Session", value = ui.lastOpenedSessionId ?: "(none)")
    }
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
public fun AddModelDialogDestination(
    viewModel: MainViewModel,
    ui: AppUiState,
    preselectedAuthId: String?,
    onDismiss: () -> Unit,
) {
    LegacyDialogUnavailable(title = "Add Model", onDismiss = onDismiss)
}

@Composable
public fun EditModelDialogDestination(
    viewModel: MainViewModel,
    ui: AppUiState,
    modelId: String,
    onDismiss: () -> Unit,
) {
    LegacyDialogUnavailable(title = "Edit Model", onDismiss = onDismiss)
}

@Composable
public fun AddAuthDialogDestination(
    viewModel: MainViewModel,
    ui: AppUiState,
    onDismiss: () -> Unit,
) {
    LegacyDialogUnavailable(title = "Add Auth", onDismiss = onDismiss)
}

@Composable
public fun EditAuthDialogDestination(
    viewModel: MainViewModel,
    ui: AppUiState,
    authId: String,
    onDismiss: () -> Unit,
) {
    LegacyDialogUnavailable(title = "Edit Auth", onDismiss = onDismiss)
}

@Composable
public fun DeleteAuthConfirmDialogDestination(
    viewModel: MainViewModel,
    ui: AppUiState,
    authId: String,
    onDismiss: () -> Unit,
) {
    LegacyDialogUnavailable(title = "Delete Auth", onDismiss = onDismiss)
}

@Composable
private fun LegacyDialogUnavailable(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Text(
                text = "This dialog is being migrated to page-scoped ViewModels.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
public fun AppSettingsContent(viewModel: MainViewModel, ui: AppUiState) {
    val tabs = listOf("Models", "Auth Providers", "Preferences")
    var selectedTab by remember { mutableStateOf(0) }
    SettingsContent(
        viewModel = viewModel,
        ui = ui,
        modifier = Modifier.fillMaxSize(),
        selectedTab = selectedTab,
        onTabSelected = { tab -> selectedTab = tab },
        tabs = tabs,
    )
}
