package io.github.stream29.kode.agentapitest

import io.github.stream29.kode.app.di.appModule
import io.github.stream29.kode.app.viewmodel.MainViewModel
import io.github.stream29.kode.config.api.AUTH_MODE_API_KEY
import io.github.stream29.kode.config.api.AppConfig
import io.github.stream29.kode.config.api.ConfigProvider
import io.github.stream29.kode.config.api.ConfigSource
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.core.hooks.AssistantResponseHook
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.hooks.ToolCallAfterHook
import io.github.stream29.kode.core.hooks.ToolCallBeforeHook
import io.github.stream29.kode.core.hooks.ToolCallHookResult
import io.github.stream29.kode.core.hooks.UserPromptHook
import io.github.stream29.kode.providers.builtin.TEST_DETERMINISTIC_PROVIDER_ID
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.SessionMessage
import io.github.stream29.kode.session.core.model.ToolExchangeMessage
import io.github.stream29.kode.session.core.model.UserMessage
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

public fun main() {
    runBlocking {
        validateConfigLoadFromPresetField()
        validatePresetBoundHookExecution()
        validateContinueInputBehaviorWithTestProvider()
        if (isLiveAgentApiCheckEnabled()) {
            validateContinueInputBehaviorWithAnthropicHaiku()
        } else {
            println("agent-api-test: skipped live anthropic check (set KODE_AGENT_API_TEST_ENABLE_LIVE=true to enable)")
        }
    }
    println("agent-api-test: all checks passed")
}

private fun isLiveAgentApiCheckEnabled(): Boolean {
    val value = System.getenv("KODE_AGENT_API_TEST_ENABLE_LIVE")?.trim()?.lowercase().orEmpty()
    return value == "1" || value == "true" || value == "yes"
}

private suspend fun validateContinueInputBehaviorWithTestProvider() {
    stopKoin()
    val configOverrideModule = module {
        single<ConfigManager> {
            ConfigManager(
                provider = InMemoryConfigProvider(initialConfig = AppConfig()),
                source = null,
            )
        }
    }
    val koinApp = startKoin {
        allowOverride(override = true)
        modules(appModule, configOverrideModule)
    }

    var createdSessionId: String? = null
    try {
        val koin = koinApp.koin
        val viewModel = koin.get<MainViewModel>()
        val sessionManager = koin.get<SessionManager>()

        val existingAuthIds = viewModel.chatPageUiState.value.auths.map { auth -> auth.id }.toSet()
        viewModel.quickSetupProvider(
            providerId = TEST_DETERMINISTIC_PROVIDER_ID,
            authMode = AUTH_MODE_API_KEY,
            apiKey = TEST_PROVIDER_DUMMY_API_KEY,
            baseUrlInput = "",
            addRecommendedModels = false,
            connectOAuthNow = false,
        )

        val testAuthId = waitUntilValue(
            description = "register test provider auth",
            timeoutMillis = 30_000,
        ) {
            viewModel.chatPageUiState.value.auths
                .firstOrNull { auth ->
                    auth.providerId == TEST_DETERMINISTIC_PROVIDER_ID && auth.id !in existingAuthIds
                }
                ?.id
        }

        val previousSessionId = viewModel.currentSessionId
        viewModel.newSessionDirInput = "."
        viewModel.confirmNewSessionDir()
        waitUntil(
            description = "create test-provider session",
            timeoutMillis = 30_000,
        ) {
            val current = viewModel.currentSessionId
            current != null && current != previousSessionId
        }

        val sessionId = requireNotNull(viewModel.currentSessionId)
        createdSessionId = sessionId
        val pendingBeforeContinue = sessionManager.getTrailingPendingToolCall(sessionId = sessionId, agentId = null)
        ensure(pendingBeforeContinue == null) {
            "expected no pending tool call before continueFromInput, got ${pendingBeforeContinue?.toolName}"
        }

        val followUpInput = "AGENT_API_TEST_INPUT_NO_PENDING"
        viewModel.continueFromInput(input = followUpInput)

        waitUntil(
            description = "persist follow-up input as USER message",
            timeoutMillis = 30_000,
        ) {
            val session = sessionManager.getSession(sessionId)
            val messages = session?.messages ?: return@waitUntil false
            messages.any { message -> message is UserMessage && message.content == followUpInput }
        }

        val messages = requireNotNull(sessionManager.getSession(sessionId)).messages
        ensure(!containsUserInterruptForInput(messages = messages, input = followUpInput)) {
            "follow-up input was incorrectly persisted as synthetic userInterrupt"
        }

        println("test provider continue-input routing check passed")
    } finally {
        if (createdSessionId != null) {
            runCatching {
                koinApp.koin.get<SessionManager>().stopRun(sessionId = requireNotNull(createdSessionId))
            }
        }
        stopKoin()
    }
}

private suspend fun validateContinueInputBehaviorWithAnthropicHaiku() {
    stopKoin()
    val koinApp = startKoin {
        modules(appModule)
    }

    var createdSessionId: String? = null
    try {
        val koin = koinApp.koin
        val viewModel = koin.get<MainViewModel>()
        val sessionManager = koin.get<SessionManager>()

        waitUntil(
            description = "load models into MainViewModel",
            timeoutMillis = 30_000,
        ) {
            viewModel.chatPageUiState.value.models.isNotEmpty()
        }

        val targetModelId = resolveAnthropicHaikuModelId(viewModel = viewModel)
        viewModel.switchModel(modelId = targetModelId)

        val previousSessionId = viewModel.currentSessionId
        viewModel.newSessionDirInput = "."
        viewModel.confirmNewSessionDir()
        waitUntil(
            description = "create new test session",
            timeoutMillis = 30_000,
        ) {
            val current = viewModel.currentSessionId
            current != null && current != previousSessionId
        }

        val sessionId = requireNotNull(viewModel.currentSessionId)
        createdSessionId = sessionId
        val firstPrompt = "Reply with exactly: READY"
        viewModel.taskInput = firstPrompt
        viewModel.runTask()
        waitForRunToStartAndFinish(
            viewModel = viewModel,
            sessionId = sessionId,
            startTimeoutMillis = 30_000,
            finishTimeoutMillis = 180_000,
            phaseName = "first anthropic run",
        )

        val pendingBeforeContinue = sessionManager.getTrailingPendingToolCall(sessionId = sessionId, agentId = null)
        ensure(pendingBeforeContinue == null) {
            "expected no pending tool call before continueFromInput, got ${pendingBeforeContinue?.toolName}"
        }

        val followUpInput = "AGENT_API_TEST_INPUT_NO_PENDING"
        viewModel.continueFromInput(input = followUpInput)
        waitForRunToStartAndFinish(
            viewModel = viewModel,
            sessionId = sessionId,
            startTimeoutMillis = 30_000,
            finishTimeoutMillis = 180_000,
            phaseName = "continueFromInput anthropic run",
        )

        val messages = requireNotNull(sessionManager.getSession(sessionId)).messages
        ensure(messages.any { message -> message is UserMessage && message.content == followUpInput }) {
            "expected follow-up input to be appended as USER message"
        }
        ensure(!containsUserInterruptForInput(messages = messages, input = followUpInput)) {
            "follow-up input was incorrectly persisted as synthetic userInterrupt"
        }

        println("anthropic haiku continue-input routing check passed")
    } finally {
        if (createdSessionId != null) {
            runCatching {
                koinApp.koin.get<SessionManager>().stopRun(sessionId = requireNotNull(createdSessionId))
            }
        }
        stopKoin()
    }
}

private suspend fun waitForRunToStartAndFinish(
    viewModel: MainViewModel,
    sessionId: String,
    startTimeoutMillis: Long,
    finishTimeoutMillis: Long,
    phaseName: String,
) {
    waitUntil(
        description = "$phaseName start",
        timeoutMillis = startTimeoutMillis,
    ) {
        val state = viewModel.sessionUiState.value
        state.currentSessionId == sessionId && state.isRunning
    }

    waitUntil(
        description = "$phaseName finish",
        timeoutMillis = finishTimeoutMillis,
    ) {
        val state = viewModel.sessionUiState.value
        state.currentSessionId == sessionId && !state.isRunning && !state.isWaitingForInput
    }
}

private suspend fun waitUntil(
    description: String,
    timeoutMillis: Long,
    predicate: suspend () -> Boolean,
) {
    val startAt = System.currentTimeMillis()
    while (!predicate()) {
        if (System.currentTimeMillis() - startAt > timeoutMillis) {
            throw IllegalStateException("Timeout while waiting to $description")
        }
        delay(200)
    }
}

private suspend fun <T> waitUntilValue(
    description: String,
    timeoutMillis: Long,
    valueProvider: suspend () -> T?,
): T {
    val startAt = System.currentTimeMillis()
    while (true) {
        val value = valueProvider()
        if (value != null) {
            return value
        }
        if (System.currentTimeMillis() - startAt > timeoutMillis) {
            throw IllegalStateException("Timeout while waiting to $description")
        }
        delay(200)
    }
}

private fun resolveAnthropicHaikuModelId(viewModel: MainViewModel): String {
    val models = viewModel.chatPageUiState.value.models
    val exact = models.firstOrNull { model -> model.id == "anthropic-claude-haiku-4-5" }
    if (exact != null) {
        return exact.id
    }

    val fallback = models.firstOrNull { model ->
        val id = model.id.lowercase()
        val name = model.model.lowercase()
        id.contains("anthropic") && id.contains("haiku") ||
            name.contains("anthropic") && name.contains("haiku")
    }
    return requireNotNull(fallback?.id) {
        "anthropic haiku model is not configured"
    }
}

private fun containsUserInterruptForInput(messages: List<SessionMessage>, input: String): Boolean {
    return messages.any { message ->
        if (message is ToolExchangeMessage && message.toolName == ToolNames.USER_INTERRUPT) {
            val interruptMessage = message.arguments
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
            if (interruptMessage == input) {
                return@any true
            }
        }
        false
    }
}

private suspend fun validateConfigLoadFromPresetField() {
    val yaml = """
        preset:
          builtin: explore
          file: /tmp/preset.md
    """.trimIndent()

    val provider = InMemoryConfigProvider(initialConfig = null)
    val source = InMemoryConfigSource(initialContent = yaml)
    val configManager = ConfigManager(provider = provider, source = source)

    val loadedConfig = configManager.load()

    ensure(loadedConfig.preset.builtin == "explore") {
        "expected preset.builtin to be loaded"
    }
    ensure(loadedConfig.preset.file == "/tmp/preset.md") {
        "expected preset.file to be loaded"
    }

    println("config preset load check passed")
}

private fun validatePresetBoundHookExecution() {
    val sessionId = "s-1"
    val presetName = "build"
    val toolName = "demo"
    val userInput = "hello"
    val initialToolArgs = "args"

    val hookManager = HookManager(
        userPromptHooks = listOf(AppendUserHook(tag = "base-user")),
        toolCallBeforeHooks = listOf(AppendToolBeforeHook(tag = "base-before")),
        toolCallAfterHooks = listOf(AppendToolAfterHook(tag = "base-after")),
        assistantResponseHooks = listOf(AppendAssistantHook(tag = "base-assistant")),
    )

    hookManager.registerPresetHooks(
        presetName = presetName,
        userPromptHooks = listOf(AppendUserHook(tag = "preset-user")),
        toolCallBeforeHooks = listOf(AppendToolBeforeHook(tag = "preset-before")),
        toolCallAfterHooks = listOf(AppendToolAfterHook(tag = "preset-after")),
        assistantResponseHooks = listOf(AppendAssistantHook(tag = "preset-assistant")),
    )

    hookManager.bindSessionPreset(sessionId = sessionId, presetName = presetName)

    val userPrompt = hookManager.applyUserPromptHooks(sessionId = sessionId, input = userInput)
    ensure(userPrompt == "hello|base-user|preset-user") {
        "unexpected user prompt hook chain: $userPrompt"
    }

    val beforeResult = hookManager.applyToolCallBeforeHooks(
        sessionId = sessionId,
        toolName = toolName,
        toolArgs = initialToolArgs,
    )
    ensure(beforeResult.allowed) {
        "tool should be allowed by before hooks"
    }
    ensure(beforeResult.toolArgs == "args|base-before|preset-before") {
        "unexpected tool args after before-hooks: ${beforeResult.toolArgs}"
    }

    val toolResult = hookManager.applyToolCallAfterHooks(
        sessionId = sessionId,
        toolName = toolName,
        toolArgs = beforeResult.toolArgs,
        result = "result",
    )
    ensure(toolResult == "result|base-after|preset-after") {
        "unexpected tool result after after-hooks: $toolResult"
    }

    val assistant = hookManager.applyAssistantResponseHooks(sessionId = sessionId, content = "done")
    ensure(assistant == "done|base-assistant|preset-assistant") {
        "unexpected assistant response hook chain: $assistant"
    }

    hookManager.unbindSessionPreset(sessionId = sessionId)
    val unboundUserPrompt = hookManager.applyUserPromptHooks(sessionId = sessionId, input = userInput)
    ensure(unboundUserPrompt == "hello|base-user") {
        "preset hooks should not run after unbind"
    }

    println("preset hook binding check passed")
}

private class InMemoryConfigProvider(initialConfig: AppConfig?) : ConfigProvider {
    private var storedConfig: AppConfig? = initialConfig

    override suspend fun load(): AppConfig? = storedConfig

    override suspend fun save(config: AppConfig) {
        storedConfig = config
    }

    override suspend fun exists(): Boolean = storedConfig != null

    override suspend fun initialize(defaultConfig: AppConfig) {
        if (storedConfig == null) {
            storedConfig = defaultConfig
        }
    }
}

private class InMemoryConfigSource(initialContent: String?) : ConfigSource {
    private var rawContent: String? = initialContent

    override suspend fun read(): String? = rawContent

    override suspend fun write(content: String) {
        rawContent = content
    }

    fun currentContent(): String? = rawContent
}

private class AppendUserHook(private val tag: String) : UserPromptHook {
    override fun onUserPrompt(sessionId: String, input: String): String = "$input|$tag"
}

private class AppendToolBeforeHook(private val tag: String) : ToolCallBeforeHook {
    override fun onToolCallBefore(
        sessionId: String,
        toolName: String,
        toolArgs: String,
    ): ToolCallHookResult = ToolCallHookResult.allow(toolArgs = "$toolArgs|$tag")
}

private class AppendToolAfterHook(private val tag: String) : ToolCallAfterHook {
    override fun onToolCallAfter(
        sessionId: String,
        toolName: String,
        toolArgs: String,
        result: String,
    ): String = "$result|$tag"
}

private class AppendAssistantHook(private val tag: String) : AssistantResponseHook {
    override fun onAssistantResponse(sessionId: String, content: String): String = "$content|$tag"
}

private inline fun ensure(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        throw IllegalStateException(lazyMessage())
    }
}

private const val TEST_PROVIDER_DUMMY_API_KEY: String = "agent-api-test-dummy-key"
