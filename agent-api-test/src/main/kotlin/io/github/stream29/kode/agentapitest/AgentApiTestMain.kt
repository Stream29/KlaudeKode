package io.github.stream29.kode.agentapitest

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
import kotlinx.coroutines.runBlocking

public fun main() {
    runBlocking {
        validateConfigMigrationFromLegacyAgentField()
        validatePresetBoundHookExecution()
    }
    println("agent-api-test: all checks passed")
}

private suspend fun validateConfigMigrationFromLegacyAgentField() {
    val legacyYaml = """
        agent:
          builtin: explore
          file: /tmp/preset.md
    """.trimIndent()

    val provider = InMemoryConfigProvider(initialConfig = null)
    val source = InMemoryConfigSource(initialContent = legacyYaml)
    val configManager = ConfigManager(provider = provider, source = source)

    val loaded = configManager.load()

    ensure(loaded.preset.builtin == "explore") {
        "expected preset.builtin to migrate from legacy agent.builtin"
    }
    ensure(loaded.preset.file == "/tmp/preset.md") {
        "expected preset.file to migrate from legacy agent.file"
    }
    ensure(loaded.agent.builtin == null && loaded.agent.file == null) {
        "expected legacy agent field to be normalized to empty content"
    }

    val rewritten = source.currentContent().orEmpty()
    ensure(rewritten.contains("preset:")) {
        "expected normalized config source to contain preset section"
    }

    println("config migration check passed")
}

private fun validatePresetBoundHookExecution() {
    val hookManager = HookManager(
        userPromptHooks = listOf(AppendUserHook(tag = "base-user")),
        toolCallBeforeHooks = listOf(AppendToolBeforeHook(tag = "base-before")),
        toolCallAfterHooks = listOf(AppendToolAfterHook(tag = "base-after")),
        assistantResponseHooks = listOf(AppendAssistantHook(tag = "base-assistant")),
    )

    hookManager.registerPresetHooks(
        presetName = "build",
        userPromptHooks = listOf(AppendUserHook(tag = "preset-user")),
        toolCallBeforeHooks = listOf(AppendToolBeforeHook(tag = "preset-before")),
        toolCallAfterHooks = listOf(AppendToolAfterHook(tag = "preset-after")),
        assistantResponseHooks = listOf(AppendAssistantHook(tag = "preset-assistant")),
    )

    hookManager.bindSessionPreset(sessionId = "s-1", presetName = "build")

    val userPrompt = hookManager.applyUserPromptHooks(sessionId = "s-1", input = "hello")
    ensure(userPrompt == "hello|base-user|preset-user") {
        "unexpected user prompt hook chain: $userPrompt"
    }

    val beforeResult = hookManager.applyToolCallBeforeHooks(
        sessionId = "s-1",
        toolName = "demo",
        toolArgs = "args",
    )
    ensure(beforeResult.allowed) {
        "tool should be allowed by before hooks"
    }
    ensure(beforeResult.toolArgs == "args|base-before|preset-before") {
        "unexpected tool args after before-hooks: ${beforeResult.toolArgs}"
    }

    val toolResult = hookManager.applyToolCallAfterHooks(
        sessionId = "s-1",
        toolName = "demo",
        toolArgs = beforeResult.toolArgs,
        result = "result",
    )
    ensure(toolResult == "result|base-after|preset-after") {
        "unexpected tool result after after-hooks: $toolResult"
    }

    val assistant = hookManager.applyAssistantResponseHooks(sessionId = "s-1", content = "done")
    ensure(assistant == "done|base-assistant|preset-assistant") {
        "unexpected assistant response hook chain: $assistant"
    }

    hookManager.unbindSessionPreset(sessionId = "s-1")
    val unboundUserPrompt = hookManager.applyUserPromptHooks(sessionId = "s-1", input = "hello")
    ensure(unboundUserPrompt == "hello|base-user") {
        "preset hooks should not run after unbind"
    }

    println("preset hook binding check passed")
}

private class InMemoryConfigProvider(initialConfig: AppConfig?) : ConfigProvider {
    private var storedConfig: AppConfig? = initialConfig

    override suspend fun load(): AppConfig? {
        return storedConfig
    }

    override suspend fun save(config: AppConfig) {
        storedConfig = config
    }

    override suspend fun exists(): Boolean {
        return storedConfig != null
    }

    override suspend fun initialize(defaultConfig: AppConfig) {
        if (storedConfig == null) {
            storedConfig = defaultConfig
        }
    }
}

private class InMemoryConfigSource(initialContent: String?) : ConfigSource {
    private var rawContent: String? = initialContent

    override suspend fun read(): String? {
        return rawContent
    }

    override suspend fun write(content: String) {
        rawContent = content
    }

    public fun currentContent(): String? {
        return rawContent
    }
}

private class AppendUserHook(private val tag: String) : UserPromptHook {
    override fun onUserPrompt(sessionId: String, input: String): String {
        return "$input|$tag"
    }
}

private class AppendToolBeforeHook(private val tag: String) : ToolCallBeforeHook {
    override fun onToolCallBefore(sessionId: String, toolName: String, toolArgs: String): ToolCallHookResult {
        return ToolCallHookResult.allow(toolArgs = "$toolArgs|$tag")
    }
}

private class AppendToolAfterHook(private val tag: String) : ToolCallAfterHook {
    override fun onToolCallAfter(sessionId: String, toolName: String, toolArgs: String, result: String): String {
        return "$result|$tag"
    }
}

private class AppendAssistantHook(private val tag: String) : AssistantResponseHook {
    override fun onAssistantResponse(sessionId: String, content: String): String {
        return "$content|$tag"
    }
}

private inline fun ensure(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        throw IllegalStateException(lazyMessage())
    }
}
