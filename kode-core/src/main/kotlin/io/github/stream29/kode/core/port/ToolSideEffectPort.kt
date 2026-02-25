package io.github.stream29.kode.core.port

public data class ToolCallPreHookResult(
    val allowed: Boolean,
    val reason: String?,
    val toolArgs: String,
)

public interface ToolSideEffectPort {
    public fun applyToolCallBeforeHooks(sessionId: String, toolName: String, toolArgs: String): ToolCallPreHookResult

    public fun applyToolCallAfterHooks(sessionId: String, toolName: String, toolArgs: String, result: String): String
}
