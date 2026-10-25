package com.redrivermutual.claims.adapter.in.web;

import com.redrivermutual.claims.domain.DateOfLossPolicy;

/**
 * One entry of the {@code errors} array in a validation problem.
 *
 * @param field   dot-separated JSON path, e.g. {@code propertyAddress.postalCode}
 * @param code    stable code for the portal
 * @param message human-readable text that never contains the submitted value
 */
public record FieldViolation(String field, ErrorCode code, String message) {

    static final String DATE_OF_LOSS_FIELD = "dateOfLoss";

    static FieldViolation forDateOfLoss(DateOfLossPolicy.Violation violation, int maxAgeDays) {
        return switch (violation) {
            case IN_FUTURE -> new FieldViolation(DATE_OF_LOSS_FIELD, ErrorCode.DATE_IN_FUTURE,
                    "Date of loss cannot be in the future.");
            case TOO_OLD -> new FieldViolation(DATE_OF_LOSS_FIELD, ErrorCode.DATE_TOO_OLD,
                    "Date of loss must be within the last " + maxAgeDays + " days.");
        };
    }
}
