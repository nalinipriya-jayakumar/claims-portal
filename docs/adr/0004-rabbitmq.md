# 0004. RabbitMQ as the message broker

**Status:** Accepted

## Context
The brief needs a no-cost queue that runs locally. The failure-handling discussion needs retries
and a dead-letter queue.

## Decision
RabbitMQ 4.1 with a durable topic exchange, `claims.events`. The publisher owns the exchange; each
consumer owns its queue and dead-letter queue (`claims.submitted`, `claims.submitted.dlx`,
`claims.submitted.dlq`). Retries are in-process with exponential backoff (3 attempts), and
malformed events go straight to the dead-letter queue.

## Alternatives
- Kafka (KRaft): replay and stronger enterprise alignment, but retries and dead-letter topics must be
  built by hand and the container is heavier. An equal choice if replay matters more.
- ActiveMQ, Redis Streams, SQS: less common in this stack, or cloud-only.

## Consequences
- Built-in dead-lettering, and a management UI for the demo.
- Switching to Kafka touches only the publisher adapter and the listener adapter; the use cases and the
  event contract are unaffected.
