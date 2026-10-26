package com.redrivermutual.claims.application.port.out;

import com.redrivermutual.claims.domain.ClaimNumber;

/** Strategy for issuing claim numbers; swappable without touching the use case (ADR-0006). */
public interface ClaimNumberGenerator {

    ClaimNumber next();
}
