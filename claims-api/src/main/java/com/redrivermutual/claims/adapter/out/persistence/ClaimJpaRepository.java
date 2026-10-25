package com.redrivermutual.claims.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.redrivermutual.claims.domain.LossType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface ClaimJpaRepository extends JpaRepository<ClaimEntity, UUID> {

    Optional<ClaimEntity> findByClaimNumber(String claimNumber);

    /** Served by ix_claim_incident. */
    Optional<ClaimEntity> findFirstByPolicyNumberAndDateOfLossAndLossTypeOrderBySubmittedAtAsc(
            String policyNumber, LocalDate dateOfLoss, LossType lossType);
}
