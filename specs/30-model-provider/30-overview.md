# 30 Model Provider Overview

## Scope
This spec set defines target-state contracts for model provider identity, provider API obligations, auth capability
rules, error classification, and registry or preset consistency.
It owns `MOD-REQ-300..399` and is the owner source for provider-domain contracts consumed by session, script, and
agent runtime.

## Must Have
- A clear owner boundary for provider API, typed auth capability checks, and registry or preset behavior.
- Fail-fast contracts for unsupported auth mode and malformed provider auth payload.
- A deterministic-provider contract for stable test execution and replayable behavior.
- Cross-spec references by `[REQ-ID]` token instead of duplicated provider rules.

## Must NOT Have
- Session or script specs redefining provider auth and client-creation internals.
- Legacy unchecked auth casts or `createClientAny` style untyped client creation.
- Registry or preset surfaces with ambiguous ownership of provider ID constraints.

## Normative Requirements
- [MOD-REQ-300] The model-provider domain is the single owner for provider identity, typed auth capability,
  provider client creation contract, builtin registry or preset consistency, and deterministic-provider behavior.
  Non-owner specs MUST reference this domain by ID and MUST NOT duplicate provider normative text.
- [MOD-REQ-303] Provider domain non-goals are script protocol legality, session lifecycle or snapshot ownership,
  and todo persistence ownership. These remain in [SCR-REQ-200](../20-script/20-overview.md#scr-req-200),
  [SES-REQ-105](../10-session.md#ses-req-105), and [TODO-REQ-502](../50-todo/52-persistence-contract.md#todo-req-502).
- [MOD-REQ-304] Any runtime that binds auth to providers MUST use provider-owned capability gates such as
  `supportsAuth` and provider-owned typed-auth parsing rules from
  [MOD-REQ-301](./31-provider-api-contract.md#mod-req-301) and
  [MOD-REQ-302](./32-auth-capability-and-error.md#mod-req-302).

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in
  [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-002](../00-governance.md#gov-req-002),
  [GOV-REQ-004](../00-governance.md#gov-req-004), [GOV-REQ-005](../00-governance.md#gov-req-005), and
  [GOV-REQ-006](../00-governance.md#gov-req-006).
- Session integration consumes provider ownership through [SES-REQ-106](../10-session.md#ses-req-106).
- Script boundaries consume this non-goal split through [SCR-REQ-205](../20-script/20-overview.md#scr-req-205).

## Conformance Mapping
- MOD-REQ-300 -> specs/conformance/mapping.yaml#mod-req-300
- MOD-REQ-303 -> specs/conformance/mapping.yaml#mod-req-303
- MOD-REQ-304 -> specs/conformance/mapping.yaml#mod-req-304
