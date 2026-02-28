# Cowork Requirements

Unless specified, always respond in Chinese.

Never use `gradlew` without `--no-daemon`.

# Kode Development SOPs

This file should be updated when essential. (For example, new module being added)

## references

- `koog`(reference/koog) The powerful agent framework. Cloned from GitHub.
- `SimpleMainKts`(reference/SimpleMainKts) Example of using kts scripts. Cloned from GitHub.
- `kimi-cli`(reference/kimi-cli) Moonshot AI's CLI tool for Kimi. Cloned from GitHub.
- `opencode`(reference/opencode) OpenCode base repo. Cloned from GitHub.
- `oh-my-opencode`(reference/oh-my-opencode) OhMyOpenCode agents/framework. Cloned from GitHub.
- `kotlinx-serialization-csv`(reference/kotlinx-serialization-csv) CSV serialization for Kotlinx Serialization. Cloned
  from GitHub.
- `kotlinx.collections.immutable`(reference/kotlinx.collections.immutable) Immutable collections for Kotlin. Cloned from
  GitHub.
- `Kori`(reference/Kori) AI-powered Markdown notepad with Mermaid support. Cloned from GitHub.
- `compose-markdown`(reference/compose-markdown) Compose Markdown rendering library. Cloned from GitHub.
- `multiplatform-markdown-renderer`(reference/multiplatform-markdown-renderer) Multiplatform Markdown renderer for
  Compose. Cloned from GitHub.
- `remote-compose-androidx`(reference/remote-compose-androidx) AndroidX Remote Compose related sources via sparse
  checkout (`compose/remote` and Glance `remotecompose` path). Cloned from GitHub.
- `compose-remote-layout`(reference/compose-remote-layout) Community Compose Remote Layout implementation by utsmannn.
  Cloned from GitHub.
- `kotlin-multiplatform-oidc`(reference/kotlin-multiplatform-oidc) Kotlin Multiplatform OpenID Connect/OAuth 2.0
  library. Added as git submodule.

## Coding Standards

### General Principles

1. Follow Kotlin coding conventions
2. Use meaningful variable and function names
3. Keep functions small and focused
4. Write self-documenting code with clear intent
5. Avoid using default parameters
6. Use named parameters when it's better

### File Naming

- Kotlin files: PascalCase (e.g., `App.kt`, `UserService.kt`)
- Configuration files: lowercase with hyphens (e.g., `build.gradle.kts`)

### Code Style

- Use 4 spaces for indentation (configured in `.editorconfig` if present)
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations

### Suppress Scope

- Avoid file-level `@file:Suppress` whenever possible.
- Prefer the narrowest suppression scope (statement/property/function/class) to keep warnings visible elsewhere.

### Compose State Management

UI state management rule: expose UI-visible state from ViewModel via `StateFlow`, and collect it in Compose with
`collectAsStateWithLifecycle`.
Do not let composables directly depend on mutable ViewModel fields as the primary render source.
For session runtime state, keep `MutableStateFlow` in domain/session layer and bridge it into ViewModel `StateFlow`.

## Dependency Management

### Adding Dependencies

1. Add dependencies to `gradle/libs.versions.toml` (version catalog)
2. Reference them in module `build.gradle.kts` files
3. Run `./gradlew build --refresh-dependencies` to update

### Time Dependency Compatibility

- Keep `kotlinx-datetime` on `0.7.1-0.6.x-compat` until Koog public APIs fully migrate away from
  `kotlinx.datetime.Instant/Clock`.
- Do not switch to plain `0.7.1` while Koog artifacts in use still require old ABI classes at runtime.

### Version Catalog Structure

```toml
[versions]
kotlin = "x.y.z"

[libraries]
library-name = { module = "group:artifact", version.ref = "kotlin" }

[plugins]
plugin-name = { id = "plugin.id", version.ref = "kotlin" }
```

## Common Tasks

### Test Harness Module

- `:agent-api-test` has been removed.
- Manual/behavior verification should use existing tests in `:kode-core` and `:kode-session-core`.

### Adding a New Module

1. Create module directory
2. Add module in `settings.gradle.kts`: `include(":module-name")`
3. Create `build.gradle.kts` in module directory
4. Apply convention plugin: `id("buildsrc.convention.kotlin-jvm")`

### Modifying Build Logic

- Shared logic: Edit files in `buildSrc/src/main/kotlin/`
- Module-specific: Edit module's `build.gradle.kts`
- After changes to `buildSrc`, run full build to recompile

### Architectural Decisions

- 2026-02-18：移除 `:virtual-thread-dispatcher` 模块，改用 `Dispatchers.IO` 处理文件/配置 IO 场景，降低运行时资源风险并简化依赖关系。
- 2026-02-18：OAuth 鉴权链路引入 `kotlin-multiplatform-oidc` 处理 Auth Code + PKCE；Device Flow（含 `openai_codex_bridge`
  ）暂沿用现有实现。
- 2026-02-18：OAuth 浏览器回调流程对齐 opencode：先注册回调等待再打开浏览器，回调等待显式 5 分钟超时，回调服务按端口启动避免
  host 绑定差异导致超时。
- 2026-02-18：OIDC token exchange 使用 `DefaultOpenIdConnectClient` 默认 HTTP 客户端（含 ContentNegotiation 兼容解析），避免
  `Exchange token failed: 200 null` 解析失败。
- 2026-02-18：OpenAI Browser OAuth 对齐 opencode：授权 URL/PKCE/state 与 token exchange 走兼容手工实现；其他 Auth Code
  provider 继续走 `kotlin-multiplatform-oidc`。
- 2026-02-18：OpenAI Subscription 模型调用链路对齐 opencode：OAuth access token 走
  `https://chatgpt.com/backend-api/codex/responses`，不再默认走 `https://api.openai.com/v1`。
- 2026-02-18：OpenAI Subscription 统一强制 Responses endpoint，并在请求参数注入 `instructions`（取系统提示词）以兼容 codex
  responses 对 instructions 的要求。
- 2026-02-18：OpenAI Browser OAuth 对齐 opencode：授权 URL/PKCE/state 与 token exchange 走兼容手工实现；其他 Auth Code
  provider 继续走 `kotlin-multiplatform-oidc`。
- 2026-02-19：Provider API 移除 `createClientAny` 与 unchecked cast；provider 客户端创建统一走显式 `supportsAuth` 校验 +
  强类型 auth 解析（`ApiKey`/`OAuthAccessToken`）。
- 2026-02-19：Conversation 工具执行链路移除 `Tool<Any?, Any?>` 强转桥接，统一为 `Tool<*, *>` 的安全执行适配，避免业务层类型逃逸。
- 2026-02-19：Session/Bridge 关键接口去默认参数（`agentId`、`listSessions(filter)`、`deleteSession(hardDelete)`
  ），要求调用点显式传参，降低隐式语义分支。
- 2026-02-19：删除纯转发层 `SessionAwareAgentFactoryProvider` 与 `WebToolsProvider`，直接在组合根和 `MainViewModel` 装配
  `SessionAwareAgentFactory` 与 `WebTools`。
- 2026-02-19：OpenAI provider/auth-mode 常量在 `kode-core`/`app` 统一引用 `kode-config-api` 常量，减少跨模块硬编码漂移。
- 2026-02-19：测试链路默认执行离线确定性校验；需要设置 `KODE_AGENT_API_TEST_ENABLE_LIVE=true` 才运行 Anthropic live 链路。
- 2026-02-19：新增内置 `test-deterministic` provider（`provider-builtin`）用于测试链路；基于 Koog mock executor，`execute`
  固定返回 deterministic tool-call，避免 tool-only 协议下 assistant 文本违规，稳定复现 continue/no-pending 路径。
- 2026-02-19：模块收纳调整（历史）：脚本能力并入 `:tools:kotlin-script-tool`，配置与 UI 模块收纳为
  `:config:{api,core,fs,legacy}` 与 `:ui:{core,components,bridge}`（通过 `projectDir` 映射保留目录）；该条现状已由
  2026-02-25 硬切决策覆盖。
- 2026-02-19：Session 存储改为 `sessions/<id>/meta.json + agents/<agentId>/{meta.json,messages/<seq>.json}`；会话加载仅按
  agent `activeStartSeq..nextSeq` 读取活跃窗口，UI 继续只消费 `SessionUiState.messages`；本阶段停用自动 checkpoint 落盘。
- 2026-02-19：会话消息模型硬切为仅两种 `AgentMessage`：`UserMessage` 与 `AgentScript`，并在两者上强制携带 `koogMessages`
  原始协议消息列表；LLM 请求历史统一由 `SessionUiState.messages -> koogMessages` 还原，不再依赖 metadata 反推。
- 2026-02-19：会话存储执行无兼容硬切：`FileSessionStorage` 引入 schema 版本门禁，版本变更时直接清空历史 `sessions` 与
  `session-meta.csv`，不做旧消息格式迁移。
- 2026-02-19：工具执行策略升级为 strict script-only：`ToolRegistryFactory` 仅注册 `KotlinScriptTool`；`ConversationAgent`
  仅向模型暴露 `executeKotlinScript`，任何非 script tool call 直接抛协议错误并终止当前轮次。
- 2026-02-19：script-only 收敛阶段暂停 MCP/ACP：会话工厂不再合并 MCP registry，`MainViewModel.startAcpServer` 直接拒绝启动并提示已禁用。
- 2026-02-19：代理执行架构重构为接口化：删除 `ConversationAgent`，新增公共 `Agent` 接口与 `MainAgent`/`SubAgent` 两个实现，共享
  `ScriptOnlyAgentEngine` 执行内核，主/子代理职责边界显式化。
- 2026-02-19：工具名协议统一为单一事实来源 `ToolNames`（`kode-session-core`）；跨层仅使用统一新名字（camelCase），移除旧别名兼容（如
  `await_user_input` / `wait_for_user_input` / `fork_subagent` / `spawn_subagent` / `create_agent`）。
- 2026-02-19：`ConversationAgent.DEFAULT_SYSTEM_PROMPT` 对齐 strict script-only：仅允许 `executeKotlinScript`，非 script
  工具调用视为协议违规（fail-fast）。
- 2026-02-19：`FileSessionStorage` 存储读取策略改为 strict fail-fast：移除 legacy `session-meta.csv` 兼容解析与 fail-open
  吞错路径（含 metadata/agent/message 解码容错）；数据损坏或结构缺失一律显式抛错。
- 2026-02-19：会话存储 schema 版本升级到 `4`，以强制切断不含 `koogMessages` 的历史消息数据。
- 2026-02-19：script-only 运行时移除 `ToolRegistry` 依赖：`ScriptOnlyAgentEngine` 直接向模型暴露单一 `executeKotlinScript`
  descriptor，并按调用即时创建 `KotlinScriptTool` 执行；agent 绑定 `ScriptContext` 通过 `awaitForUserInput` 信号驱动挂起/恢复。
- 2026-02-19：`ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT` 明确声明 `ScriptContext` receiver API（含
  `suspendForUserInput`）的调用时机与停止语义；后续新增 receiver 方法必须同步写入 system prompt 使用约定。
- 2026-02-19：`ScriptContext` 新增 `sayToUser(text)` 用户输出通道：脚本侧 `println` 仅用于 agent 自检/调试；运行时每轮消费
  `ScriptContext.outputList` 并落盘到 `AgentScript.outputList`，主聊天 UI 将该 list 按元素展开为独立消息展示。
- 2026-02-19：会话存储 schema 版本升级到 `5`，以硬切引入 `AgentScript.outputList` 的消息结构变更。
- 2026-02-19：聊天主视图移除 RawMessage 模式：`Chat` 统一直接消费 `SessionUiState.messages`（仅 `UserMessage`/`AgentScript`
  ），不再提供 debug raw 列表切换入口。
- 2026-02-19：`AgentScript` UI 展示协议升级：每条脚本消息先展示可折叠 script preview（展开显示脚本内容与脚本结果），再按顺序逐条渲染
  `outputList` 为 assistant 消息。
- 2026-02-19：配置模型移除 `UiConfig.debugShowRawMessageList`，RawMessage 视图切换配置不再生效（硬切）。
- 2026-02-19：移除 `:tools:file-search-tool` 模块与 `kode-core` 对该模块依赖；文件检索能力收敛到 script-only 路线（由
  `executeKotlinScript` + `ScriptContext` 统一承载）。
- 2026-02-19：会话主体设计回归：运行态以 `SessionState` 作为聚合根（内部 `MutableStateFlow`
  持有会话全量状态），任何状态变更都必须先校验后原子持久化，再对外发布新状态。
- 2026-02-19：会话快照边界明确：`SessionSnapshot` 仅用于序列化/导入导出/跨层传输；UI 与运行循环不得依赖快照回推运行态。
- 2026-02-19：执行边界收敛：`Session*Runtime` 只负责执行（run/continue + 模型解析），不再承担 `createSession` 等生命周期写操作。
- 2026-02-19：会话命名统一：`ConversationSession -> SessionSnapshot`（序列化 DTO），`Session -> SessionState`（运行态聚合根），原
  `SessionState` 枚举重命名为 `SessionRunState`。
- 2026-02-19：移除未使用会话二级快照模型 `SessionDataSnapshot/AgentSnapshot/SubAgentSnapshot`，避免与 `SessionSnapshot`
  边界重复。
- 2026-02-20：会话编排继续下沉到 `SessionManager`：新增 `createConversationSession` / `prepareConversationContinuation` /
  `updateSessionWorkDir`，由 session/core 统一处理创建、continue 输入插入合法性与工作目录更新；`MainViewModel` 仅保留 UI
  输入归一化与展示状态。
- 2026-02-24：ACP 保持显式禁用语义（`startAcpServer/stopAcpServer` 均仅提示 disabled），并移除 `MainViewModel` 内不可达 ACP
  support/session 实现与无调用点 MCP registry helper，保留 `runMcpTest` 健康/测试链路。
- 2026-02-25：Legacy pruning 硬切：移除已弃用工具模块，模块图以 `settings.gradle.kts` 为准；保留受保护表面 `:tools:web-tool`
  ，并维持现行运行时兼容表面（strict script-only、`ToolNames` 单一事实源、legacy alias 仅历史语义不得回流）。
- 2026-02-27：`ScriptContext` 升级为接口并新增 `systemPromptInjection` 抽象属性；默认实现为 `DefaultScriptContext`。脚本编译期
  receiver 类型改为运行时具体实现（`this::class.starProjectedType`），`ScriptOnlyAgentEngine` 通过 `scriptContextFactory`
  注入具体上下文并将其 `systemPromptInjection` 拼接进 fallback system prompt。
- 2026-02-28：todo 持久化真源硬切为 `sessions/<id>/agents/<agentId>/todo.json`，且 `todo.json` 是 todo 状态唯一持久化来源；禁止向
  `AgentConfig` 的 todo 相关字段双写或回填。
- 2026-02-28：system prompt 注入策略硬切：`ScriptOnlyAgentEngine.run` 每轮都必须动态合成本轮 system prompt（注入当前 todo
  状态）；初始化阶段构建的 fallback prompt 仅用于初始化，不得作为每轮运行时提示词。
- 2026-02-28：todo 运行态持有策略硬切：`DefaultScriptContext` 持有 `MutableStateFlow<List<TodoNode>>`；所有 todo API
  调用必须先更新该 StateFlow，再由 StateFlow 变更触发自动持久化链路。

## Critical Interaction Contract

- 这种很重要的设计，你都要加到@AGENTS.md 里面，用简短凝练的语言记录。如果发生了变更就要更新。这句话本身也要记录进去。
- Chat resume contract: non-empty input = append as `UserMessage` then resume; empty input = resume directly.
- Resume legality contract: strict script-only 下历史不得出现 trailing `AgentScript.status == PENDING_INPUT`
  ；若存在则按协议违规报错。
- Input insertion contract: script-only 下不再走 `waitForUserInput`/resume 工具结果注入链路。
- Stop contract is two-phase:
    - first stop click = safe-stop (wait current tool call to finish, then suspend at safe point).
    - second stop click = force-stop (cancel run, rollback unfinished trailing pending script).
- Never leave conversation in illegal pending-script state after stop/continue paths.
- Tool-only execution contract: each model response batch in conversation loop must contain at least one
  `Message.Tool.Call`; non-empty `Message.Assistant` text is protocol violation and should fail fast.
- Script-only tool contract: only `Message.Tool.Call.tool == executeKotlinScript` is legal; any other tool name must
  fail fast.
- Script receiver contract: each agent run owns an isolated `ScriptContext`; script-side `suspendForUserInput()` only
  flips await signal, and engine must consume this signal after tool execution to enter pending-input state.
- System prompt contract: `ScriptOnlyAgentEngine.DEFAULT_SYSTEM_PROMPT` must explicitly document currently supported
  `ScriptContext` receiver methods and usage constraints; adding a new receiver method requires updating this prompt in
  the same change.
- Dynamic system prompt injection contract: `ScriptOnlyAgentEngine.run` 每轮都必须基于当前 todo 状态动态合成 system
  prompt；初始化阶段构建的 fallback prompt 仅用于初始化，不得作为每轮运行时提示词。
- Script context extensibility contract: `ScriptContext` must stay as interface; concrete implementations can expose
  extra receiver APIs for scripts, and `systemPromptInjection` must document those extra APIs in the same change.
- Script receiver typing contract: Kotlin script compilation must bind implicit receiver to runtime concrete
  `ScriptContext` implementation type (not the interface type), otherwise concrete-only APIs are unavailable in script.
- User output contract: script-side user-visible text must use `ScriptContext.sayToUser(text)`; `println` output is
  debug-only and must not be treated as user message.
- Output projection contract: engine must persist per-turn `ScriptContext.outputList` into `AgentScript.outputList`;
  chat UI must render each list element as one assistant message.
- Chat rendering contract: `Chat` 页面只渲染 `SessionUiState.messages` 原始 `AgentMessage` 序列；`UserMessage` 直接渲染，
  `AgentScript` 先渲染折叠脚本预览再渲染 `outputList`。不再维护 RawMessage 模式。
- Session persistence contract: persist `UserMessage`/`AgentScript` only, and each message must include `koogMessages`
  raw payload; do not persist synthetic/fallback assistant text.
- Todo persistence source contract: `todo.json` is the only persisted source for todo state (
  `sessions/<id>/agents/<agentId>/todo.json`); do not dual-write or backfill todo state via any `AgentConfig` todo
  fields.
- Todo stateflow ownership contract: `DefaultScriptContext` holds `MutableStateFlow<List<TodoNode>>`; every todo API
  mutation must update this StateFlow, and persistence must be triggered from StateFlow emissions.
- Session aggregate contract: `SessionState` 是会话唯一写入口；状态迁移必须遵循“合法性校验 -> 状态更新 -> 持久化提交 ->
  状态发布”的顺序。
- Snapshot boundary contract: `SessionSnapshot` 只作为持久化/导入导出 DTO；运行态逻辑与 UI 订阅以 `SessionState` 为准，不允许用
  snapshot 反推临时运行字段。
- Continue gate contract: `continueCurrentSession()` 仅作为 `continueFromInput("")` 入口别名；空输入/非空输入/程序化
  continue 必须统一先过 `prepareConversationContinuation` legality gate，再进入 runtime resume。
- Legacy surface contract: `await_user_input`/`wait_for_user_input`/`spawn_subagent` 等旧别名与多工具模式叙事仅可作为历史背景，不得作为当前实现或迁移目标。
- Session title refresh contract: `SessionExecutionRuntime.generateSessionTitleFromConversation`
  必须基于会话历史触发模型生成标题，不允许返回恒定 `null` 导致刷新按钮退化为仅 fallback 标题。
