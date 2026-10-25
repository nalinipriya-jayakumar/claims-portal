package com.redrivermutual.claims.domain;

public class InvalidDateOfLossException extends RuntimeException {

    private final DateOfLossPolicy.Violation violation;
    private final int maxAgeDays;

    public InvalidDateOfLossException(DateOfLossPolicy.Violation violation, int maxAgeDays) {
        super("Date of loss violates rule " + violation);
        this.violation = violation;
        this.maxAgeDays = maxAgeDays;
    }

    public DateOfLossPolicy.Violation violation() {
        return violation;
    }

    public int maxAgeDays() {
        return maxAgeDays;
    }
}
