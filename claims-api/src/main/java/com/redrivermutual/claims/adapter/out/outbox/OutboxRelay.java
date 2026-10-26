package com.redrivermutual.claims.adapter.out.outbox;

import com.redrivermutual.claims.adapter.in.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Moves committed events from the outbox table to the broker (ADR-0002).
 *
 * <p>Each run locks a batch of unpublished rows, publishes them oldest first and marks each
 * one published, all in one transaction. On the first failure it stops, so events keep their
 * order and the rest wait for the next run. Delivery is at-least-once: if the process dies after
 * a publish but before the commit, the row is published again later, and the consumer's
 * de-duplication on event ID absorbs the repeat (ADR-0003).
 *
 * <p>Trade-off: the row locks are held while waiting for broker confirms. With small batches
 * and a local broker that is milliseconds; at higher volume, CDC (Debezium) replaces polling.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int batchSize;

    OutboxRelay(OutboxEventRepository repository,
                OutboxEventPublisher publisher,
                PlatformTransactionManager transactionManager,
                Clock clock,
                OutboxRelayProperties properties) {
        this.repository = repository;
        this.publisher = publisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = properties.batchSize();
    }

    /** @return how many events were published in this run */
    public int publishPendingBatch() {
        Integer published = transaction.execute(status -> publishLocked(repository.lockUnpublishedBatch(batchSize)));
        return published == null ? 0 : published;
    }

    private int publishLocked(List<OutboxEventEntity> pending) {
        int published = 0;
        for (OutboxEventEntity event : pending) {
            // Log lines for this event carry the correlation ID of the request that created it.
            try (var ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY,
                    event.getCorrelationId() == null ? "" : event.getCorrelationId())) {
                if (!publish(event)) {
                    log.warn("{} event(s) left for the next run", pending.size() - published);
                    break;
                }
                published++;
            }
        }
        return published;
    }

    private boolean publish(OutboxEventEntity event) {
        try {
            publisher.publish(OutboxMessage.from(event));
        } catch (OutboxEventPublisher.PublishFailedException e) {
            log.warn("Could not publish {} eventId={} claimNumber={}: {}",
                    event.getEventType(), event.getId(), event.getAggregateId(), e.getMessage());
            return false;
        }
        event.markPublished(clock.instant());
        log.info("Published {} eventId={} claimNumber={}", event.getEventType(), event.getId(), event.getAggregateId());
        return true;
    }
}
