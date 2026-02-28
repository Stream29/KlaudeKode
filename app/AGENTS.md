# App & UI Guidelines

This document contains critical architectural decisions and interaction contracts for the `app` and UI modules.

## Architectural Decisions

- 2026-02-19：删除纯转发层 `SessionAwareAgentFactoryProvider` 与 `WebToolsProvider`，直接在组合根和 `MainViewModel` 装配 `SessionAwareAgentFactory` 与 `WebTools`。
- 2026-02-19：聊天主视图移除 RawMessage 模式：`Chat` 统一直接消费 `SessionUiState.messages`（仅 `UserMessage`/`AgentScript`），不再提供 debug raw 列表切换入口。
- 2026-02-19：`AgentScript` UI 展示协议升级：每条脚本消息先展示可折叠 script preview（展开显示脚本内容与脚本结果），再按顺序逐条渲染 `outputList` 为 assistant 消息。
- 2026-02-19：配置模型移除 `UiConfig.debugShowRawMessageList`，RawMessage 视图切换配置不再生效（硬切）。
- 2026-02-24：ACP 保持显式禁用语义（`startAcpServer/stopAcpServer` 均仅提示 disabled），并移除 `MainViewModel` 内不可达 ACP support/session 实现与无调用点 MCP registry helper，保留 `runMcpTest` 健康/测试链路。

## Critical Interaction Contract

- Chat rendering contract: `Chat` 页面只渲染 `SessionUiState.messages` 原始 `AgentMessage` 序列；`UserMessage` 直接渲染，`AgentScript` 先渲染折叠脚本预览再渲染 `outputList`。不再维护 RawMessage 模式。