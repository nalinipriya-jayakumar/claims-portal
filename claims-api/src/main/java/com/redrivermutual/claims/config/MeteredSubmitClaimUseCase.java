package com.redrivermutual.claims.config;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts outcomes after the transaction commits; a decorator, so the use case stays metrics-free.
 * {@code claims.submitted}: new claims. {@code claims.replayed}: retries answered from an earlier
 * submission. {@code claims.possible_duplicates}: new claims flagged as repeating a known incident.
 */
class MeteredSubmitClaimUseCase implements SubmitClaimUseCase {

    private final SubmitClaimUseCase delegate;
    private final Counter submitted;
    private final Counter replayed;
    private final Counter possibleDuplicates;

    MeteredSubmitClaimUseCase(SubmitClaimUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.submitted = Counter.builder("claims.submitted")
                .description("Claims accepted and committed with their ClaimSubmitted event")
                .register(registry);
        this.replayed = Counter.builder("claims.replayed")
                .description("Retried submissions answered with the original claim (Idempotency-Key)")
                .register(registry);
        this.possibleDuplicates = Counter.builder("claims.possible_duplicates")
                .description("New claims flagged as a possible repeat of an earlier claim for the same incident")
                .register(registry);
    }

    @Override
    public SubmissionResult submit(SubmitClaimCommand command) {
        SubmissionResult result = delegate.submit(command);
        if (result.replayed()) {
            replayed.increment();
        } else {
            submitted.increment();
            if (result.claim().duplicateOf().isPresent()) {
                possibleDuplicates.increment();
            }
        }
        return result;
    }
}
