package com.redrivermutual.notification.application.port.in;

import com.redrivermutual.notification.domain.ClaimConfirmation;

import java.util.UUID;

public interface SendClaimConfirmationUseCase {

    enum Outcome {
        SENT,
        DUPLICATE_SKIPPED
    }

    /**
     * Sends the confirmation email for one ClaimSubmitted event, at most once per event ID.
     *
     * @throws com.redrivermutual.notification.application.port.out.EmailDeliveryException if the
     *         email could not be sent; the caller retries
     */
    Outcome handle(UUID eventId, ClaimConfirmation confirmation);
}
