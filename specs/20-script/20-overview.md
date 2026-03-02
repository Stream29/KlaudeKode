# 20 Script Overview

## Scope
This spec set defines the target-state contract for the script domain.
It owns script-only execution boundaries, tool protocol authority, `ScriptContext` receiver rules, and pending or stop or continue legality.
It owns `SCR-REQ-200..299` and serves as the owner source for script runtime behavior.

## Must Have
- A clear owner boundary for script protocol, context semantics, and legality gates.
- Strict script-only runtime behavior that is consumed by session and agent specs through ID references.
- Explicit non-goals that keep provider, session persistence, and todo persistence in their owner specs.

## Must NOT Have
- Multi-tool orchestration as an active runtime contract.
- Normative script behavior duplicated in non-owner specs.
- Script-domain ownership of provider auth or model capability contracts.

## Normative Requirements
- [SCR-REQ-200] Script runtime MUST enforce strict script-only behavior. Runtime progress is tool-call driven, and free-form assistant text is never a legal execution substitute. Detailed tool batch legality is defined by [SCR-REQ-201](./21-tool-protocol.md#scr-req-201) and [SCR-REQ-202](./21-tool-protocol.md#scr-req-202).
- [SCR-REQ-204] Script domain ownership includes tool protocol, `ScriptContext` composition and output semantics, and pending or stop or continue legality. Other domains MUST reference script requirements by ID and MUST NOT redefine script normative bodies.
- [SCR-REQ-205] Script domain non-goals are provider selection or auth, session snapshot persistence boundary, and todo storage contract. These concerns stay in [MOD-REQ-300](../30-model-provider/30-overview.md#mod-req-300), [SES-REQ-105](../10-session.md#ses-req-105), and [TODO-REQ-502](../50-todo/52-persistence-contract.md#todo-req-502).

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-002](../00-governance.md#gov-req-002), [GOV-REQ-004](../00-governance.md#gov-req-004), [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Session orchestration consumes script ownership via [SES-REQ-104](../10-session.md#ses-req-104) and [SES-REQ-103](../10-session.md#ses-req-103).
- Detailed protocol rules are owned by [SCR-REQ-201](./21-tool-protocol.md#scr-req-201), [SCR-REQ-202](./21-tool-protocol.md#scr-req-202), [SCR-REQ-203](./23-stop-continue-and-pending.md#scr-req-203), and [SCR-REQ-210](./22-script-context.md#scr-req-210).

## Conformance Mapping
- SCR-REQ-200 -> specs/conformance/mapping.yaml#scr-req-200
- SCR-REQ-204 -> specs/conformance/mapping.yaml#scr-req-204
- SCR-REQ-205 -> specs/conformance/mapping.yaml#scr-req-205
