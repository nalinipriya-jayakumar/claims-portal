package com.redrivermutual.claims.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CustomerAndAddressTest {

    @Test
    void customerEmailIsLowerCasedAndNamesTrimmed() {
        Customer customer = new Customer(" Jane ", "O'Neil", "Jane.ONeil@Example.COM", "2045550142");

        assertThat(customer.firstName()).isEqualTo("Jane");
        assertThat(customer.email()).isEqualTo("jane.oneil@example.com");
    }

    @Test
    void blankPhoneBecomesAbsent() {
        assertThat(new Customer("Jane", "Doe", "j@example.com", "  ").phone()).isNull();
    }

    @Test
    void customerRejectsBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Customer(" ", "Doe", "j@example.com", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"r3c1a1", "R3C1A1", "r3c 1a1", "R3C 1A1"})
    void postalCodeIsNormalized(String raw) {
        PropertyAddress address = new PropertyAddress("1 Main St", "Winnipeg", Province.MB, raw);

        assertThat(address.postalCode()).isEqualTo("R3C 1A1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "R3C-1A1", "R3C 1A", "D3C 1A1X"})
    void invalidPostalCodeIsRejected(String raw) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PropertyAddress("1 Main St", "Winnipeg", Province.MB, raw));
    }

    @Test
    void toStringNeverContainsPersonalInformation() {
        Claim claim = Claim.submit(ClaimNumber.of(2026, 1), validDetails(), java.time.Instant.now());

        assertThat(claim.toString())
                .contains("CLM-2026-000001", "WATER")
                .doesNotContain("Jane", "O'Neil", "jane.oneil", "123 Main", "R3C", "Pipe burst", "HO-12345678");
    }
}
