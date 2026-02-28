# Provider Builtin Agent Guidelines

This document contains critical architectural decisions and interaction contracts for the `provider-builtin` module.

## Architectural Decisions

- 2026-02-19：新增内置 `test-deterministic` provider（`provider-builtin`）用于测试链路；基于 Koog mock executor，`execute` 固定返回 deterministic tool-call，避免 tool-only 协议下 assistant 文本违规，稳定复现 continue/no-pending 路径。