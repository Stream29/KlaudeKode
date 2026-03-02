# 32 Auth Capability and Error

## Scope
This spec defines provider-domain auth-mode handling for `ApiKey` and `OAuth`, capability gate expectations, and
error classification in provider client initialization.
It is the owner contract for auth compatibility outcomes consumed by runtime setup.

## Must Have
- Explicit auth-mode handling for `LlmAuth.ApiKey` and `LlmAuth.OAuthAccessToken`.
- A fail-fast capability gate using `supportsAuth` before provider client creation.
- Diagnosable error categories for missing credentials and auth type mismatches.
- Deterministic failure behavior for equal input state.

## Must NOT Have
- Silent auth fallback from one auth mode to another.
- Best-effort client creation after `supportsAuth` rejects auth.
- Ambiguous error messages that hide provider ID or auth type.

## Normative Requirements
- [MOD-REQ-302] Provider auth-mode handling MUST normalize runtime auth into `LlmAuth.ApiKey` or
  `LlmAuth.OAuthAccessToken` before provider invocation. Capability selection MUST be checked with `supportsAuth`.
- [MOD-REQ-309] Runtime MUST call `supportsAuth` before `createClient`. If capability is rejected, runtime MUST fail
  fast as auth capability mismatch and MUST NOT attempt fallback or best-effort client creation.
- [MOD-REQ-310] When typed auth parsing detects mismatched auth mode, the raised error MUST include provider ID and
  actual auth type so the failure is classifiable as auth type mismatch.
- [MOD-REQ-311] Missing credential material MUST fail fast and be classified as missing credential configuration.
  Missing API key and missing OAuth access token MUST remain distinct diagnosis outcomes.
- [MOD-REQ-312] Provider auth-path failure behavior MUST be deterministic. Equal provider ID, equal auth payload, and
  equal credential-store state MUST produce the same error class and fail point.

## Cross-Spec Dependencies
- This spec depends on governance authority and schema rules in
  [GOV-REQ-001](../00-governance.md#gov-req-001), [GOV-REQ-005](../00-governance.md#gov-req-005), and
  [GOV-REQ-006](../00-governance.md#gov-req-006).
- Provider API obligations are owned by [MOD-REQ-301](./31-provider-api-contract.md#mod-req-301) and
  [MOD-REQ-306](./31-provider-api-contract.md#mod-req-306).
- Session consumes provider auth compatibility through [SES-REQ-106](../10-session.md#ses-req-106).

## Conformance Mapping
- MOD-REQ-302 -> specs/conformance/mapping.yaml#mod-req-302
- MOD-REQ-309 -> specs/conformance/mapping.yaml#mod-req-309
- MOD-REQ-310 -> specs/conformance/mapping.yaml#mod-req-310
- MOD-REQ-311 -> specs/conformance/mapping.yaml#mod-req-311
- MOD-REQ-312 -> specs/conformance/mapping.yaml#mod-req-312
