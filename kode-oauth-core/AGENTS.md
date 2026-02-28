# Kode OAuth Core Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `kode-oauth-core` module.

## Architectural Decisions

- 2026-02-18：OAuth 鉴权链路引入 `kotlin-multiplatform-oidc` 处理 Auth Code + PKCE；Device Flow（含 `openai_codex_bridge`）暂沿用现有实现。
- 2026-02-18：OAuth 浏览器回调流程对齐 opencode：先注册回调等待再打开浏览器，回调等待显式 5 分钟超时，回调服务按端口启动避免 host 绑定差异导致超时。
- 2026-02-18：OIDC token exchange 使用 `DefaultOpenIdConnectClient` 默认 HTTP 客户端（含 ContentNegotiation 兼容解析），避免 `Exchange token failed: 200 null` 解析失败。