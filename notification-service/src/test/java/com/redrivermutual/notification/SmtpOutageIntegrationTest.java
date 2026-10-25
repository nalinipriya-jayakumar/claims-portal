package com.redrivermutual.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrivermutual.events.ClaimSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redrivermutual.notification.NotificationFixtures.event;
import static com.redrivermutual.notification.NotificationFixtures.uniqueClaimNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * AC-06: with the mail server unreachable, the event is retried and then dead-lettered, and it
 * is not recorded as processed, so replaying it later will still send the email.
 *
 * <p>Uses its own queue, fed directly through the default exchange, so no other test context
 * can consume this event; the context is closed afterwards.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=1",
        "notification.messaging.queue=" + SmtpOutageIntegrationTest.QUEUE,
        "notification.messaging.max-attempts=3"
})
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class SmtpOutageIntegrationTest {

    static final String QUEUE = "test.smtp-outage";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void eventIsRetriedThenDeadLetteredAndNotMarkedProcessed() throws Exception {
        ClaimSubmittedEvent event = event(UUID.randomUUID(), uniqueClaimNumber(), "jane.oneil@example.com");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        rabbitTemplate.send("", QUEUE, new Message(objectMapper.writeValueAsBytes(event), properties));

        Message deadLetter = receive(QUEUE + ".dlq", event.eventId().toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deaths = (List<Map<String, Object>>) deadLetter.getMessageProperties()
                .getHeaders().get("x-death");
        assertThat(deaths).first().satisfies(death -> assertThat(death).containsEntry("queue", QUEUE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?",
                Integer.class, event.eventId())).as("rolled back with the failed send").isZero();
    }

    private Message receive(String queue, String messageId) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 1_000);
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return fail("Message %s did not reach %s", messageId, queue);
    }
}
