# 0003. Idempotent consumer keyed on event ID

**Status:** Accepted

## Context
The outbox and RabbitMQ both deliver at least once. A customer must not receive two emails for one claim.

## Decision
The notification service keeps a `processed_event` table keyed on event ID. In one transaction it
inserts the event ID (`INSERT ... ON CONFLICT DO NOTHING`), skips the event if the row already
existed, otherwise sends the email, then commits. A failed send rolls the row back so the retry
can try again. A concurrent duplicate blocks on the row lock and then sees the conflict.

## Alternatives
- Rely on broker "exactly-once" settings: RabbitMQ does not provide end-to-end exactly-once delivery.
- Check, then send, then record: leaves a window in which two consumers both send.

## Consequences
- Redelivery and concurrent consumers produce one email (tested in `ClaimConfirmationFlowIntegrationTest`).
- Known limitation: if the process dies after SMTP accepted the email but before the commit, the event
  is redelivered and a second email is sent. Accepted for a notification; an email provider that
  supports idempotency keys would close this gap in production.
