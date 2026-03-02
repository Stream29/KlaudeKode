# 33 Registry and Preset Contract

## Scope
This spec defines builtin provider registry and preset registry consistency rules, lookup behavior, and
deterministic-provider expectations for test execution.
It is the owner contract for registry-level invariants in the model-provider domain.

## Must Have
- Duplicate-ID rejection for builtin providers and presets.
- One-to-one consistency between registered providers and published presets.
- Deterministic list and lookup behavior.
- An explicit deterministic-provider contract for test-only provider behavior.

## Must NOT Have
- Registry initialization that tolerates duplicate provider or preset IDs.
- Presets that drift from provider identity or model capability metadata.
- Nondeterministic ordering from equivalent registry state.

## Normative Requirements
- [MOD-REQ-313] Builtin provider registry initialization MUST reject duplicate provider IDs and fail fast before
  exposing list or find operations.
- [MOD-REQ-314] Builtin preset registry initialization MUST reject duplicate preset IDs and MUST preserve one-to-one
  ID parity with builtin providers, including `test-deterministic`.
- [MOD-REQ-315] `listProviders()` and `listPresets()` MUST return deterministic ordering by normalized
  `displayName` so equal registry state yields equal iteration order.
- [MOD-REQ-316] `findProvider(id)` and `findPreset(id)` MUST trim input. Blank IDs MUST return `null`, and non-blank
  lookup MUST remain exact by normalized key.
- [MOD-REQ-317] Deterministic test provider contract requires provider ID `test-deterministic`, model ID
  `test-deterministic-tool-only`, and `supportsAuth(auth)` accepting only `LlmAuth.ApiKey`.
- [MOD-REQ-318] Deterministic provider execution used for tool-only test paths MUST emit tool-call-first responses
  targeting `executeKotlinScript`, and MUST avoid assistant-text progress paths forbidden by
  [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201) and
  [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202).

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in
  [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and
  [GOV-REQ-006](../00-governance.md#gov-req-006).
- Provider API and auth contract coupling is defined by
  [MOD-REQ-301](./31-provider-api-contract.md#mod-req-301),
  [MOD-REQ-302](./32-auth-capability-and-error.md#mod-req-302), and
  [MOD-REQ-305](./31-provider-api-contract.md#mod-req-305).
- Script tool-only legality consumed by deterministic provider tests is defined by
  [SCR-REQ-201](../20-script/21-tool-protocol.md#scr-req-201),
  [SCR-REQ-202](../20-script/21-tool-protocol.md#scr-req-202), and
  [SCR-REQ-203](../20-script/23-stop-continue-and-pending.md#scr-req-203).

## Conformance Mapping
- MOD-REQ-313 -> specs/conformance/mapping.yaml#mod-req-313
- MOD-REQ-314 -> specs/conformance/mapping.yaml#mod-req-314
- MOD-REQ-315 -> specs/conformance/mapping.yaml#mod-req-315
- MOD-REQ-316 -> specs/conformance/mapping.yaml#mod-req-316
- MOD-REQ-317 -> specs/conformance/mapping.yaml#mod-req-317
- MOD-REQ-318 -> specs/conformance/mapping.yaml#mod-req-318
