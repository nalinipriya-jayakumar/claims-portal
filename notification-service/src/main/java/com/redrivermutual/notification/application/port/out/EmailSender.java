package com.redrivermutual.notification.application.port.out;

/** Delivers an email. SMTP today; a cloud email API would be another adapter (OCP). */
public interface EmailSender {

    /** @throws EmailDeliveryException if the email could not be handed to the mail server */
    void send(String recipient, RenderedEmail email);
}
