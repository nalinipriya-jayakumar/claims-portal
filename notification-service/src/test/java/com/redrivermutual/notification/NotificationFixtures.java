package com.redrivermutual.notification;

import com.redrivermutual.events.ClaimSubmittedEvent;
import com.redrivermutual.notification.domain.ClaimConfirmation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class NotificationFixtures {

    private NotificationFixtures() {
    }

    public static ClaimConfirmation confirmation(String claimNumber) {
        return new ClaimConfirmation(claimNumber, "Jane", "jane.oneil@example.com", "WATER", LocalDate.of(2026, 10, 15));
    }

    /** Year 2099 keeps test claim numbers apart from anything the real sequence issues. */
    public static String uniqueClaimNumber() {
        return "CLM-2099-%06d".formatted(ThreadLocalRandom.current().nextInt(1, 1_000_000));
    }

    public static ClaimSubmittedEvent event(UUID eventId, String claimNumber, String email) {
        return ClaimSubmittedEvent.v1(eventId, Instant.parse("2026-10-18T15:00:00Z"), "test-correlation",
                new ClaimSubmittedEvent.Data(claimNumber, "Jane", email, "WATER", LocalDate.of(2026, 10, 15)));
    }
}
