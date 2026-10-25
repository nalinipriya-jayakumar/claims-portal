package com.redrivermutual.claims.adapter.out.persistence;

import com.redrivermutual.claims.application.port.out.IdempotencyStore;
import com.redrivermutual.claims.domain.ClaimNumber;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code idempotency_key} table. Plain JDBC on the same transaction as the JPA claim save
 * (Spring's JPA transaction manager shares its connection with JdbcTemplate).
 */
@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Entry> find(UUID idempotencyKey) {
        return jdbc.query("SELECT request_fingerprint, claim_number FROM idempotency_key WHERE idempotency_key = ?",
                        (row, n) -> new Entry(row.getString(1).strip(), new ClaimNumber(row.getString(2))),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    /** MANDATORY: recording the key outside the claim's transaction would defeat it. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID idempotencyKey, String fingerprint, ClaimNumber claimNumber, Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO idempotency_key (idempotency_key, request_fingerprint, claim_number, created_at)
                    VALUES (?, ?, ?, ?)
                    """, idempotencyKey, fingerprint, claimNumber.value(), Timestamp.from(at));
        } catch (DuplicateKeyException e) {
            throw new KeyAlreadyRecordedException(e);
        }
    }
}
