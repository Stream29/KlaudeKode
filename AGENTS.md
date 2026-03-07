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
- 2026-02-19：测试链路默认执行离线确定性校验；需要设置 `KODE_AGENT_API_TEST_ENABLE_LIVE=true` 才运行 Anthropic live 链路。
- 2026-02-19：移除 `:tools:file-search-tool` 模块与 `kode-core` 对该模块依赖；文件检索能力收敛到 script-only 路线（由 `executeKotlinScript` + `ScriptContext` 统一承载）。
- 2026-02-25：Legacy pruning 硬切：移除已弃用工具模块，模块图以 `settings.gradle.kts` 为准；保留受保护表面 `:tools:web-tool`，并维持现行运行时兼容表面（strict script-only、`ToolNames` 单一事实源、legacy alias 仅历史语义不得回流）。
- 2026-03-06：会话契约收敛为“运行态单一 owner + pending-input 单一真值源 + 两段 stop 语义”：`isWaitingForInput` 仅由 pending-input 派生，不得再以 `SessionRunState.Suspended` 直接等价。
- 2026-03-06：持久化采用“迁移优先、默认非破坏”策略：schema 不匹配默认 fail-fast/迁移，不得无显式开关执行 destructive reset；新旧布局保持“新写入、旧可读”兼容。

## Critical Interaction Contract

- 这种很重要的全局设计，你都要加到根目录的 `@AGENTS.md` 里面，用简短凝练的语言记录。如果发生了变更就要更新。这句话本身也要记录进去。
- **IMPORTANT**: 你需要优先阅读具体模块目录下的 `AGENTS.md`（如 `kode-core/AGENTS.md`, `kode-session-core/AGENTS.md`, `app/AGENTS.md` 等），那里包含了各个模块具体的架构设计和契约。只有全局性的设计才放在根目录的 `AGENTS.md` 中。
- Legacy surface contract: `await_user_input`/`wait_for_user_input`/`spawn_subagent` 等旧别名与多工具模式叙事仅可作为历史背景，不得作为当前实现或迁移目标。
