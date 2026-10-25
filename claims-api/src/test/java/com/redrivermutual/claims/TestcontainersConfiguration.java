package com.redrivermutual.claims;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real PostgreSQL 16 and RabbitMQ 4.1 for integration tests, the same images as
 * docker-compose.yml, so migrations, constraints, jsonb, publisher confirms and routing behave
 * exactly as in production. Docker must be running.
 *
 * <p>One container of each is shared by every test context in the JVM; tests must not assume
 * empty tables or queues.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return RABBITMQ;
    }
}
