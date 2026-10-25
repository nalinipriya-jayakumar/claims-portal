package com.redrivermutual.notification.adapter.in.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.redrivermutual.events.ClaimEventsMessaging;
import com.redrivermutual.events.ClaimSubmittedEvent;
import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Inbound adapter: turns a {@code ClaimSubmitted} message into a {@link ClaimConfirmation} and
 * hands it to the use case. Anything that cannot be turned into a valid confirmation is
 * permanent, so it is reported as {@link NonRetryableEventException}.
 *
 * <p>Unknown JSON fields are ignored (tolerant reader), so the publisher can add fields to v1
 * without breaking this consumer.
 */
@Component
class ClaimSubmittedListener {

    static final String MDC_KEY = "correlationId";

    /** The header comes from another service; restrict it before it reaches the logs. */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private final ObjectReader eventReader;
    private final SendClaimConfirmationUseCase sendClaimConfirmation;

    ClaimSubmittedListener(ObjectMapper objectMapper, SendClaimConfirmationUseCase sendClaimConfirmation) {
        // Explicit rather than relying on the shared mapper's default.
        this.eventReader = objectMapper.readerFor(ClaimSubmittedEvent.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.sendClaimConfirmation = sendClaimConfirmation;
    }

    @RabbitListener(queues = "${notification.messaging.queue}")
    void onClaimSubmitted(Message message) {
        try (var ignored = MDC.putCloseable(MDC_KEY, correlationId(message))) {
            ClaimSubmittedEvent event = parse(message);
            sendClaimConfirmation.handle(event.eventId(), toConfirmation(event));
        }
    }

    private ClaimSubmittedEvent parse(Message message) {
        ClaimSubmittedEvent event;
        try {
            event = eventReader.readValue(message.getBody());
        } catch (IOException e) {
            // Jackson's message can quote the payload, so it is not passed on.
            throw new NonRetryableEventException("Unreadable ClaimSubmitted payload: " + e.getClass().getSimpleName());
        }
        if (!ClaimSubmittedEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new NonRetryableEventException("Unexpected event type " + event.eventType());
        }
        if (event.eventVersion() != ClaimSubmittedEvent.CURRENT_VERSION) {
            throw new NonRetryableEventException("Unsupported event version " + event.eventVersion());
        }
        return event;
    }

    private static ClaimConfirmation toConfirmation(ClaimSubmittedEvent event) {
        ClaimSubmittedEvent.Data data = event.data();
        try {
            return new ClaimConfirmation(data.claimNumber(), data.customerFirstName(), data.customerEmail(),
                    data.lossType(), data.dateOfLoss());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NonRetryableEventException("Invalid ClaimSubmitted event " + event.eventId() + ": " + e.getMessage());
        }
    }

    private static String correlationId(Message message) {
        Object header = message.getMessageProperties().getHeader(ClaimEventsMessaging.HEADER_CORRELATION_ID);
        return header instanceof String id && SAFE_CORRELATION_ID.matcher(id).matches() ? id : "";
    }
}
