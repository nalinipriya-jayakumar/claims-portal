package com.redrivermutual.claims.adapter.out.persistence;

import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.domain.ClaimNumber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Claim numbers from the {@code claim_number_seq} database sequence (ADR-0006). The sequence is
 * safe under concurrency and across API replicas, and survives restarts. The year comes from
 * the configured business clock, so it matches the date the customer sees.
 */
@Component
class SequenceClaimNumberGenerator implements ClaimNumberGenerator {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    SequenceClaimNumberGenerator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public ClaimNumber next() {
        Long sequence = jdbc.queryForObject("SELECT nextval('claim_number_seq')", Long.class);
        if (sequence == null) {
            throw new IllegalStateException("claim_number_seq returned no value");
        }
        return ClaimNumber.of(Year.now(clock).getValue(), sequence);
    }
}
