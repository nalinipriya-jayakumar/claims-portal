package com.redrivermutual.notification.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static com.redrivermutual.notification.NotificationFixtures.confirmation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClaimConfirmationTest {

    @Test
    void emailIsMaskedForLogs() {
        assertThat(EmailAddresses.mask("jane.doe@example.com")).isEqualTo("j***@example.com");
        assertThat(EmailAddresses.mask("not-an-address")).isEqualTo("***");
        assertThat(EmailAddresses.mask(null)).isEqualTo("***");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane@example", "jane example.com", "jane@exa mple.com", "a@b.c\r\nBcc: x@y.z", ""})
    void invalidOrHeaderInjectingAddressesAreRejected(String address) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ClaimConfirmation("CLM-2026-000001", "Jane", address, "FIRE", LocalDate.of(2026, 10, 1)));
    }

    @Test
    void malformedClaimNumberIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ClaimConfirmation("CLM-1", "Jane", "jane@example.com", "FIRE", LocalDate.of(2026, 10, 1)));
    }

    @Test
    void recipientIsNormalizedAndBlankNameBecomesAbsent() {
        ClaimConfirmation c = new ClaimConfirmation("CLM-2026-000001", "  ", " Jane@Example.COM ", "FIRE",
                LocalDate.of(2026, 10, 1));

        assertThat(c.recipientEmail()).isEqualTo("jane@example.com");
        assertThat(c.customerFirstName()).isNull();
    }

    @Test
    void toStringDoesNotExposeNameOrAddress() {
        assertThat(confirmation("CLM-2026-000001").toString())
                .contains("CLM-2026-000001", "j***@example.com")
                .doesNotContain("Jane", "jane.oneil");
    }
}
