package com.redrivermutual.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.List;

/**
 * Mailpit, the same SMTP capture server reviewers use in docker-compose.yml, plus a small client
 * for its HTTP API so tests can assert on the emails that were actually delivered.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MailpitTestConfiguration {

    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;

    @SuppressWarnings("resource")
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.24")
            .withExposedPorts(SMTP_PORT, HTTP_PORT)
            .waitingFor(Wait.forHttp("/livez").forPort(HTTP_PORT));

    static {
        MAILPIT.start();
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties() {
        return registry -> {
            registry.add("spring.mail.host", MAILPIT::getHost);
            registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
        };
    }

    @Bean
    Mailpit mailpit() {
        return new Mailpit(RestClient.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(HTTP_PORT)));
    }

    /** Captured email as Mailpit reports it. */
    public record CapturedEmail(String id, String subject, List<String> to, String text, String html) {
    }

    public static final class Mailpit {
        private final RestClient http;

        Mailpit(RestClient http) {
            this.http = http;
        }

        /** Every captured email whose subject contains {@code text}, with bodies. */
        public List<CapturedEmail> withSubjectContaining(String text) {
            JsonNode list = http.get().uri("/api/v1/messages?limit=500").retrieve().body(JsonNode.class);
            List<CapturedEmail> matches = new ArrayList<>();
            for (JsonNode summary : list.path("messages")) {
                if (summary.path("Subject").asText().contains(text)) {
                    JsonNode full = http.get().uri("/api/v1/message/{id}", summary.path("ID").asText())
                            .retrieve().body(JsonNode.class);
                    List<String> to = new ArrayList<>();
                    full.path("To").forEach(address -> to.add(address.path("Address").asText()));
                    matches.add(new CapturedEmail(full.path("ID").asText(), full.path("Subject").asText(), to,
                            full.path("Text").asText(), full.path("HTML").asText()));
                }
            }
            return matches;
        }
    }
}
