package io.github.stream29.kode.core.agent

import io.github.stream29.kode.tools.scripting.ScriptContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public interface UserCommunicationScriptContext : ScriptContext {
    public fun sayToUser(message: String)
    public fun consumeOutputList(): List<String>
    public fun suspendForUserInput()
    public fun consumeAwaitForUserInputSignal(): Boolean
}

public class UserCommunicationScriptContextImpl : UserCommunicationScriptContext {
    @OptIn(ExperimentalAtomicApi::class)
    private val awaitForUserInput: AtomicBoolean = AtomicBoolean(false)
    private val outputLock: Any = Any()
    private val outputList: MutableList<String> = mutableListOf()

    override val systemPromptInjection: String = """
        ### `sayToUser(text: String)`
        - Append one user-visible output entry.
        - Each call corresponds to one UI message entry.
        - May be written in markdown with mermaid.

        ### `suspendForUserInput()`
        - You must call `suspendForUserInput()` to finish your output. Otherwise, you will be forced to continue.
        - Runtime behavior: the run enters pending-input and resumes after the user provides input.
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

    @OptIn(ExperimentalAtomicApi::class)
    override fun suspendForUserInput() {
        awaitForUserInput.compareAndSet(expectedValue = false, newValue = true)
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun consumeAwaitForUserInputSignal(): Boolean {
        return awaitForUserInput.exchange(newValue = false)
    }
}
