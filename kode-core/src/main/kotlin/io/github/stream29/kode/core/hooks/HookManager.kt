package io.github.stream29.kode.core.hooks

import java.util.concurrent.ConcurrentHashMap

public interface UserPromptHook {
    public fun onUserPrompt(sessionId: String, input: String): String
}

public interface ToolCallBeforeHook {
    public fun onToolCallBefore(sessionId: String, toolName: String, toolArgs: String): ToolCallHookResult
}

public interface ToolCallAfterHook {
    public fun onToolCallAfter(sessionId: String, toolName: String, toolArgs: String, result: String): String
}

public interface AssistantResponseHook {
    public fun onAssistantResponse(sessionId: String, content: String): String
}

public data class ToolCallHookResult(
    val allowed: Boolean,
    val toolArgs: String,
    val reason: String?
) {
    public companion object {
        public fun allow(toolArgs: String): ToolCallHookResult {
            return ToolCallHookResult(
                allowed = true,
                toolArgs = toolArgs,
                reason = null
            )
        }

        public fun block(toolArgs: String, reason: String): ToolCallHookResult {
            return ToolCallHookResult(
                allowed = false,
                toolArgs = toolArgs,
                reason = reason
            )
        }
    }
}

public class HookManager(
    private val userPromptHooks: List<UserPromptHook>,
    private val toolCallBeforeHooks: List<ToolCallBeforeHook>,
    private val toolCallAfterHooks: List<ToolCallAfterHook>,
    private val assistantResponseHooks: List<AssistantResponseHook>
) {
    private data class PresetHookBundle(
        val userPromptHooks: List<UserPromptHook>,
        val toolCallBeforeHooks: List<ToolCallBeforeHook>,
        val toolCallAfterHooks: List<ToolCallAfterHook>,
        val assistantResponseHooks: List<AssistantResponseHook>,
    )

    private val presetHooksByName: ConcurrentHashMap<String, PresetHookBundle> = ConcurrentHashMap()
    private val sessionPresetBinding: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    public fun registerPresetHooks(
        presetName: String,
        userPromptHooks: List<UserPromptHook> = emptyList(),
        toolCallBeforeHooks: List<ToolCallBeforeHook> = emptyList(),
        toolCallAfterHooks: List<ToolCallAfterHook> = emptyList(),
        assistantResponseHooks: List<AssistantResponseHook> = emptyList(),
    ) {
        val normalizedName = presetName.trim()
        if (normalizedName.isBlank()) {
            return
        }
        presetHooksByName[normalizedName] = PresetHookBundle(
            userPromptHooks = userPromptHooks,
            toolCallBeforeHooks = toolCallBeforeHooks,
            toolCallAfterHooks = toolCallAfterHooks,
            assistantResponseHooks = assistantResponseHooks,
        )
    }

    public fun bindSessionPreset(sessionId: String, presetName: String?) {
        val normalizedSessionId = sessionId.trim()
        if (normalizedSessionId.isBlank()) {
            return
        }
        val normalizedPreset = presetName?.trim().orEmpty()
        if (normalizedPreset.isBlank()) {
            sessionPresetBinding.remove(normalizedSessionId)
            return
        }
        sessionPresetBinding[normalizedSessionId] = normalizedPreset
    }

    public fun unbindSessionPreset(sessionId: String) {
        val normalizedSessionId = sessionId.trim()
        if (normalizedSessionId.isBlank()) {
            return
        }
        sessionPresetBinding.remove(normalizedSessionId)
    }

    private fun resolvePresetHooks(sessionId: String): PresetHookBundle? {
        val presetName = sessionPresetBinding[sessionId]
        return presetName?.let { presetHooksByName[it] }
    }

    public fun applyUserPromptHooks(sessionId: String, input: String): String {
        var current = input
        val presetHooks = resolvePresetHooks(sessionId)
        val hooks = userPromptHooks + (presetHooks?.userPromptHooks ?: emptyList())
        hooks.forEach { hook ->
            current = hook.onUserPrompt(sessionId, current)
        }
        return current
    }

    public fun applyToolCallBeforeHooks(
        sessionId: String,
        toolName: String,
        toolArgs: String
    ): ToolCallHookResult {
        var currentArgs = toolArgs
        val presetHooks = resolvePresetHooks(sessionId)
        val hooks = toolCallBeforeHooks + (presetHooks?.toolCallBeforeHooks ?: emptyList())
        hooks.forEach { hook ->
            val result = hook.onToolCallBefore(sessionId, toolName, currentArgs)
            if (!result.allowed) {
                return result
            }
            currentArgs = result.toolArgs
        }
        return ToolCallHookResult.allow(currentArgs)
    }

    public fun applyToolCallAfterHooks(
        sessionId: String,
        toolName: String,
        toolArgs: String,
        result: String
    ): String {
        var current = result
        val presetHooks = resolvePresetHooks(sessionId)
        val hooks = toolCallAfterHooks + (presetHooks?.toolCallAfterHooks ?: emptyList())
        hooks.forEach { hook ->
            current = hook.onToolCallAfter(sessionId, toolName, toolArgs, current)
        }
        return current
    }

    public fun applyAssistantResponseHooks(sessionId: String, content: String): String {
        var current = content
        val presetHooks = resolvePresetHooks(sessionId)
        val hooks = assistantResponseHooks + (presetHooks?.assistantResponseHooks ?: emptyList())
        hooks.forEach { hook ->
            current = hook.onAssistantResponse(sessionId, current)
        }
        return current
    }

    public companion object {
        public fun empty(): HookManager {
            return HookManager(
                userPromptHooks = emptyList(),
                toolCallBeforeHooks = emptyList(),
                toolCallAfterHooks = emptyList(),
                assistantResponseHooks = emptyList()
            )
        }
    }
}
