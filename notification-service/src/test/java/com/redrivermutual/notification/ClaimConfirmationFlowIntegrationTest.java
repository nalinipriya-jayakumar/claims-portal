package com.redrivermutual.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrivermutual.events.ClaimEventsMessaging;
import com.redrivermutual.events.ClaimSubmittedEvent;
import com.redrivermutual.notification.MailpitTestConfiguration.CapturedEmail;
import com.redrivermutual.notification.MailpitTestConfiguration.Mailpit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.redrivermutual.notification.NotificationFixtures.event;
import static com.redrivermutual.notification.NotificationFixtures.uniqueClaimNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

/**
 * The asynchronous half of the workflow against real RabbitMQ, PostgreSQL and Mailpit:
 * a ClaimSubmitted event on the exchange becomes exactly one email containing the claim number.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MailpitTestConfiguration.class})
class ClaimConfirmationFlowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String DEAD_LETTER_QUEUE = "claims.submitted.dlq";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publishedEventProducesOneEmailToTheCustomerWithTheClaimNumber() throws Exception {
        String claimNumber = uniqueClaimNumber();
        UUID eventId = UUID.randomUUID();

        publish(event(eventId, claimNumber, "jane.oneil@example.com"));

        CapturedEmail email = await().atMost(TIMEOUT)
                .until(() -> mailpit.withSubjectContaining(claimNumber), emails -> emails.size() == 1)
                .getFirst();
        assertThat(email.to()).containsExactly("jane.oneil@example.com");
        assertThat(email.text()).contains(claimNumber, "Hello Jane,", "Water damage");
        assertThat(email.html()).contains(claimNumber);
        assertThat(jdbc.queryForObject("SELECT claim_number FROM processed_event WHERE event_id = ?",
                String.class, eventId)).isEqualTo(claimNumber);
    }

    @Test
    void sameEventDeliveredTwiceSendsOnlyOneEmail() throws Exception {
        String claimNumber = uniqueClaimNumber();
        ClaimSubmittedEvent event = event(UUID.randomUUID(), claimNumber, "jane.oneil@example.com");
        String markerClaimNumber = uniqueClaimNumber();

        publish(event);
        publish(event);
        // The queue is FIFO with one consumer per instance: once the marker's email exists, both
        // copies have been handled, and any concurrent copy is blocked by the processed_event row.
        publish(event(UUID.randomUUID(), markerClaimNumber, "marker@example.com"));
        await().atMost(TIMEOUT).until(() -> !mailpit.withSubjectContaining(markerClaimNumber).isEmpty());

        assertThat(mailpit.withSubjectContaining(claimNumber)).hasSize(1);
    }

    @Test
    void malformedMessageGoesStraightToTheDeadLetterQueue() {
        String messageId = UUID.randomUUID().toString();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        rabbitTemplate.send(ClaimEventsMessaging.EXCHANGE, ClaimEventsMessaging.CLAIM_SUBMITTED_ROUTING_KEY,
                new Message("{not json".getBytes(StandardCharsets.UTF_8), properties));

        Message deadLetter = receiveFromDeadLetterQueue(messageId);
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
    }

    private void publish(ClaimSubmittedEvent event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(ClaimEventsMessaging.HEADER_CORRELATION_ID, "it-" + event.eventId());
        rabbitTemplate.send(ClaimEventsMessaging.EXCHANGE, ClaimEventsMessaging.CLAIM_SUBMITTED_ROUTING_KEY,
                new Message(objectMapper.writeValueAsBytes(event), properties));
    }

    private Message receiveFromDeadLetterQueue(String messageId) {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 1_000);
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return fail("Message %s did not reach %s", messageId, DEAD_LETTER_QUEUE);
    }
}
