package com.redrivermutual.claims.domain;

import java.util.Locale;

import static com.redrivermutual.claims.domain.DomainPreconditions.requireText;

/**
 * The policyholder submitting the claim. Email is normalized to lower case; phone is optional.
 */
public record Customer(String firstName, String lastName, String email, String phone) {

    public Customer {
        firstName = requireText(firstName, "firstName");
        lastName = requireText(lastName, "lastName");
        email = requireText(email, "email").toLowerCase(Locale.ROOT);
        phone = (phone == null || phone.isBlank()) ? null : phone.strip();
    }

    /** Never prints personal information, so a logged Customer cannot leak PII (NFR-06). */
    @Override
    public String toString() {
        return "Customer[redacted]";
    }
}
