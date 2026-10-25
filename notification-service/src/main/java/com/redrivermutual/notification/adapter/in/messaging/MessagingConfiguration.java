package com.redrivermutual.notification.adapter.in.messaging;

import com.redrivermutual.events.ClaimEventsMessaging;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * This service's broker topology and failure handling (the consumer owns its queues):
 *
 * <pre>
 *   claims.events --claim.submitted--> claims.submitted --(rejected)--> claims.submitted.dlx --> claims.submitted.dlq
 * </pre>
 *
 * A failed message is retried in-process with exponential backoff; when attempts run out, or
 * at once for a {@link NonRetryableEventException}, it is rejected and RabbitMQ dead-letters it.
 * Messages in the DLQ keep their headers (plus {@code x-death}) and can be replayed by shovelling
 * them back to the exchange after the cause is fixed.
 */
@Configuration
@EnableConfigurationProperties(NotificationMessagingProperties.class)
class MessagingConfiguration {

    @Bean
    Declarables claimSubmittedTopology(NotificationMessagingProperties properties) {
        // Same declaration as the publisher's, so whichever service starts first creates it.
        TopicExchange events = ExchangeBuilder.topicExchange(ClaimEventsMessaging.EXCHANGE).durable(true).build();
        DirectExchange deadLetters = ExchangeBuilder.directExchange(properties.deadLetterExchange()).durable(true).build();

        Queue queue = QueueBuilder.durable(properties.queue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.deadLetterQueue())
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue()).build();

        return new Declarables(
                events,
                deadLetters,
                queue,
                deadLetterQueue,
                BindingBuilder.bind(queue).to(events).with(ClaimEventsMessaging.CLAIM_SUBMITTED_ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(properties.deadLetterQueue()));
    }

    /** Replaces Boot's default factory so retries can tell transient failures from permanent ones. */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            NotificationMessagingProperties properties,
            MeterRegistry meterRegistry) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);

        // traverseCauses: the listener's exception arrives wrapped in ListenerExecutionFailedException.
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(properties.maxAttempts(),
                Map.of(NonRetryableEventException.class, false), true, true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(properties.initialInterval().toMillis(), properties.multiplier(),
                        properties.maxInterval().toMillis())
                .recoverer(new DeadLetteringRecoverer(meterRegistry))
                .build());

        // The recoverer has already logged; reject quietly instead of logging the full exception again.
        factory.setErrorHandler(t -> {
            throw new AmqpRejectAndDontRequeueException("Message rejected and dead-lettered");
        });
        return factory;
    }
}
