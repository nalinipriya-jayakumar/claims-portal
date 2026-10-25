package com.redrivermutual.claims.application;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.IdempotencyKeyConflictException;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmissionResult;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.IdempotencyStore;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.ClaimStatus;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import com.redrivermutual.claims.domain.InvalidDateOfLossException;
import com.redrivermutual.claims.domain.LossType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final FakeIdempotencyStore idempotency = new FakeIdempotencyStore();
    private final AtomicInteger numbersIssued = new AtomicInteger();
    private final ClaimNumberGenerator generator = () -> ClaimNumber.of(2026, numbersIssued.incrementAndGet());
    private final SubmitClaimService service = new SubmitClaimService(
            repository, generator, outbox, idempotency, new DateOfLossPolicy(FIXED_CLOCK, 365), FIXED_CLOCK);

    @Test
    void validClaimIsNumberedSavedAndReturnedAsSubmitted() {
        SubmissionResult result = service.submit(SubmitClaimCommand.withoutKey(validDetails()));

        Claim claim = result.claim();
        assertThat(result.replayed()).isFalse();
        assertThat(claim.claimNumber()).isEqualTo(ClaimNumber.of(2026, 1));
        assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.submittedAt()).isEqualTo(NOW);
        assertThat(claim.details()).isEqualTo(validDetails());
        assertThat(claim.duplicateOf()).isEmpty();
        assertThat(repository.findByClaimNumber(claim.claimNumber())).contains(claim);
        assertThat(outbox.recorded).containsExactly(claim);
    }

    @Test
    void futureDateOfLossIsRejectedBeforeANumberIsIssuedOrAnythingWritten() {
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> service.submit(new SubmitClaimCommand(withDateOfLoss(TODAY.plusDays(1)), key)))
                .isInstanceOf(InvalidDateOfLossException.class);
        assertThat(numbersIssued).hasValue(0);
        assertThat(repository.claims).isEmpty();
        assertThat(outbox.recorded).isEmpty();
        assertThat(idempotency.entries).as("a rejected request does not use up its key").isEmpty();
    }

    // --- Case 1: retries with an Idempotency-Key ---

    @Test
    void retryWithTheSameKeyReturnsTheOriginalClaimAndCreatesNothing() {
        UUID key = UUID.randomUUID();
        Claim original = service.submit(new SubmitClaimCommand(validDetails(), key)).claim();

        SubmissionResult retry = service.submit(new SubmitClaimCommand(validDetails(), key));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.claim()).isEqualTo(original);
        assertThat(numbersIssued).as("no second claim number").hasValue(1);
        assertThat(repository.claims).hasSize(1);
        assertThat(outbox.recorded).as("no second event, so no second email").hasSize(1);
    }

    @Test
    void sameKeyWithADifferentClaimIsAConflictAndCreatesNothing() {
        UUID key = UUID.randomUUID();
        service.submit(new SubmitClaimCommand(validDetails(), key));

        assertThatThrownBy(() -> service.submit(new SubmitClaimCommand(withDateOfLoss(TODAY.minusDays(10)), key)))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(repository.claims).hasSize(1);
        assertThat(outbox.recorded).hasSize(1);
    }

    @Test
    void differentKeysForTheSameContentAreSeparateSubmissions() {
        service.submit(new SubmitClaimCommand(validDetails(), UUID.randomUUID()));

        SubmissionResult second = service.submit(new SubmitClaimCommand(validDetails(), UUID.randomUUID()));

        assertThat(second.replayed()).isFalse();
        assertThat(repository.claims).hasSize(2);
    }

    // --- Case 2: a new report of a known incident is flagged, not rejected ---

    @Test
    void repeatReportOfTheSameIncidentIsAcceptedAndFlaggedWithTheFirstClaim() {
        Claim first = service.submit(SubmitClaimCommand.withoutKey(validDetails())).claim();
        Claim second = service.submit(SubmitClaimCommand.withoutKey(validDetails())).claim();
        Claim third = service.submit(SubmitClaimCommand.withoutKey(validDetails())).claim();

        assertThat(first.duplicateOf()).isEmpty();
        assertThat(second.duplicateOf()).contains(first.claimNumber());
        assertThat(third.duplicateOf()).as("always points at the earliest").contains(first.claimNumber());
        assertThat(outbox.recorded).as("each customer still gets a confirmation").hasSize(3);
    }

    @Test
    void differentDateOrLossTypeIsNotADuplicate() {
        service.submit(SubmitClaimCommand.withoutKey(validDetails()));
        ClaimDetails v = validDetails();

        Claim otherDate = service.submit(SubmitClaimCommand.withoutKey(withDateOfLoss(TODAY.minusDays(1)))).claim();
        Claim otherType = service.submit(SubmitClaimCommand.withoutKey(new ClaimDetails(v.customer(), v.policyNumber(),
                v.dateOfLoss(), LossType.THEFT, v.propertyAddress(), v.description()))).claim();

        assertThat(otherDate.duplicateOf()).isEmpty();
        assertThat(otherType.duplicateOf()).isEmpty();
    }

    private static ClaimDetails withDateOfLoss(LocalDate date) {
        ClaimDetails v = validDetails();
        return new ClaimDetails(v.customer(), v.policyNumber(), date, v.lossType(), v.propertyAddress(), v.description());
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final Map<ClaimNumber, Claim> claims = new LinkedHashMap<>();

        @Override
        public void save(Claim claim) {
            claims.put(claim.claimNumber(), claim);
        }

        @Override
        public Optional<Claim> findByClaimNumber(ClaimNumber claimNumber) {
            return Optional.ofNullable(claims.get(claimNumber));
        }

        @Override
        public Optional<ClaimNumber> findEarliestForIncident(String policyNumber, LocalDate dateOfLoss, LossType lossType) {
            return claims.values().stream()
                    .filter(c -> c.details().policyNumber().equals(policyNumber)
                            && c.details().dateOfLoss().equals(dateOfLoss)
                            && c.details().lossType() == lossType)
                    .min(Comparator.comparing(Claim::submittedAt).thenComparing(c -> c.claimNumber().value()))
                    .map(Claim::claimNumber);
        }
    }

    private static final class RecordingOutbox implements OutboxPort {
        private final List<Claim> recorded = new ArrayList<>();

        @Override
        public void recordClaimSubmitted(Claim claim) {
            recorded.add(claim);
        }
    }

    private static final class FakeIdempotencyStore implements IdempotencyStore {
        private final Map<UUID, Entry> entries = new HashMap<>();

        @Override
        public Optional<Entry> find(UUID idempotencyKey) {
            return Optional.ofNullable(entries.get(idempotencyKey));
        }

        @Override
        public void record(UUID idempotencyKey, String fingerprint, ClaimNumber claimNumber, Instant at) {
            entries.put(idempotencyKey, new Entry(fingerprint, claimNumber));
        }
    }
}
