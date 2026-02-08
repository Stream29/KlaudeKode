# Session 架构概论

## 核心概念：Session

Session是完全面向用户的概念。对于用户来说，Session和他们所见的对话窗口严格一比一对应。

Session不是chat界面的ViewModel，因为Session的生命周期比chat界面的ViewModel长。
我们每次打开chat界面的时候生成的SessionChatViewModel应该把自己的行为传导给对应的Session，
而对应的Session会通过compose的state等方法把自己的状态更新传导给外层的SessionChatViewModel。

```kotlin
@Stable
data class Session(
    val metadata: MutableStateFlow<SessionMetadata>,
    val config: MutableStateFlow<SessionConfig>,
    val agent: MutableStateFlow<Agent>,
    val subagents: MutableStateFlow<PersistentMap<AgentId,SubAgent>>
)
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

### 管理

```kotlin
interface SessionRepository {
    suspend fun listSessions(): List<SessionMetadata>
    suspend fun loadSession(id: SessionId): Session
    suspend fun persistSession(id: SessionId, session: Session)
}
```

#### 存储

Session的存储应该放在全局的存储，即.kode目录下。
在存储Session的时候，我们需要做到严格的metadata和实际数据分离。
在.kode/session-meta.csv中，我们只存储session的metadata。
我们应该使用kotlinx-serialization-csv这个库来作为SessionMetadata类的持久化层实现，并区分接口和实现（未来有可能会提供csv之外的其他实现）
在.kode/sessions/<session_id>/中，我们存储Session的实际数据。

#### 磁盘与内存

默认我们向用户展示Session列表的时候，只需要读取一个List<SessionMetadata>即可，完全不需要将任何session载入内存。
当用户希望进入某个Session开始交互时，我们才会loadSession，获取实际的Session包装成ViewModel进入chat页面。
SessionFactory必须进行强保证：在程序运行的一次生命周期内，对同一个SessionId，不管调用多少次loadSession，返回的都是同一个实例。

## 核心概念：Agent

Agent严格对应着一个不断调用工具的agent loop。

每个Session都只有一个固定的前台agent作为对话内容的显示。而其余的称为subagent，默认不显示。

```kotlin
@Stable
data class Agent(
    val state: MutableStateFlow<AgentState>,
    val config: MutableStateFlow<AgentConfig>,
    val messages: MutableStateFlow<PersistentList<Message>>
)
```

### 亲子关系

类似Session，Agent的状态也分为运行中和挂起两种。
区别在于，Agent的状态算是Session的状态的一部分，因此用户选择stop session时，同时相当于stop all agents。
类似地，只要有一个Agent处于运行状态，Session就处于运行状态。

在协程意义上，主agent直接运行在Session的协程Job之上，而其余的subagent则运行在主agent的协程Job之下。
这也符合我们对于运行状态的定义。

### agent loop

对于主agent来说，agent loop的实现应该是我们每次执行一次resume操作的背后逻辑：
强迫LLM输出一个工具调用并将对应的消息添加到messages中。
如果是普通的工具，执行之，得到结果，并再次resume。
如果是await_user_input，则将主agent切换到挂起状态。
如果是await_agent_result，则保持运行状态（因为这个状态下不能安全地持久化），通过Kotlin协程的await等待subagent完成。

对于subagent来说，这个过程基本类似，但是subagent无权await_user_input，也无权直接和用户交互。
但是类似地，subagent有权和自己的父agent交互。
subagent无权开辟自己的subagent。

### multi agent协作

```kotlin
@Stable
data class SubAgent(
    val delegate: Agent,
    val result: CompletableDeferred<String>
)
```

#### 创建subagent

主agent有不同方式去开辟一个subagent：
可以fork一个新的agent，也可以spawn一个新的agent。二者都需要填入参数：taskDescription和expectedResult。
二者的区别在于，fork的agent会继承父agent的messages，
并且其systemPrompt会被修改为forkedAgent的版本，注入taskDescription和expectedResult，
同时其messages在继承父agent的基础上会多一个特殊的工具调用：fork，无参数，
返回结果："You are the forked subagent, Your task: .... Expected Result: ..."。
而spawn出来的agent会是全新的，对之前的信息没有任何了解，仅继承父agent的配置，并在system prompt里注入taskDescription和expectedResult

开辟subagent是个异步的过程。createAgent工具返回一个AgentId即完成工具调用，不阻塞主agent。
主agent可以调用pollAgentResult/awaitAgentResult/killAgent来获取结果。
pollAgentResult是个非阻塞调用，如果对应的agent已经运行完成，则直接返回结果；否则返回"pending"。
awaitAgentResult是个阻塞调用，参数里会包含timeout，等待直到对应的agent运行完成或者超时。
killAgent直接杀掉并移除对应的agent。
此外额外提供一个listActiveAgents方法，返回当前所有处于运行状态的agent的AgentId。

#### 子agent与父agent通信

不管是主agent还是subagent，都可以给别的agent发送消息，也都有接收消息的能力。
当发送消息时，有sayToAgent工具，填入agentId和message即可。
接收消息是个很特殊的操作，类似于中断机制，不管对方处于什么状态，都会向对方的messages里强制注入一个特殊的工具调用及结果：
receiveAgentMessage，结果内容为agentId和message。
如果接收消息的agent是个已经停止的subagent，则该工具直接报错"Target agent is already dead."，不产生副作用。
这种情况只有可能是主agent向子agent发送消息，因为主agent不会先于子agent停止。
如果接受消息的agent是个正在运行的agent，则正常向其注入即可。

#### 子agent的生命周期

主agent只有在awaitUserInput的挂起循环，永远不会终止。
子agent在调用一个特殊的工具returnAgentResult时，会自动终止。
这个工具的行为是填充对应的agentResult，这样主agent就能拿到来。
子agent一旦终止就不能继续。