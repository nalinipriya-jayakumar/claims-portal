package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.ClaimNumber;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Remembers which claim each idempotency key produced, and for which content. */
public interface IdempotencyStore {

    record Entry(String fingerprint, ClaimNumber claimNumber) {
    }

    Optional<Entry> find(UUID idempotencyKey);

    /**
     * Must run in the same transaction that saves the claim.
     *
     * @throws KeyAlreadyRecordedException if another request recorded this key first (a concurrent retry)
     */
    void record(UUID idempotencyKey, String fingerprint, ClaimNumber claimNumber, Instant at);

    /** A concurrent request with the same key won the race; retrying the use case replays its claim. */
    class KeyAlreadyRecordedException extends RuntimeException {
        public KeyAlreadyRecordedException(Throwable cause) {
            super("Idempotency key recorded concurrently", cause);
        }
    }
}
