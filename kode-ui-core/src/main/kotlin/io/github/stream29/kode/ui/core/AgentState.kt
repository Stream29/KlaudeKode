package io.github.stream29.kode.ui.core

/**
 * Interface for exposing agent state to the UI layer.
 */
public interface AgentState {
    /**
     * Whether the agent is currently running.
     */
    public val isRunning: Boolean

    /**
     * Whether the agent is waiting for user input.
     */
    public val isWaitingForInput: Boolean

    /**
     * Current task or activity description.
     */
    public val currentTask: String
}

/**
 * Events that can be emitted by the agent during execution.
 */
public sealed interface AgentEvent {
    /**
     * A tool is about to be called.
     */
    public data class ToolCallStarting(
        val toolName: String,
        val arguments: String
    ) : AgentEvent

    /**
     * A tool call has completed.
     */
    public data class ToolCallCompleted(
        val toolName: String,
        val result: String
    ) : AgentEvent

    /**
     * Agent is sending a message to the user.
     */
    public data class MessageToUser(
        val message: String
    ) : AgentEvent

    /**
     * Agent encountered an error.
     */
    public data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AgentEvent
}

/**
 * Callback interface for agent events.
 */
public interface AgentEventListener {
    public fun onEvent(event: AgentEvent)
}
