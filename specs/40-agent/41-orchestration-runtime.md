# 41 Orchestration Runtime

## Scope
This spec defines target-state runtime responsibilities for agent orchestration `run` and `continue` paths.
It covers execution-only boundaries, continuation legality integration, and lifecycle ownership limits.

## Must Have
- A strict execution-only boundary for agent runtime surfaces.
- Explicit `run` and `continue` responsibility contracts.
- Clear lifecycle boundaries between agent runtime and session lifecycle ownership.

## Must NOT Have
- Runtime-side ownership of session creation, deletion, or storage lifecycle mutations.
- Continue paths that skip session legality gates.
- Runtime behavior that treats non-script protocol text as a legal execution event.

## Normative Requirements
- [AGT-REQ-401] Agent runtime surfaces MUST be execution-only. Runtime responsibilities are limited to `run` or `continue`
  orchestration plus model-response parsing for the current turn. Runtime MUST NOT own session lifecycle mutations such as
  create-session, delete-session, or snapshot storage writes.
- [AGT-REQ-404] `run` MUST start one isolated execution turn with a fresh script receiver instance, consistent with
  [SCR-REQ-209](../20-script/22-script-context.md#scr-req-209). Runtime MUST consume script protocol legality from
  [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201) and [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202).
- [AGT-REQ-409] `continue` MUST execute only after session-level legality is satisfied by
  [SES-REQ-103](../10-session.md#ses-req-103). Continue entry semantics MUST stay aligned with
  [SES-REQ-102](../10-session.md#ses-req-102) for empty-input alias behavior.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema requirements in [GOV-REQ-001](../00-governance.md#gov-req-001),
  [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Runtime legality depends on [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201),
  [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202), and [SCR-REQ-203](../20-script/23-stop-continue-and-pending.md#scr-req-203).
- Session lifecycle ownership and continue gate dependencies are defined by [SES-REQ-101](../10-session.md#ses-req-101),
  [SES-REQ-102](../10-session.md#ses-req-102), and [SES-REQ-103](../10-session.md#ses-req-103).

## Conformance Mapping
- AGT-REQ-401 -> specs/conformance/mapping.yaml#agt-req-401
- AGT-REQ-404 -> specs/conformance/mapping.yaml#agt-req-404
- AGT-REQ-409 -> specs/conformance/mapping.yaml#agt-req-409
