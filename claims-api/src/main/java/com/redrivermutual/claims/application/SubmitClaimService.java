package com.redrivermutual.claims.application;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.IdempotencyStore;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.ClaimFingerprint;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Submits a claim: check the rules, then either replay an earlier submission with the same
 * idempotency key, or number the claim, flag it if it repeats a known incident, save it, record
 * the ClaimSubmitted event and remember the key.
 *
 * <p>Plain Java with constructor-injected ports, so it is unit tested without Spring. Everything
 * it writes must commit together; the caller provides that transaction
 * (see {@code TransactionalSubmitClaimUseCase}).
 */
public class SubmitClaimService implements SubmitClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitClaimService.class);

    private final ClaimRepository claimRepository;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final OutboxPort outbox;
    private final IdempotencyStore idempotencyStore;
    private final DateOfLossPolicy dateOfLossPolicy;
    private final Clock clock;

    public SubmitClaimService(ClaimRepository claimRepository,
                              ClaimNumberGenerator claimNumberGenerator,
                              OutboxPort outbox,
                              IdempotencyStore idempotencyStore,
                              DateOfLossPolicy dateOfLossPolicy,
                              Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository);
        this.claimNumberGenerator = Objects.requireNonNull(claimNumberGenerator);
        this.outbox = Objects.requireNonNull(outbox);
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore);
        this.dateOfLossPolicy = Objects.requireNonNull(dateOfLossPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public SubmissionResult submit(SubmitClaimCommand command) {
        Objects.requireNonNull(command, "command");
        ClaimDetails details = command.details();
        // The web layer checks this too; enforcing it here keeps the rule true for every caller.
        dateOfLossPolicy.enforce(details.dateOfLoss());

        UUID key = command.idempotencyKey();
        String fingerprint = ClaimFingerprint.of(details);
        if (key != null) {
            Optional<SubmissionResult> replay = replayIfKnown(key, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        // Case 2: a new report of a known incident is accepted, and flagged for the claims team.
        Optional<ClaimNumber> duplicateOf = claimRepository.findEarliestForIncident(
                details.policyNumber(), details.dateOfLoss(), details.lossType());

        Claim claim = Claim.submit(claimNumberGenerator.next(), details, clock.instant(), duplicateOf.orElse(null));
        claimRepository.save(claim);
        outbox.recordClaimSubmitted(claim);
        if (key != null) {
            idempotencyStore.record(key, fingerprint, claim.claimNumber(), clock.instant());
        }

        if (duplicateOf.isPresent()) {
            log.info("Claim submitted claimNumber={} lossType={} possibleDuplicateOf={}",
                    claim.claimNumber(), details.lossType(), duplicateOf.get());
        } else {
            log.info("Claim submitted claimNumber={} lossType={}", claim.claimNumber(), details.lossType());
        }
        return SubmissionResult.created(claim);
    }

    /** Case 1: the same submission sent again (a retry) gets the original answer. */
    private Optional<SubmissionResult> replayIfKnown(UUID key, String fingerprint) {
        return idempotencyStore.find(key).map(entry -> {
            if (!entry.fingerprint().equals(fingerprint)) {
                log.warn("Idempotency key reused for a different claim; originally claimNumber={}", entry.claimNumber());
                throw new IdempotencyKeyConflictException();
            }
            Claim original = claimRepository.findByClaimNumber(entry.claimNumber())
                    .orElseThrow(() -> new IllegalStateException("Idempotency entry without its claim " + entry.claimNumber()));
            log.info("Replayed earlier submission claimNumber={}", original.claimNumber());
            return SubmissionResult.replayed(original);
        });
    }
}
