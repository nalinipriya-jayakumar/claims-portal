package com.redrivermutual.claims.application;

import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.ClaimStatus;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import com.redrivermutual.claims.domain.InvalidDateOfLossException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redrivermutual.claims.ClaimFixtures.FIXED_CLOCK;
import static com.redrivermutual.claims.ClaimFixtures.NOW;
import static com.redrivermutual.claims.ClaimFixtures.TODAY;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test with hand-written fakes for the ports: no Spring context, no database. */
class SubmitClaimServiceTest {

    private final FakeClaimRepository repository = new FakeClaimRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final AtomicInteger numbersIssued = new AtomicInteger();
    private final ClaimNumberGenerator generator = () -> ClaimNumber.of(2026, numbersIssued.incrementAndGet());
    private final SubmitClaimService service = new SubmitClaimService(
            repository, generator, outbox, new DateOfLossPolicy(FIXED_CLOCK, 365), FIXED_CLOCK);

    @Test
    void validClaimIsNumberedSavedAndReturnedAsSubmitted() {
        Claim claim = service.submit(validDetails());

        assertThat(claim.claimNumber()).isEqualTo(ClaimNumber.of(2026, 1));
        assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.submittedAt()).isEqualTo(NOW);
        assertThat(claim.details()).isEqualTo(validDetails());
        assertThat(repository.findByClaimNumber(claim.claimNumber())).contains(claim);
    }

    @Test
    void submittedEventIsRecordedForTheSavedClaim() {
        Claim claim = service.submit(validDetails());

        assertThat(outbox.recorded).containsExactly(claim);
    }

    @Test
    void eachSubmissionGetsItsOwnNumber() {
        Claim first = service.submit(validDetails());
        Claim second = service.submit(validDetails());

        assertThat(second.claimNumber()).isNotEqualTo(first.claimNumber());
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void futureDateOfLossIsRejectedBeforeANumberIsIssuedOrAnythingWritten() {
        ClaimDetails valid = validDetails();
        ClaimDetails future = new ClaimDetails(valid.customer(), valid.policyNumber(), TODAY.plusDays(1),
                valid.lossType(), valid.propertyAddress(), valid.description());

        assertThatThrownBy(() -> service.submit(future)).isInstanceOf(InvalidDateOfLossException.class);
        assertThat(numbersIssued).hasValue(0);
        assertThat(repository.claims).isEmpty();
        assertThat(outbox.recorded).isEmpty();
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final Map<ClaimNumber, Claim> claims = new HashMap<>();

        @Override
        public void save(Claim claim) {
            claims.put(claim.claimNumber(), claim);
        }

        @Override
        public Optional<Claim> findByClaimNumber(ClaimNumber claimNumber) {
            return Optional.ofNullable(claims.get(claimNumber));
        }
    }

    private static final class RecordingOutbox implements OutboxPort {
        private final List<Claim> recorded = new ArrayList<>();

        @Override
        public void recordClaimSubmitted(Claim claim) {
            recorded.add(claim);
        }
    }
}
