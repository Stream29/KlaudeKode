package io.github.stream29.kode.core.agent

import io.github.stream29.kode.tools.scripting.ScriptContext
import java.util.concurrent.atomic.AtomicBoolean

public interface UserCommunicationScriptContext : ScriptContext {
    public fun sayToUser(message: String)
    public fun consumeOutputList(): List<String>
    public fun suspendForUserInput()
    public fun consumeAwaitForUserInputSignal(): Boolean
}

public class UserCommunicationScriptContextImpl : UserCommunicationScriptContext {
    private val awaitForUserInput: AtomicBoolean = AtomicBoolean(false)
    private val outputLock: Any = Any()
    private val outputList: MutableList<String> = mutableListOf()

    override val defaultImports: List<String> = emptyList()

    override val systemPromptInjection: String = """
        ### `sayToUser(text: String)`
        - Record one user-visible output entry in an internal side-channel buffer.
        - Runtime consumes the buffered entries after script execution.
        - May be written in markdown with mermaid.

        ### `suspendForUserInput()`
        - You must call `suspendForUserInput()` to finish your output. Otherwise, you will be forced to continue.
        - Set an internal await-input signal only; this method does not block by itself.
        - Runtime consumes that signal and then enters pending-input until user input arrives.
        - Do not call consumeAwaitForUserInputSignal(); it is runtime-internal.
        - You can do other work in script and call this method at the end of the script.
    """.trimIndent()

    override fun sayToUser(message: String) {
        synchronized(outputLock) {
            outputList.add(message)
        }
    }

    override fun consumeOutputList(): List<String> {
        synchronized(outputLock) {
            val snapshot = outputList.toList()
            outputList.clear()
            return snapshot
        }
    }

    override fun suspendForUserInput() {
        awaitForUserInput.set(true)
    }

    override fun consumeAwaitForUserInputSignal(): Boolean {
        return awaitForUserInput.getAndSet(false)
    }
}
