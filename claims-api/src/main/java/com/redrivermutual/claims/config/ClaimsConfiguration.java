package com.redrivermutual.claims.config;

import com.redrivermutual.claims.adapter.out.outbox.OutboxRelayProperties;
import com.redrivermutual.claims.application.SubmitClaimService;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.out.ClaimNumberGenerator;
import com.redrivermutual.claims.application.port.out.ClaimRepository;
import com.redrivermutual.claims.application.port.out.IdempotencyStore;
import com.redrivermutual.claims.application.port.out.OutboxPort;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Wires the framework-free domain and use cases. Keeping Spring annotations out of those
 * classes is what lets them be unit tested with plain constructors.
 */
@Configuration
@EnableConfigurationProperties({ClaimRulesProperties.class, OutboxRelayProperties.class})
public class ClaimsConfiguration {

    @Bean
    Clock clock(ClaimRulesProperties rules) {
        return Clock.system(rules.zone());
    }

    @Bean
    DateOfLossPolicy dateOfLossPolicy(Clock clock, ClaimRulesProperties rules) {
        return new DateOfLossPolicy(clock, rules.dateOfLossMaxAgeDays());
    }

    @Bean
    SubmitClaimUseCase submitClaimUseCase(ClaimRepository claimRepository,
                                          ClaimNumberGenerator claimNumberGenerator,
                                          OutboxPort outbox,
                                          IdempotencyStore idempotencyStore,
                                          DateOfLossPolicy dateOfLossPolicy,
                                          Clock clock,
                                          PlatformTransactionManager transactionManager,
                                          MeterRegistry meterRegistry) {
        SubmitClaimService service =
                new SubmitClaimService(claimRepository, claimNumberGenerator, outbox, idempotencyStore,
                        dateOfLossPolicy, clock);
        // Counted only after the transaction commits.
        return new MeteredSubmitClaimUseCase(
                new TransactionalSubmitClaimUseCase(service, new TransactionTemplate(transactionManager)),
                meterRegistry);
    }
}
