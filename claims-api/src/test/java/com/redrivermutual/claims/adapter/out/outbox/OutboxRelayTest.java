package com.redrivermutual.claims.adapter.out.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redrivermutual.claims.ClaimFixtures.FIXED_CLOCK;
import static com.redrivermutual.claims.ClaimFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Relay ordering and failure rules, with the database and broker replaced by test doubles. */
class OutboxRelayTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final OutboxRelay relay = new OutboxRelay(repository, publisher, transactionManager, FIXED_CLOCK,
            new OutboxRelayProperties(false, Duration.ofSeconds(1), 50));

    OutboxRelayTest() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }

    @Test
    void publishesEveryPendingEventOldestFirstAndMarksThemPublished() {
        List<OutboxEventEntity> pending = List.of(event(1), event(2), event(3));
        given(repository.lockUnpublishedBatch(50)).willReturn(pending);

        int published = relay.publishPendingBatch();

        assertThat(published).isEqualTo(3);
        assertThat(publisher.sent).extracting(OutboxMessage::aggregateId)
                .containsExactly("CLM-2026-000001", "CLM-2026-000002", "CLM-2026-000003");
        assertThat(pending).allSatisfy(e -> assertThat(e.getPublishedAt()).isEqualTo(NOW));
    }

    @Test
    void stopsAtTheFirstFailureSoLaterEventsKeepTheirOrder() {
        List<OutboxEventEntity> pending = List.of(event(1), event(2), event(3));
        given(repository.lockUnpublishedBatch(50)).willReturn(pending);
        publisher.failOn = "CLM-2026-000002";

        int published = relay.publishPendingBatch();

        assertThat(published).isEqualTo(1);
        assertThat(pending.get(0).getPublishedAt()).isEqualTo(NOW);
        assertThat(pending.get(1).getPublishedAt()).as("failed: retried next run").isNull();
        assertThat(pending.get(2).getPublishedAt()).as("not attempted after a failure").isNull();
        assertThat(publisher.sent).extracting(OutboxMessage::aggregateId).containsExactly("CLM-2026-000001");
    }

    @Test
    void nothingPendingPublishesNothing() {
        given(repository.lockUnpublishedBatch(50)).willReturn(List.of());

        assertThat(relay.publishPendingBatch()).isZero();
        assertThat(publisher.sent).isEmpty();
    }

    private static OutboxEventEntity event(int sequence) {
        return new OutboxEventEntity(UUID.randomUUID(), "Claim", "CLM-2026-%06d".formatted(sequence),
                "ClaimSubmitted", 1, "{}", "corr-" + sequence, Instant.parse("2026-10-18T14:00:00Z").plusSeconds(sequence));
    }

    private static final class RecordingPublisher implements OutboxEventPublisher {
        private final List<OutboxMessage> sent = new ArrayList<>();
        private String failOn;

        @Override
        public void publish(OutboxMessage message) throws PublishFailedException {
            if (message.aggregateId().equals(failOn)) {
                throw new PublishFailedException("broker unavailable");
            }
            sent.add(message);
        }
    }
}
