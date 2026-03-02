# 21 Tool Protocol

## Scope
This spec defines the script-domain tool protocol for each model response batch in the script-only loop.
It covers tool-only constraints, single-tool constraints, and fail-fast behavior.

## Must Have
- A tool-only contract where every execution batch is driven by tool calls.
- A single legal tool name, `executeKotlinScript`, for script execution.
- Deterministic fail-fast behavior for protocol violations.

## Must NOT Have
- Assistant-text execution paths treated as valid progress.
- Acceptance of unknown tool names or fallback to secondary tools.
- Partial execution after protocol validation fails.

## Normative Requirements
- [SCR-REQ-201] Every script-loop model response batch MUST contain at least one `Message.Tool.Call`. Any non-empty `Message.Assistant` text or any batch without a tool call MUST fail fast as a protocol violation.
- [SCR-REQ-202] The only legal tool name is `executeKotlinScript`. If `Message.Tool.Call.tool` is not `executeKotlinScript`, runtime MUST fail fast before tool execution.
- [SCR-REQ-206] On tool protocol violation, runtime MUST terminate the current turn as failure and MUST NOT execute partial, fallback, or best-effort tool behavior.
- [SCR-REQ-207] A mixed batch that includes both assistant text and tool calls is invalid even when the tool call is `executeKotlinScript`. Validation MUST remain strict and deterministic.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Domain boundary ownership for strict script-only behavior is defined by [SCR-REQ-200](./20-overview.md#scr-req-200).
- Session consumes this protocol through [SES-REQ-104](../10-session.md#ses-req-104) and continue legality flow in [SES-REQ-103](../10-session.md#ses-req-103).

## Conformance Mapping
- SCR-REQ-201 -> specs/conformance/mapping.yaml#scr-req-201
- SCR-REQ-202 -> specs/conformance/mapping.yaml#scr-req-202
- SCR-REQ-206 -> specs/conformance/mapping.yaml#scr-req-206
- SCR-REQ-207 -> specs/conformance/mapping.yaml#scr-req-207
