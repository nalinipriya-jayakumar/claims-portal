package com.redrivermutual.claims.domain;

import org.junit.jupiter.api.Test;

import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimFingerprintTest {

    @Test
    void isAHexSha256AndContainsNoPersonalInformation() {
        String fingerprint = ClaimFingerprint.of(validDetails());

        assertThat(fingerprint).matches("^[0-9a-f]{64}$").doesNotContain("jane", "Jane");
    }

    @Test
    void formattingDifferencesDoNotChangeIt() {
        ClaimDetails v = validDetails();
        ClaimDetails reformatted = new ClaimDetails(
                new Customer("  Jane ", "O'Neil", "JANE.ONEIL@EXAMPLE.COM", "2045550142"),
                v.policyNumber(), v.dateOfLoss(), v.lossType(),
                new PropertyAddress("123 Main Street", "Winnipeg", Province.MB, "r3c1a1"),
                v.description());

        assertThat(ClaimFingerprint.of(reformatted)).isEqualTo(ClaimFingerprint.of(v));
    }

    @Test
    void anyChangeOfContentChangesIt() {
        ClaimDetails v = validDetails();
        ClaimDetails otherDescription = new ClaimDetails(v.customer(), v.policyNumber(), v.dateOfLoss(),
                v.lossType(), v.propertyAddress(), v.description() + " Also the dryer.");

        assertThat(ClaimFingerprint.of(otherDescription)).isNotEqualTo(ClaimFingerprint.of(v));
    }
}
