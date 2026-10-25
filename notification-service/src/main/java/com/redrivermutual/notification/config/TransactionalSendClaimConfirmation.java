package com.redrivermutual.notification.config;

import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Runs the use case in one transaction, so the processed-event row commits only if the email
 * was handed to the mail server. Keeps Spring out of the application layer.
 */
class TransactionalSendClaimConfirmation implements SendClaimConfirmationUseCase {

    private final SendClaimConfirmationUseCase delegate;
    private final TransactionTemplate transaction;

    TransactionalSendClaimConfirmation(SendClaimConfirmationUseCase delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public Outcome handle(UUID eventId, ClaimConfirmation confirmation) {
        return transaction.execute(status -> delegate.handle(eventId, confirmation));
    }
}
