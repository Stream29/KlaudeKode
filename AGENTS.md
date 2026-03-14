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
- Manual/behavior verification should use existing tests in `:kode-core` and `:session`.

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
- 2026-02-19：测试链路默认执行离线确定性校验；需要设置 `KODE_AGENT_API_TEST_ENABLE_LIVE=true` 才运行 Anthropic live 链路。
- 2026-02-19：移除 `:tools:file-search-tool` 模块与 `kode-core` 对该模块依赖；文件检索能力收敛到 script-only 路线（由 `executeKotlinScript` + `ScriptContext` 统一承载）。
- 2026-02-25：Legacy pruning 硬切：移除已弃用工具模块，模块图以 `settings.gradle.kts` 为准；保留受保护表面 `:tools:web-tool`，并维持现行运行时兼容表面（strict script-only、`ToolNames` 单一事实源、legacy alias 仅历史语义不得回流）。
- 2026-03-06：会话契约收敛为“运行态单一 owner + pending-input 单一真值源 + 两段 stop 语义”：`isWaitingForInput` 仅由 pending-input 派生，不得再以 `SessionRunState.Suspended` 直接等价。
- 2026-03-06（历史记录，已废弃）：曾采用“迁移优先、默认非破坏”策略；该策略不再生效，统一以 2026-03-07 决策为准。
- 2026-03-07：会话模型移除 checkpoint 与 schema 迁移语义；持久化层改为对运行态 `StateFlow` 变更进行监听并自动落盘，`SessionManager` 继续提供显式持久化路径作为编排边界。
- 2026-03-07：持久化契约收敛到 canonical-only：移除 `session-meta.csv`/`meta.json`/legacy `todo.json` 与 legacy message type 的兼容读取，旧布局数据不再作为当前运行时输入面。
- 2026-03-08：模块边界收敛为 `:session + :agent`：原 `:kode-session-core` 的模块名切换为 `:session`（目录保持 `kode-session-core`），新增 `:agent` 承载 agent 域模型；`TodoItem` 成为唯一 canonical 类型并迁入 `:agent`，`TodoNode`/`TodoItem` alias 不再保留。
- 2026-03-08：agent 域模型进一步从 `:session` 抽离到 `:agent`：`SessionMessage/AgentMessage/AgentScript/UserMessage`、`AgentState/AgentConfig/Agent/SubAgent` 与 `ToolNames` 迁入 `:agent`，`:session` 保留会话元数据、运行编排与持久化契约。
- 2026-03-08：`kode-core` 中 `MainAgent/SubAgent` 收敛为“接口 + 实现”分离：`MainAgent`/`SubAgent` 为契约，`MainAgentImpl`/`SubAgentImpl` 为默认实现，避免接口缺位导致的实现泄漏。
- 2026-03-08：彻底移除未实装的 `Preset`/`Hooks` 特性：删除 `AppConfig.preset` 与模板 preset 段、移除 app 层 preset UI/状态流、删去 provider preset 模型与注册表，以及 `HookManager`/tool hook 注入链路，运行时保持 script-only 主路径。
- 2026-03-08：`SessionManager` 依赖反转落地：会话编排改为注入 `SessionRuntimeStore + SessionPersistencePort`，默认以 `SessionFactory + RepositorySessionPersistencePort` 组合，`SessionManager` 不再直接依赖 `SessionRepository`。
- 2026-03-08：`SessionExecutionRuntime` 可测试性增强：引入 `MainAgentFactory`、`modelRuntimeResolver`、`promptExecutorFactory` 注入点，默认实现维持现有行为，测试可替换主 agent 组装与模型解析路径。
- 2026-03-08：移除 `KoogSessionBridge` 中间层：`ScriptOnlyAgentEngine` 与 `SessionExecutionRuntime` 直接使用 `SessionManager` 完成消息投影与脚本交换持久化，bridge 语义（script-only 校验与 koog message 构造）下沉到 session side-effect 适配层。
- 2026-03-08：`SessionExecutionRuntime` 职责继续收敛为编排：会话标题生成链路从 runtime 主类抽离到 `SessionTitleGenerator`（通过 `SessionTitleGenerationPort` 注入），运行态保留执行上下文组装与 agent 调度。
- 2026-03-08：会话查询读路径收敛：新增 `SessionQueryPort`，统一 runtime/engine 的消息投影与会话存在性校验，避免在多个编排类重复直接访问 `SessionManager` 查询细节。
- 2026-03-08：app 层 runtime 组装切换为 DI 工厂：`ChatViewModel` 不再直接 `new SessionExecutionRuntime`，改为注入 `SessionExecutionRuntimeFactory`（默认 `DefaultSessionExecutionRuntimeFactory` 由 Koin 提供），便于测试覆盖与依赖替换。
- 2026-03-08：模型选择语义收敛为全局默认：移除 session 级 `preferredModel`/`preferred_model_id` 持久化字段与 `createConversationSession` 对应参数，运行时模型来源统一为 `config.defaults.modelId`。
- 2026-03-08：`SessionExecutionRuntime` 继续收敛为编排层：执行上下文组装抽离为 `SessionExecutionContextFactory`（默认函数式工厂 `defaultSessionExecutionContextFactory`），runtime 通过工厂注入拼装 `MainAgent + ModelRuntime`，进一步降低主类职责并提升可替换测试 seam。
- 2026-03-08：继续推进“无状态优先”：`SessionExecutionContextFactory` 默认实现由状态类改为函数式工厂 `defaultSessionExecutionContextFactory`，`SessionExecutionRuntime` 的会话作用域消息适配改为扩展函数返回匿名对象，减少显式状态持有类。
- 2026-03-08：`SessionExecutionRuntime` 进一步去状态化：`sessionQueryPort`/执行上下文工厂/标题生成器从 `lazy` 字段改为按需函数构造，runtime 仅保留依赖引用与编排调用。
- 2026-03-08：大文件治理落地到“无状态优先”实践：app/ui/config/test 的超长 Kotlin 文件按职责拆分为多文件（路由壳层、页面组合、渲染支持与测试支撑分离），默认优先顶层函数与扩展函数，避免新增状态持有类。
- 2026-03-08：`SessionManager` 进一步收敛为“生命周期编排 + 锁/持久化副作用边界”：运行态业务规则（run/suspend/continue/rollback/subagent 状态判定、active 列表等）下沉到 `SessionState` 扩展（`SessionStateDomainExtensions`），并将持久化观察器协作者工厂化（`SessionPersistenceObserverCoordinatorFactory`）以便 Koin/测试覆盖替换。
- 2026-03-08：subagent 运行时编排从 `SessionManager` 抽离到 `SessionSubAgentCoordinator`（含 `SessionSubAgentCoordinatorFactory` DI seam）：`SessionState` 继续承载 subagent 领域状态变更，协调器承载 job 注册/取消与 poll/await 副作用，`SessionManager` 保留 API 门面与生命周期编排。
- 2026-03-14：模块目录形状收敛：`config/*`、`ui/*`、`providers/*` 直接承载对应 leaf module；移除 `:ui:bridge`，其纯 UI model 下沉到 `:ui:core`；provider 子模块名去掉重复的 `provider-` 前缀，模块图仍以 `settings.gradle.kts` 为准。

## Critical Interaction Contract

- 这种很重要的全局设计，你都要加到根目录的 `@AGENTS.md` 里面，用简短凝练的语言记录。如果发生了变更就要更新。这句话本身也要记录进去。
- **IMPORTANT**: 你需要优先阅读具体模块目录下的 `AGENTS.md`（如 `kode-core/AGENTS.md`, `session(目录: kode-session-core)/AGENTS.md`, `app/AGENTS.md` 等），那里包含了各个模块具体的架构设计和契约。只有全局性的设计才放在根目录的 `AGENTS.md` 中。
- Legacy surface contract: `await_user_input`/`wait_for_user_input`/`spawn_subagent` 等旧别名与多工具模式叙事仅可作为历史背景，不得作为当前实现或迁移目标。
