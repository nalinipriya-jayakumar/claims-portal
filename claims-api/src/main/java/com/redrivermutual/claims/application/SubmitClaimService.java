package com.redrivermutual.claims.application;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;

/**
 * Submits a claim: check the rules, number it, save it, record the ClaimSubmitted event.
 *
 * <p>Plain Java with constructor-injected ports, so it is unit tested without Spring. The save
 * and the outbox record must commit together; the caller provides that transaction (see
 * {@code TransactionalSubmitClaimUseCase}).
 */
public class SubmitClaimService implements SubmitClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitClaimService.class);

    private final ClaimRepository claimRepository;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final OutboxPort outbox;
    private final DateOfLossPolicy dateOfLossPolicy;
    private final Clock clock;

    public SubmitClaimService(ClaimRepository claimRepository,
                              ClaimNumberGenerator claimNumberGenerator,
                              OutboxPort outbox,
                              DateOfLossPolicy dateOfLossPolicy,
                              Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository);
        this.claimNumberGenerator = Objects.requireNonNull(claimNumberGenerator);
        this.outbox = Objects.requireNonNull(outbox);
        this.dateOfLossPolicy = Objects.requireNonNull(dateOfLossPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Claim submit(ClaimDetails details) {
        Objects.requireNonNull(details, "details");
        // The web layer checks this too; enforcing it here keeps the rule true for every caller.
        dateOfLossPolicy.enforce(details.dateOfLoss());

        Claim claim = Claim.submit(claimNumberGenerator.next(), details, clock.instant());
        claimRepository.save(claim);
        outbox.recordClaimSubmitted(claim);

        log.info("Claim submitted claimNumber={} lossType={}", claim.claimNumber(), details.lossType());
        return claim;
    }
}
