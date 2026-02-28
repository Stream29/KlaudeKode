# Provider OpenAI Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `provider-openai` module.

## Architectural Decisions

- 2026-02-18：OpenAI Browser OAuth 对齐 opencode：授权 URL/PKCE/state 与 token exchange 走兼容手工实现；其他 Auth Code provider 继续走 `kotlin-multiplatform-oidc`。
- 2026-02-18：OpenAI Subscription 模型调用链路对齐 opencode：OAuth access token 走 `https://chatgpt.com/backend-api/codex/responses`，不再默认走 `https://api.openai.com/v1`。
- 2026-02-18：OpenAI Subscription 统一强制 Responses endpoint，并在请求参数注入 `instructions`（取系统提示词）以兼容 codex responses 对 instructions 的要求。
- 2026-02-19：OpenAI provider/auth-mode 常量在 `kode-core`/`app` 统一引用 `kode-config-api` 常量，减少跨模块硬编码漂移。