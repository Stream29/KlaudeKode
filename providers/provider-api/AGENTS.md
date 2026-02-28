# Provider API Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `provider-api` module.

## Architectural Decisions

- 2026-02-19：Provider API 移除 `createClientAny` 与 unchecked cast；provider 客户端创建统一走显式 `supportsAuth` 校验 + 强类型 auth 解析（`ApiKey`/`OAuthAccessToken`）。