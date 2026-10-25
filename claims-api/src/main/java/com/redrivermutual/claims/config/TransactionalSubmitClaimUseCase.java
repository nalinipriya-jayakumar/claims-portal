package com.redrivermutual.claims.config;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decorator that runs the use case in one database transaction, so the claim row and its
 * outbox row commit or roll back together (NFR-03). Doing this here rather than with
 * {@code @Transactional} on the service keeps the application layer free of Spring.
 */
class TransactionalSubmitClaimUseCase implements SubmitClaimUseCase {

    private final SubmitClaimUseCase delegate;
    private final TransactionTemplate transaction;

    TransactionalSubmitClaimUseCase(SubmitClaimUseCase delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public Claim submit(ClaimDetails details) {
        return transaction.execute(status -> delegate.submit(details));
    }
}
