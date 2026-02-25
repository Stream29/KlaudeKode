package io.github.stream29.kode.core.port

public interface RuntimeSideEffectPort {
    public fun isSafeStopRequested(sessionId: String): Boolean

    public fun onSafeStopReached(sessionId: String)

    public fun onToolCallStarting(sessionId: String, toolName: String, arguments: String)

    public fun onToolCallCompleted(sessionId: String, toolName: String, result: String)

    public fun onToolCallFailed(sessionId: String, message: String)

    public fun log(message: String)
}
