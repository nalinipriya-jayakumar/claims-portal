# 0001. Event-driven: two Spring Boot services and a broker

**Status:** Accepted

## Context
The brief's one hard rule is that the confirmation email is generated and sent by a separate,
asynchronous consumer service. The design must also show clear separation of concerns, be easy to
run locally, and stay small enough to explain in one review session.

## Decision
Two independently deployable Spring Boot applications in one Maven multi-module repository:
`claims-api` (validate, number, save, record event) and `notification-service` (consume, render,
send). They are joined only by RabbitMQ and share no code except the `event-contracts` module.
Inside each service a ports-and-adapters layout keeps the domain and use cases free of Spring.

## Alternatives
- Layered monolith sending email synchronously: fails the brief, and an SMTP outage would fail claims.
- Monolith with `@Async` or Spring events: not a separate subscriber, and work is lost on a crash.
- Modular monolith with a real queue: the consumer would share the API's process and lifecycle.
- Fine-grained microservices or serverless: more moving parts or a cloud dependency, no benefit here.

The weighted comparison is in section 4 of the architecture document (this option scored 109/120).

## Consequences
- Email never touches the customer's request; each side fails, scales and deploys on its own.
- Six containers instead of one, and eventual consistency between "claim accepted" and "email sent",
  which the portal's wording reflects. Docker Compose health checks absorb the extra containers.
