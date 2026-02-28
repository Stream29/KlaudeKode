# Kode Core Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `kode-core` module.

## Architectural Decisions

- 2026-02-19：Conversation 工具执行链路移除 `Tool<Any?, Any?>` 强转桥接，统一为 `Tool<*, *>` 的安全执行适配，避免业务层类型逃逸。
- 2026-02-19：代理执行架构重构为接口化：删除 `ConversationAgent`，新增公共 `Agent` 接口与 `MainAgent`/`SubAgent` 两个实现，共享 `ScriptOnlyAgentEngine` 执行内核，主/子代理职责边界显式化。
- 2026-02-19：script-only 运行时移除 `ToolRegistry` 依赖：`ScriptOnlyAgentEngine` 直接向模型暴露单一 `executeKotlinScript` descriptor，并按调用即时创建 `KotlinScriptTool` 执行；agent 绑定 `ScriptContext` 通过 `awaitForUserInput` 信号驱动挂起/恢复。
- 2026-02-19：`ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT` 明确声明 `ScriptContext` receiver API（含 `suspendForUserInput`）的调用时机与停止语义；后续新增 receiver 方法必须同步写入 system prompt 使用约定。
- 2026-02-19：`ScriptContext` 新增 `sayToUser(text)` 用户输出通道：脚本侧 `println` 仅用于 agent 自检/调试；运行时每轮消费 `ScriptContext.outputList` 并落盘到 `AgentScript.outputList`，主聊天 UI 将该 list 按元素展开为独立消息展示。
- 2026-02-19：执行边界收敛：`Session*Runtime` 只负责执行（run/continue + 模型解析），不再承担 `createSession` 等生命周期写操作。
- 2026-02-27：`ScriptContext` 升级为接口并新增 `systemPromptInjection` 抽象属性；默认实现为 `DefaultScriptContext`。脚本编译期 receiver 类型改为运行时具体实现（`this::class.starProjectedType`），`ScriptOnlyAgentEngine` 通过 `scriptContextFactory` 注入具体上下文并将其 `systemPromptInjection` 拼接进 fallback system prompt。
- 2026-02-28：system prompt 注入策略硬切：`ScriptOnlyAgentEngine.run` 每轮都必须动态合成本轮 system prompt（注入当前 todo 状态）；初始化阶段构建的 fallback prompt 仅用于初始化，不得作为每轮运行时提示词。
- 2026-02-28：todo 运行态持有策略硬切：`DefaultScriptContext` 持有 `MutableStateFlow<List<TodoNode>>`；所有 todo API 调用必须先更新该 StateFlow，再由 StateFlow 变更触发自动持久化链路。
- 2026-02-28：ScriptContext 组件化设计：`ScriptContext` 必须通过接口隔离功能（如 `TodoListScriptContext` 与 `UserCommunicationScriptContext`）。`MainAgentScriptContext` 采用 Kotlin 接口委托（`by`）组合各组件实现，并拼接它们的 `systemPromptInjection`。

## Critical Interaction Contract

- ScriptContext composition contract: `ScriptContext` implementations must be modularized into discrete functional interfaces (e.g., `TodoListScriptContext`, `UserCommunicationScriptContext`). The main context should compose these via Kotlin interface delegation (`by`) and concatenate their respective `systemPromptInjection` strings.
- Resume legality contract: strict script-only 下历史不得出现 trailing `AgentScript.status == PENDING_INPUT` ；若存在则按协议违规报错。
- Stop contract is two-phase:
    - first stop click = safe-stop (wait current tool call to finish, then suspend at safe point).
    - second stop click = force-stop (cancel run, rollback unfinished trailing pending script).
- Never leave conversation in illegal pending-script state after stop/continue paths.
- Tool-only execution contract: each model response batch in conversation loop must contain at least one `Message.Tool.Call`; non-empty `Message.Assistant` text is protocol violation and should fail fast.
- Script-only tool contract: only `Message.Tool.Call.tool == executeKotlinScript` is legal; any other tool name must fail fast.
- Script receiver contract: each agent run owns an isolated `ScriptContext`; script-side `suspendForUserInput()` only flips await signal, and engine must consume this signal after tool execution to enter pending-input state.
- System prompt contract: `ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT` must explicitly document currently supported `ScriptContext` receiver methods and usage constraints; adding a new receiver method requires updating this prompt in the same change.
- Dynamic system prompt injection contract: `ScriptOnlyAgentEngine.run` 每轮都必须基于当前 todo 状态动态合成 system prompt；初始化阶段构建的 fallback prompt 仅用于初始化，不得作为每轮运行时提示词。
- Script context extensibility contract: `ScriptContext` must stay as interface; concrete implementations can expose extra receiver APIs for scripts, and `systemPromptInjection` must document those extra APIs in the same change.
- Script receiver typing contract: Kotlin script compilation must bind implicit receiver to runtime concrete `ScriptContext` implementation type (not the interface type), otherwise concrete-only APIs are unavailable in script.
- User output contract: script-side user-visible text must use `ScriptContext.sayToUser(text)`; `println` output is debug-only and must not be treated as user message.
- Output projection contract: engine must persist per-turn `ScriptContext.outputList` into `AgentScript.outputList`; chat UI must render each list element as one assistant message.
- Todo stateflow ownership contract: `DefaultScriptContext` holds `MutableStateFlow<List<TodoNode>>`; every todo API mutation must update this StateFlow, and persistence must be triggered from StateFlow emissions.
- Session title refresh contract: `SessionExecutionRuntime.generateSessionTitleFromConversation` 必须基于会话历史触发模型生成标题，不允许返回恒定 `null` 导致刷新按钮退化为仅 fallback 标题。