package com.redrivermutual.claims.domain;

import java.util.Objects;

final class DomainPreconditions {

    private DomainPreconditions() {
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }
}
