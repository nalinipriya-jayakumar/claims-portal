# Home Insurance Claim Submission Portal

A customer submits a home insurance claim in a web form, gets a claim number straight away, and
receives a confirmation email containing that claim number. The email is generated and sent by a
separate service that reacts to a `ClaimSubmitted` event on a message queue, never on the
customer's request.

Built for the Red River Mutual Software Developer III technical challenge.

| | |
|---|---|
| Stack | Java 21, Spring Boot 3.5, React 19 + TypeScript, PostgreSQL 16, RabbitMQ 4.1, Mailpit, Docker Compose |
| Architecture | [docs/architecture.md](docs/architecture.md) · decisions in [docs/adr](docs/adr/README.md) |
| API contract | [claims-api/src/main/resources/openapi/claims-api.yaml](claims-api/src/main/resources/openapi/claims-api.yaml): the source of truth; server interface, models and portal types are generated from it ([ADR-0010](docs/adr/0010-contract-driven-api.md)) |

## Quick start

Prerequisite: **Docker** with Docker Compose v2 (Docker Desktop on Windows or macOS). Nothing else
is needed to run the system: no JDK, no Node, no accounts, no secrets.

```bash
git clone <repository-url> claims-portal
cd claims-portal
docker compose up -d --build
```

The first build downloads base images and dependencies and takes a few minutes. Compose starts the
infrastructure first and each application only once what it depends on is healthy. When
`docker compose ps` shows every service as `healthy`:

| Open | What it is |
|---|---|
| http://localhost:3000 | **Claim portal** (the customer's view) |
| http://localhost:8025 | **Mailpit inbox**: every email the system sends lands here |
| http://localhost:15672 | RabbitMQ management (user `claims`, password `local-dev-only`) |
| http://localhost:8080/actuator/health | Claims API health (direct, for operators) |

Check the whole workflow from the command line (Git Bash on Windows):

```bash
./scripts/smoke-test.sh
```

It submits a claim through the portal's edge, checks that an invalid one is rejected, and waits for
the matching email in Mailpit. It prints `PASS` with the claim number.

Stop with `docker compose down`, or `docker compose down -v` to delete all data as well.

## Five-minute demo

1. **Submit an empty form.** Open http://localhost:3000 and click **Submit claim**. Focus moves to
   an error summary listing every missing field; each link jumps to its field. Errors also appear
   next to each field as you leave it.
2. **Submit a real claim.** Fill in the form with any `@example.com` address and a date of loss in
   the last year. The button is disabled while the request is in flight. The confirmation
   page shows the claim number, for example `CLM-2026-000007`, and asks you to check your email.
3. **See the email.** Open http://localhost:8025. A message "We received your claim
   CLM-2026-000007" arrived within a second or two, addressed to you, with the claim number, loss
   details and next steps.
4. **Show the server is the source of truth.** Bypass the form:
   ```bash
   curl -s -X POST localhost:3000/api/v1/claims -H 'Content-Type: application/json' \
        -d '{"policyNumber":"HO-1","dateOfLoss":"2099-01-01"}'
   ```
   The answer is `400 application/problem+json` with one error per field and a `correlationId`,
   and nothing is saved or published.
5. **Show the email really is asynchronous.** Stop the consumer, submit a claim, start it again:
   ```bash
   docker compose stop notification-service
   # submit a claim in the portal: it is accepted with a claim number
   docker compose start notification-service
   ```
   The email arrives a few seconds after the restart. The claim was never at risk.
6. **Follow one claim through the logs.** Every log line for a claim carries the same correlation ID,
   from the browser's request to the email:
   ```bash
   docker compose logs claims-api notification-service | grep <correlationId>
   ```
7. **Look at the operational view.** RabbitMQ (http://localhost:15672 → Queues) shows
   `claims.submitted` and its dead-letter queue `claims.submitted.dlq`.
   http://localhost:8080/actuator/prometheus shows `claims_submitted_total` and `outbox_backlog`;
   http://localhost:8081/actuator/prometheus shows `notification_emails_total{outcome="sent"}`.

## How it works

```text
Browser ─▶ Nginx (portal + /api proxy) ─▶ Claims API ──one transaction──▶ PostgreSQL (claim + outbox_event)
                                              │
                                              └─ Outbox relay (every 1 s) ─▶ RabbitMQ ─▶ Notification Service ─▶ Mailpit (SMTP)
                                                                                              └─▶ PostgreSQL (processed_event)
```

- **Two services, joined only by the broker.** `claims-api` and `notification-service` are separate
  Spring Boot applications. They share no database and no code except the versioned event contract
  in `event-contracts` (ADR-0001).
- **No lost or phantom events.** The claim and its event are saved in one transaction; a relay
  publishes the event afterwards and only marks it sent once RabbitMQ confirms it and has routed it to a
  queue (transactional outbox, ADR-0002).
- **One email per claim.** Delivery is at-least-once, so the consumer records each event ID in the
  same transaction as the send and skips repeats (ADR-0003).
- **Failures are retried or parked, never dropped.** SMTP failures are retried with exponential
  backoff, then moved to a dead-letter queue; malformed events go there immediately.
- **Contract-driven API.** The OpenAPI document is the source of truth. On every build the Maven
  plugin `openapi-generator` generates the `ClaimsApi` interface and the request/response models,
  with the contract's constraints as Bean Validation annotations. The controller implements that
  interface, and the portal's TypeScript types are generated from the same file. Drift fails the build (ADR-0010).
- **Ports and adapters inside each service.** Business rules and use cases are plain Java, tested
  without Spring; adapters handle HTTP, JPA/JDBC, RabbitMQ, SMTP and templates.

More detail, including diagrams and the failure table, is in [docs/architecture.md](docs/architecture.md).

## Repository layout

```text
claims-api/              Spring Boot: REST API, validation, claim numbers, persistence, outbox relay
                         OpenAPI contract in src/main/resources/openapi; generated code in target/generated-sources
notification-service/    Spring Boot: queue listener, idempotency, email rendering and sending
event-contracts/         ClaimSubmittedEvent v1 and broker names (the only shared code)
claim-portal-ui/         React + TypeScript portal; Nginx config for the container
docker/                  Java service Dockerfile, Postgres init script (one database per service)
docker-compose.yml       The whole system
scripts/smoke-test.sh    End-to-end check against the running stack
docs/                    Architecture overview and ADRs
.github/workflows/ci.yml Build, test, secret scan, images, end-to-end
.run/                    Shared IntelliJ IDEA run configurations
http/claims-api.http     Ready-made API requests for IntelliJ's HTTP client
```

## Developing locally

Prerequisites for development: **JDK 21**, **Node.js 24 LTS** (or 20.19+), and Docker.
Maven is not required; use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

1. Start only the infrastructure:
   ```bash
   docker compose up -d postgres rabbitmq mailpit
   ```
2. Run the services. In **IntelliJ IDEA**, open the folder (it imports the Maven project) and use the
   shared run configurations: `All services` starts both Spring Boot apps, and
   `Portal dev server (npm)` starts the portal. From a terminal instead (the first command installs
   the shared `event-contracts` module into your local Maven repository, once):
   ```bash
   ./mvnw -q install -DskipTests
   ./mvnw -pl claims-api spring-boot:run
   ./mvnw -pl notification-service spring-boot:run
   cd claim-portal-ui && npm install && npm run dev
   ```
3. Open http://localhost:5173. The Vite dev server forwards `/api` to the Claims API on :8080, so
   the browser uses one origin in development too.

Don't run the full Compose stack and the IDE apps at the same time; both use ports 8080 and 8081.

### Changing the API contract

The contract is [claims-api/src/main/resources/openapi/claims-api.yaml](claims-api/src/main/resources/openapi/claims-api.yaml).
Change it first, then follow the compiler:

1. `./mvnw -pl claims-api compile` regenerates `ClaimsApi` and the models into
   `claims-api/target/generated-sources/openapi` (in IntelliJ: Maven tool window → **Reload**, or
   **Generate Sources and Update Folders**). Never edit generated code.
2. `cd claim-portal-ui && npm run generate:api` regenerates the portal's types; commit the result.
   CI fails if it is stale.
3. Fix whatever no longer compiles or type-checks, and update `FieldMessages` or the domain enums if the
   change adds fields or values. `ContractConformanceTest` points at any enum that is out of step.

## Tests

| Command | What runs |
|---|---|
| `./mvnw verify` | All Java modules: 143 tests. Generates the API code from the contract first. Unit tests, Spring slice tests, and integration tests against real PostgreSQL, RabbitMQ and Mailpit started by Testcontainers (Docker must be running) |
| `./mvnw -pl claims-api test` | One module |
| `cd claim-portal-ui && npm test` | Portal: 29 tests with Vitest and Testing Library |
| `cd claim-portal-ui && npm run typecheck` | TypeScript in strict mode |
| `./scripts/smoke-test.sh` | End to end against the running Compose stack |

**Approach.** Most tests are fast unit tests of the domain and use cases, run without Spring
(`DateOfLossPolicyTest`, `SubmitClaimServiceTest`, `SendClaimConfirmationServiceTest`).
Every validation rule and its error code is covered in `ClaimRequestValidatorTest`; the HTTP
contract (status codes, problem+json, headers, no echoed values, no stack traces) in
`ClaimControllerTest`. Integration tests run against the same container images as production, because the
guarantees that matter here (transactions, `SKIP LOCKED`, publisher confirms, dead-lettering)
cannot be shown with mocks.

**The tests that matter most:**

| Test | Proves |
|---|---|
| `ClaimConfirmationFlowIntegrationTest` | An event produces exactly one email with the claim number; a duplicate delivery sends nothing more; a malformed message is dead-lettered |
| `SubmitClaimRollbackIntegrationTest` | If recording the event fails, the claim is not saved either |
| `OutboxRelayIntegrationTest` | Events reach the broker with event ID and correlation ID; an unroutable event is not marked published and is delivered once a queue exists |
| `SmtpOutageIntegrationTest` | With SMTP down: retried, then dead-lettered, and not marked processed, so a replay still sends |
| `ClaimControllerTest` | 201/400/413/415/500 behaviour and that errors never contain submitted values |
| `ContractConformanceTest` | The domain's loss types, provinces, statuses and the API's error codes match the OpenAPI contract exactly |
| `DuplicateSubmissionIntegrationTest` | A retry with the same `Idempotency-Key` returns the original claim and creates no second claim, event or email, even with four requests at once; a repeat report of the same incident is saved with its duplicate flag |
| `App.test.tsx` | Error summary and focus, disabled button while submitting, confirmation page, server errors mapped to fields |

CI (`.github/workflows/ci.yml`) runs all of the above on every push and pull request, plus a
gitleaks secret scan and an end-to-end job that builds the images, starts the stack and runs the smoke test.

## Configuration and secrets

- Nothing secret is committed. Every credential in `docker-compose.yml` and `application.yml` has
  a **local-only** default (`local-dev-only`) so a fresh clone runs without setup.
- To change them, copy `.env.example` to `.env` (gitignored) and edit it. Postgres applies its
  passwords only when its volume is first created, so run `docker compose down -v` afterwards.
- The applications read everything from environment variables (`CLAIMS_DB_*`, `NOTIFICATION_DB_*`,
  `RABBITMQ_*`, `SMTP_*`). In production these come from a secrets manager.
- Business rules that are assumptions are configurable: `claims.rules.zone` (America/Winnipeg) and
  `claims.rules.date-of-loss-max-age-days` (365).

## Assumptions

The brief leaves these open; each is a deliberate decision.

| # | Assumption |
|---|---|
| A-01 | Policy numbers look like `HO-12345678`. Format is checked; there is no policy lookup. |
| A-02 | The policy is not verified as active; a `PolicyVerifier` port is the production extension. |
| A-03 | The date of loss cannot be in the future or more than 365 days ago, judged on the Winnipeg calendar. |
| A-04 | Submission is anonymous, as for a public first-notice-of-loss form. |
| A-05 | Save and publish happen atomically through a transactional outbox. |
| A-06 | If the email fails, the claim is still accepted; the email is retried, then dead-lettered. |
| A-07 | Retries are made safe with an `Idempotency-Key` header: the same submission sent again returns the original claim. A new report of an already-reported incident (same policy, date of loss and loss type) is accepted and flagged internally as a possible duplicate for the claims team, never rejected (ADR-0011). |
| A-08 | PostgreSQL in Docker, rather than in-memory storage. |
| A-09 | Data retention and privacy obligations (PIPEDA) are out of scope; listed for production. |
| A-10 | No file upload (it would need object storage, size and type limits, and malware scanning). |
| A-11 | English only; English/French support is a production requirement in Canada. |

There is deliberately no endpoint to look up a claim: without authentication, sequential claim
numbers would let anyone enumerate other people's claims.

## What I would add for production

- **Security:** TLS everywhere (edge, database, broker, SMTP); a CAPTCHA and rate limiting on
  the form; a secrets manager; image and dependency scanning in the pipeline; a WAF in front of the portal.
- **Business:** policy verification against the policy system; a claims-team view of claims flagged as possible duplicates; a purge job for idempotency keys older than 24 hours;
  a claim status page behind customer authentication; file upload with scanning; English/French.
- **Email:** a transactional email provider with bounce and complaint handling; per-message
  idempotency keys to remove the rare duplicate after a crash (ADR-0003).
- **Operations:** OpenTelemetry tracing across the HTTP and broker hops; dashboards and alerts on
  `outbox_backlog` and dead letters; an operator tool to replay the dead-letter queue; data retention
  and purge jobs; CDC (Debezium) instead of polling if volume grows.
- **Deployment:** the same images on Kubernetes or OpenShift with managed PostgreSQL and RabbitMQ,
  two or more API replicas, and notification consumers scaled on queue depth.

## Troubleshooting

- **Integration tests fail with "Could not find a valid Docker environment".** Docker must be
  running. With Docker Engine 29 or later, Testcontainers older than 1.21.4 is rejected; the parent
  POM already overrides the version.
- **`npm` is blocked in PowerShell** ("running scripts is disabled"). Run
  `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once, or use `npm.cmd`.
- **Port already in use.** Another copy of the services is running, either in Compose or in the IDE.
- **The Claims API logs "Could not publish ... No queue is bound".** This is expected until the
  Notification Service has started once and declared its queue. Events wait in the outbox and are
  delivered then.

## AI-assisted development

AI tools were used throughout, and every part of the result was reviewed, run and tested before it
was accepted.

- **Tools.** Claude (Anthropic), through Claude Code in the Claude desktop app with IntelliJ IDEA.
- **Analysis and design.** From the challenge brief, Claude drafted a requirements analysis and a
  comparison of architecture options as working documents. I reviewed them, chose the event-driven
  two-service design and the decisions recorded in the ADRs, and used them as the specification for
  the build. The design as built is in [docs/architecture.md](docs/architecture.md) and [docs/adr](docs/adr/README.md).
- **Implementation.** The code was written with Claude in eight agreed slices (foundations, API
  validation, persistence and outbox, relay, notification service, portal, containers, documentation).
  I reviewed each slice and confirmed it before the next one started.
- **Validation.** Each slice was built and tested before review: 172 automated tests in total. Beyond
  the tests, every slice was run against the real containers. This found and fixed real defects:
  an entity/schema type mismatch caught by Hibernate validation, a contract bug in which YAML read the province code `ON` as `true` (it would have rejected every claim from Ontario, and was caught when the code started being generated from the contract), Testcontainers' incompatibility
  with Docker Engine 29, a correlation ID missing from a warning log, and an error-summary focus bug
  that only showed in a real browser, not in jsdom.
- **Security review.** No credentials are committed (gitleaks scan, local-only defaults). Logs from the
  whole test suite were searched for customer email addresses and names, and none were found. Spring's
  default dead-letter logging was replaced because it would have logged message bodies. Error responses
  are tested to never echo submitted values, and `npm audit` reports no known vulnerabilities in
  runtime dependencies.
- **Accountability.** I can explain and defend every part of this solution; the ADRs and
  `docs/architecture.md` record the reasoning and trade-offs behind it.
