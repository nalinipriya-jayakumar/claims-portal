package com.redrivermutual.claims.application.port.in;

import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;

import java.util.Objects;
import java.util.UUID;

public interface SubmitClaimUseCase {

    /**
     * Validates the business rules, assigns a claim number and saves the claim, unless the same
     * submission was already accepted under the same idempotency key, in which case the original
     * claim is returned and nothing new is created.
     *
     * @throws com.redrivermutual.claims.domain.InvalidDateOfLossException if the date of loss is not acceptable
     * @throws IdempotencyKeyConflictException if the key was already used for a different claim
     */
    SubmissionResult submit(SubmitClaimCommand command);

    /**
     * @param idempotencyKey identifies one submission across retries; {@code null} when the caller sent none
     */
    record SubmitClaimCommand(ClaimDetails details, UUID idempotencyKey) {
        public SubmitClaimCommand {
            Objects.requireNonNull(details, "details");
        }

        public static SubmitClaimCommand withoutKey(ClaimDetails details) {
            return new SubmitClaimCommand(details, null);
        }
    }

    /**
     * @param replayed {@code true} when {@code claim} was created by an earlier request with the same key
     */
    record SubmissionResult(Claim claim, boolean replayed) {
        public SubmissionResult {
            Objects.requireNonNull(claim, "claim");
        }

        public static SubmissionResult created(Claim claim) {
            return new SubmissionResult(claim, false);
        }

        public static SubmissionResult replayed(Claim claim) {
            return new SubmissionResult(claim, true);
        }
    }

    /** The idempotency key was already used for a claim with different content. */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException() {
            super("Idempotency key already used for a different claim");
        }
    }
}
