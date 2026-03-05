# Agent

Agent严格对应着一个不断调用工具的agent loop。
当Agent被从磁盘中唤醒，进入运行状态，其中的所有状态都保存为MutableStateFlow，被前端和持久化层订阅。
前端实时将其展示给用户，持久化层将其实时持久化。

每个Session都只有一个固定的前台agent作为对话内容的显示。而其余的称为subagent，默认不显示。

```kotlin
interface Agent {
    val offset: MutableStateFlow<Int> // 当前对话窗口下第一个有效的消息的索引
    val limit: MutableStateFlow<Int> // 当前对话窗口的消息数量
    suspend fun fetchMessage(index: Int): AgentMessage // 前端通过LazyList惰性获取消息列表
    suspend fun appendMessage(message: AgentMessage) // 前端通过appendMessage向消息列表追加消息，并更新offset和limit
    suspend fun CoroutineScope.resume() // Session在resume时，会resume所有还活着的agent
    suspend fun softInterrupt() // 给一个信号，让agent完成当前这轮loop之后立刻suspend
}

interface MainAgent: Agent { 
    val metadata: MutableStateFlow<MainAgentMetadata>
}
interface SubAgent: Agent { 
    val metadata: MutableStateFlow<SubAgentMetadata>
    val task: String // 每个SubAgent都是基于父Agent给自己的任务的！
    val answer: MutableStateFlow<String> // 子Agent提交给MainAgent的答案。提交完即意味着失去活性
}
```

### 亲子关系

类似Session，Agent的状态也分为运行中和挂起两种。
区别在于，Agent的状态算是Session的状态的一部分，因此用户选择stop session时，需要stop all agents。
类似地，只要有一个Agent处于运行状态，Session就处于运行状态。

在协程意义上，主agent和subagents直接运行在Session的协程Job之上。正常情况下可以一键cancel
这也符合我们对于运行状态的定义。

### agent loop

对于主agent来说，agent loop的实现应该是我们每次执行一次resume操作的背后逻辑：
- LLM被强迫输出一个对kotlin脚本工具的调用
- 在当前Agent的ScriptContext中执行该脚本
- 得到脚本的执行结果，和脚本的工具调用合在一起，拼装成一个AgentMessage，并添加到messages中（此时一定是个合法状态，被监听到立即落盘）
- 以上过程，需要try-catch CancellationException。
如果被hardInterrupt则会触发这个异常，并把脚本的执行结果设为"This operation was interrupted by user."，
得到一个合法的AgentMessage，添加到messages中。
- 检查ScriptContext中接受到的信号：如果有suspendForUserInput，则切换状态为suspended并跳出循环
- 检查softInterrupt信号：如果有，则切换状态为suspended并跳出循环
- 继续下一轮循环

对于subagent来说，这个过程基本类似，但是subagent无权await_user_input，也无权直接和用户交互。
subagent可以returnAnswer，返回的结果可以被父agent收到，并被父agent记录下来。
类似地，subagent无权sayToUser，但是有权和自己的父agent交互。
subagent无权开辟自己的subagent。

### multi agent协作

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