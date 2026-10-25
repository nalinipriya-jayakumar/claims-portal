-- Claims API schema, version 1.
-- The claim and its outbox row are written in one transaction (ADR-002), so an event exists
-- if and only if the claim committed.

-- Claim numbers: CLM-{year}-{sequence}. The sequence is global, not reset each year, so numbers
-- stay unique across years and API replicas without locking. Gaps after a rollback are expected.
CREATE SEQUENCE claim_number_seq AS integer START WITH 1 MAXVALUE 999999 NO CYCLE;

CREATE TABLE claim (
    id              uuid          PRIMARY KEY,
    claim_number    varchar(15)   NOT NULL,
    first_name      varchar(50)   NOT NULL,
    last_name       varchar(50)   NOT NULL,
    email           varchar(254)  NOT NULL,
    phone           varchar(10),
    policy_number   varchar(20)   NOT NULL,
    date_of_loss    date          NOT NULL,
    loss_type       varchar(20)   NOT NULL,
    street          varchar(100)  NOT NULL,
    city            varchar(60)   NOT NULL,
    province        varchar(2)    NOT NULL,
    postal_code     varchar(7)    NOT NULL,
    description     varchar(2000) NOT NULL,
    status          varchar(20)   NOT NULL,
    submitted_at    timestamptz   NOT NULL,
    version         bigint        NOT NULL,

    -- The final guard on uniqueness, whatever the generator does (FR-08).
    CONSTRAINT uk_claim_claim_number UNIQUE (claim_number),
    CONSTRAINT ck_claim_claim_number CHECK (claim_number ~ '^CLM-[0-9]{4}-[0-9]{6}$'),
    CONSTRAINT ck_claim_loss_type CHECK (loss_type IN ('FIRE', 'WATER', 'WIND_HAIL', 'THEFT', 'VANDALISM', 'OTHER')),
    CONSTRAINT ck_claim_status CHECK (status IN ('SUBMITTED'))
);

CREATE TABLE outbox_event (
    id              uuid          PRIMARY KEY,   -- equals the event ID the consumer de-duplicates on
    aggregate_type  varchar(50)   NOT NULL,
    aggregate_id    varchar(64)   NOT NULL,      -- the claim number
    event_type      varchar(100)  NOT NULL,
    event_version   integer       NOT NULL,
    payload         jsonb         NOT NULL,
    correlation_id  varchar(64),
    created_at      timestamptz   NOT NULL,
    published_at    timestamptz                  -- null until the relay (slice 3) publishes it
);

-- The relay only ever reads unpublished rows, oldest first; a partial index keeps that cheap
-- however many published rows accumulate.
CREATE INDEX ix_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
