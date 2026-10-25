package com.redrivermutual.claims.adapter.in.web;

import java.util.List;

public class ClaimValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public ClaimValidationException(List<FieldViolation> violations) {
        super(violations.size() + " field(s) failed validation");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
