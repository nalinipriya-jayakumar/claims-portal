package com.redrivermutual.claims.adapter.in.web;

import java.net.URI;

/** RFC 9457 {@code type} values used by this API. */
final class ProblemTypes {

    static final URI VALIDATION_ERROR = URI.create("/problems/validation-error");
    static final URI MALFORMED_REQUEST = URI.create("/problems/malformed-request");
    static final URI PAYLOAD_TOO_LARGE = URI.create("/problems/payload-too-large");
    static final URI INTERNAL_ERROR = URI.create("/problems/internal-error");

    private ProblemTypes() {
    }
}
