package com.redrivermutual.notification.adapter.in.messaging;

import com.redrivermutual.notification.application.port.out.EmailDeliveryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

/**
 * Called when a message will not be retried any more. Rejects it without requeue, which makes
 * RabbitMQ move it to the dead-letter queue, and logs one line for operations to act on.
 *
 * <p>Replaces Spring's {@code RejectAndDontRequeueRecoverer}, whose log line includes the message
 * body, and the body carries the customer's name and email address (NFR-06).
 */
class DeadLetteringRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetteringRecoverer.class);

    private final Counter deadLettered;

    DeadLetteringRecoverer(MeterRegistry registry) {
        this.deadLettered = Counter.builder("notification.dead_lettered")
                .description("Messages moved to the dead-letter queue; any increase needs attention")
                .register(registry);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        log.error("Dead-lettering messageId={} reason={}", message.getMessageProperties().getMessageId(), reason(cause));
        log.debug("Dead-letter cause", cause);
        deadLettered.increment();
        throw new ListenerExecutionFailedException("Retries exhausted or message not retryable",
                new AmqpRejectAndDontRequeueException(cause), message);
    }

    /** Only our own exceptions' messages are known to be free of personal information. */
    private static String reason(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof NonRetryableEventException || t instanceof EmailDeliveryException) {
                return t.getMessage();
            }
        }
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
