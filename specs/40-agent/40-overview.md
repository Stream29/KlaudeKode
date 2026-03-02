# 40 Agent Overview

## Scope
This spec set defines target-state contracts for agent orchestration ownership, main or sub-agent role boundaries,
runtime delegation boundaries, and message projection responsibilities.
It owns `AGT-REQ-400..499` and is the owner source for agent-domain contracts consumed by session and script domains.

## Must Have
- A clear owner boundary for `MainAgent` orchestration responsibilities and `SubAgent` delegated execution responsibilities.
- A clear scope split between agent orchestration contracts and script or provider owner contracts.
- Cross-spec dependency references by `[REQ-ID]` token for all non-agent owner behaviors.

## Must NOT Have
- Session or script specs redefining agent runtime ownership boundaries.
- Agent contracts that bypass script-domain legality gates.
- Active runtime behavior based on legacy alias surfaces or legacy multi-tool narrative text.

## Normative Requirements
- [AGT-REQ-400] Agent domain is the single owner for orchestration role boundaries. `MainAgent` MUST own user-facing
  orchestration entry, continuation delegation, and sub-agent dispatch policy. `SubAgent` MUST own delegated execution
  only within the boundary set by `MainAgent`.
- [AGT-REQ-403] Agent-domain non-goals are script protocol legality, provider auth capability, and todo persistence
  ownership. These concerns remain in [SCR-REQ-200](../20-script/20-overview.md#scr-req-200),
  [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201), [MOD-REQ-300](../30-model-provider/30-overview.md#mod-req-300),
  and [TODO-REQ-502](../50-todo/52-persistence-contract.md#todo-req-502).
- [AGT-REQ-408] Session integrations consuming agent behavior MUST reference [AGT-REQ-400](#agt-req-400),
  [AGT-REQ-401](./41-orchestration-runtime.md#agt-req-401), and [AGT-REQ-402](./42-message-projection.md#agt-req-402).
  Session adapters MUST NOT restate agent runtime or projection ownership as a second normative source.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema requirements in [GOV-REQ-001](../00-governance.md#gov-req-001),
  [GOV-REQ-002](../00-governance.md#gov-req-002), [GOV-REQ-004](../00-governance.md#gov-req-004),
  [GOV-REQ-005](../00-governance.md#gov-req-005), and [GOV-REQ-006](../00-governance.md#gov-req-006).
- Session consumes this owner boundary through [SES-REQ-107](../10-session.md#ses-req-107).
- Runtime execution split is detailed by [AGT-REQ-401](./41-orchestration-runtime.md#agt-req-401), and output projection
  ownership is detailed by [AGT-REQ-402](./42-message-projection.md#agt-req-402).

## Conformance Mapping
- AGT-REQ-400 -> specs/conformance/mapping.yaml#agt-req-400
- AGT-REQ-403 -> specs/conformance/mapping.yaml#agt-req-403
- AGT-REQ-408 -> specs/conformance/mapping.yaml#agt-req-408
