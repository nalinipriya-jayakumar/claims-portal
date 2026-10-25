package com.redrivermutual.claims.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.redrivermutual.claims.domain.DomainPreconditions.requireText;

/**
 * Address of the insured property. The postal code is normalized to {@code A1A 1A1}.
 */
public record PropertyAddress(String street, String city, Province province, String postalCode) {

    private static final Pattern POSTAL_CODE = Pattern.compile("^[A-Z]\\d[A-Z] \\d[A-Z]\\d$");

    public PropertyAddress {
        street = requireText(street, "street");
        city = requireText(city, "city");
        Objects.requireNonNull(province, "province");
        postalCode = normalizePostalCode(requireText(postalCode, "postalCode"));
    }

    static String normalizePostalCode(String raw) {
        String compact = raw.replace(" ", "").toUpperCase(Locale.ROOT);
        String normalized = compact.length() == 6
                ? compact.substring(0, 3) + " " + compact.substring(3)
                : compact;
        if (!POSTAL_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("postalCode must be a Canadian postal code");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "PropertyAddress[province=" + province + "]";
    }
}
