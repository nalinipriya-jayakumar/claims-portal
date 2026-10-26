package com.redrivermutual.claims.adapter.out.outbox;

/**
 * Sends one outbox message to the broker. Returning normally means the broker has taken
 * responsibility for the message; anything else must throw so the row stays unpublished.
 * RabbitMQ today; a Kafka adapter would implement the same interface (ADR-0004).
 */
public interface OutboxEventPublisher {

    void publish(OutboxMessage message) throws PublishFailedException;

    class PublishFailedException extends Exception {
        public PublishFailedException(String message) {
            super(message);
        }

        public PublishFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
