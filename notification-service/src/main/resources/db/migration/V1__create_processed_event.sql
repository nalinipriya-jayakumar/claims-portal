-- Notification service schema, version 1. Lives in its own database (ADR-007).

-- One row per ClaimSubmitted event that produced an email. The primary key is what turns
-- at-least-once delivery into "one email per claim" (ADR-003): the row is inserted in the same
-- transaction that sends the email, so a redelivered event finds it and is skipped, and a
-- failed send rolls it back so the retry can try again.
CREATE TABLE processed_event (
    event_id      uuid         PRIMARY KEY,
    claim_number  varchar(15)  NOT NULL,
    processed_at  timestamptz  NOT NULL
);
