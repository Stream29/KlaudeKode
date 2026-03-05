# Session 架构概论

## 核心概念：Session

Session是完全面向用户的概念。对于用户来说，Session和他们所见的对话窗口严格一比一对应。

Session在未被唤醒时，保存为磁盘里的一个文件夹。被唤醒时，其所有状态都保存为MutableStateFlow，被前端和持久化层订阅。
前端实时将其展示给用户，持久化层将其实时持久化。

```kotlin
interface Session {
    val metadata: MutableStateFlow<SessionMetadata>
    val mainAgent: MainAgent
    val activeSubagents: MutableStateFlow<PersistantList<MutableStateFlow<SubAgent>>>
    val deadSubagents: MutableStateFlow<PersistantList<SubAgentMetadata>>
    fun resume() // 立刻返回，并在Session的CoroutineScope中resume agents
    fun softInterrupt() // 立刻返回，并在Session的CoroutineScope中soft interrupt agents
    fun hardInterrupt() // 立刻返回，并在Session的CoroutineScope中hard interrupt agents
}
```

### 状态

```kotlin
enum class SessionState {
    Running, Suspended
}
```

Session的状态处于且仅处于以下二者之一：
- 运行中：此时一定有一个Kotlin协程的Job正在运行。
  允许用户继续输入，但不允许用户提交。
  允许用户中止这个Job的运行，切换到挂起状态。
  用户中止时，必须通过Kotlin的协作式取消，确保Session的状态不会因为取消而被损坏。
  可以认为，在这个状态下，Session状态的所有权完全被正在工作的Kotlin协程Job占有，只有它可以更改session的状态。
  用户通过UI实时接收状态变更的信息，本地的存储实时保存Session状态的变更。
- 挂起：此时Session的状态一定已经被保存且完全静态，唯一需要实时同步的状态是用户的输入内容，
  用户可以通过提交消息（实际上在提交后自动resume）和恢复运行将状态原子地转换为运行中。
  只有Session处于挂起状态才可以duplicate。
  可以认为，在这个状态下，Session状态的所有权完全被用户占有，只有用户可以更改Session的状态。
  本地的存储实时保存用户的更改。
  注意：保存到存储却没有被载入内存的Session算是挂起的一种特殊情况。

当用户点击停止按钮时，分两种情况：
- 用户第一次按下停止按钮时，向该Session的所有Agent发送一个中止信号（通过Kotlin协程）。
等到所有Agent都已经处理完这个中止信号，进入Suspended状态时，则认为软停止成功。
- 用户第二次按下停止按钮时，直接强行cancel当前的Job，进入Suspended状态。
Agent的协程在被取消的时候，应该确保不会把状态给损坏掉。

### 管理

```kotlin
interface SessionRepository {
    suspend fun listSessions(): List<SessionMetadata> // SessionMetadata应该是轻量的！
    suspend fun loadSession(id: SessionId): Session // Session创建即被监听！
    suspend fun detachSession(instance: Session) // 释放对应资源！
}
```

#### 磁盘与内存

默认我们向用户展示Session列表的时候，只需要读取一个List<SessionMetadata>即可，完全不需要将任何session载入内存。
当用户希望进入某个Session开始交互时，我们才会loadSession，获取实际的Session包装成ViewModel进入chat页面。
SessionFactory必须进行强保证：在程序运行的一次生命周期内，对同一个SessionId，不管调用多少次loadSession，返回的都是同一个实例。

