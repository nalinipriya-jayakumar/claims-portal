package com.redrivermutual.claims.config;

import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Counts committed claims ({@code claims.submitted}); a decorator, so the use case stays metrics-free. */
class MeteredSubmitClaimUseCase implements SubmitClaimUseCase {

    private final SubmitClaimUseCase delegate;
    private final Counter submitted;

    MeteredSubmitClaimUseCase(SubmitClaimUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.submitted = Counter.builder("claims.submitted")
                .description("Claims accepted and committed with their ClaimSubmitted event")
                .register(registry);
    }

    @Override
    public Claim submit(ClaimDetails details) {
        Claim claim = delegate.submit(details);
        submitted.increment();
        return claim;
    }
}
