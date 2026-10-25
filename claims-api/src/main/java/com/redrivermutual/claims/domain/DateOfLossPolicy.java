package com.redrivermutual.claims.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Business rule for the date of loss: not in the future, and no older than a configurable
 * number of days (assumption A-03). "Today" is taken from the clock's zone, so a claim
 * submitted late in the evening in Winnipeg is judged against the Winnipeg calendar date.
 */
public final class DateOfLossPolicy {

    public enum Violation {
        IN_FUTURE,
        TOO_OLD
    }

    private final Clock clock;
    private final int maxAgeDays;

    public DateOfLossPolicy(Clock clock, int maxAgeDays) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAgeDays < 0) {
            throw new IllegalArgumentException("maxAgeDays must not be negative");
        }
        this.maxAgeDays = maxAgeDays;
    }

    public Optional<Violation> check(LocalDate dateOfLoss) {
        Objects.requireNonNull(dateOfLoss, "dateOfLoss");
        LocalDate today = LocalDate.now(clock);
        if (dateOfLoss.isAfter(today)) {
            return Optional.of(Violation.IN_FUTURE);
        }
        if (dateOfLoss.isBefore(today.minusDays(maxAgeDays))) {
            return Optional.of(Violation.TOO_OLD);
        }
        return Optional.empty();
    }

    public void enforce(LocalDate dateOfLoss) {
        check(dateOfLoss).ifPresent(violation -> {
            throw new InvalidDateOfLossException(violation, maxAgeDays);
        });
    }

    public int maxAgeDays() {
        return maxAgeDays;
    }
}
