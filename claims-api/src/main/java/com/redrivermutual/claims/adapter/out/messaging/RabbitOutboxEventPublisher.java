package com.redrivermutual.claims.adapter.out.messaging;

import com.redrivermutual.claims.adapter.out.outbox.OutboxEventPublisher;
import com.redrivermutual.claims.adapter.out.outbox.OutboxMessage;
import com.redrivermutual.events.ClaimEventsMessaging;
import com.redrivermutual.events.ClaimSubmittedEvent;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes outbox messages to the {@code claims.events} exchange and only reports success once
 * RabbitMQ has both <em>confirmed</em> the message (it is persisted) and <em>routed</em> it to at
 * least one queue. A message nobody is bound to receive is a failure, not a success, so it is
 * retried rather than silently lost.
 */
@Component
class RabbitOutboxEventPublisher implements OutboxEventPublisher {

    private static final Map<String, String> ROUTING_KEYS = Map.of(
            ClaimSubmittedEvent.EVENT_TYPE, ClaimEventsMessaging.CLAIM_SUBMITTED_ROUTING_KEY);

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    RabbitOutboxEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = properties.confirmTimeout();
    }

    @Override
    public void publish(OutboxMessage outboxMessage) throws PublishFailedException {
        String routingKey = ROUTING_KEYS.get(outboxMessage.eventType());
        if (routingKey == null) {
            throw new PublishFailedException("No routing key for event type " + outboxMessage.eventType());
        }

        CorrelationData confirmation = new CorrelationData(outboxMessage.eventId().toString());
        try {
            rabbitTemplate.send(ClaimEventsMessaging.EXCHANGE, routingKey, toAmqpMessage(outboxMessage), confirmation);
            CorrelationData.Confirm confirm = confirmation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new PublishFailedException("Broker rejected the message: " + confirm.getReason());
            }
            if (confirmation.getReturned() != null) {
                throw new PublishFailedException("No queue is bound for routing key " + routingKey);
            }
        } catch (AmqpException e) {
            throw new PublishFailedException("Broker unavailable", e);
        } catch (TimeoutException e) {
            throw new PublishFailedException("No publisher confirm within " + confirmTimeout, e);
        } catch (ExecutionException e) {
            throw new PublishFailedException("Publisher confirm failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Interrupted while waiting for publisher confirm", e);
        }
    }

    private static Message toAmqpMessage(OutboxMessage outboxMessage) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        // The event ID doubles as the AMQP message ID: the consumer de-duplicates on it.
        properties.setMessageId(outboxMessage.eventId().toString());
        properties.setType(outboxMessage.eventType());
        properties.setTimestamp(Date.from(outboxMessage.occurredAt()));
        properties.setHeader(ClaimEventsMessaging.HEADER_EVENT_TYPE, outboxMessage.eventType());
        properties.setHeader(ClaimEventsMessaging.HEADER_EVENT_VERSION, outboxMessage.eventVersion());
        if (outboxMessage.correlationId() != null) {
            properties.setHeader(ClaimEventsMessaging.HEADER_CORRELATION_ID, outboxMessage.correlationId());
        }
        return new Message(outboxMessage.payload().getBytes(StandardCharsets.UTF_8), properties);
    }
}
