package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.LossType;

import java.time.LocalDate;
import java.util.Optional;

public interface ClaimRepository {

    /**
     * @throws DuplicateClaimNumberException if the claim number is already taken; uniqueness is
     *         enforced by the store, not only by the generator
     */
    void save(Claim claim);

    Optional<Claim> findByClaimNumber(ClaimNumber claimNumber);

    /** The first claim submitted for this incident, if any: used to flag possible duplicates. */
    Optional<ClaimNumber> findEarliestForIncident(String policyNumber, LocalDate dateOfLoss, LossType lossType);
}
