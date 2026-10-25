package com.redrivermutual.notification.application;

import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.application.port.out.EmailSender;
import com.redrivermutual.notification.application.port.out.EmailTemplateRenderer;
import com.redrivermutual.notification.application.port.out.ProcessedEventStore;
import com.redrivermutual.notification.application.port.out.RenderedEmail;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import com.redrivermutual.notification.domain.EmailAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends one confirmation email per ClaimSubmitted event.
 *
 * <p>Must run inside a transaction (see {@code TransactionalSendClaimConfirmation}): the
 * processed-event row is inserted first, the email sent second, and both "happen" only when the
 * transaction commits. If sending fails the row is rolled back and the retry tries again. If the
 * process dies after the email left but before the commit, the event is redelivered and a second
 * email is sent: a rare, accepted duplicate for a notification (architecture doc, section 11).
 */
public class SendClaimConfirmationService implements SendClaimConfirmationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendClaimConfirmationService.class);

    private final ProcessedEventStore processedEvents;
    private final EmailTemplateRenderer renderer;
    private final EmailSender emailSender;
    private final Clock clock;

    public SendClaimConfirmationService(ProcessedEventStore processedEvents,
                                        EmailTemplateRenderer renderer,
                                        EmailSender emailSender,
                                        Clock clock) {
        this.processedEvents = Objects.requireNonNull(processedEvents);
        this.renderer = Objects.requireNonNull(renderer);
        this.emailSender = Objects.requireNonNull(emailSender);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Outcome handle(UUID eventId, ClaimConfirmation confirmation) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(confirmation, "confirmation");

        if (!processedEvents.markProcessed(eventId, confirmation.claimNumber(), clock.instant())) {
            log.info("Duplicate event skipped eventId={} claimNumber={}", eventId, confirmation.claimNumber());
            return Outcome.DUPLICATE_SKIPPED;
        }

        RenderedEmail email = renderer.renderClaimConfirmation(confirmation);
        emailSender.send(confirmation.recipientEmail(), email);

        log.info("Confirmation email sent eventId={} claimNumber={} to={}",
                eventId, confirmation.claimNumber(), EmailAddresses.mask(confirmation.recipientEmail()));
        return Outcome.SENT;
    }
}
