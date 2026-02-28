# Session 架构说明（收敛版）

## 文档状态

本文件描述当前已收敛的 strict script-only 会话契约。
`await_user_input`、`wait_for_user_input`、`spawn_subagent`、`fork_subagent`、`create_agent`、多工具模式等内容仅作为历史背景，不是现行行为。

## 核心模型

- 会话运行态聚合根是 `SessionState`，它是唯一写入口。
- 状态迁移顺序固定为：合法性校验 -> 状态更新 -> 持久化提交 -> 状态发布。
- 会话运行状态使用 `SessionRunState`（运行中 / 挂起）。

## 消息与持久化

- 持久化消息仅允许 `UserMessage` 与 `AgentScript`。
- 每条消息必须携带 `koogMessages` 原始协议负载。
- `AgentScript` 必须持久化 `outputList`，UI 逐条投影为 assistant 消息。
- 不允许写入 synthetic/fallback assistant 文本。

## 执行协议（strict script-only）

- 每轮模型响应必须包含至少一个 `Message.Tool.Call`。
- 若出现非空 `Message.Assistant` 文本，视为协议违规并 fail-fast。
- 合法工具仅有 `executeKotlinScript`。
- 任意其他工具名均视为协议违规并 fail-fast。

## Continue 语义（统一入口）

- `continueCurrentSession()` 只是 `continueFromInput("")` 的别名。
- 非空输入：先追加一条 `UserMessage`，再进入恢复流程。
- 空输入：不追加消息，直接进入恢复流程。
- 两种路径都必须先通过 `prepareConversationContinuation` legality gate，再进入 runtime resume。

## 挂起合法性与停止语义

- 恢复前历史不得存在 trailing `AgentScript.status == PENDING_INPUT`。
- 若存在上述状态，按协议违规处理。
- Stop 采用两阶段：
    - 第一次 stop: safe-stop，等待当前工具调用结束并在安全点挂起。
    - 第二次 stop: force-stop，取消运行并回滚未完成的尾部 pending script。
- stop/continue 路径结束后，不得遗留非法 pending-script 状态。

## 运行边界

- `SessionSnapshot` 仅用于持久化、导入导出、跨层传输。
- UI 与运行循环只依赖 `SessionState`，不得用 snapshot 反推运行态临时字段。
- `Session*Runtime` 仅负责 run/continue 与模型解析，不负责创建会话等生命周期写操作。

## 功能裁剪状态

- MCP/ACP 在当前收敛阶段保持显式禁用语义。
- 子代理相关旧叙事（创建、轮询、消息注入、结果等待）不属于当前实现范围。

## 历史说明

旧版 Session 设计曾包含多工具 agent loop 与 subagent 编排，这些内容已被严格 script-only 契约替代。阅读历史资料时，请以
AGENTS.md 的 Critical Interaction Contract 为准。