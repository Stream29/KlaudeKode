# Kode Session Core Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `kode-session-core` module.

## Architectural Decisions

- 2026-02-19：Session/Bridge 关键接口去默认参数（`agentId`、`listSessions(filter)`、`deleteSession(hardDelete)`），要求调用点显式传参，降低隐式语义分支。
- 2026-02-19：Session 存储改为 `sessions/<id>/meta.json + agents/<agentId>/{meta.json,messages/<seq>.json}`；会话加载仅按 agent `activeStartSeq..nextSeq` 读取活跃窗口，UI 继续只消费 `SessionUiState.messages`；本阶段停用自动 checkpoint 落盘。
- 2026-02-19：会话消息模型硬切为仅两种 `AgentMessage`：`UserMessage` 与 `AgentScript`，并在两者上强制携带 `koogMessages` 原始协议消息列表；LLM 请求历史统一由 `SessionUiState.messages -> koogMessages` 还原，不再依赖 metadata 反推。
- 2026-02-19：会话存储执行无兼容硬切：`FileSessionStorage` 引入 schema 版本门禁，版本变更时直接清空历史 `sessions` 与 `session-meta.csv`，不做旧消息格式迁移。
- 2026-02-19：工具名协议统一为单一事实来源 `ToolNames`（`kode-session-core`）；跨层仅使用统一新名字（camelCase），移除旧别名兼容（如 `await_user_input` / `wait_for_user_input` / `fork_subagent` / `spawn_subagent` / `create_agent`）。
- 2026-02-19：`FileSessionStorage` 存储读取策略改为 strict fail-fast：移除 legacy `session-meta.csv` 兼容解析与 fail-open 吞错路径（含 metadata/agent/message 解码容错）；数据损坏或结构缺失一律显式抛错。
- 2026-02-19：会话存储 schema 版本升级到 `4`，以强制切断不含 `koogMessages` 的历史消息数据。
- 2026-02-19：会话存储 schema 版本升级到 `5`，以硬切引入 `AgentScript.outputList` 的消息结构变更。
- 2026-02-19：会话主体设计回归：运行态以 `SessionState` 作为聚合根（内部 `MutableStateFlow` 持有会话全量状态），任何状态变更都必须先校验后原子持久化，再对外发布新状态。
- 2026-02-19：会话快照边界明确：`SessionSnapshot` 仅用于序列化/导入导出/跨层传输；UI 与运行循环不得依赖快照回推运行态。
- 2026-02-19：会话命名统一：`ConversationSession -> SessionSnapshot`（序列化 DTO），`Session -> SessionState`（运行态聚合根），原 `SessionState` 枚举重命名为 `SessionRunState`。
- 2026-02-19：移除未使用会话二级快照模型 `SessionDataSnapshot/AgentSnapshot/SubAgentSnapshot`，避免与 `SessionSnapshot` 边界重复。
- 2026-02-20：会话编排继续下沉到 `SessionManager`：新增 `createConversationSession` / `prepareConversationContinuation` / `updateSessionWorkDir`，由 session/core 统一处理创建、continue 输入插入合法性与工作目录更新；`MainViewModel` 仅保留 UI 输入归一化与展示状态。
- 2026-02-28：todo 持久化真源硬切为 `sessions/<id>/agents/<agentId>/todo.json`，且 `todo.json` 是 todo 状态唯一持久化来源；禁止向 `AgentConfig` 的 todo 相关字段双写或回填。

## Critical Interaction Contract

- Chat resume contract: non-empty input = append as `UserMessage` then resume; empty input = resume directly.
- Input insertion contract: script-only 下不再走 `waitForUserInput`/resume 工具结果注入链路。
- Session persistence contract: persist `UserMessage`/`AgentScript` only, and each message must include `koogMessages` raw payload; do not persist synthetic/fallback assistant text.
- Todo persistence source contract: `todo.json` is the only persisted source for todo state (`sessions/<id>/agents/<agentId>/todo.json`); do not dual-write or backfill todo state via any `AgentConfig` todo fields.
- Session aggregate contract: `SessionState` 是会话唯一写入口；状态迁移必须遵循“合法性校验 -> 状态更新 -> 持久化提交 -> 状态发布”的顺序。
- Snapshot boundary contract: `SessionSnapshot` 只作为持久化/导入导出 DTO；运行态逻辑与 UI 订阅以 `SessionState` 为准，不允许用 snapshot 反推临时运行字段。
- Continue gate contract: `continueCurrentSession()` 仅作为 `continueFromInput("")` 入口别名；空输入/非空输入/程序化 continue 必须统一先过 `prepareConversationContinuation` legality gate，再进入 runtime resume。