package com.redrivermutual.claims.domain;

import java.time.LocalDate;
import java.util.Objects;

import static com.redrivermutual.claims.domain.DomainPreconditions.requireText;

/**
 * What the customer reported: everything about a claim except the identity the system assigns.
 */
public record ClaimDetails(
        Customer customer,
        String policyNumber,
        LocalDate dateOfLoss,
        LossType lossType,
        PropertyAddress propertyAddress,
        String description) {

    public ClaimDetails {
        Objects.requireNonNull(customer, "customer");
        policyNumber = requireText(policyNumber, "policyNumber");
        Objects.requireNonNull(dateOfLoss, "dateOfLoss");
        Objects.requireNonNull(lossType, "lossType");
        Objects.requireNonNull(propertyAddress, "propertyAddress");
        description = requireText(description, "description");
    }

    @Override
    public String toString() {
        return "ClaimDetails[lossType=" + lossType + ", dateOfLoss=" + dateOfLoss + "]";
    }
}
