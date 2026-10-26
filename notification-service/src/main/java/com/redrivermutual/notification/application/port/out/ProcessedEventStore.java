package com.redrivermutual.notification.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Remembers which events have produced an email (idempotent consumer, ADR-0003). */
public interface ProcessedEventStore {

    /**
     * Records the event as processed, atomically with respect to other consumers.
     *
     * @return {@code true} if this call recorded it, {@code false} if it was already recorded
     */
    boolean markProcessed(UUID eventId, String claimNumber, Instant processedAt);
}
