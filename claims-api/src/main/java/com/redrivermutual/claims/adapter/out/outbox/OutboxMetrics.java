package com.redrivermutual.claims.adapter.out.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * {@code outbox.backlog}: events committed but not yet published. It should sit near zero; a
 * rising value means the broker is down or no consumer queue is bound, and is the alert to set.
 * Measured with one indexed count query each time metrics are scraped.
 */
@Configuration
class OutboxMetrics {

    OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        Gauge.builder("outbox.backlog", repository, OutboxEventRepository::countUnpublished)
                .description("Outbox events waiting to be published to the broker")
                .register(registry);
    }
}
