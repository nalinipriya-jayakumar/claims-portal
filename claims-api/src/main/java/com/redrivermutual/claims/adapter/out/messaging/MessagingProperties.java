package com.redrivermutual.claims.adapter.out.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param confirmTimeout how long to wait for RabbitMQ to confirm a published message
 */
@ConfigurationProperties("claims.messaging")
public record MessagingProperties(@DefaultValue("5s") Duration confirmTimeout) {
}
