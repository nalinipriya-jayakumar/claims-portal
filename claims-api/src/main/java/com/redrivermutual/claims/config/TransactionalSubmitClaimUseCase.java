package com.redrivermutual.claims.config;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.out.IdempotencyStore;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decorator that runs the use case in one database transaction, so the claim, its outbox row
 * and its idempotency key commit or roll back together. Doing this here rather than with
 * {@code @Transactional} on the service keeps the application layer free of Spring.
 *
 * <p>Two requests with the same idempotency key at the same moment: the second one's key insert
 * waits for the first transaction, then fails on the primary key and rolls back. Running it once
 * more, in a fresh transaction, finds the committed key and replays the first request's claim.
 */
class TransactionalSubmitClaimUseCase implements SubmitClaimUseCase {

    private final SubmitClaimUseCase delegate;
    private final TransactionTemplate transaction;

    TransactionalSubmitClaimUseCase(SubmitClaimUseCase delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public SubmissionResult submit(SubmitClaimCommand command) {
        try {
            return transaction.execute(status -> delegate.submit(command));
        } catch (IdempotencyStore.KeyAlreadyRecordedException concurrentRetry) {
            return transaction.execute(status -> delegate.submit(command));
        }
    }
}
