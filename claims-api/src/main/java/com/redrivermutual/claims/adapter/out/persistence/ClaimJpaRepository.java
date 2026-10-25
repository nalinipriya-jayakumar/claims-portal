package com.redrivermutual.claims.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClaimJpaRepository extends JpaRepository<ClaimEntity, UUID> {

    Optional<ClaimEntity> findByClaimNumber(String claimNumber);
}
