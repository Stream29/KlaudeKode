# 10 Session

## Scope
This spec defines the target-state orchestration contract for session lifecycle, continue semantics, legality gating, and runtime state boundaries.
It owns `SES-REQ-100..199` and references script, model-provider, agent, and todo owner requirements by ID.

## Must Have
- `SessionState` as the aggregate owner of session runtime state.
- A legality gate before any continue or resume path enters runtime execution.
- Enforcement of strict script-only behavior through script-domain owner requirements.
- A hard boundary between runtime `SessionState` and transport `SessionSnapshot`.
- Cross-spec dependency statements that use `[REQ-ID]` tokens.

## Must NOT Have
- Session-level duplication of script, provider, agent, or todo normative text.
- Continue paths that bypass legality validation.
- Runtime state reconstruction from snapshot-only transient data.
- Legacy multi-tool or subagent narratives as active contract behavior.

## Normative Requirements
- [SES-REQ-100] `SessionState` is the aggregate root for session runtime state. All session runtime mutations MUST be applied through `SessionState` ownership boundaries.
- [SES-REQ-101] Session transition order MUST be legality check, state mutation, persistence commit, and state publication.
- [SES-REQ-102] `continueCurrentSession()` MUST be an alias of `continueFromInput("")`. Non-empty input MUST append one user message before resume. Empty input MUST append no message before resume.
- [SES-REQ-103] Every continue or resume path MUST pass a legality gate before runtime execution. Histories that violate pending-state legality MUST fail fast.
- [SES-REQ-104] Session orchestration MUST enforce strict script-only execution by relying on [SCR-REQ-200](./20-script/20-overview.md#scr-req-200), [SCR-REQ-201](./20-script/21-tool-protocol.md#scr-req-201), and [SCR-REQ-202](./20-script/21-tool-protocol.md#scr-req-202). Session behavior MUST NOT redefine those script protocol rules.
- [SES-REQ-105] `SessionSnapshot` is a boundary artifact for persistence, import or export, and cross-layer transport only. Runtime loops and UI-facing runtime state MUST use `SessionState` instead of snapshot-derived transient fields.
- [SES-REQ-106] Session integration with model provider behavior MUST reference [MOD-REQ-300](./30-model-provider/30-overview.md#mod-req-300), [MOD-REQ-301](./30-model-provider/31-provider-api-contract.md#mod-req-301), and [MOD-REQ-302](./30-model-provider/32-auth-capability-and-error.md#mod-req-302). Session requirements MUST NOT restate provider auth, capability, or error contracts.
- [SES-REQ-107] Session integration with agent orchestration and projection MUST reference [AGT-REQ-400](./40-agent/40-overview.md#agt-req-400), [AGT-REQ-401](./40-agent/41-orchestration-runtime.md#agt-req-401), and [AGT-REQ-402](./40-agent/42-message-projection.md#agt-req-402). Session requirements MUST NOT restate agent runtime or projection ownership.
- [SES-REQ-108] Session integration with todo ownership and persistence MUST reference [TODO-REQ-500](./50-todo/50-overview.md#todo-req-500), [TODO-REQ-501](./50-todo/51-state-ownership.md#todo-req-501), and [TODO-REQ-502](./50-todo/52-persistence-contract.md#todo-req-502). Session requirements MUST NOT restate todo domain internals.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules from [GOV-REQ-001](./00-governance.md#gov-req-001), [GOV-REQ-002](./00-governance.md#gov-req-002), [GOV-REQ-004](./00-governance.md#gov-req-004), [GOV-REQ-005](./00-governance.md#gov-req-005), and [GOV-REQ-006](./00-governance.md#gov-req-006).
- Strict script-only protocol and pending legality are owned by [SCR-REQ-200](./20-script/20-overview.md#scr-req-200), [SCR-REQ-201](./20-script/21-tool-protocol.md#scr-req-201), [SCR-REQ-202](./20-script/21-tool-protocol.md#scr-req-202), and [SCR-REQ-203](./20-script/23-stop-continue-and-pending.md#scr-req-203).
- Provider selection, capability, and auth behavior are owned by [MOD-REQ-300](./30-model-provider/30-overview.md#mod-req-300), [MOD-REQ-301](./30-model-provider/31-provider-api-contract.md#mod-req-301), and [MOD-REQ-302](./30-model-provider/32-auth-capability-and-error.md#mod-req-302).
- Agent runtime and message projection behavior are owned by [AGT-REQ-400](./40-agent/40-overview.md#agt-req-400), [AGT-REQ-401](./40-agent/41-orchestration-runtime.md#agt-req-401), and [AGT-REQ-402](./40-agent/42-message-projection.md#agt-req-402).
- Todo state ownership and persistence behavior are owned by [TODO-REQ-500](./50-todo/50-overview.md#todo-req-500), [TODO-REQ-501](./50-todo/51-state-ownership.md#todo-req-501), and [TODO-REQ-502](./50-todo/52-persistence-contract.md#todo-req-502).

## Conformance Mapping
- SES-REQ-100 -> specs/conformance/mapping.yaml#ses-req-100
- SES-REQ-101 -> specs/conformance/mapping.yaml#ses-req-101
- SES-REQ-102 -> specs/conformance/mapping.yaml#ses-req-102
- SES-REQ-103 -> specs/conformance/mapping.yaml#ses-req-103
- SES-REQ-104 -> specs/conformance/mapping.yaml#ses-req-104
- SES-REQ-105 -> specs/conformance/mapping.yaml#ses-req-105
- SES-REQ-106 -> specs/conformance/mapping.yaml#ses-req-106
- SES-REQ-107 -> specs/conformance/mapping.yaml#ses-req-107
- SES-REQ-108 -> specs/conformance/mapping.yaml#ses-req-108
