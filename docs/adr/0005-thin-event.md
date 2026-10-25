# 0005. Thin, versioned ClaimSubmitted event

**Status:** Accepted

## Context
The consumer needs a little claim data to write the email. The event crosses a broker and should
carry as little personal information as possible, and the services must not share a database.

## Decision
`ClaimSubmittedEvent` v1 carries the event ID, type, version, time and correlation ID, plus only the
claim number, first name, email, loss type and date of loss. The description, address, phone and
policy number stay in the claims database. Fields may be added within v1, and the consumer ignores
unknown fields. Breaking changes publish a new version.

## Alternatives
- Fat event with the whole claim: more personal information on the broker for no benefit.
- Event with an ID only, and the consumer calls the API back: couples the services and needs an
  internal read API.

## Consequences
- No shared database and no callback; less personal information in transit.
- `ClaimSubmittedEventTest` pins the JSON shape, so a contract change is always deliberate.
