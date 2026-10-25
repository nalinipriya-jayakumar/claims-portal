package com.redrivermutual.claims.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrivermutual.claims.adapter.in.web.CorrelationIdFilter;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.events.ClaimSubmittedEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes the ClaimSubmitted event to the outbox table. Maps the domain claim to the thin,
 * versioned contract here, so the use case knows nothing about the wire format.
 */
@Component
class JpaOutboxAdapter implements OutboxPort {

    static final String AGGREGATE_TYPE = "Claim";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    JpaOutboxAdapter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** MANDATORY: an outbox row written outside the claim's transaction would defeat the pattern. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordClaimSubmitted(Claim claim) {
        ClaimSubmittedEvent event = toEvent(claim, MDC.get(CorrelationIdFilter.MDC_KEY));
        repository.save(new OutboxEventEntity(
                event.eventId(),
                AGGREGATE_TYPE,
                claim.claimNumber().value(),
                event.eventType(),
                event.eventVersion(),
                toJson(event),
                event.correlationId(),
                event.occurredAt()));
    }

    static ClaimSubmittedEvent toEvent(Claim claim, String correlationId) {
        return ClaimSubmittedEvent.v1(
                UUID.randomUUID(),
                claim.submittedAt(),
                correlationId,
                new ClaimSubmittedEvent.Data(
                        claim.claimNumber().value(),
                        claim.details().customer().firstName(),
                        claim.details().customer().email(),
                        claim.details().lossType().name(),
                        claim.details().dateOfLoss()));
    }

    private String toJson(ClaimSubmittedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + event.eventType(), e);
        }
    }
}
