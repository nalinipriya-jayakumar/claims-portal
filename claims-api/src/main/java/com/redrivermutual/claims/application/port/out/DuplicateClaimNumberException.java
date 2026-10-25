package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.ClaimNumber;

public class DuplicateClaimNumberException extends RuntimeException {

    public DuplicateClaimNumberException(ClaimNumber claimNumber) {
        super("Claim number already exists: " + claimNumber);
    }
}
