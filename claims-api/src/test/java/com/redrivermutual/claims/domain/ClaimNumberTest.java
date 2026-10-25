package com.redrivermutual.claims.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClaimNumberTest {

    @Test
    void formatsYearAndZeroPaddedSequence() {
        assertThat(ClaimNumber.of(2026, 123).value()).isEqualTo("CLM-2026-000123");
        assertThat(ClaimNumber.of(2026, 999_999).value()).isEqualTo("CLM-2026-999999");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 1_000_000})
    void rejectsSequenceOutsideSixDigits(long sequence) {
        assertThatIllegalArgumentException().isThrownBy(() -> ClaimNumber.of(2026, sequence));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CLM-26-000001", "CLM-2026-1", "clm-2026-000001", "CLM-2026-000001 "})
    void rejectsMalformedValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClaimNumber(value));
    }

    @Test
    void equalNumbersAreEqual() {
        assertThat(ClaimNumber.of(2026, 7)).isEqualTo(new ClaimNumber("CLM-2026-000007"));
    }
}
