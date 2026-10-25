package com.redrivermutual.notification.application.port.out;

import java.util.Objects;

/** A ready-to-send email: plain-text and HTML alternatives of the same message. */
public record RenderedEmail(String subject, String textBody, String htmlBody) {

    public RenderedEmail {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
        Objects.requireNonNull(htmlBody, "htmlBody");
        if (subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("subject must be a single line");
        }
    }
}
