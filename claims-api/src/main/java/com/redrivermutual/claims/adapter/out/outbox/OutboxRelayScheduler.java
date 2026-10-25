package com.redrivermutual.claims.adapter.out.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the relay on a fixed delay: the next run starts {@code interval} after the previous one
 * finished, so runs never overlap within one instance.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "claims.outbox.relay.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler {

    private final OutboxRelay relay;

    OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.relay.interval:1s}", initialDelayString = "${claims.outbox.relay.interval:1s}")
    void relay() {
        relay.publishPendingBatch();
    }
}
