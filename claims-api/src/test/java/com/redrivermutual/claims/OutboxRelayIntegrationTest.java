package com.redrivermutual.claims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrivermutual.claims.adapter.in.web.CorrelationIdFilter;
import com.redrivermutual.claims.adapter.out.outbox.OutboxEventEntity;
import com.redrivermutual.claims.adapter.out.outbox.OutboxEventRepository;
import com.redrivermutual.claims.adapter.out.outbox.OutboxRelay;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.events.ClaimEventsMessaging;
import com.redrivermutual.events.ClaimSubmittedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Outbox to RabbitMQ against real containers: a committed claim reaches a bound queue with its
 * event ID, type and correlation ID, and an event nobody can receive is never marked published.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxRelayIntegrationTest {

    private static final String TEST_QUEUE = "test.claim-submitted";

    @Autowired
    private SubmitClaimUseCase submitClaim;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final TopicExchange exchange = new TopicExchange(ClaimEventsMessaging.EXCHANGE);
    private final Queue queue = new Queue(TEST_QUEUE, false, false, false);
    private final Binding binding = BindingBuilder.bind(queue).to(exchange)
            .with(ClaimEventsMessaging.CLAIM_SUBMITTED_ROUTING_KEY);

    /** Stands in for the notification service, which owns the real queue from slice 4. */
    @BeforeEach
    void bindTestQueue() {
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @AfterEach
    void deleteTestQueue() {
        amqpAdmin.deleteQueue(TEST_QUEUE);
    }

    @Test
    void committedClaimIsPublishedWithItsEventIdTypeAndCorrelationId() throws Exception {
        Claim claim = submitWithCorrelationId("it-correlation-1");
        OutboxEventEntity row = outboxRow(claim);

        relayUntilPublished(row.getId());

        Message message = receive(row.getId());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getType()).isEqualTo(ClaimSubmittedEvent.EVENT_TYPE);
        assertThat((String) message.getMessageProperties().getHeader(ClaimEventsMessaging.HEADER_CORRELATION_ID))
                .isEqualTo("it-correlation-1");
        assertThat((Integer) message.getMessageProperties().getHeader(ClaimEventsMessaging.HEADER_EVENT_VERSION))
                .isEqualTo(1);

        ClaimSubmittedEvent event = objectMapper.readValue(message.getBody(), ClaimSubmittedEvent.class);
        assertThat(event.eventId()).isEqualTo(row.getId());
        assertThat(event.correlationId()).isEqualTo("it-correlation-1");
        assertThat(event.data().claimNumber()).isEqualTo(claim.claimNumber().value());

        assertThat(outboxRow(claim).getPublishedAt()).isNotNull();
    }

    @Test
    void eventWithNoBoundQueueStaysUnpublishedAndIsDeliveredOnceAQueueExists() {
        amqpAdmin.removeBinding(binding);
        Claim claim = submitWithCorrelationId("it-correlation-2");

        relay.publishPendingBatch();
        assertThat(outboxRow(claim).getPublishedAt()).as("unroutable: must not be marked published").isNull();

        amqpAdmin.declareBinding(binding);
        relayUntilPublished(outboxRow(claim).getId());

        assertThat(receive(outboxRow(claim).getId())).isNotNull();
    }

    private Claim submitWithCorrelationId(String correlationId) {
        ClaimDetails valid = validDetails();
        ClaimDetails details = new ClaimDetails(valid.customer(), valid.policyNumber(),
                LocalDate.now(WINNIPEG).minusDays(1), valid.lossType(), valid.propertyAddress(), valid.description());
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            return submitClaim.submit(SubmitClaimCommand.withoutKey(details)).claim();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private OutboxEventEntity outboxRow(Claim claim) {
        return outboxEvents.findByAggregateId(claim.claimNumber().value()).getFirst();
    }

    /** Other test classes share the database, so older pending rows may be relayed first. */
    private void relayUntilPublished(UUID eventId) {
        for (int run = 0; run < 20; run++) {
            relay.publishPendingBatch();
            if (outboxEvents.findById(eventId).orElseThrow().getPublishedAt() != null) {
                return;
            }
        }
        fail("Event %s was not published", eventId);
    }

    private Message receive(UUID eventId) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Message message = rabbitTemplate.receive(TEST_QUEUE, 2_000);
            if (message == null) {
                break;
            }
            if (eventId.toString().equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return fail("No message with id %s on %s", eventId, TEST_QUEUE);
    }
}
