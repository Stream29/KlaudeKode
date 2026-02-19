# Cowork Requirements

Unless specified, always respond in Chinese.

Use IDEA tools as long as possible. (for grammar checking, building, running, etc.)

Use IDEA's tools to build instead of `gradlew build`

Use IDEA's tools instead of LSP tools. Never use Kotlin LSP.

# Kode Development SOPs

This file should be updated when essential. (For example, new module being added)

## references

- `koog`(reference/koog) The powerful agent framework. Cloned from GitHub.
- `SimpleMainKts`(reference/SimpleMainKts) Example of using kts scripts. Cloned from GitHub.
- `kimi-cli`(reference/kimi-cli) Moonshot AI's CLI tool for Kimi. Cloned from GitHub.
- `opencode`(reference/opencode) OpenCode base repo. Cloned from GitHub.
- `oh-my-opencode`(reference/oh-my-opencode) OhMyOpenCode agents/framework. Cloned from GitHub.
- `kotlinx-serialization-csv`(reference/kotlinx-serialization-csv) CSV serialization for Kotlinx Serialization. Cloned from GitHub.
- `kotlinx.collections.immutable`(reference/kotlinx.collections.immutable) Immutable collections for Kotlin. Cloned from GitHub.
- `Kori`(reference/Kori) AI-powered Markdown notepad with Mermaid support. Cloned from GitHub.
- `compose-markdown`(reference/compose-markdown) Compose Markdown rendering library. Cloned from GitHub.
- `multiplatform-markdown-renderer`(reference/multiplatform-markdown-renderer) Multiplatform Markdown renderer for Compose. Cloned from GitHub.
- `remote-compose-androidx`(reference/remote-compose-androidx) AndroidX Remote Compose related sources via sparse checkout (`compose/remote` and Glance `remotecompose` path). Cloned from GitHub.
- `compose-remote-layout`(reference/compose-remote-layout) Community Compose Remote Layout implementation by utsmannn. Cloned from GitHub.
- `kotlin-multiplatform-oidc`(reference/kotlin-multiplatform-oidc) Kotlin Multiplatform OpenID Connect/OAuth 2.0 library. Added as git submodule.

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

UI state management rule: expose UI-visible state from ViewModel via `StateFlow`, and collect it in Compose with `collectAsStateWithLifecycle`.
Do not let composables directly depend on mutable ViewModel fields as the primary render source.
For session runtime state, keep `MutableStateFlow` in domain/session layer and bridge it into ViewModel `StateFlow`.


## Dependency Management

### Adding Dependencies
1. Add dependencies to `gradle/libs.versions.toml` (version catalog)
2. Reference them in module `build.gradle.kts` files
3. Run `./gradlew build --refresh-dependencies` to update

### Time Dependency Compatibility
- Keep `kotlinx-datetime` on `0.7.1-0.6.x-compat` until Koog public APIs fully migrate away from `kotlinx.datetime.Instant/Clock`.
- Do not switch to plain `0.7.1` while Koog artifacts in use still require old ABI classes at runtime.

### Version Catalog Structure
```toml
[versions]
kotlin = "x.y.z"

[libraries]
library-name = { module = "group:artifact", version.ref = "kotlin" }

[plugins]
plugin-name = { id = "plugin.id", version.ref = "version-ref" }
```

## Common Tasks

### Test Harness Module
- `agent-api-test` is the dedicated module for manual API behavior verification.
- Main entry: `io.github.stream29.kode.agentapitest.AgentApiTestMainKt`.
- Prefer running it from IDEA run configuration for quick behavior checks.

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
- 2026-02-18：OAuth 鉴权链路引入 `kotlin-multiplatform-oidc` 处理 Auth Code + PKCE；Device Flow（含 `openai_codex_bridge`）暂沿用现有实现。
- 2026-02-18：OAuth 浏览器回调流程对齐 opencode：先注册回调等待再打开浏览器，回调等待显式 5 分钟超时，回调服务按端口启动避免 host 绑定差异导致超时。
- 2026-02-18：OIDC token exchange 使用 `DefaultOpenIdConnectClient` 默认 HTTP 客户端（含 ContentNegotiation 兼容解析），避免 `Exchange token failed: 200 null` 解析失败。
- 2026-02-18：OpenAI Browser OAuth 对齐 opencode：授权 URL/PKCE/state 与 token exchange 走兼容手工实现；其他 Auth Code provider 继续走 `kotlin-multiplatform-oidc`。
- 2026-02-18：OpenAI Subscription 模型调用链路对齐 opencode：OAuth access token 走 `https://chatgpt.com/backend-api/codex/responses`，不再默认走 `https://api.openai.com/v1`。
- 2026-02-18：OpenAI Subscription 统一强制 Responses endpoint，并在请求参数注入 `instructions`（取系统提示词）以兼容 codex responses 对 instructions 的要求。
- 2026-02-18：OpenAI Browser OAuth 对齐 opencode：授权 URL/PKCE/state 与 token exchange 走兼容手工实现；其他 Auth Code provider 继续走 `kotlin-multiplatform-oidc`。
- 2026-02-19：Provider API 移除 `createClientAny` 与 unchecked cast；provider 客户端创建统一走显式 `supportsAuth` 校验 + 强类型 auth 解析（`ApiKey`/`OAuthAccessToken`）。
- 2026-02-19：Conversation 工具执行链路移除 `Tool<Any?, Any?>` 强转桥接，统一为 `Tool<*, *>` 的安全执行适配，避免业务层类型逃逸。
- 2026-02-19：Session/Bridge 关键接口去默认参数（`agentId`、`listSessions(filter)`、`deleteSession(hardDelete)`），要求调用点显式传参，降低隐式语义分支。
- 2026-02-19：删除纯转发层 `SessionAwareAgentFactoryProvider` 与 `WebToolsProvider`，直接在组合根和 `MainViewModel` 装配 `SessionAwareAgentFactory` 与 `WebTools`。
- 2026-02-19：OpenAI provider/auth-mode 常量在 `kode-core`/`app` 统一引用 `kode-config-api` 常量，减少跨模块硬编码漂移。
- 2026-02-19：`agent-api-test` 默认执行离线确定性校验；需要设置 `KODE_AGENT_API_TEST_ENABLE_LIVE=true` 才运行 Anthropic live 链路。
- 2026-02-19：新增内置 `test-deterministic` provider（`provider-builtin`）用于测试链路；基于 Koog mock executor，`execute` 固定返回 deterministic tool-call，避免 tool-only 协议下 assistant 文本违规，供 `agent-api-test` 稳定复现 continue/no-pending 路径。
- 2026-02-19：模块收纳调整：脚本相关模块合并到 `:tools:kotlin-script-tool`（整合原根模块 `:scripting-tool` 与工具模块脚本能力）；工具模块统一为 `:tools:{communication,file-search,kotlin-script,shell,task,think,todo,web}-tool`；配置与 UI 模块分别收纳为 `:config:{api,core,fs,legacy}` 与 `:ui:{core,components,bridge}`（通过 `projectDir` 映射保留现有目录）。
- 2026-02-19：Session 存储改为 `sessions/<id>/meta.json + agents/<agentId>/{meta.json,messages/<seq>.json}`；会话加载仅按 agent `activeStartSeq..nextSeq` 读取活跃窗口，UI 继续只消费 `SessionUiState.messages`；本阶段停用自动 checkpoint 落盘。
- 2026-02-19：会话消息模型改为 sealed `AgentMessage`（`UserMessage` / `ToolExchangeMessage` / `SuspendMessage` / `ResumeMessage`）；普通工具调用按单条 `ToolExchangeMessage` 持久化，`waitForUserInput` 保持 Suspend/Resume 双消息特例；UI 不再做 call/result 配对。
- 2026-02-19：工具名协议统一为单一事实来源 `ToolNames`（`kode-session-core`）；跨层仅使用统一新名字（camelCase），移除旧别名兼容（如 `await_user_input` / `wait_for_user_input` / `fork_subagent` / `spawn_subagent` / `create_agent`）。

## Critical Interaction Contract

- 这种很重要的设计，你都要加到@AGENTS.md 里面，用简短凝练的语言记录。如果发生了变更就要更新。这句话本身也要记录进去。
- Chat resume contract: non-empty input = append user response semantics then resume; empty input = resume directly.
- Resume legality contract: before every resume, history must be legal (either waiting for `waitForUserInput` result, or no pending tool call).
- Input insertion contract:
- trailing `waitForUserInput` call -> treat input as that tool result.
- trailing non-`waitForUserInput` pending tool call -> rollback pending call, then append synthetic `userInterrupt` tool call + result pair.
- no pending tool call -> append as regular USER message, then resume.
- Stop contract is two-phase:
  - first stop click = safe-stop (wait current tool call to finish, then suspend at safe point).
  - second stop click = force-stop (cancel run, rollback unfinished trailing tool call).
- Never leave conversation in illegal pending-tool-call state after stop/continue paths.
- Tool-only execution contract: each model response batch in conversation loop must contain at least one `Message.Tool.Call`; non-empty `Message.Assistant` text is protocol violation and should fail fast.
- Session persistence contract: do not persist synthetic/fallback assistant text from executor loop; persist user/tool-call/tool-result records only to avoid blank assistant rows.
- Debug raw-view contract: raw message panel serializes `SessionMessage` directly from `SessionUiState.messages`; it must stay isolated from friendly message projection/grouping logic.
