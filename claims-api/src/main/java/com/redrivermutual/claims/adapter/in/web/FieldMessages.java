package com.redrivermutual.claims.adapter.in.web;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Customer-facing wording for each field and error code. The rules themselves come from the
 * OpenAPI contract (generated Bean Validation annotations); the contract's stable {@code code}
 * is what clients key on, and this catalogue only supplies readable text for it.
 * No message ever includes the submitted value.
 */
final class FieldMessages {

    private record Wording(String required, String invalid, String tooShort, String tooLong) {
    }

    private static final Map<String, Wording> FIELDS = Map.ofEntries(
            entry("customer", new Wording("Enter your contact details.", null, null, null)),
            entry("customer.firstName", new Wording("Enter your first name.",
                    "First name can contain letters, spaces, apostrophes and hyphens.", null,
                    "First name must be 50 characters or fewer.")),
            entry("customer.lastName", new Wording("Enter your last name.",
                    "Last name can contain letters, spaces, apostrophes and hyphens.", null,
                    "Last name must be 50 characters or fewer.")),
            entry("customer.email", new Wording("Enter your email address.",
                    "Enter an email address like name@example.com.", null,
                    "Email address must be 254 characters or fewer.")),
            entry("customer.phone", new Wording(null, "Enter a 10-digit phone number, digits only.", null, null)),
            entry("policyNumber", new Wording("Enter your policy number.",
                    "Enter a policy number like HO-12345678.", null, null)),
            entry("dateOfLoss", new Wording("Enter the date of loss.", "Enter the date as YYYY-MM-DD.", null, null)),
            entry("lossType", new Wording("Select the type of loss.", "Select the type of loss.", null, null)),
            entry("propertyAddress", new Wording("Enter the property address.", null, null, null)),
            entry("propertyAddress.street", new Wording("Enter the street address.", null, null,
                    "Street address must be 100 characters or fewer.")),
            entry("propertyAddress.city", new Wording("Enter the city.", null, null,
                    "City must be 60 characters or fewer.")),
            entry("propertyAddress.province", new Wording("Select the province or territory.",
                    "Select the province or territory.", null, null)),
            entry("propertyAddress.postalCode", new Wording("Enter the postal code.",
                    "Enter a postal code like R3C 1A1.", null, null)),
            entry("description", new Wording("Describe the damage or loss.", null,
                    "Description must be at least 20 characters.", "Description must be 2,000 characters or fewer.")),
            entry("consentAcknowledged", new Wording(
                    "Confirm that you agree to be contacted about this claim.", null, null, null)));

    private FieldMessages() {
    }

    static String messageFor(String field, ErrorCode code) {
        Wording wording = FIELDS.get(field);
        String specific = wording == null ? null : switch (code) {
            case REQUIRED, MUST_BE_TRUE -> wording.required();
            case INVALID_FORMAT, NOT_ALLOWED_VALUE -> wording.invalid();
            case TOO_SHORT -> wording.tooShort();
            case TOO_LONG -> wording.tooLong();
            default -> null;
        };
        return specific != null ? specific : generic(code);
    }

    private static String generic(ErrorCode code) {
        return switch (code) {
            case REQUIRED -> "This field is required.";
            case UNKNOWN_FIELD -> "This field is not recognised.";
            case NOT_ALLOWED_VALUE -> "Select one of the listed options.";
            case INVALID_FORMAT -> "This value has the wrong format.";
            case TOO_SHORT -> "This value is too short.";
            case TOO_LONG -> "This value is too long.";
            case DATE_IN_FUTURE -> "Date of loss cannot be in the future.";
            case DATE_TOO_OLD -> "Date of loss is too far in the past.";
            case MUST_BE_TRUE -> "This must be confirmed.";
        };
    }
}
