package com.redrivermutual.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrivermutual.claims.adapter.out.outbox.OutboxEventEntity;
import com.redrivermutual.claims.adapter.out.outbox.OutboxEventRepository;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The use case as wired in production, against PostgreSQL: the claim and its outbox row are
 * written together. Rollback when the outbox fails is in {@link SubmitClaimRollbackIntegrationTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubmitClaimPersistenceIntegrationTest {

    @Autowired
    private SubmitClaimUseCase submitClaim;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void claimAndItsClaimSubmittedEventAreSavedTogether() throws Exception {
        Claim claim = submitClaim.submit(recentDetails());

        assertThat(claimRows(claim)).isEqualTo(1);

        List<OutboxEventEntity> events = outboxEvents.findByAggregateId(claim.claimNumber().value());
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("ClaimSubmitted");
            assertThat(event.getEventVersion()).isEqualTo(1);
            assertThat(event.getAggregateType()).isEqualTo("Claim");
            assertThat(event.getPublishedAt()).as("not yet published; the relay does that").isNull();
        });

        JsonNode payload = objectMapper.readTree(events.getFirst().getPayload());
        assertThat(payload.path("eventId").asText()).isEqualTo(events.getFirst().getId().toString());
        assertThat(payload.path("data").path("claimNumber").asText()).isEqualTo(claim.claimNumber().value());
        assertThat(payload.path("data").path("customerEmail").asText()).isEqualTo("jane.oneil@example.com");
    }

    @Test
    void eventCarriesOnlyWhatTheEmailNeeds() throws Exception {
        Claim claim = submitClaim.submit(recentDetails());

        String payload = outboxEvents.findByAggregateId(claim.claimNumber().value()).getFirst().getPayload();

        assertThat(objectMapper.readTree(payload).path("data").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("claimNumber", "customerFirstName", "customerEmail", "lossType", "dateOfLoss");
        assertThat(payload).doesNotContain("Pipe burst", "123 Main", "HO-12345678", "2045550142");
    }

    @Test
    void claimNumbersComeFromTheDatabaseSequenceAndIncrease() {
        Claim first = submitClaim.submit(recentDetails());
        Claim second = submitClaim.submit(recentDetails());

        assertThat(first.claimNumber().value()).matches("^CLM-\\d{4}-\\d{6}$");
        assertThat(second.claimNumber().value()).isGreaterThan(first.claimNumber().value());
    }

    private int claimRows(Claim claim) {
        return jdbc.queryForObject("SELECT count(*) FROM claim WHERE claim_number = ?", Integer.class,
                claim.claimNumber().value());
    }

    /** The fixture's date is fixed; the real clock runs here, so use a date that is always valid. */
    private static ClaimDetails recentDetails() {
        ClaimDetails details = validDetails();
        return new ClaimDetails(details.customer(), details.policyNumber(), LocalDate.now(WINNIPEG).minusDays(1),
                details.lossType(), details.propertyAddress(), details.description());
    }
}
