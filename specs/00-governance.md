# 00 Governance

## Scope
This spec defines the governance backbone for the formal contract system under `specs/`.
It sets the authority model, owner-spec rule, ID taxonomy, pre-allocated ID ranges, cross-reference format, and mandatory section schema for every spec file.

## Must Have
- A single normative authority model where formal specs override conflicting secondary sources.
- A single-owner model where each requirement ID has exactly one owner spec.
- A shared requirement ID taxonomy across governance and all domain specs.
- Pre-allocated requirement ID ranges for early cross-references across domains.
- A standard cross-reference format using `[REQ-ID]` with Markdown links.
- A mandatory section schema that every spec file follows in the same order.

## Must NOT Have
- Normative behavior defined only in `AGENTS.md` or implementation without a formal spec requirement ID.
- Multiple specs claiming ownership of the same requirement ID.
- Copy-pasted requirement bodies across specs when a reference by ID is sufficient.
- Cross-spec references that omit the requirement ID token.
- Spec files that skip or reorder the mandatory section schema.

## Normative Requirements
- [GOV-REQ-001] Formal specs in `specs/` are the final authority for behavioral contracts. If conflicts occur with `AGENTS.md` or current implementation, the formal spec governs until implementation and secondary docs are aligned.
- [GOV-REQ-002] Every requirement is defined in exactly one owner spec. Non-owner specs MUST reference the owner requirement ID and MUST NOT restate normative text as a second source.
- [GOV-REQ-003] Requirement IDs MUST follow this taxonomy: `GOV-REQ-*`, `SES-REQ-*`, `SCR-REQ-*`, `MOD-REQ-*`, `AGT-REQ-*`, and `TODO-REQ-*`.
- [GOV-REQ-004] Requirement ID allocation is pre-reserved by numeric range to support stable early references: `GOV-REQ-001..099`, `SES-REQ-100..199`, `SCR-REQ-200..299`, `MOD-REQ-300..399`, `AGT-REQ-400..499`, and `TODO-REQ-500..599`.
- [GOV-REQ-005] Cross-spec references MUST use bracketed ID tokens in the form `[REQ-ID]`. When possible, each token MUST include a direct Markdown link to the owner location, for example `[SES-REQ-101](./10-session.md#ses-req-101)`.
- [GOV-REQ-006] Every spec file MUST include these sections in this exact order: `## Scope`, `## Must Have`, `## Must NOT Have`, `## Normative Requirements`, `## Cross-Spec Dependencies`, and `## Conformance Mapping`.
- [GOV-REQ-007] Cross-spec dependency statements MUST reference owner requirements by ID token and MUST NOT duplicate requirement ownership.
- [GOV-REQ-008] Every normative requirement ID MUST have a conformance mapping entry, and every mapping entry MUST point back to exactly one owner spec requirement.

## Cross-Spec Dependencies
- This governance spec is the owner spec for governance/process requirements.
- Session, script, model-provider, agent, and todo specs depend on [GOV-REQ-001], [GOV-REQ-002], [GOV-REQ-003], [GOV-REQ-004], [GOV-REQ-005], and [GOV-REQ-006].

## Conformance Mapping
- GOV-REQ-001 -> specs/conformance/mapping.yaml#gov-req-001
- GOV-REQ-002 -> specs/conformance/mapping.yaml#gov-req-002
- GOV-REQ-003 -> specs/conformance/mapping.yaml#gov-req-003
- GOV-REQ-004 -> specs/conformance/mapping.yaml#gov-req-004
- GOV-REQ-005 -> specs/conformance/mapping.yaml#gov-req-005
- GOV-REQ-006 -> specs/conformance/mapping.yaml#gov-req-006
- GOV-REQ-007 -> specs/conformance/mapping.yaml#gov-req-007
- GOV-REQ-008 -> specs/conformance/mapping.yaml#gov-req-008
