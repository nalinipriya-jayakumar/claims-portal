package com.redrivermutual.notification.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Everything needed to tell a customer their claim was received. Built from a ClaimSubmitted
 * event; nothing here is read back from the claims database.
 */
public record ClaimConfirmation(
        String claimNumber,
        String customerFirstName,
        String recipientEmail,
        String lossType,
        LocalDate dateOfLoss) {

    private static final Pattern CLAIM_NUMBER = Pattern.compile("^CLM-\\d{4}-\\d{6}$");

    public ClaimConfirmation {
        Objects.requireNonNull(claimNumber, "claimNumber");
        if (!CLAIM_NUMBER.matcher(claimNumber).matches()) {
            throw new IllegalArgumentException("claimNumber must match CLM-YYYY-NNNNNN");
        }
        recipientEmail = recipientEmail == null ? null : EmailAddresses.normalize(recipientEmail);
        if (!EmailAddresses.isValid(recipientEmail)) {
            throw new IllegalArgumentException("recipientEmail is not a valid address");
        }
        customerFirstName = (customerFirstName == null || customerFirstName.isBlank())
                ? null
                : customerFirstName.strip();
        Objects.requireNonNull(lossType, "lossType");
        Objects.requireNonNull(dateOfLoss, "dateOfLoss");
    }

    @Override
    public String toString() {
        return "ClaimConfirmation[claimNumber=" + claimNumber + ", recipient=" + EmailAddresses.mask(recipientEmail) + "]";
    }
}
