# AgentMessage

分为两种：UserText和AgentScript。

```kotlin
@Serializable
sealed interface AgentMessage {
    val koogMessages: Sequence<ai.koog.prompt.message.Message>

    @Serializable
    data class UserText(
        val koogMessage: ai.koog.prompt.message.Message.User
        // other fields
    ) : AgentMessage {
        // impl
    }

    @Serializable
    data class AgentScript(
        val koogToolCall: ai.koog.prompt.message.Message.Tool.Call,
        val koogToolResult: ai.koog.prompt.message.Message.Tool.Result,
        val messages: List<String>,
        val awaitForUserInput: Boolean,
        // other fields
    ) : AgentMessage {
        // impl
    }
}
```

UI应当有直接渲染AgentMessage的能力。

AgentMessage的序列可以转换为KoogMessage的序列。而system message不包含在其中，它由Agent负责生成。