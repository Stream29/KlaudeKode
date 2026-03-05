# 用户通信

## 数据结构设计

```kotlin
interface UserCommunicationScriptContext {
    fun sayToUser(message: String)
    fun suspendForUserInput()
}

class UserCommunicationScriptContextImpl: UserCommunicationScriptContext {
    // 防止脚本在多线程环境下写入数据造成可见性问题
    val outputLock: Mutex = Mutex()
    val outputList: MutableList<String> = mutableListOf()
    val awaitForUserInputSignal: AtomicBoolean = AtomicBoolean(false)
}
```

Agent可以通过调用`sayToUser`方法向用户发送消息，并通过调用`suspendForUserInput`方法等待用户输入。

`sayToUser`方法并不实际向用户发送消息，而是将消息写入`outputList`中，
在执行完脚本后将`outputList`中的内容作为当前这一轮AgentMessage.AgentScript.messages。
`suspendForUserInput`方法将`awaitForUserInputSignal`设置为`true`。
它本身不会造成任何阻塞，但是外围的Agent在拿到脚本执行后被更新的ScriptContext后，就会跳出Agent循环，等待用户输入。

这两个方法实际上并不会直接对外界造成任何副作用，它们的副作用由`UserCommunicationScriptContext`负责接收。
而Agent层面只需要查看`UserCommunicationScriptContext`被更新后的状态，就可以采取对应的行动了。
换言之，是Agent层的逻辑通过读取`UserCommunicationScriptContext`的状态变更采取了真正造成副作用的行动。