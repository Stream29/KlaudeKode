package io.github.stream29.kode.core.hooks

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
    public fun applyUserPromptHooks(sessionId: String, input: String): String {
        var current = input
        userPromptHooks.forEach { hook ->
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
        toolCallBeforeHooks.forEach { hook ->
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
        toolCallAfterHooks.forEach { hook ->
            current = hook.onToolCallAfter(sessionId, toolName, toolArgs, current)
        }
        return current
    }

    public fun applyAssistantResponseHooks(sessionId: String, content: String): String {
        var current = content
        assistantResponseHooks.forEach { hook ->
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
