package com.redrivermutual.notification.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.redrivermutual.events.ClaimEventsMessaging;
import com.redrivermutual.events.ClaimSubmittedEvent;
import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.redrivermutual.notification.NotificationFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClaimSubmittedListenerTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final SendClaimConfirmationUseCase useCase = mock(SendClaimConfirmationUseCase.class);
    private final ClaimSubmittedListener listener = new ClaimSubmittedListener(JSON, useCase);

    @Test
    void validEventIsHandedToTheUseCaseWithItsEventId() throws Exception {
        UUID eventId = UUID.randomUUID();

        listener.onClaimSubmitted(message(JSON.writeValueAsString(event(eventId, "CLM-2026-000123", "Jane@Example.com"))));

        ArgumentCaptor<ClaimConfirmation> confirmation = ArgumentCaptor.forClass(ClaimConfirmation.class);
        verify(useCase).handle(eq(eventId), confirmation.capture());
        assertThat(confirmation.getValue().claimNumber()).isEqualTo("CLM-2026-000123");
        assertThat(confirmation.getValue().recipientEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void unknownFieldsAreIgnoredSoThePublisherCanEvolveV1() throws Exception {
        String json = JSON.writeValueAsString(event(UUID.randomUUID(), "CLM-2026-000123", "jane@example.com"))
                .replaceFirst("\\{", "{\"addedLater\":\"x\",");

        listener.onClaimSubmitted(message(json));

        verify(useCase).handle(any(), any());
    }

    @Test
    void unreadableBodyIsNotRetryable() {
        assertThatThrownBy(() -> listener.onClaimSubmitted(message("not json")))
                .isInstanceOf(NonRetryableEventException.class);
        verify(useCase, never()).handle(any(), any());
    }

    @Test
    void unsupportedVersionIsNotRetryable() throws Exception {
        ClaimSubmittedEvent v1 = event(UUID.randomUUID(), "CLM-2026-000123", "jane@example.com");
        ClaimSubmittedEvent v2 = new ClaimSubmittedEvent(v1.eventId(), v1.eventType(), 2, v1.occurredAt(),
                v1.correlationId(), v1.data());

        assertThatThrownBy(() -> listener.onClaimSubmitted(message(JSON.writeValueAsString(v2))))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("version 2");
    }

    @Test
    void invalidEmailIsNotRetryableAndTheErrorDoesNotQuoteIt() throws Exception {
        String json = JSON.writeValueAsString(event(UUID.randomUUID(), "CLM-2026-000123", "secret-person@nowhere"));

        assertThatThrownBy(() -> listener.onClaimSubmitted(message(json)))
                .isInstanceOf(NonRetryableEventException.class)
                .message().doesNotContain("secret-person");
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(ClaimEventsMessaging.HEADER_CORRELATION_ID, "test-correlation");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
