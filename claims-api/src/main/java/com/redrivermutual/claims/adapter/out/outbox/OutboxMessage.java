package com.redrivermutual.claims.adapter.out.outbox;

import java.time.Instant;
import java.util.UUID;

/** An outbox row as handed to a publisher: the serialized event plus its envelope. */
public record OutboxMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateId,
        String payload,
        String correlationId,
        Instant occurredAt) {

    static OutboxMessage from(OutboxEventEntity entity) {
        return new OutboxMessage(entity.getId(), entity.getEventType(), entity.getEventVersion(),
                entity.getAggregateId(), entity.getPayload(), entity.getCorrelationId(), entity.getCreatedAt());
    }
}
