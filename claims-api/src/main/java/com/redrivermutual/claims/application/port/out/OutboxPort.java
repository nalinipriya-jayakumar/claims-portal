package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.Claim;

/**
 * Records that a claim was submitted, for later publication to the broker (transactional
 * outbox, ADR-0002). Must be called in the same transaction that saves the claim; nothing is
 * sent to the broker here.
 */
public interface OutboxPort {

    void recordClaimSubmitted(Claim claim);
}
