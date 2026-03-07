# 多Agent协作

## 当前边界（strict script-only）

- 当前版本多agent运行能力是 disabled。
- `spawn_subagent`、`await_user_input`、`wait_for_user_input` 等旧别名仅作为历史背景，不是当前实现目标。
- 运行时对多agent相关API必须返回稳定的 disabled 错误，不得产生副作用。

## 历史背景与roadmap（非当前契约）

- 本文档保留多agent叙事，仅用于历史背景和路线讨论。
- 在未新增对齐裁决与测试门禁前，不得将roadmap内容解释为当前可执行能力。
