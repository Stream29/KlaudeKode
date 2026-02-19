package io.github.stream29.kode.tools.scripting

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public class ScriptContext {
    @OptIn(ExperimentalAtomicApi::class)
    private val awaitForUserInput: AtomicBoolean = AtomicBoolean(false)
    private val outputLock: Any = Any()
    private val outputList: MutableList<String> = mutableListOf()

    public fun sayToUser(message: String) {
        synchronized(outputLock) {
            outputList.add(message)
        }
    }

    public fun consumeOutputList(): List<String> {
        synchronized(outputLock) {
            val snapshot = outputList.toList()
            outputList.clear()
            return snapshot
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    public fun suspendForUserInput() {
        awaitForUserInput.compareAndSet(expectedValue = false, newValue = true)
    }

    @OptIn(ExperimentalAtomicApi::class)
    public fun consumeAwaitForUserInputSignal(): Boolean {
        return awaitForUserInput.exchange(newValue = false)
    }
}
