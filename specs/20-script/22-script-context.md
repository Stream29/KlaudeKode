# 22 Script Context

## Scope
This spec defines `ScriptContext` composition rules, receiver exposure rules, and script-side output contracts.
It covers how context capabilities are assembled and how user-visible output is separated from debug output.

## Must Have
- Interface-based composition for `ScriptContext` capabilities.
- Per-run isolation of script receiver instances.
- A strict user output channel using `sayToUser(text)`.
- Ordered projection from `ScriptContext.outputList` to `AgentScript.outputList`.

## Must NOT Have
- A monolithic context that hides capability ownership boundaries.
- User-facing messaging through `println`.
- Receiver API changes without prompt-side documentation updates.

## Normative Requirements
- [SCR-REQ-208] `ScriptContext` MUST remain an interface-based composition root. Main context implementations MUST compose functional capability interfaces and aggregate each component `systemPromptInjection`.
- [SCR-REQ-209] Each agent run MUST use an isolated `ScriptContext` instance. Receiver mutable state MUST NOT leak across runs except explicitly passed session-owned state flows.
- [SCR-REQ-210] User-visible script output MUST be emitted through `sayToUser(text)`. `println` is debug-only and MUST NOT be projected as user-facing assistant output.
- [SCR-REQ-211] Runtime MUST persist per-turn `ScriptContext.outputList` into `AgentScript.outputList` and preserve list order for UI projection.
- [SCR-REQ-212] Any new concrete receiver API added by a `ScriptContext` implementation MUST be documented in `systemPromptInjection` in the same change.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Script boundary ownership comes from [SCR-REQ-204](./20-overview.md#scr-req-204), and protocol continuity depends on [SCR-REQ-201](./21-tool-protocol.md#scr-req-201).
- Session and agent projection layers consume this contract through [SES-REQ-107](../10-session.md#ses-req-107) and [AGT-REQ-402](../40-agent/42-message-projection.md#agt-req-402).

## Conformance Mapping
- SCR-REQ-208 -> specs/conformance/mapping.yaml#scr-req-208
- SCR-REQ-209 -> specs/conformance/mapping.yaml#scr-req-209
- SCR-REQ-210 -> specs/conformance/mapping.yaml#scr-req-210
- SCR-REQ-211 -> specs/conformance/mapping.yaml#scr-req-211
- SCR-REQ-212 -> specs/conformance/mapping.yaml#scr-req-212
