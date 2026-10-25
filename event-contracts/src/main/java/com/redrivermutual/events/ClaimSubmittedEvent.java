package com.redrivermutual.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Published once for every committed claim. This record is the whole contract between the
 * Claims API and the Notification Service; there is no shared database and no callback.
 *
 * <p>The event is deliberately thin (ADR-005): it carries only what the confirmation email needs.
 * The description and full address stay in the claims database and never travel on the broker.
 *
 * <p>Compatibility rule: within a version, fields may be added but never removed or renamed.
 * Breaking changes publish a new {@link #eventVersion()} alongside the old one.
 *
 * @param eventId       unique per event; the consumer de-duplicates on it (ADR-003)
 * @param correlationId the originating request's ID, for tracing across services; may be null
 */
public record ClaimSubmittedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        Data data) {

    public static final String EVENT_TYPE = "ClaimSubmitted";
    public static final int CURRENT_VERSION = 1;

    public ClaimSubmittedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(data, "data");
    }

    public static ClaimSubmittedEvent v1(UUID eventId, Instant occurredAt, String correlationId, Data data) {
        return new ClaimSubmittedEvent(eventId, EVENT_TYPE, CURRENT_VERSION, occurredAt, correlationId, data);
    }

    /**
     * @param lossType a plain string so the contract does not depend on either service's enums
     */
    public record Data(
            String claimNumber,
            String customerFirstName,
            String customerEmail,
            String lossType,
            LocalDate dateOfLoss) {

        public Data {
            Objects.requireNonNull(claimNumber, "claimNumber");
            Objects.requireNonNull(customerEmail, "customerEmail");
        }

        /** Excludes the customer's name and email so a logged event cannot leak PII. */
        @Override
        public String toString() {
            return "Data[claimNumber=" + claimNumber + ", lossType=" + lossType + ", dateOfLoss=" + dateOfLoss + "]";
        }
    }
}
