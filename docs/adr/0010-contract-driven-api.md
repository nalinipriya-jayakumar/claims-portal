# 0010. Contract-driven API: code generated from the OpenAPI document

**Status:** Accepted (added after the first complete version)

## Context
The OpenAPI document was written first, but the server's request and response classes, its
validation patterns and the portal's TypeScript types were all hand-written copies of it, kept in
step only by comments. Nothing failed when they drifted.

## Decision
`claims-api/src/main/resources/openapi/claims-api.yaml` is the single source of truth.

- **Server:** `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly`) generates the
  `ClaimsApi` interface and every request/response model on each build. `ClaimController` implements
  the interface, so paths, methods, media types, parameters and types come from the contract. The
  schema's `required`, `pattern`, `minLength`/`maxLength` and `format` become Bean Validation
  annotations on the generated models.
- **Rules the schema states in prose** (the date-of-loss window, consent must be true) are enforced in
  `ClaimRequestValidator`; customer wording per field and error code is in `FieldMessages`.
- **Error codes:** responses are written with the generated `FieldError`, so only codes the contract
  defines can be sent.
- **Portal:** `openapi-typescript` generates `claim-portal-ui/src/api/generated/claims-api.ts`. The
  portal's request, response and error types and its option lists are typed from it. The file is
  committed (the portal's Docker build cannot see `claims-api/`), and CI fails if it is stale.
- **Conformance tests:** `ContractConformanceTest` fails the build when the domain enums (`LossType`,
  `Province`, `ClaimStatus`) or the API's `ErrorCode` differ from the contract's.

## Alternatives
- Code-first (springdoc generates the document from annotations): the document then follows the code
  and can change by accident; the portal still needs its own types.
- Hand-written models plus a response-validation test library: catches drift only in tested paths,
  and the main libraries have limited OpenAPI 3.1 support.
- Consumer-driven contract tests (Pact, Spring Cloud Contract): valuable with independent teams and
  several consumers; heavy for one consumer in the same repository. Listed for production.

## Consequences
- A contract change that breaks the server stops compilation; one that breaks the portal stops its
  type check; a vocabulary mismatch stops the conformance test. Introducing the generator immediately
  found a contract bug: YAML read the unquoted province code `ON` as the boolean `true`, which would
  have rejected every claim from Ontario.
- Generated models are mutable classes with a `toString()` that prints every field, including
  personal information. They stay inside the web adapter, are mapped to the domain at once
  (`ClaimApiMapper`), and must never be logged.
- OpenAPI 3.1 support in openapi-generator is marked beta; `const` produced an awkward one-value enum,
  so the consent rule is stated in prose instead.
- Generated code lives in `target/generated-sources/openapi`; in IntelliJ, reload the Maven project or
  run "Generate Sources and Update Folders" after changing the contract.
