package com.redrivermutual.notification.config;

import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.UUID;

/**
 * {@code notification.emails} by {@code outcome}: sent, duplicate (skipped redelivery) and failed
 * (an attempt that will be retried or dead-lettered). A decorator keeps the use case metrics-free.
 */
class MeteredSendClaimConfirmation implements SendClaimConfirmationUseCase {

    private final SendClaimConfirmationUseCase delegate;
    private final Counter sent;
    private final Counter duplicate;
    private final Counter failed;

    MeteredSendClaimConfirmation(SendClaimConfirmationUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.sent = counter(registry, "sent");
        this.duplicate = counter(registry, "duplicate");
        this.failed = counter(registry, "failed");
    }

    @Override
    public Outcome handle(UUID eventId, ClaimConfirmation confirmation) {
        try {
            Outcome outcome = delegate.handle(eventId, confirmation);
            (outcome == Outcome.SENT ? sent : duplicate).increment();
            return outcome;
        } catch (RuntimeException e) {
            failed.increment();
            throw e;
        }
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("notification.emails")
                .description("Claim confirmation email attempts by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }
}
