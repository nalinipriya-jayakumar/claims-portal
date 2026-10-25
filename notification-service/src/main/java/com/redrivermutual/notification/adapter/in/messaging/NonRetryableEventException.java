package com.redrivermutual.notification.adapter.in.messaging;

/**
 * The message can never succeed (unreadable, wrong version, invalid fields), so retrying would
 * only delay it: it goes straight to the dead-letter queue. Messages never include field values.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
