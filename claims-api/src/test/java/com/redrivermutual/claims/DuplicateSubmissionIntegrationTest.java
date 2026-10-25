package com.redrivermutual.claims;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmissionResult;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duplicate submissions against PostgreSQL (ADR-0011). Each test uses its own policy number, because
 * the database is shared with other test classes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DuplicateSubmissionIntegrationTest {

    @Autowired
    private SubmitClaimUseCase submitClaim;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void retryWithTheSameKeyCreatesOneClaimOneEventAndOneKeyRow() {
        ClaimDetails details = uniqueIncident();
        UUID key = UUID.randomUUID();

        SubmissionResult first = submitClaim.submit(new SubmitClaimCommand(details, key));
        SubmissionResult retry = submitClaim.submit(new SubmitClaimCommand(details, key));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.claim().claimNumber()).isEqualTo(first.claim().claimNumber());
        assertThat(count("SELECT count(*) FROM claim WHERE policy_number = ?", details.policyNumber())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", first.claim().claimNumber().value()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM idempotency_key WHERE idempotency_key = ?", key)).isEqualTo(1);
    }

    /** The race a double-click or an impatient client produces: two requests, same key, same moment. */
    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneClaim() throws Exception {
        ClaimDetails details = uniqueIncident();
        UUID key = UUID.randomUUID();
        int requests = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<CompletableFuture<SubmissionResult>> results = java.util.stream.IntStream.range(0, requests)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return submitClaim.submit(new SubmitClaimCommand(details, key));
                    }, pool))
                    .toList();
            start.countDown();

            List<SubmissionResult> done = results.stream().map(CompletableFuture::join).toList();

            assertThat(done).extracting(r -> r.claim().claimNumber()).containsOnly(done.getFirst().claim().claimNumber());
            assertThat(done).filteredOn(r -> !r.replayed()).hasSize(1);
            assertThat(count("SELECT count(*) FROM claim WHERE policy_number = ?", details.policyNumber())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void repeatReportOfTheSameIncidentIsStoredWithItsDuplicateFlag() {
        ClaimDetails details = uniqueIncident();

        SubmissionResult first = submitClaim.submit(new SubmitClaimCommand(details, UUID.randomUUID()));
        SubmissionResult second = submitClaim.submit(new SubmitClaimCommand(details, UUID.randomUUID()));

        assertThat(second.replayed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT possible_duplicate_of FROM claim WHERE claim_number = ?", String.class,
                second.claim().claimNumber().value())).isEqualTo(first.claim().claimNumber().value());
        assertThat(jdbc.queryForObject("SELECT possible_duplicate_of FROM claim WHERE claim_number = ?", String.class,
                first.claim().claimNumber().value())).isNull();
    }

    private int count(String sql, Object argument) {
        return jdbc.queryForObject(sql, Integer.class, argument);
    }

    /** A policy number no other test uses, so incident matching only sees this test's claims. */
    private static ClaimDetails uniqueIncident() {
        ClaimDetails v = validDetails();
        String policy = "HO-%08d".formatted(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));
        Customer customer = new Customer(v.customer().firstName(), v.customer().lastName(), v.customer().email(),
                v.customer().phone());
        return new ClaimDetails(customer, policy, LocalDate.now(WINNIPEG).minusDays(2), v.lossType(),
                v.propertyAddress(), v.description());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
