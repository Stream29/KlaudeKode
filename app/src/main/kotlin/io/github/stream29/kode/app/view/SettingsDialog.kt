package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stream29.kode.app.viewmodel.AppUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val ui by viewModel.appUiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Models", "Auth Providers", "Preferences")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Models",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            SettingsContent(
                viewModel = viewModel,
                ui = ui,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabs = tabs
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
public fun SettingsContent(
    viewModel: MainViewModel,
    ui: AppUiState,
    modifier: Modifier,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>
) {
    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp)
        ) {
            when (selectedTab) {
                0 -> ModelsTab(viewModel, ui)
                1 -> AuthTab(viewModel, ui)
                2 -> PreferencesTab(viewModel, ui)
            }
        }
    }
}



@Composable
public fun ModelsTab(viewModel: MainViewModel, ui: AppUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<LlmModelConfig?>(null) }
    val models = ui.models
    val auths = ui.auths

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Configured Models (${models.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = { showAddDialog = true },
                enabled = auths.isNotEmpty()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Model")
            }
        }

        if (auths.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "You need to add an Auth Provider first before creating models.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (models.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No models configured. Add your first model to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(models) { model ->
                    ModelCard(
                        model = model,
                        auth = auths.find { it.id == model.authId },
                        isActive = model.id == ui.activeModelId,
                        onActivate = { viewModel.switchModel(model.id) },
                        onEdit = { editingModel = model },
                        onDelete = { viewModel.deleteModel(model.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ModelDialog(
            viewModel = viewModel,
            _ui = ui,
            auths = auths,
            onDismiss = { showAddDialog = false },
            onConfirm = { model ->
                viewModel.addModel(model)
                showAddDialog = false
            }
        )
    }

    editingModel?.let { model ->
        ModelDialog(
            viewModel = viewModel,
            _ui = ui,
            model = model,
            auths = auths,
            onDismiss = { editingModel = null },
            onConfirm = { updated ->
                viewModel.updateModel(model.id, updated)
                editingModel = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelCard(
    model: LlmModelConfig,
    auth: LlmAuthConfig?,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName ?: model.model,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Model: ${model.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID: ${model.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    auth?.let {
                        Text(
                            text = "Provider: ${it.provider} (${it.id})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } ?: Text(
                        text = "Provider: Not found (authId: ${model.authId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row {
                    if (!isActive) {
                        IconButton(onClick = onActivate) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Activate",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (isActive) {
                Text(
                    "Active Model",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDialog(
    viewModel: MainViewModel,
    _ui: AppUiState,
    model: LlmModelConfig? = null,
    auths: List<LlmAuthConfig>,
    onDismiss: () -> Unit,
    onConfirm: (LlmModelConfig) -> Unit
) {
    var id by remember { mutableStateOf(model?.id ?: "") }
    var displayName by remember { mutableStateOf(model?.displayName ?: "") }
    var modelName by remember { mutableStateOf(model?.model ?: "") }
    var selectedAuthId by remember { mutableStateOf(model?.authId ?: auths.firstOrNull()?.id ?: "") }
    var idError by remember { mutableStateOf<String?>(null) }

    val isEditing = model != null


    val suggestedId = viewModel.generateDefaultModelId(modelName, selectedAuthId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Model" else "Add Model") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        idError = null
                    },
                    label = { Text("ID") },
                    enabled = !isEditing,
                    isError = idError != null,
                    placeholder = {
                        if (suggestedId.isNotBlank()) {
                            Text(suggestedId)
                        } else {
                            Text("Auto-generated")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    supportingText = { Text("Optional friendly name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name *") },
                    supportingText = { Text("e.g., gpt-4o, claude-sonnet-4-5") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (auths.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedAuth = auths.find { it.id == selectedAuthId }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAuth?.let { "${it.provider} (${it.id})" } ?: "Select Provider",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auth Provider *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            auths.forEach { auth ->
                                DropdownMenuItem(
                                    text = { Text("${auth.provider} (${auth.id})") },
                                    onClick = {
                                        selectedAuthId = auth.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var resolvedId = id
                    if (resolvedId.isBlank()) {
                        resolvedId = viewModel.generateDefaultModelId(modelName, selectedAuthId)
                    }
                    if (resolvedId.isBlank()) {
                        idError = "Unable to generate ID"
                        return@TextButton
                    }
                    if (modelName.isBlank()) {
                        return@TextButton
                    }
                    if (selectedAuthId.isBlank()) {
                        return@TextButton
                    }

                    onConfirm(
                        LlmModelConfig(
                            id = resolvedId,
                            displayName = displayName.takeIf { it.isNotBlank() },
                            model = modelName,
                            authId = selectedAuthId
                        )
                    )
                },
                enabled = modelName.isNotBlank() && selectedAuthId.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
public fun AuthTab(viewModel: MainViewModel, ui: AppUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    var deletingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    val auths = ui.auths
    val models = ui.models

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Auth Providers (${auths.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Provider")
            }
        }

        if (auths.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No auth providers configured. Add your first provider to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auths) { auth ->
                    val dependentModels = models.filter { it.authId == auth.id }
                    AuthCard(
                        auth = auth,
                        dependentModels = dependentModels,
                        onEdit = { editingAuth = auth },
                        onDelete = { deletingAuth = auth }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AuthDialog(
            viewModel = viewModel,
            _ui = ui,
            onDismiss = { showAddDialog = false },
            onConfirm = { auth ->
                viewModel.addAuth(auth)
                showAddDialog = false
            }
        )
    }

    editingAuth?.let { auth ->
        AuthDialog(
            viewModel = viewModel,
            _ui = ui,
            auth = auth,
            onDismiss = { editingAuth = null },
            onConfirm = { updated ->
                viewModel.updateAuth(auth.id, updated)
                editingAuth = null
            }
        )
    }

    deletingAuth?.let { auth ->
        val dependentModels = models.filter { it.authId == auth.id }
        AlertDialog(
            onDismissRequest = { deletingAuth = null },
            title = { Text("Delete Auth Provider") },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            text = {
                Column {
                    Text("Are you sure you want to delete ${auth.provider} (${auth.id})?")
                    if (dependentModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Warning: ${dependentModels.size} model(s) depend on this auth:",
                            color = MaterialTheme.colorScheme.error
                        )
                        dependentModels.forEach { model ->
                            Text(
                                "  • ${model.displayName ?: model.model}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "These models will stop working.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAuth(auth.id)
                        deletingAuth = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAuth = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AuthCard(
    auth: LlmAuthConfig,
    dependentModels: List<LlmModelConfig>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (auth.provider) {
                            "Anthropic" -> Icons.Default.Psychology
                            "OpenAI" -> Icons.AutoMirrored.Filled.Chat
                            "Moonshot" -> Icons.Default.Nightlight
                            "Gemini" -> Icons.Default.Star
                            "DeepSeek" -> Icons.Default.Search
                            else -> Icons.Default.Cloud
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = auth.provider,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "ID: ${auth.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (showApiKey) auth.apiKey else "••••••••" + auth.apiKey.takeLast(4),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showApiKey) "Hide" else "Show"
                    )
                }
            }

            
            auth.baseUrl?.let { url ->
                Text(
                    text = "Base URL: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            
            if (dependentModels.isNotEmpty()) {
                Text(
                    text = "Used by ${dependentModels.size} model(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthDialog(
    viewModel: MainViewModel,
    _ui: AppUiState,
    auth: LlmAuthConfig? = null,
    onDismiss: () -> Unit,
    onConfirm: (LlmAuthConfig) -> Unit
) {
    var id by remember { mutableStateOf(auth?.id ?: "") }
    var apiKey by remember { mutableStateOf(auth?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(auth?.baseUrl ?: "") }
    var selectedProvider by remember {
        mutableStateOf(
            when (auth) {
                is LlmAuthConfig.Anthropic -> "Anthropic"
                is LlmAuthConfig.OpenAI -> "OpenAI"
                is LlmAuthConfig.Moonshot -> "Moonshot"
                is LlmAuthConfig.Gemini -> "Gemini"
                is LlmAuthConfig.DeepSeek -> "DeepSeek"
                is LlmAuthConfig.OpenAICompatible -> "OpenAICompatible"
                else -> "Anthropic"
            }
        )
    }
    var customName by remember {
        mutableStateOf(
            if (auth is LlmAuthConfig.OpenAICompatible) auth.name else ""
        )
    }
    var idError by remember { mutableStateOf<String?>(null) }

    val isEditing = auth != null
    val providers = listOf("Anthropic", "OpenAI", "Moonshot", "Gemini", "DeepSeek", "OpenAICompatible")
    val needsBaseUrl = selectedProvider == "OpenAICompatible"
    val suggestedId = viewModel.generateDefaultAuthId(selectedProvider, customName)


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Auth Provider" else "Add Auth Provider") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProvider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider) },
                                onClick = {
                                    selectedProvider = provider
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                
                if (needsBaseUrl) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Provider Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        idError = null
                    },
                    label = { Text("ID") },
                    enabled = !isEditing,
                    isError = idError != null,
                    placeholder = {
                        if (suggestedId.isNotBlank()) {
                            Text(suggestedId)
                        } else {
                            Text("Auto-generated")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key *") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(if (needsBaseUrl) "Base URL *" else "Base URL") },
                    supportingText = { Text("Optional custom endpoint") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var resolvedId = id
                    if (resolvedId.isBlank()) {
                        resolvedId = viewModel.generateDefaultAuthId(selectedProvider, customName)
                    }
                    if (resolvedId.isBlank()) {
                        idError = "Unable to generate ID"
                        return@TextButton
                    }
                    if (apiKey.isBlank()) {
                        return@TextButton
                    }
                    if (needsBaseUrl && baseUrl.isBlank()) {
                        return@TextButton
                    }

                    val config = when (selectedProvider) {
                        "Anthropic" -> LlmAuthConfig.Anthropic(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "OpenAI" -> LlmAuthConfig.OpenAI(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "Moonshot" -> LlmAuthConfig.Moonshot(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "Gemini" -> LlmAuthConfig.Gemini(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "DeepSeek" -> LlmAuthConfig.DeepSeek(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "OpenAICompatible" -> LlmAuthConfig.OpenAICompatible(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            name = customName
                        )
                        else -> LlmAuthConfig.Anthropic(
                            id = resolvedId,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                    }
                    onConfirm(config)
                },
                enabled = apiKey.isNotBlank() && (!needsBaseUrl || baseUrl.isNotBlank()) &&
                        (!needsBaseUrl || customName.isNotBlank())
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
public fun PreferencesTab(viewModel: MainViewModel, ui: AppUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            
            Text(
                "Active Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModelSelectionSection(viewModel, ui)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Runtime Defaults",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            DefaultModelSelectionSection(viewModel, ui)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Thinking Mode",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Use deliberate reasoning by default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ui.defaultThinking,
                    onCheckedChange = { viewModel.defaultThinking = it }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Generation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Temperature",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        String.format("%.1f", ui.temperature),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = ui.temperature,
                    onValueChange = { viewModel.temperature = it },
                    valueRange = 0f..1f,
                    steps = 9
                )
                Text(
                    "Lower = more focused, Higher = more creative",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        
    }
}

@Composable
public fun AppSettingsContent(viewModel: MainViewModel, ui: AppUiState) {
    var approvalActionInput by remember { mutableStateOf("") }
    var appDataDirDraft by remember(ui.appDataDir) { mutableStateOf(ui.appDataDir) }
    val showDataDirChangeDialogState = remember { mutableStateOf(false) }
    val currentAppDataDirPath = remember(ui.appDataDir) { viewModel.resolveAppDataDirPath(ui.appDataDir) }
    val draftAppDataDirPath = remember(appDataDirDraft) { viewModel.resolveAppDataDirPath(appDataDirDraft) }
    val appDataDirChanged = draftAppDataDirPath != currentAppDataDirPath

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "General",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = appDataDirDraft,
                onValueChange = { appDataDirDraft = it },
                label = { Text("App data directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draftAppDataDirPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { showDataDirChangeDialogState.value = true },
                    enabled = appDataDirChanged,
                ) {
                    Text("Apply")
                }
            }

            Text(
                "Session/global state root (restart required after changing this path)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ui.defaultSessionDir,
                onValueChange = { viewModel.defaultSessionDir = it },
                label = { Text("Default work directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                "Used as agent execution workdir, not app-private storage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Skills & Preset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ui.skillsDir,
                onValueChange = { viewModel.skillsDir = it },
                label = { Text("Skills directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ui.presetBuiltin,
                onValueChange = { viewModel.presetBuiltin = it },
                label = { Text("Preset builtin") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ui.presetFile,
                onValueChange = { viewModel.presetFile = it },
                label = { Text("Preset file path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Logging",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ui.logLevel,
                onValueChange = { viewModel.logLevel = it },
                label = { Text("Log level") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ui.logFile,
                onValueChange = { viewModel.logFile = it },
                label = { Text("Log file") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            ThemeSelectionSection(viewModel, ui)

            Spacer(modifier = Modifier.height(12.dp))
            ChatLayoutSection(viewModel, ui)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Approvals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Default YOLO",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Auto-approve tool calls by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ui.approvalDefaultYolo,
                            onCheckedChange = {
                                viewModel.approvalDefaultYolo = it
                                viewModel.yoloEnabled = it
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Auto-approve actions",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (ui.approvalAutoApproveActions.isEmpty()) {
                            Text(
                                "No auto-approve actions configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ui.approvalAutoApproveActions.forEach { action ->
                                    AssistChip(
                                        onClick = { viewModel.removeApprovalAction(action) },
                                        label = { Text(action) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = approvalActionInput,
                            onValueChange = { approvalActionInput = it },
                            label = { Text("Add action") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                viewModel.addApprovalAction(approvalActionInput)
                                approvalActionInput = ""
                            }
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Web Search",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = ui.webSearchProvider,
                        onValueChange = { viewModel.webSearchProvider = it },
                        label = { Text("Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webSearchApiKey,
                        onValueChange = { viewModel.webSearchApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webSearchBaseUrl,
                        onValueChange = { viewModel.webSearchBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webSearchHeaders,
                        onValueChange = { viewModel.webSearchHeaders = it },
                        label = { Text("Headers (Key:Value per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = ui.webSearchEnv,
                        onValueChange = { viewModel.webSearchEnv = it },
                        label = { Text("Env (KEY=VALUE per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Web Fetch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = ui.webFetchProvider,
                        onValueChange = { viewModel.webFetchProvider = it },
                        label = { Text("Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webFetchApiKey,
                        onValueChange = { viewModel.webFetchApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webFetchBaseUrl,
                        onValueChange = { viewModel.webFetchBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ui.webFetchHeaders,
                        onValueChange = { viewModel.webFetchHeaders = it },
                        label = { Text("Headers (Key:Value per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = ui.webFetchEnv,
                        onValueChange = { viewModel.webFetchEnv = it },
                        label = { Text("Env (KEY=VALUE per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-save Sessions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Automatically save conversation history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ui.autoSaveSessions,
                    onCheckedChange = { viewModel.autoSaveSessions = it }
                )
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Data Directory",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "~/.kode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedIconButton(
                    onClick = { viewModel.openDataDirectory() }
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Open"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.loadConfigForEditing() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Config File (YAML)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { viewModel.confirmClearAllSessions() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Sessions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "This will permanently delete all conversation history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))

            
        }
    }

    if (showDataDirChangeDialogState.value) {
        AlertDialog(
            onDismissRequest = { showDataDirChangeDialogState.value = false },
            title = { Text("Change app data directory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: $currentAppDataDirPath")
                    Text("Target: $draftAppDataDirPath")
                    Text("Do you want to migrate existing app data to the new directory?")
                    Text(
                        text = "Changing this path requires app restart to switch session storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.applyAppDataDirChange(
                                newInput = appDataDirDraft,
                                migrateExistingData = false,
                            )
                            showDataDirChangeDialogState.value = false
                        },
                    ) {
                        Text("Change only")
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.applyAppDataDirChange(
                                newInput = appDataDirDraft,
                                migrateExistingData = true,
                            )
                            showDataDirChangeDialogState.value = false
                        },
                    ) {
                        Text("Migrate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDataDirChangeDialogState.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultModelSelectionSection(viewModel: MainViewModel, ui: AppUiState) {
    var expanded by remember { mutableStateOf(false) }
    val models = ui.models
    val auths = ui.auths
    val defaultId = ui.defaultModelId
    val defaultModel = models.find { it.id == defaultId }

    fun getModelDisplayName(model: LlmModelConfig): String {
        val auth = auths.find { it.id == model.authId }
        val provider = auth?.provider ?: "Unknown"
        val name = model.displayName ?: model.model
        return "$provider - $name"
    }

    if (models.isEmpty()) {
        Text(
            "No models configured. Go to the Models tab to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = defaultModel?.let { getModelDisplayName(it) } ?: "Select default model",
                onValueChange = {},
                readOnly = true,
                label = { Text("Default model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(getModelDisplayName(model))
                                Text(
                                    "ID: ${model.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            viewModel.setDefaultModel(model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionSection(viewModel: MainViewModel, ui: AppUiState) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("dark", "light")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = ui.uiTheme,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        viewModel.uiTheme = option
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatLayoutSection(viewModel: MainViewModel, ui: AppUiState) {
    data class AlignmentOption(val value: String, val label: String)

    val alignmentOptions = listOf(
        AlignmentOption(value = "left", label = "Same side (left)"),
        AlignmentOption(value = "split", label = "Split sides"),
    )
    val selectedAlignment = alignmentOptions.firstOrNull { it.value == ui.messageAlignment }
        ?: alignmentOptions.first()
    var alignmentExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Chat layout",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        ExposedDropdownMenuBox(
            expanded = alignmentExpanded,
            onExpandedChange = { alignmentExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedAlignment.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Message alignment") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = alignmentExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = alignmentExpanded,
                onDismissRequest = { alignmentExpanded = false },
            ) {
                alignmentOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            viewModel.messageAlignment = option.value
                            alignmentExpanded = false
                        },
                    )
                }
            }
        }

        val ratio = ui.messageMaxWidthRatio.coerceIn(0.5f, 1f)
        val ratioPercent = (ratio * 100).toInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Message max width",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$ratioPercent%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Slider(
            value = ratio,
            onValueChange = { viewModel.messageMaxWidthRatio = it },
            valueRange = 0.5f..1f,
            steps = 9,
        )

        Text(
            text = "Controls the maximum bubble width relative to the chat pane.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(viewModel: MainViewModel, ui: AppUiState) {
    var expanded by remember { mutableStateOf(false) }
    val models = ui.models
    val auths = ui.auths
    val activeId = ui.activeModelId
    val activeModel = models.find { it.id == activeId }

    fun getModelDisplayName(model: LlmModelConfig): String {
        val auth = auths.find { it.id == model.authId }
        val provider = auth?.provider ?: "Unknown"
        val name = model.displayName ?: model.model
        return "$provider - $name"
    }

    if (models.isEmpty()) {
        Text(
            "No models configured. Go to the Models tab to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = activeModel?.let { getModelDisplayName(it) } ?: "Select a model",
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(getModelDisplayName(model))
                                Text(
                                    "ID: ${model.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            viewModel.switchModel(model.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
