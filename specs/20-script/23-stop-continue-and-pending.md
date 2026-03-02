# 23 Stop Continue and Pending

## Scope
This spec defines pending legality, stop semantics, and continue-path invariants for script-only runtime.
It is the owner contract for legal and illegal uses of `PENDING_INPUT`.

## Must Have
- An explicit legality rule for `PENDING_INPUT`.
- Two-phase stop behavior, safe-stop then force-stop.
- Continue invariants that preserve strict script-only legality.
- Cleanup guarantees that prevent illegal trailing pending script states.

## Must NOT Have
- Continue or resume paths that bypass pending legality checks.
- Stop paths that leave an illegal trailing `PENDING_INPUT` script record.
- Force-stop behavior that keeps unfinished pending artifacts in history.

## Normative Requirements
- [SCR-REQ-203] In strict script-only runtime, persisted history used for continue or resume MUST NOT end with trailing `AgentScript.status == PENDING_INPUT`. If such trailing `PENDING_INPUT` exists, legality check MUST fail fast before runtime execution.
- [SCR-REQ-213] Stop semantics are two phase. First stop request is safe-stop, runtime waits for current `executeKotlinScript` call to complete, then suspends at the next safe point.
- [SCR-REQ-214] Second stop request during an active safe-stop window is force-stop. Runtime MUST cancel current execution and rollback unfinished trailing pending script artifacts.
- [SCR-REQ-215] Continue path entry MUST pass legality checks from [SCR-REQ-203](./23-stop-continue-and-pending.md#scr-req-203) and then execute under tool protocol rules in [SCR-REQ-201](./21-tool-protocol.md#scr-req-201) and [SCR-REQ-202](./21-tool-protocol.md#scr-req-202).
- [SCR-REQ-216] After any stop or continue path completes, conversation history MUST NOT remain in an illegal pending state. `PENDING_INPUT` visibility and runtime state MUST stay consistent with session legality gates.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Session legality and continue sequencing consume this owner contract through [SES-REQ-103](../10-session.md#ses-req-103) and [SES-REQ-104](../10-session.md#ses-req-104).
- Tool protocol dependencies are defined by [SCR-REQ-201](./21-tool-protocol.md#scr-req-201) and [SCR-REQ-202](./21-tool-protocol.md#scr-req-202).

## Conformance Mapping
- SCR-REQ-203 -> specs/conformance/mapping.yaml#scr-req-203
- SCR-REQ-213 -> specs/conformance/mapping.yaml#scr-req-213
- SCR-REQ-214 -> specs/conformance/mapping.yaml#scr-req-214
- SCR-REQ-215 -> specs/conformance/mapping.yaml#scr-req-215
- SCR-REQ-216 -> specs/conformance/mapping.yaml#scr-req-216
