package com.redrivermutual.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real PostgreSQL and RabbitMQ, the same images as docker-compose.yml. Mail is configured
 * separately ({@link MailpitTestConfiguration}) so a test can point SMTP somewhere unreachable.
 * One container of each per JVM; tests must not assume empty tables or queues.
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
