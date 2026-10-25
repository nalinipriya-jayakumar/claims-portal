# 0006. Claim number from a database sequence

**Status:** Accepted

## Context
Customers read claim numbers over the phone. Numbers must be unique across API replicas and restarts.

## Decision
`CLM-{year}-{six digits}`, with the digits from the PostgreSQL sequence `claim_number_seq` and the
year from the business clock (America/Winnipeg). A unique constraint on `claim.claim_number` is the
final guard. The generator sits behind the `ClaimNumberGenerator` port.

## Alternatives
- UUID: unique, but unreadable to a customer.
- Random alphanumeric: hides claim volume, but needs collision retries.

## Consequences
- Short, sortable and unique under concurrency.
- Reveals rough claim volume (acceptable here). The sequence is global rather than reset each year,
  and stops at 999,999; production would widen the format or reset per year. Gaps after rollbacks are normal.
