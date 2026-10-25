package com.redrivermutual.claims.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByAggregateId(String aggregateId);

    /**
     * Oldest unpublished events, row-locked for the current transaction. {@code SKIP LOCKED} lets
     * several API replicas relay at once without publishing the same row twice concurrently.
     * Served by the partial index {@code ix_outbox_event_unpublished}.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    @Query("SELECT count(e) FROM OutboxEventEntity e WHERE e.publishedAt IS NULL")
    long countUnpublished();
}
