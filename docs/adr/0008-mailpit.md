# 0008. Mailpit as the SMTP server

**Status:** Accepted

## Context
Email must really be sent, at no cost and without credentials in the repository.

## Decision
Mailpit receives SMTP on port 1025 and shows every message in a web inbox on port 8025. The
notification service sends standard multipart SMTP through `JavaMailSender`, so moving to a real
provider is a configuration change. Integration tests run Mailpit in Testcontainers and assert on
its HTTP API.

## Alternatives
- A real mailbox: needs credentials, risks real delivery, and cannot be shared with reviewers.
- MailHog: no longer maintained. GreenMail: good for tests, but has no inbox for the demo.

## Consequences
- Reviewers see the email without any account. Production needs a transactional email provider
  with TLS, authentication and bounce handling.
