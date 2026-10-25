-- Duplicate submissions (ADR-0011).

-- Case 1, retries: one row per Idempotency-Key, written in the same transaction as the claim and
-- its outbox row. The primary key makes a concurrent retry wait for the first request and then
-- replay it. Only a hash of the claim is kept, never its content.
CREATE TABLE idempotency_key (
    idempotency_key      uuid         PRIMARY KEY,
    request_fingerprint  char(64)     NOT NULL,
    claim_number         varchar(15)  NOT NULL REFERENCES claim (claim_number),
    created_at           timestamptz  NOT NULL
);

-- Case 2, a new report of the same incident: accepted, and flagged for the claims team with the
-- earliest matching claim. Internal only; the API never returns it.
ALTER TABLE claim
    ADD COLUMN possible_duplicate_of varchar(15) REFERENCES claim (claim_number);

-- Serves the "earliest claim for this policy, date of loss and loss type" lookup.
CREATE INDEX ix_claim_incident ON claim (policy_number, date_of_loss, loss_type, submitted_at);
