package com.redrivermutual.claims;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.ClaimDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * If recording the event fails, the claim must not be saved either: no claim without its event
 * (NFR-03). The outbox is replaced by one that always fails, after the claim row is written.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SubmitClaimRollbackIntegrationTest.FailingOutbox.class})
class SubmitClaimRollbackIntegrationTest {

    @TestConfiguration
    static class FailingOutbox {
        @Bean
        @Primary
        OutboxPort failingOutbox() {
            return claim -> {
                throw new IllegalStateException("outbox unavailable");
            };
        }
    }

    @Autowired
    private SubmitClaimUseCase submitClaim;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void claimIsRolledBackWhenTheOutboxWriteFails() {
        int claimsBefore = claimCount();
        ClaimDetails valid = validDetails();
        ClaimDetails details = new ClaimDetails(valid.customer(), valid.policyNumber(),
                LocalDate.now(WINNIPEG).minusDays(1), valid.lossType(), valid.propertyAddress(), valid.description());

        assertThatThrownBy(() -> submitClaim.submit(SubmitClaimCommand.withoutKey(details)).claim()).hasMessage("outbox unavailable");

        assertThat(claimCount()).isEqualTo(claimsBefore);
    }

    private int claimCount() {
        return jdbc.queryForObject("SELECT count(*) FROM claim", Integer.class);
    }
}
