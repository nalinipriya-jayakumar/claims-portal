# 0007. PostgreSQL with Flyway, a database per service

**Status:** Accepted

## Context
The brief allows in-memory storage, but the outbox, unique constraints and row locking are only
meaningful against a real transactional database.

## Decision
PostgreSQL 16 in a container. Each service has its own database and login (`claims`/`claims_app`,
`notification`/`notification_app`, created by `docker/postgres/init`), and neither login can connect
to the other database. Flyway owns each schema and Hibernate only validates it. Integration tests run
against the same image with Testcontainers.

## Alternatives
- H2 in memory: a different SQL dialect, no `SKIP LOCKED`, and data lost on restart.
- One shared database: invites coupling between the services.

## Consequences
- Real constraints and transactions in both the tests and the demo; each service owns its data.
- Docker is required to run the integration tests.
