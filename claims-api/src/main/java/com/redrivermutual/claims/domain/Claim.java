package com.redrivermutual.claims.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A submitted home insurance claim: the system of record for one first notice of loss.
 *
 * @param possibleDuplicateOf the earliest earlier claim for the same policy, date of loss and loss
 *                            type, or {@code null}. An internal flag for the claims team: the claim
 *                            is accepted either way, and the flag is never shown to the customer.
 */
public record Claim(
        UUID id,
        ClaimNumber claimNumber,
        ClaimDetails details,
        ClaimStatus status,
        Instant submittedAt,
        ClaimNumber possibleDuplicateOf) {

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimNumber, "claimNumber");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (claimNumber.equals(possibleDuplicateOf)) {
            throw new IllegalArgumentException("A claim cannot be a duplicate of itself");
        }
    }

    public static Claim submit(ClaimNumber claimNumber, ClaimDetails details, Instant now) {
        return submit(claimNumber, details, now, null);
    }

    public static Claim submit(ClaimNumber claimNumber, ClaimDetails details, Instant now,
                               ClaimNumber possibleDuplicateOf) {
        // Millisecond precision keeps the value identical after a round trip through Postgres or JSON.
        return new Claim(UUID.randomUUID(), claimNumber, details, ClaimStatus.SUBMITTED,
                now.truncatedTo(ChronoUnit.MILLIS), possibleDuplicateOf);
    }

    public Optional<ClaimNumber> duplicateOf() {
        return Optional.ofNullable(possibleDuplicateOf);
    }

    @Override
    public String toString() {
        return "Claim[claimNumber=" + claimNumber + ", status=" + status
                + (possibleDuplicateOf == null ? "" : ", possibleDuplicateOf=" + possibleDuplicateOf)
                + ", " + details + "]";
    }
}
