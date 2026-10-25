package com.redrivermutual.claims.application.port.in;

import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;

public interface SubmitClaimUseCase {

    /**
     * Validates the business rules, assigns a claim number and saves the claim.
     *
     * @throws com.redrivermutual.claims.domain.InvalidDateOfLossException if the date of loss is not acceptable
     */
    Claim submit(ClaimDetails details);
}
