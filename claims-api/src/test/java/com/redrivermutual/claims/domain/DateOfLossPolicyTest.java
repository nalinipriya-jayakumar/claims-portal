package com.redrivermutual.claims.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static com.redrivermutual.claims.ClaimFixtures.FIXED_CLOCK;
import static com.redrivermutual.claims.ClaimFixtures.TODAY;
import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateOfLossPolicyTest {

    private final DateOfLossPolicy policy = new DateOfLossPolicy(FIXED_CLOCK, 365);

    @Test
    void todayIsAccepted() {
        assertThat(policy.check(TODAY)).isEmpty();
    }

    @Test
    void tomorrowIsInTheFuture() {
        assertThat(policy.check(TODAY.plusDays(1))).contains(DateOfLossPolicy.Violation.IN_FUTURE);
    }

    @Test
    void exactlyMaxAgeIsAccepted() {
        assertThat(policy.check(TODAY.minusDays(365))).isEmpty();
    }

    @Test
    void oneDayOlderThanMaxAgeIsTooOld() {
        assertThat(policy.check(TODAY.minusDays(366))).contains(DateOfLossPolicy.Violation.TOO_OLD);
    }

    @Test
    void todayIsJudgedInTheConfiguredZoneNotUtc() {
        // 03:00 UTC on 18 October is still 22:00 on 17 October in Winnipeg.
        Clock lateEveningInWinnipeg = Clock.fixed(Instant.parse("2026-10-18T03:00:00Z"), WINNIPEG);
        DateOfLossPolicy winnipegPolicy = new DateOfLossPolicy(lateEveningInWinnipeg, 365);

        assertThat(winnipegPolicy.check(LocalDate.of(2026, 10, 17))).isEmpty();
        assertThat(winnipegPolicy.check(LocalDate.of(2026, 10, 18))).contains(DateOfLossPolicy.Violation.IN_FUTURE);
    }

    @Test
    void enforceThrowsWithTheViolation() {
        assertThatThrownBy(() -> policy.enforce(TODAY.plusDays(1)))
                .isInstanceOf(InvalidDateOfLossException.class)
                .extracting("violation")
                .isEqualTo(DateOfLossPolicy.Violation.IN_FUTURE);
    }
}
