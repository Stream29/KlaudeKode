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
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            SettingsContent(
                viewModel = viewModel,
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
                0 -> ModelsTab(viewModel)
                1 -> AuthTab(viewModel)
                2 -> PreferencesTab(viewModel)
            }
        }
    }
}



@Composable
private fun ModelsTab(viewModel: MainViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<LlmModelConfig?>(null) }
    val models = viewModel.models
    val auths = viewModel.auths

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
                        isActive = model.id == viewModel.activeModelId,
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
                    label = { Text("ID *") },
                    enabled = !isEditing,
                    isError = idError != null,
                    supportingText = idError?.let { { Text(it) } },
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
                    if (id.isBlank()) {
                        idError = "ID is required"
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
                            id = id,
                            displayName = displayName.takeIf { it.isNotBlank() },
                            model = modelName,
                            authId = selectedAuthId
                        )
                    )
                },
                enabled = id.isNotBlank() && modelName.isNotBlank() && selectedAuthId.isNotBlank()
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
private fun AuthTab(viewModel: MainViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    var deletingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    val auths = viewModel.auths
    val models = viewModel.models

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
            onDismiss = { showAddDialog = false },
            onConfirm = { auth ->
                viewModel.addAuth(auth)
                showAddDialog = false
            }
        )
    }

    editingAuth?.let { auth ->
        AuthDialog(
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
                    label = { Text("ID *") },
                    enabled = !isEditing,
                    isError = idError != null,
                    supportingText = idError?.let { { Text(it) } } ?: { Text("Unique identifier for this auth") },
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
                    if (id.isBlank()) {
                        idError = "ID is required"
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
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "OpenAI" -> LlmAuthConfig.OpenAI(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "Moonshot" -> LlmAuthConfig.Moonshot(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "Gemini" -> LlmAuthConfig.Gemini(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "DeepSeek" -> LlmAuthConfig.DeepSeek(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                        "OpenAICompatible" -> LlmAuthConfig.OpenAICompatible(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            name = customName
                        )
                        else -> LlmAuthConfig.Anthropic(
                            id = id,
                            apiKey = apiKey,
                            baseUrl = baseUrl.takeIf { it.isNotBlank() }
                        )
                    }
                    onConfirm(config)
                },
                enabled = id.isNotBlank() && apiKey.isNotBlank() && (!needsBaseUrl || baseUrl.isNotBlank()) &&
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
private fun PreferencesTab(viewModel: MainViewModel) {
    var approvalActionInput by remember { mutableStateOf("") }

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

            ModelSelectionSection(viewModel)
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
            DefaultModelSelectionSection(viewModel)

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
                    checked = viewModel.defaultThinking,
                    onCheckedChange = { viewModel.defaultThinking = it }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loop Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            LoopControlField(
                label = "Max steps per turn",
                value = viewModel.maxStepsPerTurn,
                onValueChange = { viewModel.maxStepsPerTurn = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LoopControlField(
                label = "Max retries per step",
                value = viewModel.maxRetriesPerStep,
                onValueChange = { viewModel.maxRetriesPerStep = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LoopControlField(
                label = "Max Ralph iterations",
                value = viewModel.maxRalphIterations,
                onValueChange = { viewModel.maxRalphIterations = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LoopControlField(
                label = "Reserved context size",
                value = viewModel.reservedContextSize,
                onValueChange = { viewModel.reservedContextSize = it }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Workspace",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.workDir,
                onValueChange = { viewModel.workDir = it },
                label = { Text("Working directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Skills & Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.skillsDir,
                onValueChange = { viewModel.skillsDir = it },
                label = { Text("Skills directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.agentBuiltin,
                onValueChange = { viewModel.agentBuiltin = it },
                label = { Text("Agent builtin") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.agentFile,
                onValueChange = { viewModel.agentFile = it },
                label = { Text("Agent file path") },
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
                value = viewModel.logLevel,
                onValueChange = { viewModel.logLevel = it },
                label = { Text("Log level") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.logFile,
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
            ThemeSelectionSection(viewModel)
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
                            checked = viewModel.approvalDefaultYolo,
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
                        if (viewModel.approvalAutoApproveActions.isEmpty()) {
                            Text(
                                "No auto-approve actions configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                viewModel.approvalAutoApproveActions.forEach { action ->
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
                        value = viewModel.webSearchProvider,
                        onValueChange = { viewModel.webSearchProvider = it },
                        label = { Text("Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webSearchApiKey,
                        onValueChange = { viewModel.webSearchApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webSearchBaseUrl,
                        onValueChange = { viewModel.webSearchBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webSearchHeaders,
                        onValueChange = { viewModel.webSearchHeaders = it },
                        label = { Text("Headers (Key:Value per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = viewModel.webSearchEnv,
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
                        value = viewModel.webFetchProvider,
                        onValueChange = { viewModel.webFetchProvider = it },
                        label = { Text("Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webFetchApiKey,
                        onValueChange = { viewModel.webFetchApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webFetchBaseUrl,
                        onValueChange = { viewModel.webFetchBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.webFetchHeaders,
                        onValueChange = { viewModel.webFetchHeaders = it },
                        label = { Text("Headers (Key:Value per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = viewModel.webFetchEnv,
                        onValueChange = { viewModel.webFetchEnv = it },
                        label = { Text("Env (KEY=VALUE per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            
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
                    checked = viewModel.autoSaveSessions,
                    onCheckedChange = { viewModel.autoSaveSessions = it }
                )
            }

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
                        String.format("%.1f", viewModel.temperature),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = viewModel.temperature,
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

            FilledTonalButton(
                onClick = { viewModel.savePreferences() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Preferences")
            }
        }
    }
}

@Composable
private fun LoopControlField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            textValue = input
            val parsed = input.toIntOrNull()
            if (parsed != null) {
                onValueChange(parsed)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultModelSelectionSection(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val models = viewModel.models
    val auths = viewModel.auths
    val defaultId = viewModel.defaultModelId
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
private fun ThemeSelectionSection(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("dark", "light")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = viewModel.uiTheme,
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
private fun ModelSelectionSection(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val models = viewModel.models
    val auths = viewModel.auths
    val activeId = viewModel.activeModelId
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
