# Architecture decision records

Each record states the context, the decision, the alternatives rejected and what the decision costs.
They expand section 8 of `Claim_Portal_Architecture.pdf` (supplied separately); 0009 and 0010 were added during implementation.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-event-driven-two-services.md) | Event-driven: two Spring Boot services and a broker | Accepted |
| [0002](0002-transactional-outbox.md) | Transactional outbox for publishing events | Accepted |
| [0003](0003-idempotent-consumer.md) | Idempotent consumer keyed on event ID | Accepted |
| [0004](0004-rabbitmq.md) | RabbitMQ as the message broker | Accepted |
| [0005](0005-thin-event.md) | Thin, versioned ClaimSubmitted event | Accepted |
| [0006](0006-claim-number-sequence.md) | Claim number from a database sequence | Accepted |
| [0007](0007-postgresql-database-per-service.md) | PostgreSQL with Flyway, a database per service | Accepted |
| [0008](0008-mailpit.md) | Mailpit as the SMTP server | Accepted |
| [0009](0009-same-origin-edge.md) | Same-origin portal behind an Nginx edge | Accepted |
| [0010](0010-contract-driven-api.md) | Contract-driven API: code generated from the OpenAPI document | Accepted |
