# 43 Legacy Boundaries

## Scope
This spec defines target-state boundaries for legacy agent surfaces.
It identifies prohibited legacy aliases and legacy narrative patterns that are retained only as historical context.

## Must Have
- An explicit prohibition list for legacy alias surfaces.
- A clear rule that legacy multi-tool narrative text is historical only.
- Cross-spec enforcement links to current script and session owner requirements.

## Must NOT Have
- Runtime or spec contracts that treat legacy aliases as current behavior.
- New implementation work that targets legacy multi-tool narrative behavior.
- Normative requirements in other specs that reintroduce legacy semantics.

## Normative Requirements
- [AGT-REQ-406] Legacy aliases `await_user_input`, `wait_for_user_input`, and `spawn_subagent` are historical references
  only. Current runtime and spec contracts MUST NOT expose, invoke, or depend on these legacy aliases as normative
  behavior.
- [AGT-REQ-407] Legacy multi-tool narrative is historical context only. Active runtime protocol MUST remain strict
  script-only behavior owned by [SCR-REQ-200](../20-script/20-overview.md#scr-req-200) and
  [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202).
- [AGT-REQ-411] Any retained mention of legacy surfaces in docs or tests MUST be marked as historical background.
  Session and agent integration specs MUST reference current owner requirements such as
  [SES-REQ-107](../10-session.md#ses-req-107), [AGT-REQ-400](./40-overview.md#agt-req-400), and
  [AGT-REQ-401](./41-orchestration-runtime.md#agt-req-401) instead of reviving legacy behavior.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema requirements in [GOV-REQ-001](../00-governance.md#gov-req-001),
  [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Current execution protocol dependencies are owned by [SCR-REQ-200](../20-script/20-overview.md#scr-req-200),
  [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201), and [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202).
- Session-level integration dependencies are defined by [SES-REQ-103](../10-session.md#ses-req-103) and
  [SES-REQ-107](../10-session.md#ses-req-107).

## Conformance Mapping
- AGT-REQ-406 -> specs/conformance/mapping.yaml#agt-req-406
- AGT-REQ-407 -> specs/conformance/mapping.yaml#agt-req-407
- AGT-REQ-411 -> specs/conformance/mapping.yaml#agt-req-411
