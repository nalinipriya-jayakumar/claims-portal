package com.redrivermutual.claims.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Business rules that are assumptions in the brief (A-03) and therefore configurable.
 *
 * @param zone                 calendar used to decide what "today" is for the date of loss
 * @param dateOfLossMaxAgeDays oldest accepted date of loss, in days before today
 */
@Validated
@ConfigurationProperties("claims.rules")
public record ClaimRulesProperties(
        @NotNull @DefaultValue("America/Winnipeg") ZoneId zone,
        @Min(0) @DefaultValue("365") int dateOfLossMaxAgeDays) {
}
