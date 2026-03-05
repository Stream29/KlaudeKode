# 持久化

不管是Agent，还是Session等概念，都有内存里活着的状态和持久化状态的区分。

## 持久化状态

```kotlin
@Serializable
data class SessionMetadata(
    // fields
)
```
当一个Session/Agent还躺在磁盘里时，它只有`metadata`可以被读进内存，且被认为是持久化状态的。
读取`metadata`应该是个低开销的操作。

### 存储结构

Session和Agent的存储应该放在全局的存储，即 `~/.kode` 目录下。
在存储Session的时候，我们需要做到严格的metadata和实际数据分离。

在 `~/.kode/config.yml`中，我们存储全局设置。
在 `~/.kode/session-index.csv` 中，我们只存储session的metadata。
我们应该使用 `kotlinx-serialization-csv` 这个库来作为 `SessionIndex` 类的持久化层实现。

在 `~/.kode/sessions/<session_id>/` 中，我们存储单个Session及其中Agent的具体数据，其存储布局如下：
- `metadata.json`：`SessionMetadata` 的持久化层实现。
- `agents/`：Agent的持久化层实现。
  - `mainAgent/`：`MainAgent` 的持久化层实现。
  - `subAgents/<agent_id>/`：`SubAgent` 的持久化层实现。

对于不论是`mainAgent`还是`subAgents/<agent_id>`，都对应着一个持久化状态的Agent。其存储布局如下：
- `metadata.json`：`MainAgentMetadata`/`SubAgentMetadata` 的持久化层实现。
- `messages/`：Agent的消息队列。
  - `message_<message_index>.json`：`AgentMessage` 的持久化层实现。

### 程序API

存储层的实现应该是异步的，因此我们需要提供一个异步的访问约定。

```kotlin
interface SuspendProperty<T> {
    suspend fun get(): T
    suspend fun set(value: T)
}

class YamlProperty<T>(file: File, serializer: KSerializer<T>): SuspendProperty<T> { /* implementation */ }
class JsonProperty<T>(file: File, serializer: KSerializer<T>): SuspendProperty<T> { /* implementation */ }
class CsvProperty<T>(file: File, serializer: KSerializer<T>): SuspendProperty<T> { /* implementation */ }
```

存储层应该有个根，且这个根是一个接口，在测试时可以Mock，在运行时可以依赖注入。

```kotlin
interface RootRepository {
    val globalConfig: SuspendProperty<GlobalConfig>
    val sessionIndex: SuspendProperty<SessionIndex>
    fun getSession(id: SessionId): SuspendProperty<SessionRepository>
    suspend fun createSession(): SessionId // 默认零初始化，把逻辑放在业务层
}

interface SessionRepository {
    val metadata: SuspendProperty<SessionMetadata>
    val mainAgent: SuspendProperty<MainAgentRepository>
    fun getSubAgent(id: AgentId): SuspendProperty<SubAgentRepository>
    suspend fun createSubAgent(): AgentId // 默认零初始化，把逻辑放在业务层
}
interface MainAgentRepository {
    val metadata: SuspendProperty<MainAgentMetadata>
    fun getMessage(index: Int): SuspendProperty<AgentMessage>
    suspend fun appendMessage(message: AgentMessage): Int
}
interface SubAgentRepository {
    val metadata: SuspendProperty<SubAgentMetadata>
    fun getMessage(index: Int): SuspendProperty<AgentMessage>
    suspend fun appendMessage(message: AgentMessage): Int
}
```

## 运行时状态

运行时状态是指在内存里活着的状态，即在程序运行过程中，会持续更新的状态。
这个状态会被前端和使用该状态的业务订阅，同时也被持久化层订阅，这样确保一旦状态发生变化，会立刻触发异步落盘存储和界面更新。
订阅的方式为Kotlin协程的MutableStateFlow。
应当注意：每个订阅行为都应当有其生命周期管理，否则会导致内存泄漏。
订阅行为的发生即包含两层语义：
- 首先会从持久化层取得目标的最新值，作为StateFlow的初始值。
- 其次会从StateFlow中订阅状态的变更，立刻将变更提交到持久化层落盘。（所以必须要有CoroutineScope！）
不应该有一个东西同时被多次订阅，如果多个地方都需要使用，应该通过依赖注入或者参数传递的方式来实现。

```kotlin
suspend fun RootRepository.subscribeIn(scope: CoroutineScope): RootState

interface RootState {
    val repository: RootRepository
    val globalConfig: MutableStateFlow<GlobalConfig>
    val sessionIndex: MutableStateFlow<SessionIndex>
    suspend fun createSession(): SessionId // 作为业务层，处理业务层的初始化逻辑
}

suspend fun RootRepository.subscribeSessionIn(id: SessionId, scope: CoroutineScope): SessionState

interface SessionState {
    val repository: SessionRepository
    val metadata: MutableStateFlow<SessionMetadata>
    suspend fun createSubAgent(): AgentId // 作为业务层，处理业务层的初始化逻辑
}

suspend fun SessionRepository.subscribeMainAgentIn(scope: CoroutineScope): MainAgentState
suspend fun SessionRepository.subscribeSubAgentIn(id: AgentId, scope: CoroutineScope): SubAgentState

interface AgentState {
    suspend fun appendMessage(message: AgentMessage): Int // 作为业务层，处理业务层的消息逻辑，包括更新offset/limit等
}

interface MainAgentState: AgentState {
    val repository: MainAgentRepository
    val metadata: MutableStateFlow<MainAgentMetadata>
}

interface SubAgentState: AgentState {
    val repository: MainAgentRepository
    val metadata: MutableStateFlow<SubAgentMetadata>
}
```

运行时状态作为业务的载体，其对外不应该直接暴露自己的repository和MutableStateFlow。
MutableStateFlow应该在内部被封装起来，向前端提供一个StateFlow用于订阅，向ViewModel提供一组用于操作的方法。
这样就保证ViewModel的操作可以被异步落盘到持久化层，且被前端实时显示，且对自己可见。