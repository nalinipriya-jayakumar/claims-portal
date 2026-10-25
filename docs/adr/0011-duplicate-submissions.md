# 0011. Duplicate submissions: idempotency key for retries, flag for repeat reports

**Status:** Accepted (added after the first complete version)

## Context
Two claims, CLM-2026-000009 and CLM-2026-000012, were submitted 56 minutes apart for the same
policy, date of loss and loss type. ADR-0003 already guaranteed one email per event; nothing
prevented two claims. There are two different problems:

1. **Retries**: one submission reaches the server more than once (timeouts, refresh, double-click).
   The customer meant one claim.
2. **Repeat reports**: the customer fills in the form again for an incident already reported.
   Whether that is a duplicate is a business judgement: two genuine losses can share a policy,
   date and type (wind damage to the house and to the garage).

The API is anonymous, so its answers must not reveal what other submissions exist.

## Decision
**Retries: `Idempotency-Key` header (FR-18).** The client sends a UUID per submission and reuses
it when retrying. The API stores key, a SHA-256 fingerprint of the normalized claim, and the claim
number in `idempotency_key`, in the same transaction as the claim and its outbox row.
- Same key, same claim: the original `201` is returned with `Idempotent-Replayed: true`; no claim,
  event or email is created.
- Same key, different claim: `409`, nothing saved.
- Two requests with the same key at once: the second waits on the primary key, fails, and is run
  again in a new transaction, where it replays the first (`TransactionalSubmitClaimUseCase`).
- The portal reuses a key only for an identical claim, so a retry after a failure is safe and an
  edited claim is a new submission.

**Repeat reports: flag and accept.** A new claim that matches an existing one on policy number,
date of loss and loss type is saved normally, with `possible_duplicate_of` set to the earliest
match. The customer gets the usual response and email. The flag is internal: database column,
log line and the `claims.possible_duplicates` metric. The API never returns it.

## Alternatives
- Reject repeat reports with 409: blocks genuine second losses, and confirming that a claim exists
  for a policy and date leaks information to anyone who knows a policy number.
- Retry protection only: simplest, but leaves adjusters to spot repeats unaided.
- Server-side de-duplication by content without a key: cannot tell a retry from a deliberate
  second report.

## Consequences
- Retries can never create a second claim, event or email (`DuplicateSubmissionIntegrationTest`,
  including a four-thread race).
- Repeat reports reach the claims team already linked to the first claim.
- Matching is best effort: two repeat reports committed at the same instant may not see each other.
  Acceptable for a flag reviewed by a person.
- Keys are kept indefinitely in v1; production adds a purge job for keys older than 24 hours.
- Clients without a key (older clients, scripts) get the previous behaviour.
