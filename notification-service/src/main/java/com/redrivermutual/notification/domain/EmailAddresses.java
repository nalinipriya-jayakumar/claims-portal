package com.redrivermutual.notification.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailAddresses {

    /** One @, no whitespace, no CR/LF (which would allow mail header injection), a dot in the domain. */
    private static final Pattern VALID = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailAddresses() {
    }

    public static boolean isValid(String address) {
        return address != null && address.length() <= 254 && VALID.matcher(address).matches();
    }

    public static String normalize(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }

    /** {@code jane.doe@example.com} becomes {@code j***@example.com}, for logs (NFR-06). */
    public static String mask(String address) {
        if (address == null) {
            return "***";
        }
        int at = address.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
