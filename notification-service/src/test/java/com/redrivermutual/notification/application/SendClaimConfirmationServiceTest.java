package com.redrivermutual.notification.application;

import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase.Outcome;
import com.redrivermutual.notification.application.port.out.EmailDeliveryException;
import com.redrivermutual.notification.application.port.out.EmailSender;
import com.redrivermutual.notification.application.port.out.ProcessedEventStore;
import com.redrivermutual.notification.application.port.out.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redrivermutual.notification.NotificationFixtures.confirmation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test with fakes: no Spring, database, broker or mail server. */
class SendClaimConfirmationServiceTest {

    private final InMemoryProcessedEvents processedEvents = new InMemoryProcessedEvents();
    private final RecordingSender sender = new RecordingSender();
    private final SendClaimConfirmationService service = new SendClaimConfirmationService(
            processedEvents,
            c -> new RenderedEmail("We received your claim " + c.claimNumber(), "text " + c.claimNumber(), "<p>html</p>"),
            sender,
            Clock.fixed(Instant.parse("2026-10-18T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void firstDeliverySendsTheEmailToTheCustomerWithTheClaimNumber() {
        UUID eventId = UUID.randomUUID();

        Outcome outcome = service.handle(eventId, confirmation("CLM-2026-000123"));

        assertThat(outcome).isEqualTo(Outcome.SENT);
        assertThat(sender.sent).singleElement().satisfies(sent -> {
            assertThat(sent.recipient()).isEqualTo("jane.oneil@example.com");
            assertThat(sent.email().subject()).contains("CLM-2026-000123");
        });
        assertThat(processedEvents.ids).containsExactly(eventId);
    }

    @Test
    void redeliveredEventIsSkippedWithoutASecondEmail() {
        UUID eventId = UUID.randomUUID();
        service.handle(eventId, confirmation("CLM-2026-000123"));

        Outcome second = service.handle(eventId, confirmation("CLM-2026-000123"));

        assertThat(second).isEqualTo(Outcome.DUPLICATE_SKIPPED);
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void sendFailurePropagatesSoTheCallerRetries() {
        sender.fail = true;

        assertThatThrownBy(() -> service.handle(UUID.randomUUID(), confirmation("CLM-2026-000123")))
                .isInstanceOf(EmailDeliveryException.class);
        // The processed-event insert is rolled back by the surrounding transaction in production;
        // SmtpOutageIntegrationTest proves that against PostgreSQL.
    }

    private static final class InMemoryProcessedEvents implements ProcessedEventStore {
        private final Set<UUID> ids = new HashSet<>();

        @Override
        public boolean markProcessed(UUID eventId, String claimNumber, Instant processedAt) {
            return ids.add(eventId);
        }
    }

    private record Sent(String recipient, RenderedEmail email) {
    }

    private static final class RecordingSender implements EmailSender {
        private final List<Sent> sent = new ArrayList<>();
        private boolean fail;

        @Override
        public void send(String recipient, RenderedEmail email) {
            if (fail) {
                throw new EmailDeliveryException("SMTP unavailable", null);
            }
            sent.add(new Sent(recipient, email));
        }
    }
}
