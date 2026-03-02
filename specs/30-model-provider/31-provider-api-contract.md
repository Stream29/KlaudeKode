# 31 Provider API Contract

## Scope
This spec defines the `LlmProvider` API contract for provider identity, model exposure, auth capability checks, and
typed client creation.
It is the owner contract for provider API surface obligations used by runtime factories.

## Must Have
- A single provider API surface with explicit `id`, `displayName`, `llmProvider`, `models()`, `supportsAuth`, and
  `createClient` obligations.
- Typed auth handling based on `LlmAuth.ApiKey` and `LlmAuth.OAuthAccessToken`.
- Explicit auth validation before client creation.

## Must NOT Have
- Untyped auth payloads passed to provider clients.
- Unchecked auth casts in provider client creation.
- Side-effectful capability checks inside `supportsAuth`.

## Normative Requirements
- [MOD-REQ-301] Every `LlmProvider` implementation MUST expose stable `id`, `displayName`, and `llmProvider`, and
  MUST implement `models()`, `supportsAuth(auth)`, and `createClient(auth)` as the provider integration surface.
- [MOD-REQ-305] `supportsAuth(auth)` MUST be a pure capability predicate over typed `LlmAuth` input.
  `supportsAuth(auth)` MUST NOT create clients, mutate global state, or trigger network calls.
- [MOD-REQ-306] `createClient(auth)` MUST parse auth through typed helpers such as `requireApiKeyAuth` or
  `requireOAuthAccessTokenAuth`, and MUST NOT use unchecked casts or resurrect `createClientAny`-style APIs.
- [MOD-REQ-307] `models()` MUST return provider-owned model entries bound to the same `llmProvider`. Runtime model
  selection MUST treat this list as the provider authority for model availability.
- [MOD-REQ-308] Runtime auth input for provider APIs MUST be represented by `LlmAuth.ApiKey` or
  `LlmAuth.OAuthAccessToken`. Stringly typed map payloads are not a legal provider auth contract surface.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in
  [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and
  [GOV-REQ-006](../00-governance.md#gov-req-006).
- Provider ownership boundary is defined by [MOD-REQ-300](./30-overview.md#mod-req-300).
- Auth capability and error expectations for this API are defined by
  [MOD-REQ-302](./32-auth-capability-and-error.md#mod-req-302),
  [MOD-REQ-309](./32-auth-capability-and-error.md#mod-req-309), and
  [MOD-REQ-310](./32-auth-capability-and-error.md#mod-req-310).

## Conformance Mapping
- MOD-REQ-301 -> specs/conformance/mapping.yaml#mod-req-301
- MOD-REQ-305 -> specs/conformance/mapping.yaml#mod-req-305
- MOD-REQ-306 -> specs/conformance/mapping.yaml#mod-req-306
- MOD-REQ-307 -> specs/conformance/mapping.yaml#mod-req-307
- MOD-REQ-308 -> specs/conformance/mapping.yaml#mod-req-308
