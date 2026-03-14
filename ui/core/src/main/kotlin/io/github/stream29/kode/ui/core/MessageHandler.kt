package io.github.stream29.kode.ui.core

/**
 * Interface for handling messages and communication with the user.
 * Abstracts the UI layer from the agent logic.
 */
public interface MessageHandler {
    /**
     * Add a message to display to the user.
     */
    public fun addMessageToUser(message: String)

    /**
     * Log a message for debugging or visibility.
     */
    public fun log(message: String)

    /**
     * Request input from the user.
     * Suspends until the user provides input.
     */
    public suspend fun requestInput(): String

    /**
     * Add a message for a specific session.
     */
    public fun addMessageToUser(message: String, sessionId: String): Unit = addMessageToUser(message)

    /**
     * Log a message for a specific session.
     */
    public fun log(message: String, sessionId: String): Unit = log(message)

    /**
     * Request input for a specific session.
     */
    public suspend fun requestInput(sessionId: String): String = requestInput()

    public fun isSafeStopRequested(sessionId: String): Boolean = false

    public fun onSafeStopReached(sessionId: String): Unit = Unit
}

/**
 * A simple message handler implementation for testing or headless environments.
 */
public class ConsoleMessageHandler : MessageHandler {
    override fun addMessageToUser(message: String) {
        println("[Agent]: $message")
    }

    override fun log(message: String) {
        println("[Log]: $message")
    }

    override suspend fun requestInput(): String {
        print("[User Input]: ")
        return readlnOrNull().orEmpty()
    }
}
