package com.redrivermutual.claims.adapter.in.web;

/**
 * Stable machine-readable field error codes (OpenAPI {@code FieldError.code}). The portal maps
 * these to its own wording. Declaration order is the priority when a field breaks several rules.
 */
public enum ErrorCode {
    REQUIRED,
    UNKNOWN_FIELD,
    NOT_ALLOWED_VALUE,
    INVALID_FORMAT,
    TOO_SHORT,
    TOO_LONG,
    DATE_IN_FUTURE,
    DATE_TOO_OLD,
    MUST_BE_TRUE
}
