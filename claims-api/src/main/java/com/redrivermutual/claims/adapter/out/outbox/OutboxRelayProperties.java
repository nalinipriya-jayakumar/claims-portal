package com.redrivermutual.claims.adapter.out.outbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param schedulingEnabled run the relay on a timer; tests switch it off and call it directly
 * @param interval          pause between relay runs; the publish lag a customer can see (ADR-0002)
 * @param batchSize         rows locked and published per run
 */
@Validated
@ConfigurationProperties("claims.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean schedulingEnabled,
        @DefaultValue("1s") Duration interval,
        @Min(1) @DefaultValue("50") int batchSize) {
}
