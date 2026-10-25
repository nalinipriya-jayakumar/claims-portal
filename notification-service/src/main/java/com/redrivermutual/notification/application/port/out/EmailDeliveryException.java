package com.redrivermutual.notification.application.port.out;

/** The mail server could not be reached or refused the message; worth retrying. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
