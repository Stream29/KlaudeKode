package io.github.stream29.kode.app.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.app.model.MessageAlignmentPreference
import io.github.stream29.kode.app.model.SendKeyModePreference
import io.github.stream29.kode.app.util.formatModelDisplayName
import io.github.stream29.kode.app.viewmodel.AppUiState
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.api.AnthropicServiceTierConfig
import io.github.stream29.kode.config.api.AnthropicThinkingConfig
import io.github.stream29.kode.config.api.GeminiThinkingConfig
import io.github.stream29.kode.config.api.GeminiThinkingLevelConfig
import io.github.stream29.kode.config.api.OPENAI_COMPATIBLE_PROVIDER_IDS
import io.github.stream29.kode.config.api.OPENAI_NATIVE_PROVIDER_IDS
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.config.api.LlmModelParamsConfig
import io.github.stream29.kode.config.api.OAuthConfig
import io.github.stream29.kode.config.api.OpenAiEndpoint
import io.github.stream29.kode.config.api.OpenAiReasoningEffortConfig
import io.github.stream29.kode.config.api.OpenAiReasoningSummaryConfig
import io.github.stream29.kode.config.api.OpenAiServiceTierConfig
import io.github.stream29.kode.config.api.OpenAiTruncationConfig
import io.github.stream29.kode.providers.api.ProviderAuthMode
import io.github.stream29.kode.providers.api.ProviderOAuthAuthCodePkcePreset
import io.github.stream29.kode.providers.api.ProviderOAuthDeviceFlowPreset
import io.github.stream29.kode.providers.api.ProviderPreset
import io.github.stream29.kode.app.viewmodel.OAuthStatusUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    val authById = remember(auths) { auths.associateBy { auth -> auth.id } }

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
                items(
                    items = models,
                    key = { model -> model.id },
                ) { model ->
                    ModelCard(
                        model = model,
                        auth = authById[model.authId],
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
                            text = "Provider: ${(it.name ?: it.providerId)} (${it.id})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } ?: Text(
                        text = "Provider: Not found (authId: ${model.authId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    model.params?.summaryText()?.let { paramsSummary ->
                        Text(
                            text = "Params: $paramsSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
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

private data class OpenAiLikeModelDialogState(
    val endpoint: OpenAiEndpoint,
    val reasoningEffort: OpenAiReasoningEffortConfig?,
    val reasoningSummary: OpenAiReasoningSummaryConfig?,
    val chatBase: OpenAiBaseEditorState,
    val responsesBase: OpenAiBaseEditorState,
    val chatServiceTier: OpenAiServiceTierConfig?,
    val chatFrequencyPenalty: String,
    val chatPresencePenalty: String,
    val chatTopP: String,
    val chatTopLogprobs: String,
    val chatStop: String,
    val chatParallelToolCalls: OptionalBooleanChoice,
    val chatStore: OptionalBooleanChoice,
    val chatLogprobs: OptionalBooleanChoice,
    val chatPromptCacheKey: String,
    val chatSafetyIdentifier: String,
    val responsesBackground: OptionalBooleanChoice,
    val responsesInclude: String,
    val responsesMaxToolCalls: String,
    val responsesParallelToolCalls: OptionalBooleanChoice,
    val responsesTruncation: OpenAiTruncationConfig?,
    val responsesServiceTier: OpenAiServiceTierConfig?,
    val responsesStore: OptionalBooleanChoice,
    val responsesLogprobs: OptionalBooleanChoice,
    val responsesTopP: String,
    val responsesTopLogprobs: String,
    val responsesPromptCacheKey: String,
    val responsesSafetyIdentifier: String,
)

private data class AnthropicModelDialogState(
    val thinkingEnabled: Boolean,
    val thinkingBudget: String,
    val topP: String,
    val topK: String,
    val stopSequences: String,
    val container: String,
    val serviceTier: AnthropicServiceTierConfig?,
)

private data class GeminiModelDialogState(
    val thinkingBudget: String,
    val thinkingLevel: GeminiThinkingLevelConfig?,
    val topP: String,
    val topK: String,
)

private data class DeepSeekModelDialogState(
    val frequencyPenalty: String,
    val presencePenalty: String,
    val topP: String,
    val topLogprobs: String,
    val stop: String,
    val logprobs: OptionalBooleanChoice,
)

private data class OpenRouterModelDialogState(
    val reasoningEffort: String,
    val frequencyPenalty: String,
    val presencePenalty: String,
    val topP: String,
    val topK: String,
    val topLogprobs: String,
    val repetitionPenalty: String,
    val minP: String,
    val topA: String,
    val stop: String,
    val transforms: String,
    val models: String,
    val route: String,
    val providerPreferences: String,
    val logprobs: OptionalBooleanChoice,
)

private class OpenAiLikeModelDialogViewModel(initialParams: LlmModelParamsConfig?) {
    private val _state: MutableStateFlow<OpenAiLikeModelDialogState> = MutableStateFlow(
        OpenAiLikeModelDialogState(
            endpoint = extractOpenAiEndpoint(initialParams),
            reasoningEffort = extractOpenAiReasoningEffort(initialParams),
            reasoningSummary = extractOpenAiReasoningSummary(initialParams),
            chatBase = extractOpenAiChatBaseEditorState(initialParams),
            responsesBase = extractOpenAiResponsesBaseEditorState(initialParams),
            chatServiceTier = extractOpenAiChatServiceTier(initialParams),
            chatFrequencyPenalty = extractOpenAiChatFrequencyPenalty(initialParams),
            chatPresencePenalty = extractOpenAiChatPresencePenalty(initialParams),
            chatTopP = extractOpenAiChatTopP(initialParams),
            chatTopLogprobs = extractOpenAiChatTopLogprobs(initialParams),
            chatStop = extractOpenAiChatStop(initialParams),
            chatParallelToolCalls = extractOpenAiChatParallelToolCalls(initialParams),
            chatStore = extractOpenAiChatStore(initialParams),
            chatLogprobs = extractOpenAiChatLogprobs(initialParams),
            chatPromptCacheKey = extractOpenAiChatPromptCacheKey(initialParams),
            chatSafetyIdentifier = extractOpenAiChatSafetyIdentifier(initialParams),
            responsesBackground = extractOpenAiResponsesBackground(initialParams),
            responsesInclude = extractOpenAiResponsesInclude(initialParams),
            responsesMaxToolCalls = extractOpenAiResponsesMaxToolCalls(initialParams),
            responsesParallelToolCalls = extractOpenAiResponsesParallelToolCalls(initialParams),
            responsesTruncation = extractOpenAiResponsesTruncation(initialParams),
            responsesServiceTier = extractOpenAiResponsesServiceTier(initialParams),
            responsesStore = extractOpenAiResponsesStore(initialParams),
            responsesLogprobs = extractOpenAiResponsesLogprobs(initialParams),
            responsesTopP = extractOpenAiResponsesTopP(initialParams),
            responsesTopLogprobs = extractOpenAiResponsesTopLogprobs(initialParams),
            responsesPromptCacheKey = extractOpenAiResponsesPromptCacheKey(initialParams),
            responsesSafetyIdentifier = extractOpenAiResponsesSafetyIdentifier(initialParams),
        ),
    )

    val state: StateFlow<OpenAiLikeModelDialogState> = _state

    fun update(transform: (OpenAiLikeModelDialogState) -> OpenAiLikeModelDialogState) {
        _state.value = transform(_state.value)
    }
}

private class AnthropicModelDialogViewModel(initialParams: LlmModelParamsConfig?) {
    private val _state: MutableStateFlow<AnthropicModelDialogState> = MutableStateFlow(
        AnthropicModelDialogState(
            thinkingEnabled = extractAnthropicThinkingEnabled(initialParams),
            thinkingBudget = extractAnthropicThinkingBudget(initialParams),
            topP = extractAnthropicTopP(initialParams),
            topK = extractAnthropicTopK(initialParams),
            stopSequences = extractAnthropicStopSequences(initialParams),
            container = extractAnthropicContainer(initialParams),
            serviceTier = extractAnthropicServiceTier(initialParams),
        ),
    )

    val state: StateFlow<AnthropicModelDialogState> = _state

    fun update(transform: (AnthropicModelDialogState) -> AnthropicModelDialogState) {
        _state.value = transform(_state.value)
    }
}

private class GeminiModelDialogViewModel(initialParams: LlmModelParamsConfig?) {
    private val _state: MutableStateFlow<GeminiModelDialogState> = MutableStateFlow(
        GeminiModelDialogState(
            thinkingBudget = extractGeminiThinkingBudget(initialParams),
            thinkingLevel = extractGeminiThinkingLevel(initialParams),
            topP = extractGeminiTopP(initialParams),
            topK = extractGeminiTopK(initialParams),
        ),
    )

    val state: StateFlow<GeminiModelDialogState> = _state

    fun update(transform: (GeminiModelDialogState) -> GeminiModelDialogState) {
        _state.value = transform(_state.value)
    }
}

private class DeepSeekModelDialogViewModel(initialParams: LlmModelParamsConfig?) {
    private val _state: MutableStateFlow<DeepSeekModelDialogState> = MutableStateFlow(
        DeepSeekModelDialogState(
            frequencyPenalty = extractDeepSeekFrequencyPenalty(initialParams),
            presencePenalty = extractDeepSeekPresencePenalty(initialParams),
            topP = extractDeepSeekTopP(initialParams),
            topLogprobs = extractDeepSeekTopLogprobs(initialParams),
            stop = extractDeepSeekStop(initialParams),
            logprobs = extractDeepSeekLogprobs(initialParams),
        ),
    )

    val state: StateFlow<DeepSeekModelDialogState> = _state

    fun update(transform: (DeepSeekModelDialogState) -> DeepSeekModelDialogState) {
        _state.value = transform(_state.value)
    }
}

private class OpenRouterModelDialogViewModel(initialParams: LlmModelParamsConfig?) {
    private val _state: MutableStateFlow<OpenRouterModelDialogState> = MutableStateFlow(
        OpenRouterModelDialogState(
            reasoningEffort = extractOpenRouterReasoningEffort(initialParams),
            frequencyPenalty = extractOpenRouterFrequencyPenalty(initialParams),
            presencePenalty = extractOpenRouterPresencePenalty(initialParams),
            topP = extractOpenRouterTopP(initialParams),
            topK = extractOpenRouterTopK(initialParams),
            topLogprobs = extractOpenRouterTopLogprobs(initialParams),
            repetitionPenalty = extractOpenRouterRepetitionPenalty(initialParams),
            minP = extractOpenRouterMinP(initialParams),
            topA = extractOpenRouterTopA(initialParams),
            stop = extractOpenRouterStop(initialParams),
            transforms = extractOpenRouterTransforms(initialParams),
            models = extractOpenRouterModels(initialParams),
            route = extractOpenRouterRoute(initialParams),
            providerPreferences = extractOpenRouterProviderPreferences(initialParams),
            logprobs = extractOpenRouterLogprobs(initialParams),
        ),
    )

    val state: StateFlow<OpenRouterModelDialogState> = _state

    fun update(transform: (OpenRouterModelDialogState) -> OpenRouterModelDialogState) {
        _state.value = transform(_state.value)
    }
}

private class ModelDialogRuntimeManager(initialParams: LlmModelParamsConfig?) {
    val openAiLikeViewModel: OpenAiLikeModelDialogViewModel = OpenAiLikeModelDialogViewModel(initialParams)
    val anthropicViewModel: AnthropicModelDialogViewModel = AnthropicModelDialogViewModel(initialParams)
    val geminiViewModel: GeminiModelDialogViewModel = GeminiModelDialogViewModel(initialParams)
    val deepSeekViewModel: DeepSeekModelDialogViewModel = DeepSeekModelDialogViewModel(initialParams)
    val openRouterViewModel: OpenRouterModelDialogViewModel = OpenRouterModelDialogViewModel(initialParams)
}

private fun ModelDialogRuntimeManager.collectModelDialogBuildInput(
    providerId: String,
    existing: LlmModelParamsConfig?,
): ParamsBuildInput {
    val openAiLikeState = openAiLikeViewModel.state.value
    val anthropicState = anthropicViewModel.state.value
    val geminiState = geminiViewModel.state.value
    val deepSeekState = deepSeekViewModel.state.value
    val openRouterState = openRouterViewModel.state.value

    return ParamsBuildInput(
        providerId = providerId.trim().lowercase(),
        existing = existing,
        openAiEndpoint = openAiLikeState.endpoint,
        openAiReasoningEffort = openAiLikeState.reasoningEffort,
        openAiChatBase = openAiLikeState.chatBase,
        openAiResponsesBase = openAiLikeState.responsesBase,
        openAiReasoningSummary = openAiLikeState.reasoningSummary,
        openAiChatServiceTier = openAiLikeState.chatServiceTier,
        openAiChatFrequencyPenaltyInput = openAiLikeState.chatFrequencyPenalty,
        openAiChatPresencePenaltyInput = openAiLikeState.chatPresencePenalty,
        openAiChatTopPInput = openAiLikeState.chatTopP,
        openAiChatTopLogprobsInput = openAiLikeState.chatTopLogprobs,
        openAiChatStopInput = openAiLikeState.chatStop,
        openAiChatParallelToolCalls = openAiLikeState.chatParallelToolCalls,
        openAiChatStore = openAiLikeState.chatStore,
        openAiChatLogprobs = openAiLikeState.chatLogprobs,
        openAiChatPromptCacheKeyInput = openAiLikeState.chatPromptCacheKey,
        openAiChatSafetyIdentifierInput = openAiLikeState.chatSafetyIdentifier,
        openAiResponsesBackground = openAiLikeState.responsesBackground,
        openAiResponsesIncludeInput = openAiLikeState.responsesInclude,
        openAiResponsesMaxToolCallsInput = openAiLikeState.responsesMaxToolCalls,
        openAiResponsesParallelToolCalls = openAiLikeState.responsesParallelToolCalls,
        openAiResponsesTruncation = openAiLikeState.responsesTruncation,
        openAiResponsesServiceTier = openAiLikeState.responsesServiceTier,
        openAiResponsesStore = openAiLikeState.responsesStore,
        openAiResponsesLogprobs = openAiLikeState.responsesLogprobs,
        openAiResponsesTopPInput = openAiLikeState.responsesTopP,
        openAiResponsesTopLogprobsInput = openAiLikeState.responsesTopLogprobs,
        openAiResponsesPromptCacheKeyInput = openAiLikeState.responsesPromptCacheKey,
        openAiResponsesSafetyIdentifierInput = openAiLikeState.responsesSafetyIdentifier,
        anthropicThinkingEnabled = anthropicState.thinkingEnabled,
        anthropicThinkingBudgetInput = anthropicState.thinkingBudget,
        anthropicTopPInput = anthropicState.topP,
        anthropicTopKInput = anthropicState.topK,
        anthropicStopSequencesInput = anthropicState.stopSequences,
        anthropicContainerInput = anthropicState.container,
        anthropicServiceTier = anthropicState.serviceTier,
        geminiThinkingBudgetInput = geminiState.thinkingBudget,
        geminiThinkingLevel = geminiState.thinkingLevel,
        geminiTopPInput = geminiState.topP,
        geminiTopKInput = geminiState.topK,
        deepSeekFrequencyPenaltyInput = deepSeekState.frequencyPenalty,
        deepSeekPresencePenaltyInput = deepSeekState.presencePenalty,
        deepSeekTopPInput = deepSeekState.topP,
        deepSeekTopLogprobsInput = deepSeekState.topLogprobs,
        deepSeekStopInput = deepSeekState.stop,
        deepSeekLogprobs = deepSeekState.logprobs,
        openRouterReasoningEffortInput = openRouterState.reasoningEffort,
        openRouterFrequencyPenaltyInput = openRouterState.frequencyPenalty,
        openRouterPresencePenaltyInput = openRouterState.presencePenalty,
        openRouterTopPInput = openRouterState.topP,
        openRouterTopKInput = openRouterState.topK,
        openRouterTopLogprobsInput = openRouterState.topLogprobs,
        openRouterRepetitionPenaltyInput = openRouterState.repetitionPenalty,
        openRouterMinPInput = openRouterState.minP,
        openRouterTopAInput = openRouterState.topA,
        openRouterStopInput = openRouterState.stop,
        openRouterTransformsInput = openRouterState.transforms,
        openRouterModelsInput = openRouterState.models,
        openRouterRouteInput = openRouterState.route,
        openRouterProviderPreferencesInput = openRouterState.providerPreferences,
        openRouterLogprobs = openRouterState.logprobs,
    )
}

private interface ProviderModelDialogAdapter {
    val providerIds: Set<String>

    fun supportsProvider(providerId: String): Boolean {
        return providerId in providerIds
    }

    @Composable
    fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    )

    fun validateBeforeConfirm(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        modelName: String,
    ): String? {
        return null
    }

    fun buildParams(
        providerId: String,
        existing: LlmModelParamsConfig?,
        runtimeManager: ModelDialogRuntimeManager,
    ): ParamsBuildResult {
        val input = runtimeManager.collectModelDialogBuildInput(
            providerId = providerId,
            existing = existing,
        )
        return buildParams(input = input)
    }

    fun buildParams(
        input: ParamsBuildInput,
    ): ParamsBuildResult {
        return ParamsBuildResult(
            params = null,
            error = "No builder is registered for provider '${input.providerId}'",
        )
    }
}

private abstract class FamilyBasedProviderModelDialogAdapter(
    override val providerIds: Set<String>,
    private val family: ParamsUiFamily,
) : ProviderModelDialogAdapter {

    @Composable
    abstract override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    )

    override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
        return buildModelParamsConfig(
            family = family,
            input = input,
        )
    }
}

private object OpenAiNativeModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = OPENAI_NATIVE_PROVIDER_IDS,
    family = ParamsUiFamily.OpenAiNative,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        OpenAiLikeModelDialogEditorSection(
            runtimeManager = runtimeManager,
            matchedModelPreset = matchedModelPreset,
            configuredCapabilities = configuredCapabilities,
            onParamsErrorChange = onParamsErrorChange,
        )
    }

    override fun validateBeforeConfirm(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        modelName: String,
    ): String? {
        val openAiEndpointSupport = resolveOpenAiEndpointSupport(
            modelPreset = matchedModelPreset,
            configuredCapabilities = configuredCapabilities,
        )
        val endpoint = runtimeManager.openAiLikeViewModel.state.value.endpoint
        if (!openAiEndpointSupport.supports(endpoint)) {
            return "Model '${matchedModelPreset?.id ?: modelName}' does not support ${endpoint.asConfigValue()} endpoint"
        }
        return null
    }
}

private object OpenAiCompatibleModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = OPENAI_COMPATIBLE_PROVIDER_IDS,
    family = ParamsUiFamily.OpenAiCompatible,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        OpenAiLikeModelDialogEditorSection(
            runtimeManager = runtimeManager,
            matchedModelPreset = matchedModelPreset,
            configuredCapabilities = configuredCapabilities,
            onParamsErrorChange = onParamsErrorChange,
        )
    }

    override fun validateBeforeConfirm(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        modelName: String,
    ): String? {
        val openAiEndpointSupport = resolveOpenAiEndpointSupport(
            modelPreset = matchedModelPreset,
            configuredCapabilities = configuredCapabilities,
        )
        val endpoint = runtimeManager.openAiLikeViewModel.state.value.endpoint
        if (!openAiEndpointSupport.supports(endpoint)) {
            return "Model '${matchedModelPreset?.id ?: modelName}' does not support ${endpoint.asConfigValue()} endpoint"
        }
        return null
    }
}

private object AnthropicModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = setOf("anthropic"),
    family = ParamsUiFamily.Anthropic,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        AnthropicModelDialogEditorSection(
            runtimeManager = runtimeManager,
            onParamsErrorChange = onParamsErrorChange,
        )
    }
}

private object GeminiModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = setOf("gemini"),
    family = ParamsUiFamily.Gemini,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        GeminiModelDialogEditorSection(
            runtimeManager = runtimeManager,
            onParamsErrorChange = onParamsErrorChange,
        )
    }
}

private object DeepSeekModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = setOf("deepseek"),
    family = ParamsUiFamily.DeepSeek,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        DeepSeekModelDialogEditorSection(
            runtimeManager = runtimeManager,
            onParamsErrorChange = onParamsErrorChange,
        )
    }
}

private object OpenRouterModelDialogAdapter : FamilyBasedProviderModelDialogAdapter(
    providerIds = setOf("openrouter"),
    family = ParamsUiFamily.OpenRouter,
) {
    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
        OpenRouterModelDialogEditorSection(
            runtimeManager = runtimeManager,
            onParamsErrorChange = onParamsErrorChange,
        )
    }
}

private object UnsupportedProviderModelDialogAdapter : ProviderModelDialogAdapter {
    override val providerIds: Set<String> = emptySet()

    @Composable
    override fun RenderEditorSection(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        onParamsErrorChange: (String?) -> Unit,
    ) {
    }

    override fun validateBeforeConfirm(
        runtimeManager: ModelDialogRuntimeManager,
        matchedModelPreset: LLModel?,
        configuredCapabilities: List<String>?,
        modelName: String,
    ): String? {
        return null
    }

    override fun buildParams(
        providerId: String,
        existing: LlmModelParamsConfig?,
        runtimeManager: ModelDialogRuntimeManager,
    ): ParamsBuildResult {
        return ParamsBuildResult(params = null, error = "Unsupported provider '$providerId'")
    }
}

private val MODEL_DIALOG_ADAPTERS: List<ProviderModelDialogAdapter> = listOf(
    OpenAiNativeModelDialogAdapter,
    OpenAiCompatibleModelDialogAdapter,
    AnthropicModelDialogAdapter,
    GeminiModelDialogAdapter,
    DeepSeekModelDialogAdapter,
    OpenRouterModelDialogAdapter,
)

private fun resolveModelDialogProviderAdapter(providerId: String): ProviderModelDialogAdapter {
    val normalizedProviderId = providerId.trim().lowercase()
    return MODEL_DIALOG_ADAPTERS.firstOrNull { adapter ->
        adapter.supportsProvider(normalizedProviderId)
    } ?: UnsupportedProviderModelDialogAdapter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenAiLikeModelDialogEditorSection(
    runtimeManager: ModelDialogRuntimeManager,
    matchedModelPreset: LLModel?,
    configuredCapabilities: List<String>?,
    onParamsErrorChange: (String?) -> Unit,
) {
    val openAiLikeState by runtimeManager.openAiLikeViewModel.state.collectAsStateWithLifecycle()
    val openAiEndpointSupport = remember(matchedModelPreset, configuredCapabilities) {
        resolveOpenAiEndpointSupport(
            modelPreset = matchedModelPreset,
            configuredCapabilities = configuredCapabilities,
        )
    }
    val supportedOpenAiEndpoints = remember(openAiEndpointSupport) {
        openAiEndpointSupport.supportedEndpoints()
    }

    LaunchedEffect(openAiLikeState.endpoint, supportedOpenAiEndpoints) {
        if (supportedOpenAiEndpoints.isEmpty()) {
            return@LaunchedEffect
        }
        if (openAiLikeState.endpoint !in supportedOpenAiEndpoints) {
            runtimeManager.openAiLikeViewModel.update { current ->
                current.copy(endpoint = supportedOpenAiEndpoints.first())
            }
            onParamsErrorChange(null)
        }
    }

    val endpointOptions = supportedOpenAiEndpoints.ifEmpty { OPENAI_ENDPOINT_OPTIONS }
    var endpointExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = endpointExpanded,
        onExpandedChange = { endpointExpanded = it },
    ) {
        OutlinedTextField(
            value = openAiLikeState.endpoint.asConfigValue(),
            onValueChange = {},
            readOnly = true,
            label = { Text("OpenAI Endpoint") },
            supportingText = { Text(openAiEndpointSupport.descriptionText()) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = endpointExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = endpointExpanded,
            onDismissRequest = { endpointExpanded = false },
        ) {
            endpointOptions.forEach { endpoint ->
                DropdownMenuItem(
                    text = { Text(endpoint.asConfigValue()) },
                    onClick = {
                        runtimeManager.openAiLikeViewModel.update { current ->
                            current.copy(endpoint = endpoint)
                        }
                        onParamsErrorChange(null)
                        endpointExpanded = false
                    },
                )
            }
        }
    }

    val reasoningOptions = listOf(
        "Default" to null,
        "none" to OpenAiReasoningEffortConfig.None,
        "minimal" to OpenAiReasoningEffortConfig.Minimal,
        "low" to OpenAiReasoningEffortConfig.Low,
        "medium" to OpenAiReasoningEffortConfig.Medium,
        "high" to OpenAiReasoningEffortConfig.High,
    )
    val openAiServiceTierOptions = listOf(
        "Default" to null,
        "auto" to OpenAiServiceTierConfig.Auto,
        "default" to OpenAiServiceTierConfig.Default,
        "flex" to OpenAiServiceTierConfig.Flex,
        "priority" to OpenAiServiceTierConfig.Priority,
    )

    var reasoningExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = reasoningExpanded,
        onExpandedChange = { reasoningExpanded = it },
    ) {
        OutlinedTextField(
            value = reasoningOptions.firstOrNull { it.second == openAiLikeState.reasoningEffort }?.first ?: "Default",
            onValueChange = {},
            readOnly = true,
            label = { Text("Reasoning Effort") },
            supportingText = { Text("Applied to chat.reasoningEffort and responses.reasoning.effort") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = reasoningExpanded,
            onDismissRequest = { reasoningExpanded = false },
        ) {
            reasoningOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.first) },
                    onClick = {
                        runtimeManager.openAiLikeViewModel.update { current ->
                            current.copy(reasoningEffort = option.second)
                        }
                        reasoningExpanded = false
                        onParamsErrorChange(null)
                    },
                )
            }
        }
    }

    if (openAiLikeState.endpoint == OpenAiEndpoint.Chat) {
        OpenAiBaseParamsEditor(
            endpointLabel = "Chat",
            state = openAiLikeState.chatBase,
            onStateChange = { next ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatBase = next)
                }
                onParamsErrorChange(null)
            },
        )

        var chatServiceTierExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = chatServiceTierExpanded,
            onExpandedChange = { chatServiceTierExpanded = it },
        ) {
            OutlinedTextField(
                value = openAiServiceTierOptions.firstOrNull { it.second == openAiLikeState.chatServiceTier }?.first
                    ?: "Default",
                onValueChange = {},
                readOnly = true,
                label = { Text("Service Tier") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = chatServiceTierExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = chatServiceTierExpanded,
                onDismissRequest = { chatServiceTierExpanded = false },
            ) {
                openAiServiceTierOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.first) },
                        onClick = {
                            runtimeManager.openAiLikeViewModel.update { current ->
                                current.copy(chatServiceTier = option.second)
                            }
                            chatServiceTierExpanded = false
                            onParamsErrorChange(null)
                        },
                    )
                }
            }
        }

        OptionalBooleanDropdownField(
            label = "Parallel Tool Calls",
            choice = openAiLikeState.chatParallelToolCalls,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatParallelToolCalls = choice)
                }
                onParamsErrorChange(null)
            },
        )
        OptionalBooleanDropdownField(
            label = "Store",
            choice = openAiLikeState.chatStore,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatStore = choice)
                }
                onParamsErrorChange(null)
            },
        )
        OptionalBooleanDropdownField(
            label = "Logprobs",
            choice = openAiLikeState.chatLogprobs,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatLogprobs = choice)
                }
                onParamsErrorChange(null)
            },
        )

        OutlinedTextField(
            value = openAiLikeState.chatPromptCacheKey,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatPromptCacheKey = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Prompt Cache Key") },
            supportingText = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatSafetyIdentifier,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatSafetyIdentifier = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Safety Identifier") },
            supportingText = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatFrequencyPenalty,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatFrequencyPenalty = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Frequency Penalty") },
            supportingText = { Text("Optional decimal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatPresencePenalty,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatPresencePenalty = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Presence Penalty") },
            supportingText = { Text("Optional decimal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatTopP,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatTopP = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Top P") },
            supportingText = { Text("Optional, range 0..1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatTopLogprobs,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatTopLogprobs = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Top Logprobs") },
            supportingText = { Text("Optional integer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.chatStop,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(chatStop = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Stop") },
            supportingText = { Text("Comma/newline separated") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OpenAiBaseParamsEditor(
            endpointLabel = "Responses",
            state = openAiLikeState.responsesBase,
            onStateChange = { next ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesBase = next)
                }
                onParamsErrorChange(null)
            },
        )

        val reasoningSummaryOptions = listOf(
            "Default" to null,
            "auto" to OpenAiReasoningSummaryConfig.Auto,
            "concise" to OpenAiReasoningSummaryConfig.Concise,
            "detailed" to OpenAiReasoningSummaryConfig.Detailed,
        )
        val truncationOptions = listOf(
            "Default" to null,
            "auto" to OpenAiTruncationConfig.Auto,
            "disabled" to OpenAiTruncationConfig.Disabled,
        )

        var reasoningSummaryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = reasoningSummaryExpanded,
            onExpandedChange = { reasoningSummaryExpanded = it },
        ) {
            OutlinedTextField(
                value = reasoningSummaryOptions.firstOrNull { it.second == openAiLikeState.reasoningSummary }?.first
                    ?: "Default",
                onValueChange = {},
                readOnly = true,
                label = { Text("Reasoning Summary") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningSummaryExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = reasoningSummaryExpanded,
                onDismissRequest = { reasoningSummaryExpanded = false },
            ) {
                reasoningSummaryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.first) },
                        onClick = {
                            runtimeManager.openAiLikeViewModel.update { current ->
                                current.copy(reasoningSummary = option.second)
                            }
                            reasoningSummaryExpanded = false
                            onParamsErrorChange(null)
                        },
                    )
                }
            }
        }

        var responsesServiceTierExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = responsesServiceTierExpanded,
            onExpandedChange = { responsesServiceTierExpanded = it },
        ) {
            OutlinedTextField(
                value = openAiServiceTierOptions.firstOrNull { it.second == openAiLikeState.responsesServiceTier }?.first
                    ?: "Default",
                onValueChange = {},
                readOnly = true,
                label = { Text("Service Tier") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = responsesServiceTierExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = responsesServiceTierExpanded,
                onDismissRequest = { responsesServiceTierExpanded = false },
            ) {
                openAiServiceTierOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.first) },
                        onClick = {
                            runtimeManager.openAiLikeViewModel.update { current ->
                                current.copy(responsesServiceTier = option.second)
                            }
                            responsesServiceTierExpanded = false
                            onParamsErrorChange(null)
                        },
                    )
                }
            }
        }

        var truncationExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = truncationExpanded,
            onExpandedChange = { truncationExpanded = it },
        ) {
            OutlinedTextField(
                value = truncationOptions.firstOrNull { it.second == openAiLikeState.responsesTruncation }?.first
                    ?: "Default",
                onValueChange = {},
                readOnly = true,
                label = { Text("Truncation") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = truncationExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = truncationExpanded,
                onDismissRequest = { truncationExpanded = false },
            ) {
                truncationOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.first) },
                        onClick = {
                            runtimeManager.openAiLikeViewModel.update { current ->
                                current.copy(responsesTruncation = option.second)
                            }
                            truncationExpanded = false
                            onParamsErrorChange(null)
                        },
                    )
                }
            }
        }

        OptionalBooleanDropdownField(
            label = "Background",
            choice = openAiLikeState.responsesBackground,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesBackground = choice)
                }
                onParamsErrorChange(null)
            },
        )
        OptionalBooleanDropdownField(
            label = "Parallel Tool Calls",
            choice = openAiLikeState.responsesParallelToolCalls,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesParallelToolCalls = choice)
                }
                onParamsErrorChange(null)
            },
        )
        OptionalBooleanDropdownField(
            label = "Store",
            choice = openAiLikeState.responsesStore,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesStore = choice)
                }
                onParamsErrorChange(null)
            },
        )
        OptionalBooleanDropdownField(
            label = "Logprobs",
            choice = openAiLikeState.responsesLogprobs,
            onChoiceChange = { choice ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesLogprobs = choice)
                }
                onParamsErrorChange(null)
            },
        )

        OutlinedTextField(
            value = openAiLikeState.responsesPromptCacheKey,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesPromptCacheKey = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Prompt Cache Key") },
            supportingText = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.responsesSafetyIdentifier,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesSafetyIdentifier = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Safety Identifier") },
            supportingText = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.responsesTopP,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesTopP = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Top P") },
            supportingText = { Text("Optional, range 0..1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.responsesTopLogprobs,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesTopLogprobs = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Top Logprobs") },
            supportingText = { Text("Optional integer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.responsesMaxToolCalls,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesMaxToolCalls = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Max Tool Calls") },
            supportingText = { Text("Optional integer >= 1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = openAiLikeState.responsesInclude,
            onValueChange = { value ->
                runtimeManager.openAiLikeViewModel.update { current ->
                    current.copy(responsesInclude = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Include") },
            supportingText = { Text("Comma/newline separated OpenAI include keys") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnthropicModelDialogEditorSection(
    runtimeManager: ModelDialogRuntimeManager,
    onParamsErrorChange: (String?) -> Unit,
) {
    val anthropicState by runtimeManager.anthropicViewModel.state.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = anthropicState.topP,
        onValueChange = { value ->
            runtimeManager.anthropicViewModel.update { current ->
                current.copy(topP = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top P") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = anthropicState.topK,
        onValueChange = { value ->
            runtimeManager.anthropicViewModel.update { current ->
                current.copy(topK = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top K") },
        supportingText = { Text("Optional integer") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = anthropicState.container,
        onValueChange = { value ->
            runtimeManager.anthropicViewModel.update { current ->
                current.copy(container = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Container") },
        supportingText = { Text("Optional container identifier") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    val anthropicServiceTierOptions = listOf(
        "Default" to null,
        "auto" to AnthropicServiceTierConfig.Auto,
        "standard_only" to AnthropicServiceTierConfig.StandardOnly,
    )
    var anthropicServiceTierExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = anthropicServiceTierExpanded,
        onExpandedChange = { anthropicServiceTierExpanded = it },
    ) {
        OutlinedTextField(
            value = anthropicServiceTierOptions.firstOrNull { it.second == anthropicState.serviceTier }?.first ?: "Default",
            onValueChange = {},
            readOnly = true,
            label = { Text("Service Tier") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = anthropicServiceTierExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = anthropicServiceTierExpanded,
            onDismissRequest = { anthropicServiceTierExpanded = false },
        ) {
            anthropicServiceTierOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.first) },
                    onClick = {
                        runtimeManager.anthropicViewModel.update { current ->
                            current.copy(serviceTier = option.second)
                        }
                        anthropicServiceTierExpanded = false
                        onParamsErrorChange(null)
                    },
                )
            }
        }
    }

    OutlinedTextField(
        value = anthropicState.stopSequences,
        onValueChange = { value ->
            runtimeManager.anthropicViewModel.update { current ->
                current.copy(stopSequences = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Stop Sequences") },
        supportingText = { Text("Comma/newline separated") },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Enable Extended Thinking")
        Switch(
            checked = anthropicState.thinkingEnabled,
            onCheckedChange = { enabled ->
                runtimeManager.anthropicViewModel.update { current ->
                    current.copy(thinkingEnabled = enabled)
                }
                onParamsErrorChange(null)
            },
        )
    }
    if (anthropicState.thinkingEnabled) {
        OutlinedTextField(
            value = anthropicState.thinkingBudget,
            onValueChange = { value ->
                runtimeManager.anthropicViewModel.update { current ->
                    current.copy(thinkingBudget = value)
                }
                onParamsErrorChange(null)
            },
            label = { Text("Thinking Budget Tokens") },
            supportingText = { Text("Anthropic requires >= 1024") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiModelDialogEditorSection(
    runtimeManager: ModelDialogRuntimeManager,
    onParamsErrorChange: (String?) -> Unit,
) {
    val geminiState by runtimeManager.geminiViewModel.state.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = geminiState.topP,
        onValueChange = { value ->
            runtimeManager.geminiViewModel.update { current ->
                current.copy(topP = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top P") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = geminiState.topK,
        onValueChange = { value ->
            runtimeManager.geminiViewModel.update { current ->
                current.copy(topK = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top K") },
        supportingText = { Text("Optional integer") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = geminiState.thinkingBudget,
        onValueChange = { value ->
            runtimeManager.geminiViewModel.update { current ->
                current.copy(thinkingBudget = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Thinking Budget") },
        supportingText = { Text("Optional. If set, level is ignored") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    var geminiLevelExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = geminiLevelExpanded,
        onExpandedChange = { geminiLevelExpanded = it },
    ) {
        OutlinedTextField(
            value = geminiState.thinkingLevel?.name?.lowercase() ?: "Default",
            onValueChange = {},
            readOnly = true,
            label = { Text("Thinking Level") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = geminiLevelExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = geminiLevelExpanded,
            onDismissRequest = { geminiLevelExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = {
                    runtimeManager.geminiViewModel.update { current ->
                        current.copy(thinkingLevel = null)
                    }
                    geminiLevelExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("low") },
                onClick = {
                    runtimeManager.geminiViewModel.update { current ->
                        current.copy(thinkingLevel = GeminiThinkingLevelConfig.Low)
                    }
                    geminiLevelExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("high") },
                onClick = {
                    runtimeManager.geminiViewModel.update { current ->
                        current.copy(thinkingLevel = GeminiThinkingLevelConfig.High)
                    }
                    geminiLevelExpanded = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepSeekModelDialogEditorSection(
    runtimeManager: ModelDialogRuntimeManager,
    onParamsErrorChange: (String?) -> Unit,
) {
    val deepSeekState by runtimeManager.deepSeekViewModel.state.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = deepSeekState.frequencyPenalty,
        onValueChange = { value ->
            runtimeManager.deepSeekViewModel.update { current ->
                current.copy(frequencyPenalty = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Frequency Penalty") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deepSeekState.presencePenalty,
        onValueChange = { value ->
            runtimeManager.deepSeekViewModel.update { current ->
                current.copy(presencePenalty = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Presence Penalty") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deepSeekState.topP,
        onValueChange = { value ->
            runtimeManager.deepSeekViewModel.update { current ->
                current.copy(topP = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top P") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deepSeekState.topLogprobs,
        onValueChange = { value ->
            runtimeManager.deepSeekViewModel.update { current ->
                current.copy(topLogprobs = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top Logprobs") },
        supportingText = { Text("Optional integer") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deepSeekState.stop,
        onValueChange = { value ->
            runtimeManager.deepSeekViewModel.update { current ->
                current.copy(stop = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Stop") },
        supportingText = { Text("Comma/newline separated") },
        modifier = Modifier.fillMaxWidth(),
    )

    var deepSeekLogprobsExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = deepSeekLogprobsExpanded,
        onExpandedChange = { deepSeekLogprobsExpanded = it },
    ) {
        OutlinedTextField(
            value = deepSeekState.logprobs.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Logprobs") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = deepSeekLogprobsExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = deepSeekLogprobsExpanded,
            onDismissRequest = { deepSeekLogprobsExpanded = false },
        ) {
            OptionalBooleanChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        runtimeManager.deepSeekViewModel.update { current ->
                            current.copy(logprobs = option)
                        }
                        deepSeekLogprobsExpanded = false
                        onParamsErrorChange(null)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenRouterModelDialogEditorSection(
    runtimeManager: ModelDialogRuntimeManager,
    onParamsErrorChange: (String?) -> Unit,
) {
    val openRouterState by runtimeManager.openRouterViewModel.state.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = openRouterState.reasoningEffort,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(reasoningEffort = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Reasoning Effort") },
        supportingText = { Text("e.g. low/medium/high/xhigh") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = openRouterState.frequencyPenalty,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(frequencyPenalty = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Frequency Penalty") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.presencePenalty,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(presencePenalty = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Presence Penalty") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.topP,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(topP = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top P") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.topK,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(topK = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top K") },
        supportingText = { Text("Optional integer") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.topLogprobs,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(topLogprobs = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top Logprobs") },
        supportingText = { Text("Optional integer") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.repetitionPenalty,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(repetitionPenalty = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Repetition Penalty") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.minP,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(minP = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Min P") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.topA,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(topA = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Top A") },
        supportingText = { Text("Optional, range 0..1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.route,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(route = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Route") },
        supportingText = { Text("Optional route policy") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.providerPreferences,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(providerPreferences = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Provider Preferences") },
        supportingText = { Text("Optional JSON object, maps to OpenRouter provider routing") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.stop,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(stop = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Stop") },
        supportingText = { Text("Comma/newline separated") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.transforms,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(transforms = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Transforms") },
        supportingText = { Text("Comma/newline separated") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = openRouterState.models,
        onValueChange = { value ->
            runtimeManager.openRouterViewModel.update { current ->
                current.copy(models = value)
            }
            onParamsErrorChange(null)
        },
        label = { Text("Models") },
        supportingText = { Text("Comma/newline separated") },
        modifier = Modifier.fillMaxWidth(),
    )

    var openRouterLogprobsExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = openRouterLogprobsExpanded,
        onExpandedChange = { openRouterLogprobsExpanded = it },
    ) {
        OutlinedTextField(
            value = openRouterState.logprobs.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Logprobs") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = openRouterLogprobsExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = openRouterLogprobsExpanded,
            onDismissRequest = { openRouterLogprobsExpanded = false },
        ) {
            OptionalBooleanChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        runtimeManager.openRouterViewModel.update { current ->
                            current.copy(logprobs = option)
                        }
                        openRouterLogprobsExpanded = false
                        onParamsErrorChange(null)
                    },
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
    var paramsError by remember { mutableStateOf<String?>(null) }

    val isEditing = model != null
    val initialParams = model?.params
    val runtimeManager = remember(initialParams) {
        ModelDialogRuntimeManager(initialParams = initialParams)
    }

    val providerPresets = remember { viewModel.getProviderPresets() }
    val selectedAuth = remember(auths, selectedAuthId) {
        auths.firstOrNull { auth -> auth.id == selectedAuthId }
    }
    val selectedAuthPresetId = selectedAuth?.providerId
    val selectedProviderPreset = selectedAuthPresetId?.let { presetId ->
        providerPresets.firstOrNull { preset -> preset.id == presetId }
    }
    val modelPresets = selectedProviderPreset?.models.orEmpty()
    val matchedModelPreset = modelPresets.firstOrNull { preset -> preset.id == modelName }
    val selectedProviderId = selectedAuth?.providerId?.trim()?.lowercase().orEmpty()
    val selectedAdapter = remember(selectedProviderId) {
        resolveModelDialogProviderAdapter(selectedProviderId)
    }

    var presetFilter by remember { mutableStateOf("") }
    val filteredModelPresets = remember(modelPresets, presetFilter) {
        val query = presetFilter.trim().lowercase()
        if (query.isBlank()) {
            modelPresets
        } else {
            modelPresets.filter { preset ->
                preset.id.lowercase().contains(query)
            }
        }
    }


    val suggestedId = viewModel.generateDefaultModelId(modelName, selectedAuthId)
    val modelDialogScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Model" else "Add Model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(modelDialogScrollState),
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

                if (modelPresets.isNotEmpty()) {
                    if (modelPresets.size >= 12) {
                        OutlinedTextField(
                            value = presetFilter,
                            onValueChange = { presetFilter = it },
                            label = { Text("Filter Presets") },
                            supportingText = {
                                Text("${filteredModelPresets.size} / ${modelPresets.size} models")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    var expandedPreset by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedPreset,
                        onExpandedChange = { expandedPreset = it },
                    ) {
                        OutlinedTextField(
                            value = matchedModelPreset?.id ?: "Custom",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preset Model") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPreset)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )

                        ExposedDropdownMenu(
                            expanded = expandedPreset,
                            onDismissRequest = { expandedPreset = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Custom") },
                                onClick = {
                                    expandedPreset = false
                                },
                            )
                            filteredModelPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.id) },
                                    onClick = {
                                        modelName = preset.id
                                        if (!isEditing && displayName.isBlank()) {
                                            displayName = preset.id
                                        }
                                        expandedPreset = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (auths.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedAuth = auths.find { it.id == selectedAuthId }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAuth?.let { "${it.name ?: it.providerId} (${it.id})" } ?: "Select Provider",
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
                                    text = { Text("${auth.name ?: auth.providerId} (${auth.id})") },
                                    onClick = {
                                        selectedAuthId = auth.id
                                        expanded = false
                                        paramsError = null
                                    }
                                )
                            }
                        }
                    }
                }

                selectedAdapter.RenderEditorSection(
                    runtimeManager = runtimeManager,
                    matchedModelPreset = matchedModelPreset,
                    configuredCapabilities = model?.capabilities,
                    onParamsErrorChange = { paramsError = it },
                )

                paramsError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    val adapterValidationError = selectedAdapter.validateBeforeConfirm(
                        runtimeManager = runtimeManager,
                        matchedModelPreset = matchedModelPreset,
                        configuredCapabilities = model?.capabilities,
                        modelName = modelName,
                    )
                    if (adapterValidationError != null) {
                        paramsError = adapterValidationError
                        return@TextButton
                    }

                    val builtParams = selectedAdapter.buildParams(
                        providerId = selectedProviderId,
                        existing = model?.params,
                        runtimeManager = runtimeManager,
                    )
                    if (builtParams.error != null) {
                        paramsError = builtParams.error
                        return@TextButton
                    }
                    val resolvedParams = builtParams.params
                    if (resolvedParams != null && !resolvedParams.supportsProvider(selectedProviderId)) {
                        paramsError = "Params '${resolvedParams.summaryText()}' do not match provider '$selectedProviderId'"
                        return@TextButton
                    }

                    onConfirm(
                        LlmModelConfig(
                            id = resolvedId,
                            displayName = displayName.takeIf { it.isNotBlank() }
                                ?: matchedModelPreset?.id,
                            model = modelName,
                            authId = selectedAuthId,
                            params = resolvedParams,
                            maxContextSize = model?.maxContextSize,
                            capabilities = model?.capabilities,
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


private data class ParamsBuildResult(
    val params: LlmModelParamsConfig?,
    val error: String? = null,
)

private data class ParamsBuildInput(
    val providerId: String,
    val existing: LlmModelParamsConfig?,
    val openAiEndpoint: OpenAiEndpoint,
    val openAiReasoningEffort: OpenAiReasoningEffortConfig?,
    val openAiChatBase: OpenAiBaseEditorState,
    val openAiResponsesBase: OpenAiBaseEditorState,
    val openAiReasoningSummary: OpenAiReasoningSummaryConfig?,
    val openAiChatServiceTier: OpenAiServiceTierConfig?,
    val openAiChatFrequencyPenaltyInput: String,
    val openAiChatPresencePenaltyInput: String,
    val openAiChatTopPInput: String,
    val openAiChatTopLogprobsInput: String,
    val openAiChatStopInput: String,
    val openAiChatParallelToolCalls: OptionalBooleanChoice,
    val openAiChatStore: OptionalBooleanChoice,
    val openAiChatLogprobs: OptionalBooleanChoice,
    val openAiChatPromptCacheKeyInput: String,
    val openAiChatSafetyIdentifierInput: String,
    val openAiResponsesBackground: OptionalBooleanChoice,
    val openAiResponsesIncludeInput: String,
    val openAiResponsesMaxToolCallsInput: String,
    val openAiResponsesParallelToolCalls: OptionalBooleanChoice,
    val openAiResponsesTruncation: OpenAiTruncationConfig?,
    val openAiResponsesServiceTier: OpenAiServiceTierConfig?,
    val openAiResponsesStore: OptionalBooleanChoice,
    val openAiResponsesLogprobs: OptionalBooleanChoice,
    val openAiResponsesTopPInput: String,
    val openAiResponsesTopLogprobsInput: String,
    val openAiResponsesPromptCacheKeyInput: String,
    val openAiResponsesSafetyIdentifierInput: String,
    val anthropicThinkingEnabled: Boolean,
    val anthropicThinkingBudgetInput: String,
    val anthropicTopPInput: String,
    val anthropicTopKInput: String,
    val anthropicStopSequencesInput: String,
    val anthropicContainerInput: String,
    val anthropicServiceTier: AnthropicServiceTierConfig?,
    val geminiThinkingBudgetInput: String,
    val geminiThinkingLevel: GeminiThinkingLevelConfig?,
    val geminiTopPInput: String,
    val geminiTopKInput: String,
    val deepSeekFrequencyPenaltyInput: String,
    val deepSeekPresencePenaltyInput: String,
    val deepSeekTopPInput: String,
    val deepSeekTopLogprobsInput: String,
    val deepSeekStopInput: String,
    val deepSeekLogprobs: OptionalBooleanChoice,
    val openRouterReasoningEffortInput: String,
    val openRouterFrequencyPenaltyInput: String,
    val openRouterPresencePenaltyInput: String,
    val openRouterTopPInput: String,
    val openRouterTopKInput: String,
    val openRouterTopLogprobsInput: String,
    val openRouterRepetitionPenaltyInput: String,
    val openRouterMinPInput: String,
    val openRouterTopAInput: String,
    val openRouterStopInput: String,
    val openRouterTransformsInput: String,
    val openRouterModelsInput: String,
    val openRouterRouteInput: String,
    val openRouterProviderPreferencesInput: String,
    val openRouterLogprobs: OptionalBooleanChoice,
)

private data class OpenAiEndpointSupport(
    val supportsChat: Boolean,
    val supportsResponses: Boolean,
    val constrained: Boolean,
) {
    fun supports(endpoint: OpenAiEndpoint): Boolean {
        return when (endpoint) {
            OpenAiEndpoint.Chat -> supportsChat
            OpenAiEndpoint.Responses -> supportsResponses
        }
    }

    fun supportedEndpoints(): List<OpenAiEndpoint> {
        val endpoints = buildList {
            if (supportsChat) {
                add(OpenAiEndpoint.Chat)
            }
            if (supportsResponses) {
                add(OpenAiEndpoint.Responses)
            }
        }
        if (endpoints.isNotEmpty()) {
            return endpoints
        }
        return if (constrained) {
            emptyList()
        } else {
            OPENAI_ENDPOINT_OPTIONS
        }
    }

    fun descriptionText(): String {
        return when {
            !constrained -> "Bind model to chat or responses endpoint"
            supportsChat && supportsResponses -> "This model supports chat and responses endpoints"
            supportsChat -> "This model supports chat endpoint only"
            supportsResponses -> "This model supports responses endpoint only"
            else -> "This model has no OpenAI endpoint capability"
        }
    }

    companion object {
        fun unspecified(): OpenAiEndpointSupport {
            return OpenAiEndpointSupport(
                supportsChat = true,
                supportsResponses = true,
                constrained = false,
            )
        }
    }
}

private val OPENAI_ENDPOINT_OPTIONS: List<OpenAiEndpoint> = listOf(
    OpenAiEndpoint.Chat,
    OpenAiEndpoint.Responses,
)

private val OPENAI_CHAT_CAPABILITY_ALIASES: Set<String> = setOf(
    "openai_completions",
    "openai_endpoint_completions",
)

private val OPENAI_RESPONSES_CAPABILITY_ALIASES: Set<String> = setOf(
    "openai_responses",
    "openai_endpoint_responses",
)

private val OPENAI_INCLUDE_VALUES: Set<String> = setOf(
    "web_search_call.action.sources",
    "code_interpreter_call.outputs",
    "computer_call_output.output.image_url",
    "file_search_call.results",
    "message.input_image.image_url",
    "message.output_text.logprobs",
    "reasoning.encrypted_content",
)

private fun resolveOpenAiEndpointSupport(
    modelPreset: LLModel?,
    configuredCapabilities: List<String>?,
): OpenAiEndpointSupport {
    val fromConfig = resolveOpenAiEndpointSupportFromConfiguredCapabilities(configuredCapabilities)
    if (fromConfig != null) {
        return fromConfig
    }

    modelPreset ?: return OpenAiEndpointSupport.unspecified()

    val supportsChat = modelPreset.capabilities.contains(LLMCapability.OpenAIEndpoint.Completions)
    val supportsResponses = modelPreset.capabilities.contains(LLMCapability.OpenAIEndpoint.Responses)
    val hasEndpointCapability = supportsChat || supportsResponses
    if (!hasEndpointCapability) {
        return OpenAiEndpointSupport.unspecified()
    }
    return OpenAiEndpointSupport(
        supportsChat = supportsChat,
        supportsResponses = supportsResponses,
        constrained = true,
    )
}

private fun resolveOpenAiEndpointSupportFromConfiguredCapabilities(capabilities: List<String>?): OpenAiEndpointSupport? {
    val normalized = capabilities
        ?.map { value -> value.trim().lowercase() }
        ?.filter { value -> value.isNotBlank() }
        ?.toSet()
        ?: return null
    if (normalized.isEmpty()) {
        return null
    }

    val supportsChat = normalized.any { value -> value in OPENAI_CHAT_CAPABILITY_ALIASES }
    val supportsResponses = normalized.any { value -> value in OPENAI_RESPONSES_CAPABILITY_ALIASES }
    if (!supportsChat && !supportsResponses) {
        return null
    }

    return OpenAiEndpointSupport(
        supportsChat = supportsChat,
        supportsResponses = supportsResponses,
        constrained = true,
    )
}

private fun OpenAiEndpoint.asConfigValue(): String {
    return when (this) {
        OpenAiEndpoint.Chat -> "chat"
        OpenAiEndpoint.Responses -> "responses"
    }
}

private enum class ParamsUiFamily {
    OpenAiNative {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            return buildOpenAiFamilyParams(
                input = input,
                buildParams = { existingOpenAiFamily, endpoint, nextChat, nextResponses ->
                    if (existingOpenAiFamily is LlmModelParamsConfig.OpenAi) {
                        existingOpenAiFamily.copy(
                            endpoint = endpoint,
                            chat = nextChat,
                            responses = nextResponses,
                        )
                    } else {
                        LlmModelParamsConfig.OpenAi(
                            endpoint = endpoint,
                            chat = nextChat,
                            responses = nextResponses,
                        )
                    }
                },
            )
        }
    },

    OpenAiCompatible {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            return buildOpenAiFamilyParams(
                input = input,
                buildParams = { existingOpenAiFamily, endpoint, nextChat, nextResponses ->
                    if (existingOpenAiFamily is LlmModelParamsConfig.OpenAiCompatible) {
                        existingOpenAiFamily.copy(
                            endpoint = endpoint,
                            chat = nextChat,
                            responses = nextResponses,
                        )
                    } else {
                        LlmModelParamsConfig.OpenAiCompatible(
                            endpoint = endpoint,
                            chat = nextChat,
                            responses = nextResponses,
                        )
                    }
                },
            )
        }
    },

    Anthropic {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            val existingAnthropic = (input.existing as? LlmModelParamsConfig.Anthropic) ?: LlmModelParamsConfig.Anthropic()
            val topPResult = parseOptionalDoubleField(
                input = input.anthropicTopPInput,
                fieldName = "Anthropic topP",
                min = 0.0,
                max = 1.0,
            )
            if (topPResult.error != null) {
                return ParamsBuildResult(params = null, error = topPResult.error)
            }
            val topKResult = parseOptionalIntField(
                input = input.anthropicTopKInput,
                fieldName = "Anthropic topK",
                min = 1,
            )
            if (topKResult.error != null) {
                return ParamsBuildResult(params = null, error = topKResult.error)
            }
            val thinking = if (!input.anthropicThinkingEnabled) {
                AnthropicThinkingConfig(enabled = false)
            } else {
                val budget = input.anthropicThinkingBudgetInput.trim().ifBlank { "1024" }.toIntOrNull()
                    ?: return ParamsBuildResult(params = null, error = "Anthropic thinking budget must be an integer")
                if (budget < 1024) {
                    return ParamsBuildResult(params = null, error = "Anthropic thinking budget must be >= 1024")
                }
                AnthropicThinkingConfig(enabled = true, budgetTokens = budget)
            }
            return ParamsBuildResult(
                params = existingAnthropic.copy(
                    topP = topPResult.value,
                    topK = topKResult.value,
                    stopSequences = parseDelimitedValues(input.anthropicStopSequencesInput),
                    container = input.anthropicContainerInput.trim().ifBlank { null },
                    serviceTier = input.anthropicServiceTier,
                    thinking = thinking,
                )
            )
        }
    },

    Gemini {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            val existingGemini = (input.existing as? LlmModelParamsConfig.Gemini) ?: LlmModelParamsConfig.Gemini()
            val topPResult = parseOptionalDoubleField(
                input = input.geminiTopPInput,
                fieldName = "Gemini topP",
                min = 0.0,
                max = 1.0,
            )
            if (topPResult.error != null) {
                return ParamsBuildResult(params = null, error = topPResult.error)
            }
            val topKResult = parseOptionalIntField(
                input = input.geminiTopKInput,
                fieldName = "Gemini topK",
                min = 1,
            )
            if (topKResult.error != null) {
                return ParamsBuildResult(params = null, error = topKResult.error)
            }
            val budget = input.geminiThinkingBudgetInput.trim().takeIf { value -> value.isNotBlank() }?.toIntOrNull()
            if (input.geminiThinkingBudgetInput.trim().isNotBlank() && budget == null) {
                return ParamsBuildResult(params = null, error = "Gemini thinking budget must be an integer")
            }
            val nextThinking = when {
                budget != null -> GeminiThinkingConfig(thinkingBudget = budget)
                input.geminiThinkingLevel != null -> GeminiThinkingConfig(thinkingLevel = input.geminiThinkingLevel)
                else -> null
            }
            return ParamsBuildResult(
                params = existingGemini.copy(
                    topP = topPResult.value,
                    topK = topKResult.value,
                    thinking = nextThinking,
                )
            )
        }
    },

    DeepSeek {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            val existingDeepSeek = (input.existing as? LlmModelParamsConfig.DeepSeek) ?: LlmModelParamsConfig.DeepSeek()
            val frequencyPenaltyResult = parseOptionalDoubleField(
                input = input.deepSeekFrequencyPenaltyInput,
                fieldName = "DeepSeek frequency penalty",
            )
            if (frequencyPenaltyResult.error != null) {
                return ParamsBuildResult(params = null, error = frequencyPenaltyResult.error)
            }
            val presencePenaltyResult = parseOptionalDoubleField(
                input = input.deepSeekPresencePenaltyInput,
                fieldName = "DeepSeek presence penalty",
            )
            if (presencePenaltyResult.error != null) {
                return ParamsBuildResult(params = null, error = presencePenaltyResult.error)
            }
            val topPResult = parseOptionalDoubleField(
                input = input.deepSeekTopPInput,
                fieldName = "DeepSeek topP",
                min = 0.0,
                max = 1.0,
            )
            if (topPResult.error != null) {
                return ParamsBuildResult(params = null, error = topPResult.error)
            }
            val topLogprobsResult = parseOptionalIntField(
                input = input.deepSeekTopLogprobsInput,
                fieldName = "DeepSeek topLogprobs",
                min = 0,
            )
            if (topLogprobsResult.error != null) {
                return ParamsBuildResult(params = null, error = topLogprobsResult.error)
            }
            return ParamsBuildResult(
                params = existingDeepSeek.copy(
                    frequencyPenalty = frequencyPenaltyResult.value,
                    presencePenalty = presencePenaltyResult.value,
                    logprobs = input.deepSeekLogprobs.booleanValue,
                    stop = parseDelimitedValues(input.deepSeekStopInput),
                    topLogprobs = topLogprobsResult.value,
                    topP = topPResult.value,
                )
            )
        }
    },

    OpenRouter {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            val existingOpenRouter = (input.existing as? LlmModelParamsConfig.OpenRouter) ?: LlmModelParamsConfig.OpenRouter()
            val frequencyPenaltyResult = parseOptionalDoubleField(
                input = input.openRouterFrequencyPenaltyInput,
                fieldName = "OpenRouter frequency penalty",
            )
            if (frequencyPenaltyResult.error != null) {
                return ParamsBuildResult(params = null, error = frequencyPenaltyResult.error)
            }
            val presencePenaltyResult = parseOptionalDoubleField(
                input = input.openRouterPresencePenaltyInput,
                fieldName = "OpenRouter presence penalty",
            )
            if (presencePenaltyResult.error != null) {
                return ParamsBuildResult(params = null, error = presencePenaltyResult.error)
            }
            val topPResult = parseOptionalDoubleField(
                input = input.openRouterTopPInput,
                fieldName = "OpenRouter topP",
                min = 0.0,
                max = 1.0,
            )
            if (topPResult.error != null) {
                return ParamsBuildResult(params = null, error = topPResult.error)
            }
            val topKResult = parseOptionalIntField(
                input = input.openRouterTopKInput,
                fieldName = "OpenRouter topK",
                min = 1,
            )
            if (topKResult.error != null) {
                return ParamsBuildResult(params = null, error = topKResult.error)
            }
            val topLogprobsResult = parseOptionalIntField(
                input = input.openRouterTopLogprobsInput,
                fieldName = "OpenRouter topLogprobs",
                min = 0,
            )
            if (topLogprobsResult.error != null) {
                return ParamsBuildResult(params = null, error = topLogprobsResult.error)
            }
            val repetitionPenaltyResult = parseOptionalDoubleField(
                input = input.openRouterRepetitionPenaltyInput,
                fieldName = "OpenRouter repetition penalty",
            )
            if (repetitionPenaltyResult.error != null) {
                return ParamsBuildResult(params = null, error = repetitionPenaltyResult.error)
            }
            val minPResult = parseOptionalDoubleField(
                input = input.openRouterMinPInput,
                fieldName = "OpenRouter minP",
                min = 0.0,
                max = 1.0,
            )
            if (minPResult.error != null) {
                return ParamsBuildResult(params = null, error = minPResult.error)
            }
            val topAResult = parseOptionalDoubleField(
                input = input.openRouterTopAInput,
                fieldName = "OpenRouter topA",
                min = 0.0,
                max = 1.0,
            )
            if (topAResult.error != null) {
                return ParamsBuildResult(params = null, error = topAResult.error)
            }
            val providerPreferencesResult = parseOptionalJsonObjectField(
                input = input.openRouterProviderPreferencesInput,
                fieldName = "OpenRouter providerPreferences",
            )
            if (providerPreferencesResult.error != null) {
                return ParamsBuildResult(params = null, error = providerPreferencesResult.error)
            }
            return ParamsBuildResult(
                params = existingOpenRouter.copy(
                    frequencyPenalty = frequencyPenaltyResult.value,
                    presencePenalty = presencePenaltyResult.value,
                    logprobs = input.openRouterLogprobs.booleanValue,
                    stop = parseDelimitedValues(input.openRouterStopInput),
                    topLogprobs = topLogprobsResult.value,
                    topP = topPResult.value,
                    topK = topKResult.value,
                    repetitionPenalty = repetitionPenaltyResult.value,
                    minP = minPResult.value,
                    topA = topAResult.value,
                    transforms = parseDelimitedValues(input.openRouterTransformsInput),
                    models = parseDelimitedValues(input.openRouterModelsInput),
                    route = input.openRouterRouteInput.trim().ifBlank { null },
                    providerPreferences = providerPreferencesResult.value,
                    reasoningEffort = input.openRouterReasoningEffortInput.trim().ifBlank { null },
                )
            )
        }
    },

    Unsupported {
        override fun buildParams(input: ParamsBuildInput): ParamsBuildResult {
            return ParamsBuildResult(params = input.existing)
        }
    },
    ;

    abstract fun buildParams(input: ParamsBuildInput): ParamsBuildResult
}

private fun buildOpenAiFamilyParams(
    input: ParamsBuildInput,
    buildParams: (
        existingOpenAiFamily: LlmModelParamsConfig.OpenAiFamily?,
        openAiEndpoint: OpenAiEndpoint,
        nextChat: io.github.stream29.kode.config.api.OpenAiChatParamsConfig,
        nextResponses: io.github.stream29.kode.config.api.OpenAiResponsesParamsConfig,
    ) -> LlmModelParamsConfig,
): ParamsBuildResult {
    val existingOpenAiFamily = input.existing as? LlmModelParamsConfig.OpenAiFamily
    val currentChat = existingOpenAiFamily?.chat ?: io.github.stream29.kode.config.api.OpenAiChatParamsConfig()
    val currentResponses = existingOpenAiFamily?.responses ?: io.github.stream29.kode.config.api.OpenAiResponsesParamsConfig()

    val chatBaseResult = buildOpenAiBaseParams(
        existing = currentChat.base,
        editor = input.openAiChatBase,
        fieldPrefix = "OpenAI chat base",
    )
    if (chatBaseResult.error != null) {
        return ParamsBuildResult(params = null, error = chatBaseResult.error)
    }

    val responsesBaseResult = buildOpenAiBaseParams(
        existing = currentResponses.base,
        editor = input.openAiResponsesBase,
        fieldPrefix = "OpenAI responses base",
    )
    if (responsesBaseResult.error != null) {
        return ParamsBuildResult(params = null, error = responsesBaseResult.error)
    }

    val chatFrequencyPenaltyResult = parseOptionalDoubleField(
        input = input.openAiChatFrequencyPenaltyInput,
        fieldName = "OpenAI chat frequency penalty",
    )
    if (chatFrequencyPenaltyResult.error != null) {
        return ParamsBuildResult(params = null, error = chatFrequencyPenaltyResult.error)
    }
    val chatPresencePenaltyResult = parseOptionalDoubleField(
        input = input.openAiChatPresencePenaltyInput,
        fieldName = "OpenAI chat presence penalty",
    )
    if (chatPresencePenaltyResult.error != null) {
        return ParamsBuildResult(params = null, error = chatPresencePenaltyResult.error)
    }
    val chatTopPResult = parseOptionalDoubleField(
        input = input.openAiChatTopPInput,
        fieldName = "OpenAI chat topP",
        min = 0.0,
        max = 1.0,
    )
    if (chatTopPResult.error != null) {
        return ParamsBuildResult(params = null, error = chatTopPResult.error)
    }
    val chatTopLogprobsResult = parseOptionalIntField(
        input = input.openAiChatTopLogprobsInput,
        fieldName = "OpenAI chat topLogprobs",
        min = 0,
    )
    if (chatTopLogprobsResult.error != null) {
        return ParamsBuildResult(params = null, error = chatTopLogprobsResult.error)
    }

    val responsesTopPResult = parseOptionalDoubleField(
        input = input.openAiResponsesTopPInput,
        fieldName = "OpenAI responses topP",
        min = 0.0,
        max = 1.0,
    )
    if (responsesTopPResult.error != null) {
        return ParamsBuildResult(params = null, error = responsesTopPResult.error)
    }
    val responsesTopLogprobsResult = parseOptionalIntField(
        input = input.openAiResponsesTopLogprobsInput,
        fieldName = "OpenAI responses topLogprobs",
        min = 0,
    )
    if (responsesTopLogprobsResult.error != null) {
        return ParamsBuildResult(params = null, error = responsesTopLogprobsResult.error)
    }
    val responsesMaxToolCallsResult = parseOptionalIntField(
        input = input.openAiResponsesMaxToolCallsInput,
        fieldName = "OpenAI responses maxToolCalls",
        min = 1,
    )
    if (responsesMaxToolCallsResult.error != null) {
        return ParamsBuildResult(params = null, error = responsesMaxToolCallsResult.error)
    }
    val includeResult = parseOpenAiIncludeValuesField(
        input = input.openAiResponsesIncludeInput,
    )
    if (includeResult.error != null) {
        return ParamsBuildResult(params = null, error = includeResult.error)
    }

    val nextChat = currentChat.copy(
        base = requireNotNull(chatBaseResult.base),
        frequencyPenalty = chatFrequencyPenaltyResult.value,
        presencePenalty = chatPresencePenaltyResult.value,
        parallelToolCalls = input.openAiChatParallelToolCalls.booleanValue,
        promptCacheKey = input.openAiChatPromptCacheKeyInput.trim().ifBlank { null },
        safetyIdentifier = input.openAiChatSafetyIdentifierInput.trim().ifBlank { null },
        serviceTier = input.openAiChatServiceTier,
        store = input.openAiChatStore.booleanValue,
        logprobs = input.openAiChatLogprobs.booleanValue,
        reasoningEffort = input.openAiReasoningEffort,
        stop = parseDelimitedValues(input.openAiChatStopInput),
        topLogprobs = chatTopLogprobsResult.value,
        topP = chatTopPResult.value,
    )

    val nextReasoning = io.github.stream29.kode.config.api.OpenAiReasoningConfig(
        effort = input.openAiReasoningEffort,
        summary = input.openAiReasoningSummary,
    ).takeUnless { reasoning ->
        reasoning.effort == null && reasoning.summary == null
    }

    val nextResponses = currentResponses.copy(
        base = requireNotNull(responsesBaseResult.base),
        background = input.openAiResponsesBackground.booleanValue,
        include = includeResult.value,
        maxToolCalls = responsesMaxToolCallsResult.value,
        parallelToolCalls = input.openAiResponsesParallelToolCalls.booleanValue,
        reasoning = nextReasoning,
        truncation = input.openAiResponsesTruncation,
        promptCacheKey = input.openAiResponsesPromptCacheKeyInput.trim().ifBlank { null },
        safetyIdentifier = input.openAiResponsesSafetyIdentifierInput.trim().ifBlank { null },
        serviceTier = input.openAiResponsesServiceTier,
        store = input.openAiResponsesStore.booleanValue,
        logprobs = input.openAiResponsesLogprobs.booleanValue,
        topLogprobs = responsesTopLogprobsResult.value,
        topP = responsesTopPResult.value,
    )

    val params = buildParams(
        existingOpenAiFamily,
        input.openAiEndpoint,
        nextChat,
        nextResponses,
    )
    return ParamsBuildResult(params = params)
}

private fun extractOpenAiEndpoint(params: LlmModelParamsConfig?): OpenAiEndpoint {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.endpoint ?: OpenAiEndpoint.Chat
}

private fun extractOpenAiReasoningEffort(params: LlmModelParamsConfig?): OpenAiReasoningEffortConfig? {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.reasoningEffort()
}

private fun extractOpenAiChatBaseEditorState(params: LlmModelParamsConfig?): OpenAiBaseEditorState {
    val base = (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.base
    return extractOpenAiBaseEditorState(base)
}

private fun extractOpenAiResponsesBaseEditorState(params: LlmModelParamsConfig?): OpenAiBaseEditorState {
    val base = (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.base
    return extractOpenAiBaseEditorState(base)
}

private fun extractOpenAiBaseEditorState(base: io.github.stream29.kode.config.api.BaseModelParamsConfig?): OpenAiBaseEditorState {
    return OpenAiBaseEditorState(
        temperatureInput = base?.temperature?.toString().orEmpty(),
        maxTokensInput = base?.maxTokens?.toString().orEmpty(),
        numberOfChoicesInput = base?.numberOfChoices?.toString().orEmpty(),
        speculationInput = base?.speculation.orEmpty(),
        userInput = base?.user.orEmpty(),
        toolChoiceMode = OptionalToolChoiceModeChoice.fromToolChoice(base?.toolChoice),
        toolChoiceNameInput = (base?.toolChoice as? io.github.stream29.kode.config.api.ToolChoiceConfig.Named)?.name.orEmpty(),
        schemaLevel = OptionalSchemaLevelChoice.fromLevel(base?.schema?.level),
        schemaNameInput = base?.schema?.name.orEmpty(),
        schemaJsonInput = base?.schema?.schema?.let { schema ->
            SETTINGS_JSON.encodeToString(JsonObject.serializer(), schema)
        }.orEmpty(),
    )
}

private fun extractOpenAiReasoningSummary(params: LlmModelParamsConfig?): OpenAiReasoningSummaryConfig? {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.reasoning?.summary
}

private fun extractOpenAiChatServiceTier(params: LlmModelParamsConfig?): OpenAiServiceTierConfig? {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.serviceTier
}

private fun extractOpenAiChatFrequencyPenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.frequencyPenalty?.toString().orEmpty()
}

private fun extractOpenAiChatPresencePenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.presencePenalty?.toString().orEmpty()
}

private fun extractOpenAiChatTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.topP?.toString().orEmpty()
}

private fun extractOpenAiChatTopLogprobs(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.topLogprobs?.toString().orEmpty()
}

private fun extractOpenAiChatStop(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.stop?.joinToString(separator = "\n").orEmpty()
}

private fun extractOpenAiChatParallelToolCalls(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.parallelToolCalls
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiChatStore(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.store
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiChatLogprobs(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.logprobs
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiChatPromptCacheKey(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.promptCacheKey.orEmpty()
}

private fun extractOpenAiChatSafetyIdentifier(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.chat?.safetyIdentifier.orEmpty()
}

private fun extractOpenAiResponsesBackground(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.background
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiResponsesInclude(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.include?.joinToString(separator = "\n").orEmpty()
}

private fun extractOpenAiResponsesMaxToolCalls(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.maxToolCalls?.toString().orEmpty()
}

private fun extractOpenAiResponsesParallelToolCalls(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.parallelToolCalls
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiResponsesTruncation(params: LlmModelParamsConfig?): OpenAiTruncationConfig? {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.truncation
}

private fun extractOpenAiResponsesServiceTier(params: LlmModelParamsConfig?): OpenAiServiceTierConfig? {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.serviceTier
}

private fun extractOpenAiResponsesStore(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.store
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiResponsesLogprobs(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    val value = (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.logprobs
    return OptionalBooleanChoice.fromBoolean(value)
}

private fun extractOpenAiResponsesTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.topP?.toString().orEmpty()
}

private fun extractOpenAiResponsesTopLogprobs(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.topLogprobs?.toString().orEmpty()
}

private fun extractOpenAiResponsesPromptCacheKey(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.promptCacheKey.orEmpty()
}

private fun extractOpenAiResponsesSafetyIdentifier(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenAiFamily)?.responses?.safetyIdentifier.orEmpty()
}

private fun extractAnthropicThinkingEnabled(params: LlmModelParamsConfig?): Boolean {
    return (params as? LlmModelParamsConfig.Anthropic)?.thinking?.enabled == true
}

private fun extractAnthropicThinkingBudget(params: LlmModelParamsConfig?): String {
    val budget = (params as? LlmModelParamsConfig.Anthropic)?.thinking?.budgetTokens
    return budget?.toString().orEmpty()
}

private fun extractAnthropicTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Anthropic)?.topP?.toString().orEmpty()
}

private fun extractAnthropicTopK(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Anthropic)?.topK?.toString().orEmpty()
}

private fun extractAnthropicStopSequences(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Anthropic)?.stopSequences?.joinToString(separator = "\n").orEmpty()
}

private fun extractAnthropicContainer(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Anthropic)?.container.orEmpty()
}

private fun extractAnthropicServiceTier(params: LlmModelParamsConfig?): AnthropicServiceTierConfig? {
    return (params as? LlmModelParamsConfig.Anthropic)?.serviceTier
}

private fun extractGeminiThinkingBudget(params: LlmModelParamsConfig?): String {
    val budget = (params as? LlmModelParamsConfig.Gemini)?.thinking?.thinkingBudget
    return budget?.toString().orEmpty()
}

private fun extractGeminiThinkingLevel(params: LlmModelParamsConfig?): GeminiThinkingLevelConfig? {
    return (params as? LlmModelParamsConfig.Gemini)?.thinking?.thinkingLevel
}

private fun extractGeminiTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Gemini)?.topP?.toString().orEmpty()
}

private fun extractGeminiTopK(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.Gemini)?.topK?.toString().orEmpty()
}

private fun extractDeepSeekFrequencyPenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.DeepSeek)?.frequencyPenalty?.toString().orEmpty()
}

private fun extractDeepSeekPresencePenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.DeepSeek)?.presencePenalty?.toString().orEmpty()
}

private fun extractDeepSeekTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.DeepSeek)?.topP?.toString().orEmpty()
}

private fun extractDeepSeekTopLogprobs(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.DeepSeek)?.topLogprobs?.toString().orEmpty()
}

private fun extractDeepSeekStop(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.DeepSeek)?.stop?.joinToString(separator = "\n").orEmpty()
}

private fun extractDeepSeekLogprobs(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    return OptionalBooleanChoice.fromBoolean((params as? LlmModelParamsConfig.DeepSeek)?.logprobs)
}

private fun extractOpenRouterReasoningEffort(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.reasoningEffort.orEmpty()
}

private fun extractOpenRouterFrequencyPenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.frequencyPenalty?.toString().orEmpty()
}

private fun extractOpenRouterPresencePenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.presencePenalty?.toString().orEmpty()
}

private fun extractOpenRouterTopP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.topP?.toString().orEmpty()
}

private fun extractOpenRouterTopK(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.topK?.toString().orEmpty()
}

private fun extractOpenRouterTopLogprobs(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.topLogprobs?.toString().orEmpty()
}

private fun extractOpenRouterRepetitionPenalty(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.repetitionPenalty?.toString().orEmpty()
}

private fun extractOpenRouterMinP(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.minP?.toString().orEmpty()
}

private fun extractOpenRouterTopA(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.topA?.toString().orEmpty()
}

private fun extractOpenRouterStop(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.stop?.joinToString(separator = "\n").orEmpty()
}

private fun extractOpenRouterTransforms(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.transforms?.joinToString(separator = "\n").orEmpty()
}

private fun extractOpenRouterModels(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.models?.joinToString(separator = "\n").orEmpty()
}

private fun extractOpenRouterRoute(params: LlmModelParamsConfig?): String {
    return (params as? LlmModelParamsConfig.OpenRouter)?.route.orEmpty()
}

private fun extractOpenRouterProviderPreferences(params: LlmModelParamsConfig?): String {
    val value = (params as? LlmModelParamsConfig.OpenRouter)?.providerPreferences ?: return ""
    return SETTINGS_JSON.encodeToString(JsonObject.serializer(), value)
}

private fun extractOpenRouterLogprobs(params: LlmModelParamsConfig?): OptionalBooleanChoice {
    return OptionalBooleanChoice.fromBoolean((params as? LlmModelParamsConfig.OpenRouter)?.logprobs)
}

private data class OpenAiBaseEditorState(
    val temperatureInput: String = "",
    val maxTokensInput: String = "",
    val numberOfChoicesInput: String = "",
    val speculationInput: String = "",
    val userInput: String = "",
    val toolChoiceMode: OptionalToolChoiceModeChoice = OptionalToolChoiceModeChoice.Default,
    val toolChoiceNameInput: String = "",
    val schemaLevel: OptionalSchemaLevelChoice = OptionalSchemaLevelChoice.Default,
    val schemaNameInput: String = "",
    val schemaJsonInput: String = "",
)

private enum class OptionalToolChoiceModeChoice(
    val label: String,
) {
    Default(label = "Default"),
    Auto(label = "auto"),
    None(label = "none"),
    Required(label = "required"),
    Named(label = "named"),
    ;

    companion object {
        fun fromToolChoice(toolChoice: io.github.stream29.kode.config.api.ToolChoiceConfig?): OptionalToolChoiceModeChoice {
            return when (toolChoice) {
                null -> Default
                io.github.stream29.kode.config.api.ToolChoiceConfig.Auto -> Auto
                io.github.stream29.kode.config.api.ToolChoiceConfig.None -> None
                io.github.stream29.kode.config.api.ToolChoiceConfig.Required -> Required
                is io.github.stream29.kode.config.api.ToolChoiceConfig.Named -> Named
            }
        }
    }
}

private enum class OptionalSchemaLevelChoice(
    val label: String,
    val level: io.github.stream29.kode.config.api.JsonSchemaLevelConfig?,
) {
    Default(label = "Default", level = null),
    Basic(label = "basic", level = io.github.stream29.kode.config.api.JsonSchemaLevelConfig.Basic),
    Standard(label = "standard", level = io.github.stream29.kode.config.api.JsonSchemaLevelConfig.Standard),
    ;

    companion object {
        fun fromLevel(level: io.github.stream29.kode.config.api.JsonSchemaLevelConfig?): OptionalSchemaLevelChoice {
            return entries.firstOrNull { choice -> choice.level == level } ?: Default
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenAiBaseParamsEditor(
    endpointLabel: String,
    state: OpenAiBaseEditorState,
    onStateChange: (OpenAiBaseEditorState) -> Unit,
) {
    Text(
        text = "$endpointLabel Base Params",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )

    OutlinedTextField(
        value = state.temperatureInput,
        onValueChange = { value -> onStateChange(state.copy(temperatureInput = value)) },
        label = { Text("Temperature") },
        supportingText = { Text("Optional decimal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.maxTokensInput,
        onValueChange = { value -> onStateChange(state.copy(maxTokensInput = value)) },
        label = { Text("Max Tokens") },
        supportingText = { Text("Optional integer >= 1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.numberOfChoicesInput,
        onValueChange = { value -> onStateChange(state.copy(numberOfChoicesInput = value)) },
        label = { Text("Number Of Choices") },
        supportingText = { Text("Optional integer >= 1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.userInput,
        onValueChange = { value -> onStateChange(state.copy(userInput = value)) },
        label = { Text("User") },
        supportingText = { Text("Optional") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.speculationInput,
        onValueChange = { value -> onStateChange(state.copy(speculationInput = value)) },
        label = { Text("Speculation") },
        supportingText = { Text("Optional") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    var toolChoiceExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = toolChoiceExpanded,
        onExpandedChange = { toolChoiceExpanded = it },
    ) {
        OutlinedTextField(
            value = state.toolChoiceMode.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tool Choice") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = toolChoiceExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = toolChoiceExpanded,
            onDismissRequest = { toolChoiceExpanded = false },
        ) {
            OptionalToolChoiceModeChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onStateChange(state.copy(toolChoiceMode = option))
                        toolChoiceExpanded = false
                    },
                )
            }
        }
    }

    if (state.toolChoiceMode == OptionalToolChoiceModeChoice.Named) {
        OutlinedTextField(
            value = state.toolChoiceNameInput,
            onValueChange = { value -> onStateChange(state.copy(toolChoiceNameInput = value)) },
            label = { Text("Tool Choice Name") },
            supportingText = { Text("Required when tool choice is named") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    var schemaLevelExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = schemaLevelExpanded,
        onExpandedChange = { schemaLevelExpanded = it },
    ) {
        OutlinedTextField(
            value = state.schemaLevel.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Schema Level") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = schemaLevelExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = schemaLevelExpanded,
            onDismissRequest = { schemaLevelExpanded = false },
        ) {
            OptionalSchemaLevelChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onStateChange(state.copy(schemaLevel = option))
                        schemaLevelExpanded = false
                    },
                )
            }
        }
    }

    if (state.schemaLevel.level != null) {
        OutlinedTextField(
            value = state.schemaNameInput,
            onValueChange = { value -> onStateChange(state.copy(schemaNameInput = value)) },
            label = { Text("Schema Name") },
            supportingText = { Text("Required when schema level is enabled") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.schemaJsonInput,
            onValueChange = { value -> onStateChange(state.copy(schemaJsonInput = value)) },
            label = { Text("Schema JSON") },
            supportingText = { Text("Required JSON object") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class OptionalBooleanChoice(
    val label: String,
    val booleanValue: Boolean?,
) {
    Default(label = "Default", booleanValue = null),
    Enabled(label = "Enabled", booleanValue = true),
    Disabled(label = "Disabled", booleanValue = false),
    ;

    companion object {
        fun fromBoolean(value: Boolean?): OptionalBooleanChoice {
            return entries.firstOrNull { choice -> choice.booleanValue == value } ?: Default
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalBooleanDropdownField(
    label: String,
    choice: OptionalBooleanChoice,
    onChoiceChange: (OptionalBooleanChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = choice.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            OptionalBooleanChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onChoiceChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private data class OptionalNumberParseResult<T>(
    val value: T?,
    val error: String? = null,
)

private fun parseOptionalDoubleField(
    input: String,
    fieldName: String,
    min: Double? = null,
    max: Double? = null,
): OptionalNumberParseResult<Double> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) {
        return OptionalNumberParseResult(value = null)
    }
    val parsed = trimmed.toDoubleOrNull()
        ?: return OptionalNumberParseResult(value = null, error = "$fieldName must be a decimal")
    if (min != null && parsed < min) {
        return OptionalNumberParseResult(value = null, error = "$fieldName must be >= $min")
    }
    if (max != null && parsed > max) {
        return OptionalNumberParseResult(value = null, error = "$fieldName must be <= $max")
    }
    return OptionalNumberParseResult(value = parsed)
}

private fun parseOptionalIntField(
    input: String,
    fieldName: String,
    min: Int? = null,
): OptionalNumberParseResult<Int> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) {
        return OptionalNumberParseResult(value = null)
    }
    val parsed = trimmed.toIntOrNull()
        ?: return OptionalNumberParseResult(value = null, error = "$fieldName must be an integer")
    if (min != null && parsed < min) {
        return OptionalNumberParseResult(value = null, error = "$fieldName must be >= $min")
    }
    return OptionalNumberParseResult(value = parsed)
}

private fun parseDelimitedValues(input: String): List<String>? {
    val values = input
        .split(',', '\n')
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
    return values.takeIf { it.isNotEmpty() }
}

private fun parseOpenAiIncludeValuesField(input: String): OptionalNumberParseResult<List<String>> {
    val includes = parseDelimitedValues(input) ?: return OptionalNumberParseResult(value = null)
    val invalid = includes.filterNot { value -> value in OPENAI_INCLUDE_VALUES }
    if (invalid.isNotEmpty()) {
        return OptionalNumberParseResult(
            value = null,
            error = "OpenAI include contains unsupported values: ${invalid.joinToString()}",
        )
    }
    return OptionalNumberParseResult(value = includes)
}

private fun parseOptionalJsonObjectField(
    input: String,
    fieldName: String,
): OptionalNumberParseResult<JsonObject> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) {
        return OptionalNumberParseResult(value = null)
    }
    val parsed = runCatching {
        SETTINGS_JSON.parseToJsonElement(trimmed)
    }.getOrNull() as? JsonObject
        ?: return OptionalNumberParseResult(value = null, error = "$fieldName must be a valid JSON object")
    return OptionalNumberParseResult(value = parsed)
}

private data class BaseParamsBuildResult(
    val base: io.github.stream29.kode.config.api.BaseModelParamsConfig? = null,
    val error: String? = null,
)

private fun buildOpenAiBaseParams(
    existing: io.github.stream29.kode.config.api.BaseModelParamsConfig,
    editor: OpenAiBaseEditorState,
    fieldPrefix: String,
): BaseParamsBuildResult {
    val temperatureResult = parseOptionalDoubleField(
        input = editor.temperatureInput,
        fieldName = "$fieldPrefix temperature",
    )
    if (temperatureResult.error != null) {
        return BaseParamsBuildResult(error = temperatureResult.error)
    }
    val maxTokensResult = parseOptionalIntField(
        input = editor.maxTokensInput,
        fieldName = "$fieldPrefix maxTokens",
        min = 1,
    )
    if (maxTokensResult.error != null) {
        return BaseParamsBuildResult(error = maxTokensResult.error)
    }
    val numberOfChoicesResult = parseOptionalIntField(
        input = editor.numberOfChoicesInput,
        fieldName = "$fieldPrefix numberOfChoices",
        min = 1,
    )
    if (numberOfChoicesResult.error != null) {
        return BaseParamsBuildResult(error = numberOfChoicesResult.error)
    }

    val toolChoice = when (editor.toolChoiceMode) {
        OptionalToolChoiceModeChoice.Default -> null
        OptionalToolChoiceModeChoice.Auto -> io.github.stream29.kode.config.api.ToolChoiceConfig.Auto
        OptionalToolChoiceModeChoice.None -> io.github.stream29.kode.config.api.ToolChoiceConfig.None
        OptionalToolChoiceModeChoice.Required -> io.github.stream29.kode.config.api.ToolChoiceConfig.Required
        OptionalToolChoiceModeChoice.Named -> {
            val name = editor.toolChoiceNameInput.trim()
            if (name.isBlank()) {
                return BaseParamsBuildResult(error = "$fieldPrefix toolChoice.name is required when mode=named")
            }
            io.github.stream29.kode.config.api.ToolChoiceConfig.Named(
                name = name,
            )
        }
    }

    val schemaLevel = editor.schemaLevel.level
    val schema = if (schemaLevel == null) {
        null
    } else {
        val schemaName = editor.schemaNameInput.trim()
        if (schemaName.isBlank()) {
            return BaseParamsBuildResult(error = "$fieldPrefix schema.name is required when schema level is enabled")
        }
        val schemaText = editor.schemaJsonInput.trim()
        if (schemaText.isBlank()) {
            return BaseParamsBuildResult(error = "$fieldPrefix schema JSON is required when schema level is enabled")
        }
        val schemaObject = runCatching {
            SETTINGS_JSON.parseToJsonElement(schemaText)
        }.getOrNull() as? JsonObject
            ?: return BaseParamsBuildResult(error = "$fieldPrefix schema JSON must be a valid JSON object")

        io.github.stream29.kode.config.api.JsonSchemaConfig(
            name = schemaName,
            level = schemaLevel,
            schema = schemaObject,
        )
    }

    return BaseParamsBuildResult(
        base = existing.copy(
            temperature = temperatureResult.value,
            maxTokens = maxTokensResult.value,
            numberOfChoices = numberOfChoicesResult.value,
            speculation = editor.speculationInput.trim().ifBlank { null },
            schema = schema,
            toolChoice = toolChoice,
            user = editor.userInput.trim().ifBlank { null },
        )
    )
}

private fun buildModelParamsConfig(
    family: ParamsUiFamily,
    input: ParamsBuildInput,
): ParamsBuildResult {
    return buildModelParamsConfig(
        providerId = input.providerId,
        existing = input.existing,
        family = family,
        openAiEndpoint = input.openAiEndpoint,
        openAiReasoningEffort = input.openAiReasoningEffort,
        openAiChatBase = input.openAiChatBase,
        openAiResponsesBase = input.openAiResponsesBase,
        openAiReasoningSummary = input.openAiReasoningSummary,
        openAiChatServiceTier = input.openAiChatServiceTier,
        openAiChatFrequencyPenaltyInput = input.openAiChatFrequencyPenaltyInput,
        openAiChatPresencePenaltyInput = input.openAiChatPresencePenaltyInput,
        openAiChatTopPInput = input.openAiChatTopPInput,
        openAiChatTopLogprobsInput = input.openAiChatTopLogprobsInput,
        openAiChatStopInput = input.openAiChatStopInput,
        openAiChatParallelToolCalls = input.openAiChatParallelToolCalls,
        openAiChatStore = input.openAiChatStore,
        openAiChatLogprobs = input.openAiChatLogprobs,
        openAiChatPromptCacheKeyInput = input.openAiChatPromptCacheKeyInput,
        openAiChatSafetyIdentifierInput = input.openAiChatSafetyIdentifierInput,
        openAiResponsesBackground = input.openAiResponsesBackground,
        openAiResponsesIncludeInput = input.openAiResponsesIncludeInput,
        openAiResponsesMaxToolCallsInput = input.openAiResponsesMaxToolCallsInput,
        openAiResponsesParallelToolCalls = input.openAiResponsesParallelToolCalls,
        openAiResponsesTruncation = input.openAiResponsesTruncation,
        openAiResponsesServiceTier = input.openAiResponsesServiceTier,
        openAiResponsesStore = input.openAiResponsesStore,
        openAiResponsesLogprobs = input.openAiResponsesLogprobs,
        openAiResponsesTopPInput = input.openAiResponsesTopPInput,
        openAiResponsesTopLogprobsInput = input.openAiResponsesTopLogprobsInput,
        openAiResponsesPromptCacheKeyInput = input.openAiResponsesPromptCacheKeyInput,
        openAiResponsesSafetyIdentifierInput = input.openAiResponsesSafetyIdentifierInput,
        anthropicThinkingEnabled = input.anthropicThinkingEnabled,
        anthropicThinkingBudgetInput = input.anthropicThinkingBudgetInput,
        anthropicTopPInput = input.anthropicTopPInput,
        anthropicTopKInput = input.anthropicTopKInput,
        anthropicStopSequencesInput = input.anthropicStopSequencesInput,
        anthropicContainerInput = input.anthropicContainerInput,
        anthropicServiceTier = input.anthropicServiceTier,
        geminiThinkingBudgetInput = input.geminiThinkingBudgetInput,
        geminiThinkingLevel = input.geminiThinkingLevel,
        geminiTopPInput = input.geminiTopPInput,
        geminiTopKInput = input.geminiTopKInput,
        deepSeekFrequencyPenaltyInput = input.deepSeekFrequencyPenaltyInput,
        deepSeekPresencePenaltyInput = input.deepSeekPresencePenaltyInput,
        deepSeekTopPInput = input.deepSeekTopPInput,
        deepSeekTopLogprobsInput = input.deepSeekTopLogprobsInput,
        deepSeekStopInput = input.deepSeekStopInput,
        deepSeekLogprobs = input.deepSeekLogprobs,
        openRouterReasoningEffortInput = input.openRouterReasoningEffortInput,
        openRouterFrequencyPenaltyInput = input.openRouterFrequencyPenaltyInput,
        openRouterPresencePenaltyInput = input.openRouterPresencePenaltyInput,
        openRouterTopPInput = input.openRouterTopPInput,
        openRouterTopKInput = input.openRouterTopKInput,
        openRouterTopLogprobsInput = input.openRouterTopLogprobsInput,
        openRouterRepetitionPenaltyInput = input.openRouterRepetitionPenaltyInput,
        openRouterMinPInput = input.openRouterMinPInput,
        openRouterTopAInput = input.openRouterTopAInput,
        openRouterStopInput = input.openRouterStopInput,
        openRouterTransformsInput = input.openRouterTransformsInput,
        openRouterModelsInput = input.openRouterModelsInput,
        openRouterRouteInput = input.openRouterRouteInput,
        openRouterProviderPreferencesInput = input.openRouterProviderPreferencesInput,
        openRouterLogprobs = input.openRouterLogprobs,
    )
}

private fun buildModelParamsConfig(
    providerId: String,
    existing: LlmModelParamsConfig?,
    family: ParamsUiFamily,
    openAiEndpoint: OpenAiEndpoint,
    openAiReasoningEffort: OpenAiReasoningEffortConfig?,
    openAiChatBase: OpenAiBaseEditorState,
    openAiResponsesBase: OpenAiBaseEditorState,
    openAiReasoningSummary: OpenAiReasoningSummaryConfig?,
    openAiChatServiceTier: OpenAiServiceTierConfig?,
    openAiChatFrequencyPenaltyInput: String,
    openAiChatPresencePenaltyInput: String,
    openAiChatTopPInput: String,
    openAiChatTopLogprobsInput: String,
    openAiChatStopInput: String,
    openAiChatParallelToolCalls: OptionalBooleanChoice,
    openAiChatStore: OptionalBooleanChoice,
    openAiChatLogprobs: OptionalBooleanChoice,
    openAiChatPromptCacheKeyInput: String,
    openAiChatSafetyIdentifierInput: String,
    openAiResponsesBackground: OptionalBooleanChoice,
    openAiResponsesIncludeInput: String,
    openAiResponsesMaxToolCallsInput: String,
    openAiResponsesParallelToolCalls: OptionalBooleanChoice,
    openAiResponsesTruncation: OpenAiTruncationConfig?,
    openAiResponsesServiceTier: OpenAiServiceTierConfig?,
    openAiResponsesStore: OptionalBooleanChoice,
    openAiResponsesLogprobs: OptionalBooleanChoice,
    openAiResponsesTopPInput: String,
    openAiResponsesTopLogprobsInput: String,
    openAiResponsesPromptCacheKeyInput: String,
    openAiResponsesSafetyIdentifierInput: String,
    anthropicThinkingEnabled: Boolean,
    anthropicThinkingBudgetInput: String,
    anthropicTopPInput: String,
    anthropicTopKInput: String,
    anthropicStopSequencesInput: String,
    anthropicContainerInput: String,
    anthropicServiceTier: AnthropicServiceTierConfig?,
    geminiThinkingBudgetInput: String,
    geminiThinkingLevel: GeminiThinkingLevelConfig?,
    geminiTopPInput: String,
    geminiTopKInput: String,
    deepSeekFrequencyPenaltyInput: String,
    deepSeekPresencePenaltyInput: String,
    deepSeekTopPInput: String,
    deepSeekTopLogprobsInput: String,
    deepSeekStopInput: String,
    deepSeekLogprobs: OptionalBooleanChoice,
    openRouterReasoningEffortInput: String,
    openRouterFrequencyPenaltyInput: String,
    openRouterPresencePenaltyInput: String,
    openRouterTopPInput: String,
    openRouterTopKInput: String,
    openRouterTopLogprobsInput: String,
    openRouterRepetitionPenaltyInput: String,
    openRouterMinPInput: String,
    openRouterTopAInput: String,
    openRouterStopInput: String,
    openRouterTransformsInput: String,
    openRouterModelsInput: String,
    openRouterRouteInput: String,
    openRouterProviderPreferencesInput: String,
    openRouterLogprobs: OptionalBooleanChoice,
): ParamsBuildResult {
    return family.buildParams(
        input = ParamsBuildInput(
            providerId = providerId,
            existing = existing,
            openAiEndpoint = openAiEndpoint,
            openAiReasoningEffort = openAiReasoningEffort,
            openAiChatBase = openAiChatBase,
            openAiResponsesBase = openAiResponsesBase,
            openAiReasoningSummary = openAiReasoningSummary,
            openAiChatServiceTier = openAiChatServiceTier,
            openAiChatFrequencyPenaltyInput = openAiChatFrequencyPenaltyInput,
            openAiChatPresencePenaltyInput = openAiChatPresencePenaltyInput,
            openAiChatTopPInput = openAiChatTopPInput,
            openAiChatTopLogprobsInput = openAiChatTopLogprobsInput,
            openAiChatStopInput = openAiChatStopInput,
            openAiChatParallelToolCalls = openAiChatParallelToolCalls,
            openAiChatStore = openAiChatStore,
            openAiChatLogprobs = openAiChatLogprobs,
            openAiChatPromptCacheKeyInput = openAiChatPromptCacheKeyInput,
            openAiChatSafetyIdentifierInput = openAiChatSafetyIdentifierInput,
            openAiResponsesBackground = openAiResponsesBackground,
            openAiResponsesIncludeInput = openAiResponsesIncludeInput,
            openAiResponsesMaxToolCallsInput = openAiResponsesMaxToolCallsInput,
            openAiResponsesParallelToolCalls = openAiResponsesParallelToolCalls,
            openAiResponsesTruncation = openAiResponsesTruncation,
            openAiResponsesServiceTier = openAiResponsesServiceTier,
            openAiResponsesStore = openAiResponsesStore,
            openAiResponsesLogprobs = openAiResponsesLogprobs,
            openAiResponsesTopPInput = openAiResponsesTopPInput,
            openAiResponsesTopLogprobsInput = openAiResponsesTopLogprobsInput,
            openAiResponsesPromptCacheKeyInput = openAiResponsesPromptCacheKeyInput,
            openAiResponsesSafetyIdentifierInput = openAiResponsesSafetyIdentifierInput,
            anthropicThinkingEnabled = anthropicThinkingEnabled,
            anthropicThinkingBudgetInput = anthropicThinkingBudgetInput,
            anthropicTopPInput = anthropicTopPInput,
            anthropicTopKInput = anthropicTopKInput,
            anthropicStopSequencesInput = anthropicStopSequencesInput,
            anthropicContainerInput = anthropicContainerInput,
            anthropicServiceTier = anthropicServiceTier,
            geminiThinkingBudgetInput = geminiThinkingBudgetInput,
            geminiThinkingLevel = geminiThinkingLevel,
            geminiTopPInput = geminiTopPInput,
            geminiTopKInput = geminiTopKInput,
            deepSeekFrequencyPenaltyInput = deepSeekFrequencyPenaltyInput,
            deepSeekPresencePenaltyInput = deepSeekPresencePenaltyInput,
            deepSeekTopPInput = deepSeekTopPInput,
            deepSeekTopLogprobsInput = deepSeekTopLogprobsInput,
            deepSeekStopInput = deepSeekStopInput,
            deepSeekLogprobs = deepSeekLogprobs,
            openRouterReasoningEffortInput = openRouterReasoningEffortInput,
            openRouterFrequencyPenaltyInput = openRouterFrequencyPenaltyInput,
            openRouterPresencePenaltyInput = openRouterPresencePenaltyInput,
            openRouterTopPInput = openRouterTopPInput,
            openRouterTopKInput = openRouterTopKInput,
            openRouterTopLogprobsInput = openRouterTopLogprobsInput,
            openRouterRepetitionPenaltyInput = openRouterRepetitionPenaltyInput,
            openRouterMinPInput = openRouterMinPInput,
            openRouterTopAInput = openRouterTopAInput,
            openRouterStopInput = openRouterStopInput,
            openRouterTransformsInput = openRouterTransformsInput,
            openRouterModelsInput = openRouterModelsInput,
            openRouterRouteInput = openRouterRouteInput,
            openRouterProviderPreferencesInput = openRouterProviderPreferencesInput,
            openRouterLogprobs = openRouterLogprobs,
        )
    )
}

private val SETTINGS_JSON: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}



@Composable
public fun AuthTab(viewModel: MainViewModel, ui: AppUiState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    var deletingAuth by remember { mutableStateOf<LlmAuthConfig?>(null) }
    val auths = ui.auths
    val models = ui.models
    val dependentModelsByAuthId = remember(models) { models.groupBy { model -> model.authId } }

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
                items(
                    items = auths,
                    key = { auth -> auth.id },
                ) { auth ->
                    val dependentModels = dependentModelsByAuthId[auth.id].orEmpty()
                    AuthCard(
                        auth = auth,
                        oauthStatus = ui.oauthStatusByAuthId[auth.id],
                        dependentModels = dependentModels,
                        onEdit = { editingAuth = auth },
                        onDelete = { deletingAuth = auth },
                        onConnect = {
                            viewModel.connectOAuth(auth.id)
                        },
                        onRefresh = {
                            viewModel.refreshOAuth(auth.id)
                        },
                        onDisconnect = {
                            viewModel.disconnectOAuth(auth.id)
                        },
                        onCancel = {
                            viewModel.cancelOAuth(auth.id)
                        },
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
        val dependentModels = dependentModelsByAuthId[auth.id].orEmpty()
        AlertDialog(
            onDismissRequest = { deletingAuth = null },
            title = { Text("Delete Auth Provider") },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            text = {
                Column {
                    Text("Are you sure you want to delete ${auth.name ?: auth.providerId} (${auth.id})?")
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

private fun providerConfigNameToPresetId(providerId: String): String? {
    val normalized = providerId.trim()
    if (normalized.isBlank()) {
        return null
    }
    return PROVIDER_PRESET_ID_ALIAS_BY_PROVIDER_ID[normalized] ?: normalized
}

private val PROVIDER_PRESET_ID_ALIAS_BY_PROVIDER_ID: Map<String, String> = mapOf(
    "openai" to "openai-api-key",
)

private val BASE_URL_REQUIRED_PROVIDER_IDS: Set<String> = setOf(
    "openai-compatible",
)

private fun buildAuthProviderEntries(
    presets: List<ProviderPreset>,
    editingAuth: LlmAuthConfig?,
): List<AuthProviderEntry> {
    val generated = presets.flatMap { preset ->
        val supportedModes = preset.authModes
            .mapNotNull { mode -> mode.toDialogAuthModeOrNull() }
            .distinct()
            .sortedBy { mode -> mode.priority }
        val appendModeSuffix = supportedModes.size > 1
        supportedModes.map { mode ->
            val label = if (appendModeSuffix) {
                "${preset.displayName} (${mode.label})"
            } else {
                preset.displayName
            }
            AuthProviderEntry(
                entryKey = "${preset.id}::${mode.name}",
                providerId = preset.id,
                label = label,
                authMode = mode,
                requiresBaseUrl = preset.id in BASE_URL_REQUIRED_PROVIDER_IDS,
            )
        }
    }.toMutableList()

    if (editingAuth != null) {
        val editingMode = inferAuthModeForExistingAuth(editingAuth)
        val hasExisting = generated.any { entry ->
            entry.providerId == editingAuth.providerId && entry.authMode == editingMode
        }
        if (!hasExisting) {
            generated += AuthProviderEntry(
                entryKey = "${editingAuth.providerId}::${editingMode.name}::existing",
                providerId = editingAuth.providerId,
                label = editingAuth.name ?: editingAuth.providerId,
                authMode = editingMode,
                requiresBaseUrl = editingAuth.providerId in BASE_URL_REQUIRED_PROVIDER_IDS,
            )
        }
    }

    return generated
        .distinctBy { entry -> entry.entryKey }
        .sortedBy { entry -> entry.label.lowercase() }
}

private fun ProviderAuthMode.toDialogAuthModeOrNull(): AuthMode? {
    return when (this) {
        ProviderAuthMode.ApiKey -> AuthMode.ApiKey
        ProviderAuthMode.OAuthSubscription -> AuthMode.OAuthSubscription
        ProviderAuthMode.OAuthDevice -> AuthMode.OAuthDevice
        ProviderAuthMode.CloudCredentialChain,
        ProviderAuthMode.WellKnown,
        -> null
    }
}

private fun inferAuthModeForExistingAuth(auth: LlmAuthConfig): AuthMode {
    val oauth = auth.auth.oauthConfigOrNull()
    if (oauth != null) {
        return if (oauth.isDeviceFlow(providerId = auth.providerId)) {
            AuthMode.OAuthDevice
        } else {
            AuthMode.OAuthSubscription
        }
    }
    return AuthMode.ApiKey
}

private val PROVIDER_ICON_BY_PREFIX: List<Pair<String, ImageVector>> = listOf(
    "anthropic" to Icons.Default.Psychology,
    "openai" to Icons.AutoMirrored.Filled.Chat,
    "moonshot" to Icons.Default.Nightlight,
    "gemini" to Icons.Default.Star,
    "deepseek" to Icons.Default.Search,
)

private fun resolveProviderIcon(providerId: String): ImageVector {
    val normalized = providerId.trim().lowercase()
    return PROVIDER_ICON_BY_PREFIX.firstOrNull { (prefix, _) -> normalized.startsWith(prefix) }
        ?.second
        ?: Icons.Default.Cloud
}

private fun resolveOAuthPreset(
    viewModel: MainViewModel,
    providerEntry: AuthProviderEntry,
): ProviderOAuthAuthCodePkcePreset? {
    val mode = providerEntry.authMode.authCodePresetMode ?: return null
    val presetId = providerConfigNameToPresetId(providerId = providerEntry.providerId) ?: return null
    val preset = viewModel.getProviderPresets().firstOrNull { item -> item.id == presetId } ?: return null
    return preset.oauthAuthCodePkceByMode[mode]
}

private fun resolveOAuthDevicePreset(
    viewModel: MainViewModel,
    providerEntry: AuthProviderEntry,
): ProviderOAuthDeviceFlowPreset? {
    val mode = providerEntry.authMode.devicePresetMode ?: return null
    val presetId = providerConfigNameToPresetId(providerId = providerEntry.providerId) ?: return null
    val preset = viewModel.getProviderPresets().firstOrNull { item -> item.id == presetId } ?: return null
    return preset.oauthDeviceFlowByMode[mode]
}

private enum class AuthMode(
    val isApiKey: Boolean,
    val authCodePresetMode: ProviderAuthMode?,
    val devicePresetMode: ProviderAuthMode?,
    val deviceFlowByDefault: Boolean,
    val label: String,
    val priority: Int,
) {
    ApiKey(
        isApiKey = true,
        authCodePresetMode = null,
        devicePresetMode = null,
        deviceFlowByDefault = false,
        label = "API Key",
        priority = 0,
    ),
    OAuthSubscription(
        isApiKey = false,
        authCodePresetMode = ProviderAuthMode.OAuthSubscription,
        devicePresetMode = null,
        deviceFlowByDefault = false,
        label = "OAuth Browser",
        priority = 1,
    ),
    OAuthDevice(
        isApiKey = false,
        authCodePresetMode = null,
        devicePresetMode = ProviderAuthMode.OAuthDevice,
        deviceFlowByDefault = true,
        label = "OAuth Device",
        priority = 2,
    ),
    ;
}

private data class AuthProviderEntry(
    val entryKey: String,
    val providerId: String,
    val label: String,
    val authMode: AuthMode,
    val requiresBaseUrl: Boolean = false,
)

@Composable
private fun AuthCard(
    auth: LlmAuthConfig,
    oauthStatus: OAuthStatusUi?,
    dependentModels: List<LlmModelConfig>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    var showApiKey by remember { mutableStateOf(false) }

    val providerId = auth.providerId.trim()
    val providerLabel = auth.name ?: providerId

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
                        imageVector = resolveProviderIcon(providerId = providerId),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = providerLabel,
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
                val apiKey = auth.auth.apiKeyOrNull().orEmpty()
                val maskedApiKey = if (apiKey.isBlank()) "(managed externally)" else "••••••••" + apiKey.takeLast(4)
                OutlinedTextField(
                    value = if (showApiKey && apiKey.isNotBlank()) apiKey else maskedApiKey,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        if (apiKey.isBlank()) {
                            Text("Credential")
                        } else {
                            Text("API Key")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (apiKey.isNotBlank()) {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Hide" else "Show"
                        )
                    }
                }
            }

            val baseUrl = auth.auth.baseUrl
            baseUrl?.let { url ->
                Text(
                    text = "Base URL: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val oauth = auth.auth.oauthConfigOrNull()
            if (oauth != null) {
                val canInteractiveConnect = oauth.canInteractiveConnect(providerId = providerId)
                val (flowLabel, endpointLabel, endpointValue) = when (oauth) {
                    is OAuthConfig.AuthCodePkce -> Triple(
                        "Authorization Code (PKCE)",
                        "OAuth Authorization Endpoint",
                        oauth.authorizationEndpoint ?: "not configured",
                    )

                    is OAuthConfig.DeviceFlow -> Triple(
                        "Device Flow",
                        "OAuth Device Authorization Endpoint",
                        oauth.deviceAuthorizationEndpoint ?: "not configured",
                    )
                }
                Text(
                    text = "OAuth Key: ${oauth.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "OAuth Flow: $flowLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$endpointLabel: $endpointValue",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!canInteractiveConnect) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                oauthStatus?.let { status ->
                    Text(
                        text = "OAuth Status: ${status.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            status.inProgress -> MaterialTheme.colorScheme.secondary
                            !status.connected || status.expired -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
                val isBusy = oauthStatus?.inProgress == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onConnect,
                        enabled = canInteractiveConnect && !isBusy,
                    ) {
                        Text("Connect")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isBusy,
                    ) {
                        Text("Refresh")
                    }
                    TextButton(
                        onClick = onDisconnect,
                        enabled = !isBusy,
                    ) {
                        Text("Disconnect")
                    }
                    TextButton(
                        onClick = onCancel,
                        enabled = isBusy,
                    ) {
                        Text("Cancel")
                    }
                }
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
    val existingApiKey = auth?.auth?.apiKeyOrNull().orEmpty()
    val existingBaseUrl = auth?.auth?.baseUrl.orEmpty()
    val existingOauth = auth?.auth?.oauthConfigOrNull()

    var apiKey by remember { mutableStateOf(existingApiKey) }
    var baseUrl by remember { mutableStateOf(existingBaseUrl) }
    var oauthStorage by remember { mutableStateOf(existingOauth?.storage ?: "file") }
    var oauthKey by remember { mutableStateOf(existingOauth?.key ?: "") }
    var oauthAuthorizationEndpoint by remember { mutableStateOf(existingOauth?.authorizationEndpoint ?: "") }
    var oauthTokenEndpoint by remember { mutableStateOf(existingOauth?.tokenEndpoint ?: "") }
    var oauthClientId by remember { mutableStateOf(existingOauth?.clientId ?: "") }
    var oauthScopesText by remember { mutableStateOf(existingOauth?.scopes?.joinToString(separator = " ") ?: "") }
    var oauthCallbackUri by remember { mutableStateOf(existingOauth?.callbackUri ?: "") }
    var oauthDeviceFlowStrategy by remember { mutableStateOf(existingOauth?.deviceFlowStrategy ?: "") }
    var oauthDeviceAuthorizationEndpoint by remember {
        mutableStateOf(existingOauth?.deviceAuthorizationEndpoint ?: "")
    }
    var oauthDeviceTokenEndpoint by remember { mutableStateOf(existingOauth?.deviceTokenEndpoint ?: "") }
    var oauthDeviceVerificationUri by remember { mutableStateOf(existingOauth?.deviceVerificationUri ?: "") }
    var oauthDeviceRedirectUri by remember { mutableStateOf(existingOauth?.deviceRedirectUri ?: "") }
    val providerPresets = remember(viewModel) { viewModel.getProviderPresets() }
    val providerEntries = remember(providerPresets, auth?.id, auth?.providerId) {
        buildAuthProviderEntries(
            presets = providerPresets,
            editingAuth = auth,
        )
    }
    val inferredAuthMode = auth?.let { existing -> inferAuthModeForExistingAuth(existing) }
    val initialProviderEntry = remember(providerEntries, auth?.providerId, inferredAuthMode) {
        val existingProviderId = auth?.providerId?.trim().orEmpty()
        providerEntries.firstOrNull { entry ->
            entry.providerId == existingProviderId && (inferredAuthMode == null || entry.authMode == inferredAuthMode)
        } ?: providerEntries.firstOrNull { entry ->
            entry.providerId == existingProviderId
        } ?: providerEntries.firstOrNull()
    }
    var selectedProviderEntryKey by remember { mutableStateOf(initialProviderEntry?.entryKey.orEmpty()) }
    val selectedProviderEntry: AuthProviderEntry? = providerEntries.firstOrNull { entry -> entry.entryKey == selectedProviderEntryKey }
        ?: providerEntries.firstOrNull()
    val selectedProviderId = selectedProviderEntry?.providerId.orEmpty()
    val selectedProviderLabel = selectedProviderEntry?.label ?: "No providers available"
    var customName by remember {
        mutableStateOf(auth?.name.orEmpty())
    }
    var idError by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val isEditing = auth != null
    val needsBaseUrl = selectedProviderEntry?.requiresBaseUrl == true
    val isApiKeyMode = selectedProviderEntry?.authMode?.isApiKey != false
    val showCredentialField = isApiKeyMode || needsBaseUrl || showAdvanced
    val showBaseUrlField = needsBaseUrl || showAdvanced
    val showOAuthFields = selectedProviderEntry != null && !isApiKeyMode && showAdvanced
    val suggestedId = selectedProviderEntry?.let { entry ->
        viewModel.generateDefaultAuthId(entry.providerId, customName)
    }.orEmpty()
    val oauthPreset = selectedProviderEntry?.let { entry ->
        resolveOAuthPreset(
            viewModel = viewModel,
            providerEntry = entry,
        )
    }
    val oauthDevicePreset = selectedProviderEntry?.let { entry ->
        resolveOAuthDevicePreset(
            viewModel = viewModel,
            providerEntry = entry,
        )
    }
    val hasOAuthPreset = oauthPreset != null || oauthDevicePreset != null
    val authDialogScrollState = rememberScrollState()

    LaunchedEffect(selectedProviderEntryKey) {
        if (isApiKeyMode) {
            return@LaunchedEffect
        }
        if (oauthStorage.isBlank()) {
            oauthStorage = "file"
        }
        if (oauthAuthorizationEndpoint.isBlank()) {
            oauthAuthorizationEndpoint = oauthPreset?.authorizationEndpoint.orEmpty()
        }
        if (oauthTokenEndpoint.isBlank()) {
            oauthTokenEndpoint = oauthPreset?.tokenEndpoint ?: oauthDevicePreset?.tokenEndpoint.orEmpty()
        }
        if (oauthClientId.isBlank()) {
            oauthClientId = oauthPreset?.clientId ?: oauthDevicePreset?.clientId.orEmpty()
        }
        if (oauthScopesText.isBlank()) {
            oauthScopesText = oauthPreset?.scopes?.joinToString(separator = " ")
                ?: oauthDevicePreset?.scopes?.joinToString(separator = " ").orEmpty()
        }
        if (oauthCallbackUri.isBlank()) {
            oauthCallbackUri = oauthPreset?.callbackUri.orEmpty()
        }
        if (oauthDeviceFlowStrategy.isBlank()) {
            oauthDeviceFlowStrategy = oauthDevicePreset?.strategy.orEmpty()
        }
        if (oauthDeviceAuthorizationEndpoint.isBlank()) {
            oauthDeviceAuthorizationEndpoint = oauthDevicePreset?.deviceAuthorizationEndpoint.orEmpty()
        }
        if (oauthDeviceTokenEndpoint.isBlank()) {
            oauthDeviceTokenEndpoint = oauthDevicePreset?.deviceTokenEndpoint.orEmpty()
        }
        if (oauthDeviceVerificationUri.isBlank()) {
            oauthDeviceVerificationUri = oauthDevicePreset?.verificationUri.orEmpty()
        }
        if (oauthDeviceRedirectUri.isBlank()) {
            oauthDeviceRedirectUri = oauthDevicePreset?.redirectUri.orEmpty()
        }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Auth Provider" else "Add Auth Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(authDialogScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProviderLabel,
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
                        providerEntries.forEach { providerEntry ->
                            DropdownMenuItem(
                                text = { Text(providerEntry.label) },
                                onClick = {
                                    selectedProviderEntryKey = providerEntry.entryKey
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

                if (showCredentialField) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = {
                            if (isApiKeyMode) {
                                Text("API Key *")
                            } else {
                                Text("Credential")
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Credential is managed by OAuth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showBaseUrlField) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(if (needsBaseUrl) "Base URL *" else "Base URL") },
                        supportingText = { Text("Optional custom endpoint") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isApiKeyMode && !showAdvanced) {
                    val fallback = viewModel.generateDefaultOAuthTokenStorageKey(
                        authId = id.ifBlank { suggestedId.ifBlank { "auth" } }
                    )
                    val effectiveKey = oauthKey.trim().ifBlank { fallback }
                    Text(
                        text = if (hasOAuthPreset) {
                            "OAuth preset is configured automatically. After adding, use Connect from the provider list."
                        } else {
                            "OAuth settings are required. Enable Advanced settings to configure OAuth details."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Token file: $effectiveKey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Advanced settings")
                    Switch(
                        checked = showAdvanced,
                        onCheckedChange = { enabled -> showAdvanced = enabled },
                    )
                }

                if (showOAuthFields) {
                    OutlinedTextField(
                        value = oauthStorage,
                        onValueChange = { oauthStorage = it },
                        label = { Text("OAuth Storage") },
                        supportingText = { Text("file or env") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthKey,
                        onValueChange = { oauthKey = it },
                        label = { Text("OAuth Token Key") },
                        supportingText = {
                            val fallback = viewModel.generateDefaultOAuthTokenStorageKey(
                                authId = id.ifBlank { suggestedId.ifBlank { "auth" } }
                            )
                            Text(fallback)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthAuthorizationEndpoint,
                        onValueChange = { oauthAuthorizationEndpoint = it },
                        label = { Text("OAuth Authorization Endpoint") },
                        supportingText = {
                            val fallback = oauthPreset?.authorizationEndpoint.orEmpty()
                            if (fallback.isNotBlank()) {
                                Text(fallback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthTokenEndpoint,
                        onValueChange = { oauthTokenEndpoint = it },
                        label = { Text("OAuth Token Endpoint") },
                        supportingText = {
                            val fallback = oauthPreset?.tokenEndpoint.orEmpty()
                            if (fallback.isNotBlank()) {
                                Text(fallback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthClientId,
                        onValueChange = { oauthClientId = it },
                        label = { Text("OAuth Client ID") },
                        supportingText = {
                            val fallback = oauthPreset?.clientId.orEmpty()
                            if (fallback.isNotBlank()) {
                                Text(fallback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthScopesText,
                        onValueChange = { oauthScopesText = it },
                        label = { Text("OAuth Scopes") },
                        supportingText = {
                            val fallback = oauthPreset?.scopes?.joinToString(separator = " ").orEmpty()
                            if (fallback.isNotBlank()) {
                                Text(fallback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = oauthCallbackUri,
                        onValueChange = { oauthCallbackUri = it },
                        label = { Text("OAuth Callback URI") },
                        supportingText = {
                            val fallback = oauthPreset?.callbackUri.orEmpty()
                            if (fallback.isNotBlank()) {
                                Text(fallback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (selectedProviderEntry.authMode.deviceFlowByDefault) {
                        OutlinedTextField(
                            value = oauthDeviceFlowStrategy,
                            onValueChange = { oauthDeviceFlowStrategy = it },
                            label = { Text("OAuth Device Strategy") },
                            supportingText = {
                                val fallback = oauthDevicePreset?.strategy.orEmpty()
                                if (fallback.isNotBlank()) {
                                    Text(fallback)
                                } else {
                                    Text("e.g. rfc8628 or openai_codex_bridge")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = oauthDeviceAuthorizationEndpoint,
                            onValueChange = { oauthDeviceAuthorizationEndpoint = it },
                            label = { Text("OAuth Device Authorization Endpoint") },
                            supportingText = {
                                val fallback = oauthDevicePreset?.deviceAuthorizationEndpoint.orEmpty()
                                if (fallback.isNotBlank()) {
                                    Text(fallback)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = oauthDeviceTokenEndpoint,
                            onValueChange = { oauthDeviceTokenEndpoint = it },
                            label = { Text("OAuth Device Token Poll Endpoint") },
                            supportingText = {
                                val fallback = oauthDevicePreset?.deviceTokenEndpoint.orEmpty()
                                if (fallback.isNotBlank()) {
                                    Text(fallback)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = oauthDeviceVerificationUri,
                            onValueChange = { oauthDeviceVerificationUri = it },
                            label = { Text("OAuth Device Verification URI") },
                            supportingText = {
                                val fallback = oauthDevicePreset?.verificationUri.orEmpty()
                                if (fallback.isNotBlank()) {
                                    Text(fallback)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = oauthDeviceRedirectUri,
                            onValueChange = { oauthDeviceRedirectUri = it },
                            label = { Text("OAuth Device Redirect URI") },
                            supportingText = {
                                val fallback = oauthDevicePreset?.redirectUri.orEmpty()
                                if (fallback.isNotBlank()) {
                                    Text(fallback)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var resolvedId = id
                    if (resolvedId.isBlank()) {
                        resolvedId = viewModel.generateDefaultAuthId(selectedProviderId, customName)
                    }
                    if (resolvedId.isBlank()) {
                        idError = "Unable to generate ID"
                        return@TextButton
                    }
                    if (selectedProviderEntry == null || selectedProviderId.isBlank()) {
                        idError = "Provider is required"
                        return@TextButton
                    }
                    if (isApiKeyMode && apiKey.isBlank()) {
                        return@TextButton
                    }
                    if (needsBaseUrl && baseUrl.isBlank()) {
                        return@TextButton
                    }

                    val defaultOAuthKey = viewModel.generateDefaultOAuthTokenStorageKey(authId = resolvedId)
                    val resolvedOauth = if (isApiKeyMode) {
                        null
                    } else {
                        val scopesFromInput = oauthScopesText
                            .split(',', ' ', '\n', '\t')
                            .map { token -> token.trim() }
                            .filter { token -> token.isNotBlank() }
                            .distinct()
                        val base = existingOauth
                        val baseAuthCode = base as? io.github.stream29.kode.config.api.OAuthConfig.AuthCodePkce
                        val baseDevice = base as? io.github.stream29.kode.config.api.OAuthConfig.DeviceFlow
                        val resolvedStorage = oauthStorage.trim().ifBlank {
                            base?.storage?.trim().orEmpty().ifBlank { "file" }
                        }
                        val resolvedKey = oauthKey.trim().ifBlank {
                            base?.key?.trim().orEmpty().ifBlank { defaultOAuthKey }
                        }
                        val resolvedTokenEndpoint = oauthTokenEndpoint.trim().ifBlank {
                            base?.tokenEndpoint.orEmpty()
                        }.ifBlank {
                            oauthPreset?.tokenEndpoint ?: oauthDevicePreset?.tokenEndpoint.orEmpty()
                        }.ifBlank {
                            ""
                        }
                        val resolvedClientId = oauthClientId.trim().ifBlank {
                            base?.clientId.orEmpty()
                        }.ifBlank {
                            oauthPreset?.clientId ?: oauthDevicePreset?.clientId.orEmpty()
                        }.ifBlank {
                            ""
                        }
                        val resolvedScopes = when {
                            scopesFromInput.isNotEmpty() -> scopesFromInput
                            !base?.scopes.isNullOrEmpty() -> base.scopes
                            else -> oauthPreset?.scopes ?: oauthDevicePreset?.scopes.orEmpty()
                        }

                        if (selectedProviderEntry.authMode.deviceFlowByDefault) {
                            val resolvedDeviceFlowStrategy = oauthDeviceFlowStrategy.trim().ifBlank {
                                baseDevice?.deviceFlowStrategy.orEmpty()
                            }.ifBlank {
                                oauthDevicePreset?.strategy.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedDeviceAuthorizationEndpoint = oauthDeviceAuthorizationEndpoint.trim().ifBlank {
                                baseDevice?.deviceAuthorizationEndpoint.orEmpty()
                            }.ifBlank {
                                oauthDevicePreset?.deviceAuthorizationEndpoint.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedDeviceTokenEndpoint = oauthDeviceTokenEndpoint.trim().ifBlank {
                                baseDevice?.deviceTokenEndpoint.orEmpty()
                            }.ifBlank {
                                oauthDevicePreset?.deviceTokenEndpoint.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedDeviceVerificationUri = oauthDeviceVerificationUri.trim().ifBlank {
                                baseDevice?.deviceVerificationUri.orEmpty()
                            }.ifBlank {
                                oauthDevicePreset?.verificationUri.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedDeviceRedirectUri = oauthDeviceRedirectUri.trim().ifBlank {
                                baseDevice?.deviceRedirectUri.orEmpty()
                            }.ifBlank {
                                oauthDevicePreset?.redirectUri.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            io.github.stream29.kode.config.api.OAuthConfig.DeviceFlow(
                                storage = resolvedStorage,
                                key = resolvedKey,
                                tokenEndpoint = resolvedTokenEndpoint.ifBlank { null },
                                clientId = resolvedClientId.ifBlank { null },
                                scopes = resolvedScopes,
                                tokenAdditionalParams = baseDevice?.tokenAdditionalParams.orEmpty(),
                                deviceFlowStrategy = resolvedDeviceFlowStrategy.ifBlank { null },
                                deviceAuthorizationEndpoint = resolvedDeviceAuthorizationEndpoint.ifBlank { null },
                                deviceTokenEndpoint = resolvedDeviceTokenEndpoint.ifBlank { null },
                                deviceVerificationUri = resolvedDeviceVerificationUri.ifBlank { null },
                                deviceRedirectUri = resolvedDeviceRedirectUri.ifBlank { null },
                            )
                        } else {
                            val resolvedAuthorizationEndpoint = oauthAuthorizationEndpoint.trim().ifBlank {
                                baseAuthCode?.authorizationEndpoint.orEmpty()
                            }.ifBlank {
                                oauthPreset?.authorizationEndpoint.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedCallbackUri = oauthCallbackUri.trim().ifBlank {
                                baseAuthCode?.callbackUri.orEmpty()
                            }.ifBlank {
                                oauthPreset?.callbackUri.orEmpty()
                            }.ifBlank {
                                ""
                            }
                            val resolvedAuthorizationAdditionalParams = baseAuthCode?.authorizationAdditionalParams
                                .orEmpty()
                                .ifEmpty { oauthPreset?.authorizationAdditionalParams.orEmpty() }
                            val resolvedTokenAdditionalParams = baseAuthCode?.tokenAdditionalParams
                                .orEmpty()
                                .ifEmpty { oauthPreset?.tokenAdditionalParams.orEmpty() }
                            io.github.stream29.kode.config.api.OAuthConfig.AuthCodePkce(
                                storage = resolvedStorage,
                                key = resolvedKey,
                                authorizationEndpoint = resolvedAuthorizationEndpoint.ifBlank { null },
                                tokenEndpoint = resolvedTokenEndpoint.ifBlank { null },
                                clientId = resolvedClientId.ifBlank { null },
                                scopes = resolvedScopes,
                                callbackUri = resolvedCallbackUri.ifBlank { null },
                                authorizationAdditionalParams = resolvedAuthorizationAdditionalParams,
                                tokenAdditionalParams = resolvedTokenAdditionalParams,
                            )
                        }
                    }
                    if (!isApiKeyMode) {
                        val oauthConfig = resolvedOauth
                        val storageValue = oauthConfig?.storage?.trim().orEmpty().ifBlank { "file" }
                        val keyValue = oauthConfig?.key?.trim().orEmpty()
                        if (oauthConfig == null || keyValue.isBlank()) {
                            idError = "OAuth token key is required for $selectedProviderLabel"
                            return@TextButton
                        }
                        if (storageValue != "file" && storageValue != "env") {
                            idError = "OAuth storage must be 'file' or 'env'"
                            return@TextButton
                        }
                        val isDeviceFlow = oauthConfig.isDeviceFlow(providerId = selectedProviderId)
                        val hasAuthCodeFields = oauthConfig.hasAuthCodeRequiredFields()
                        val hasDeviceFlowFields = oauthConfig.hasDeviceFlowRequiredFields()
                        val requiresDeviceBridgeTokenPoll = oauthConfig.requiresDeviceTokenPollEndpoint()

                        if (isDeviceFlow) {
                            if (!hasDeviceFlowFields) {
                                idError = "OAuth device fields are incomplete for $selectedProviderLabel"
                                return@TextButton
                            }
                            if (requiresDeviceBridgeTokenPoll && oauthConfig.deviceTokenEndpoint.isNullOrBlank()) {
                                idError = "OAuth device token poll endpoint is required for openai_codex_bridge"
                                return@TextButton
                            }
                        } else {
                            if (!hasAuthCodeFields) {
                                idError = "OAuth auth code fields are incomplete for $selectedProviderLabel"
                                return@TextButton
                            }
                        }
                    }

                    val preset = viewModel.getProviderPresets().firstOrNull { preset -> preset.id == selectedProviderId }
                    val envKeys = preset?.envKeys.orEmpty()
                    val resolvedBaseUrl = baseUrl.trim().takeIf { it.isNotBlank() }
                    val resolvedName = customName.trim().takeIf { it.isNotBlank() }
                    val resolvedAuth = if (isApiKeyMode) {
                        io.github.stream29.kode.config.api.LlmAuth.ApiKey(
                            apiKey = apiKey,
                            envKeys = envKeys,
                            baseUrl = resolvedBaseUrl,
                            customHeaders = emptyMap(),
                        )
                    } else {
                        io.github.stream29.kode.config.api.LlmAuth.OAuth(
                            oauth = requireNotNull(resolvedOauth) { "OAuth config is null" },
                            baseUrl = resolvedBaseUrl,
                            customHeaders = emptyMap(),
                        )
                    }
                    onConfirm(
                        LlmAuthConfig(
                            id = resolvedId,
                            providerId = selectedProviderId,
                            name = resolvedName,
                            auth = resolvedAuth,
                        )
                    )
                },
                enabled = selectedProviderEntry != null &&
                        (!isApiKeyMode || apiKey.isNotBlank()) && (!needsBaseUrl || baseUrl.isNotBlank()) &&
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

            Spacer(modifier = Modifier.height(12.dp))

            SendKeyModeSection(viewModel = viewModel, ui = ui)
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
                "Execution Mode",
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
                        "YOLO mode is always enabled",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Interactive tool approvals have been removed; all tool calls run directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                value = defaultModel?.let { formatModelDisplayName(it, auths) } ?: "Select default model",
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
                                Text(formatModelDisplayName(model, auths))
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
private fun SendKeyModeSection(viewModel: MainViewModel, ui: AppUiState) {
    val options = SendKeyModePreference.entries
    val selectedOption = SendKeyModePreference.fromValue(ui.sendKeyMode)
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Send key mode",
            style = MaterialTheme.typography.bodyMedium,
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedOption.label,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            viewModel.sendKeyMode = option.value
                            expanded = false
                        },
                    )
                }
            }
        }

        Text(
            text = "Applied to chat input box",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    val alignmentOptions = MessageAlignmentPreference.entries
    val selectedAlignment = MessageAlignmentPreference.fromValue(ui.messageAlignment)
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
                value = activeModel?.let { formatModelDisplayName(it, auths) } ?: "Select a model",
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
                                Text(formatModelDisplayName(model, auths))
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
