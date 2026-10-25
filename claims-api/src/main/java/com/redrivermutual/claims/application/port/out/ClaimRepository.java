package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimNumber;

import java.util.Optional;

public interface ClaimRepository {

    /**
     * @throws DuplicateClaimNumberException if the claim number is already taken; uniqueness is
     *         enforced by the store, not only by the generator (FR-08)
     */
    void save(Claim claim);

    Optional<Claim> findByClaimNumber(ClaimNumber claimNumber);
}
