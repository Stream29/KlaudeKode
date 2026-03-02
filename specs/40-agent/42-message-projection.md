# 42 Message Projection

## Scope
This spec defines target-state projection contracts from script execution output into agent-visible messages.
It covers `AgentScript.outputList` ownership, projection ordering, and UI rendering obligations.

## Must Have
- A deterministic projection contract for `AgentScript.outputList`.
- One-to-one ordered rendering obligations for each output element.
- A strict separation between user-visible output and debug-only channels.

## Must NOT Have
- Projection that reorders `outputList` elements.
- Projection that merges multiple `outputList` elements into one assistant message without an explicit owner requirement.
- User-visible rendering derived from debug `println` output.

## Normative Requirements
- [AGT-REQ-402] Agent orchestration MUST project `AgentScript.outputList` in source order. Each list element MUST map to
  exactly one assistant message render unit in UI consumption order.
- [AGT-REQ-405] Agent runtime MUST persist per-turn `ScriptContext.outputList` into `AgentScript.outputList`, aligned with
  [SCR-REQ-211](../20-script/22-script-context.md#scr-req-211). Projection MUST ignore debug-only channels defined by
  [SCR-REQ-210](../20-script/22-script-context.md#scr-req-210).
- [AGT-REQ-410] UI surfaces that render `AgentScript` MUST present script preview content and then render expanded
  `outputList` assistant messages in order. UI integration MUST NOT introduce a raw-message fallback mode as an
  alternative normative projection path.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema requirements in [GOV-REQ-001](../00-governance.md#gov-req-001),
  [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Script output-channel ownership is defined by [SCR-REQ-210](../20-script/22-script-context.md#scr-req-210) and
  [SCR-REQ-211](../20-script/22-script-context.md#scr-req-211).
- Session integration consumes projection ownership via [SES-REQ-107](../10-session.md#ses-req-107).

## Conformance Mapping
- AGT-REQ-402 -> specs/conformance/mapping.yaml#agt-req-402
- AGT-REQ-405 -> specs/conformance/mapping.yaml#agt-req-405
- AGT-REQ-410 -> specs/conformance/mapping.yaml#agt-req-410
