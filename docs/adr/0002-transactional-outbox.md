# 0002. Transactional outbox for publishing events

**Status:** Accepted

## Context
Workflow steps 3 and 4 (save the claim, publish ClaimSubmitted) write to two systems. Publishing
after commit can lose the event if the process dies in between; publishing before or inside the
transaction can announce a claim that then rolls back.

## Decision
`SubmitClaimService` saves the claim and an `outbox_event` row in one database transaction
(`TransactionalSubmitClaimUseCase`). `OutboxRelay` runs every second, locks a batch of unpublished
rows with `FOR UPDATE SKIP LOCKED`, publishes them oldest first, and marks each one published.
A row counts as published only when RabbitMQ has confirmed it (publisher confirms) and routed it to
at least one queue (mandatory flag); otherwise it stays pending and is retried.

## Alternatives
- Publish after commit: simplest, but a crash in between silently loses the confirmation email.
- Publish inside the transaction: a broker outage fails the claim, and a rollback leaves a phantom event.
- Change data capture (Debezium): no polling, but another component to run. It is the scale-up path.

## Consequences
- No lost and no phantom events; claims are accepted while the broker is down.
- Up to about one second of publish lag. Delivery is at-least-once, so duplicates are possible and
  the consumer must de-duplicate (ADR-0003).
- Row locks are held while waiting for broker confirms, which is fine for small batches.
  `outbox.backlog` is the metric to alert on.
