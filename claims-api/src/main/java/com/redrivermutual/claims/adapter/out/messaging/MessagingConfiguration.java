package com.redrivermutual.claims.adapter.out.messaging;

import com.redrivermutual.events.ClaimEventsMessaging;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchange this service publishes to. Boot's RabbitAdmin creates it on first
 * connection (idempotently). Queues belong to consumers and are declared by them.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
class MessagingConfiguration {

    @Bean
    TopicExchange claimEventsExchange() {
        return ExchangeBuilder.topicExchange(ClaimEventsMessaging.EXCHANGE).durable(true).build();
    }
}
