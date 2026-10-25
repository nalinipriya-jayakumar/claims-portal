package com.redrivermutual.claims.adapter.out.persistence;

import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.DuplicateClaimNumberException;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimNumber;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Adapts the {@link ClaimRepository} port to Spring Data JPA and PostgreSQL. */
@Repository
class JpaClaimRepository implements ClaimRepository {

    static final String UNIQUE_CLAIM_NUMBER = "uk_claim_claim_number";

    private final ClaimJpaRepository jpa;

    JpaClaimRepository(ClaimJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Claim claim) {
        try {
            // Flush now so a unique-constraint violation surfaces here as a port exception,
            // not later at commit as a generic persistence error.
            jpa.saveAndFlush(ClaimEntity.from(claim));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNIQUE_CLAIM_NUMBER)) {
                throw new DuplicateClaimNumberException(claim.claimNumber());
            }
            throw e;
        }
    }

    @Override
    public Optional<Claim> findByClaimNumber(ClaimNumber claimNumber) {
        return jpa.findByClaimNumber(claimNumber.value()).map(ClaimEntity::toDomain);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraintName) {
        return e.getCause() instanceof ConstraintViolationException violation
                && constraintName.equalsIgnoreCase(violation.getConstraintName());
    }
}
