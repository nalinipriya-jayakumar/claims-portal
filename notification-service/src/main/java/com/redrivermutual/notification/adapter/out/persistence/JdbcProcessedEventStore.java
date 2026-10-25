package com.redrivermutual.notification.adapter.out.persistence;

import com.redrivermutual.notification.application.port.out.ProcessedEventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code INSERT ... ON CONFLICT DO NOTHING} is the check and the claim in one statement. If two
 * consumers get the same event at once, the second insert waits on the first one's row lock and,
 * once the first commits, finds the conflict and returns false.
 */
@Repository
class JdbcProcessedEventStore implements ProcessedEventStore {

    private final JdbcTemplate jdbc;

    JdbcProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** MANDATORY: outside the email-sending transaction this row would protect nothing. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String claimNumber, Instant processedAt) {
        int inserted = jdbc.update("""
                INSERT INTO processed_event (event_id, claim_number, processed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, claimNumber, Timestamp.from(processedAt));
        return inserted == 1;
    }
}
