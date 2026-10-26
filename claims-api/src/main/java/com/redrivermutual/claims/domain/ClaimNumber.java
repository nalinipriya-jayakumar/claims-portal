package com.redrivermutual.claims.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Customer-facing claim identifier in the form {@code CLM-YYYY-NNNNNN} (ADR-0006).
 */
public record ClaimNumber(String value) {

    public static final long MAX_SEQUENCE = 999_999;
    private static final Pattern FORMAT = Pattern.compile("^CLM-\\d{4}-\\d{6}$");

    public ClaimNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Claim number must match CLM-YYYY-NNNNNN");
        }
    }

    public static ClaimNumber of(int year, long sequence) {
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("Year must have four digits");
        }
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Sequence must be between 1 and " + MAX_SEQUENCE);
        }
        return new ClaimNumber("CLM-%04d-%06d".formatted(year, sequence));
    }

    @Override
    public String toString() {
        return value;
    }
}
