package com.redrivermutual.claims.adapter.out.persistence;

import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.ClaimStatus;
import com.redrivermutual.claims.domain.Customer;
import com.redrivermutual.claims.domain.LossType;
import com.redrivermutual.claims.domain.PropertyAddress;
import com.redrivermutual.claims.domain.Province;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping of a claim. Kept separate from the domain {@link Claim} so persistence concerns
 * (no-arg constructor, mutable fields, version) never leak into the business model.
 */
@Entity
@Table(name = "claim")
class ClaimEntity {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, updatable = false)
    private String claimNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "date_of_loss", nullable = false)
    private LocalDate dateOfLoss;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_type", nullable = false)
    private LossType lossType;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Province province;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    /** Internal flag for the claims team (ADR-0011); never returned by the API. */
    @Column(name = "possible_duplicate_of", updatable = false)
    private String possibleDuplicateOf;

    /** Null until first persisted, which is also how Spring Data tells a new entity from an existing one. */
    @Version
    private Long version;

    protected ClaimEntity() {
    }

    static ClaimEntity from(Claim claim) {
        ClaimDetails details = claim.details();
        ClaimEntity entity = new ClaimEntity();
        entity.id = claim.id();
        entity.claimNumber = claim.claimNumber().value();
        entity.firstName = details.customer().firstName();
        entity.lastName = details.customer().lastName();
        entity.email = details.customer().email();
        entity.phone = details.customer().phone();
        entity.policyNumber = details.policyNumber();
        entity.dateOfLoss = details.dateOfLoss();
        entity.lossType = details.lossType();
        entity.street = details.propertyAddress().street();
        entity.city = details.propertyAddress().city();
        entity.province = details.propertyAddress().province();
        entity.postalCode = details.propertyAddress().postalCode();
        entity.description = details.description();
        entity.status = claim.status();
        entity.submittedAt = claim.submittedAt();
        entity.possibleDuplicateOf = claim.duplicateOf().map(ClaimNumber::value).orElse(null);
        return entity;
    }

    Claim toDomain() {
        return new Claim(
                id,
                new ClaimNumber(claimNumber),
                new ClaimDetails(
                        new Customer(firstName, lastName, email, phone),
                        policyNumber,
                        dateOfLoss,
                        lossType,
                        new PropertyAddress(street, city, province, postalCode),
                        description),
                status,
                submittedAt,
                possibleDuplicateOf == null ? null : new ClaimNumber(possibleDuplicateOf));
    }
}
