package com.redrivermutual.notification.adapter.in.messaging;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param queue           this service's queue; the dead-letter exchange and queue are derived from it
 * @param maxAttempts     total deliveries tried for a transient failure before dead-lettering
 * @param initialInterval first retry delay, multiplied by {@code multiplier} each time up to {@code maxInterval}
 */
@Validated
@ConfigurationProperties("notification.messaging")
public record NotificationMessagingProperties(
        @NotBlank @DefaultValue("claims.submitted") String queue,
        @Min(1) @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration initialInterval,
        @DecimalMin("1.0") @DefaultValue("2.0") double multiplier,
        @DefaultValue("10s") Duration maxInterval) {

    public String deadLetterExchange() {
        return queue + ".dlx";
    }

    public String deadLetterQueue() {
        return queue + ".dlq";
    }
}
