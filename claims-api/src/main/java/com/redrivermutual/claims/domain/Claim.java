package com.redrivermutual.claims.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * A submitted home insurance claim: the system of record for one first notice of loss.
 */
public record Claim(
        UUID id,
        ClaimNumber claimNumber,
        ClaimDetails details,
        ClaimStatus status,
        Instant submittedAt) {

    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimNumber, "claimNumber");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }

    public static Claim submit(ClaimNumber claimNumber, ClaimDetails details, Instant now) {
        // Millisecond precision keeps the value identical after a round trip through Postgres or JSON.
        return new Claim(UUID.randomUUID(), claimNumber, details, ClaimStatus.SUBMITTED,
                now.truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public String toString() {
        return "Claim[claimNumber=" + claimNumber + ", status=" + status + ", " + details + "]";
    }
}
