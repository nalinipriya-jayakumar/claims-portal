package com.redrivermutual.notification.config;

import com.redrivermutual.notification.application.SendClaimConfirmationService;
import com.redrivermutual.notification.application.port.in.SendClaimConfirmationUseCase;
import com.redrivermutual.notification.application.port.out.EmailSender;
import com.redrivermutual.notification.application.port.out.EmailTemplateRenderer;
import com.redrivermutual.notification.application.port.out.ProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Wires the framework-free use case, the same way the Claims API does. */
@Configuration
public class NotificationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SendClaimConfirmationUseCase sendClaimConfirmationUseCase(ProcessedEventStore processedEvents,
                                                              EmailTemplateRenderer renderer,
                                                              EmailSender emailSender,
                                                              Clock clock,
                                                              PlatformTransactionManager transactionManager,
                                                              MeterRegistry meterRegistry) {
        SendClaimConfirmationService service =
                new SendClaimConfirmationService(processedEvents, renderer, emailSender, clock);
        return new MeteredSendClaimConfirmation(
                new TransactionalSendClaimConfirmation(service, new TransactionTemplate(transactionManager)),
                meterRegistry);
    }
}
