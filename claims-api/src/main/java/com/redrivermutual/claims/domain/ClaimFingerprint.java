package com.redrivermutual.claims.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SHA-256 of a claim's normalized content. Used to tell "the same submission retried" from
 * "a different claim sent with a reused idempotency key". It is computed from the domain values,
 * after trimming and case normalization, so formatting differences in the request do not matter.
 * Only the hash is stored, never the personal information it was computed from.
 */
public final class ClaimFingerprint {

    /** Unit separator: cannot occur in any validated field, so fields cannot run into each other. */
    private static final String SEPARATOR = "\u001F";

    private ClaimFingerprint() {
    }

    public static String of(ClaimDetails details) {
        Objects.requireNonNull(details, "details");
        String canonical = Stream.of(
                        details.customer().firstName(),
                        details.customer().lastName(),
                        details.customer().email(),
                        Objects.toString(details.customer().phone(), ""),
                        details.policyNumber(),
                        details.dateOfLoss().toString(),
                        details.lossType().name(),
                        details.propertyAddress().street(),
                        details.propertyAddress().city(),
                        details.propertyAddress().province().name(),
                        details.propertyAddress().postalCode(),
                        details.description())
                .collect(Collectors.joining(SEPARATOR));
        return sha256(canonical);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
