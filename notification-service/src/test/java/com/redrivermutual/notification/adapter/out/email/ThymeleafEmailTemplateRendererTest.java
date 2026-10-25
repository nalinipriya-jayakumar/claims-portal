package com.redrivermutual.notification.adapter.out.email;

import com.redrivermutual.notification.application.port.out.RenderedEmail;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.redrivermutual.notification.NotificationFixtures.confirmation;
import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafEmailTemplateRendererTest {

    private final ThymeleafEmailTemplateRenderer renderer = new ThymeleafEmailTemplateRenderer();

    @Test
    void bothVersionsContainTheClaimNumberNameLossAndNextSteps() {
        RenderedEmail email = renderer.renderClaimConfirmation(confirmation("CLM-2026-000123"));

        assertThat(email.subject()).isEqualTo("We received your claim CLM-2026-000123");
        for (String body : new String[] {email.textBody(), email.htmlBody()}) {
            assertThat(body).contains("CLM-2026-000123", "Hello Jane,", "Water damage", "October 15, 2026",
                    "What happens next");
        }
    }

    @Test
    void htmlEscapesEventValues() {
        ClaimConfirmation hostile = new ClaimConfirmation("CLM-2026-000123", "<script>alert(1)</script>",
                "jane@example.com", "FIRE", LocalDate.of(2026, 10, 15));

        String html = renderer.renderClaimConfirmation(hostile).htmlBody();

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void missingFirstNameFallsBackToAGenericGreeting() {
        ClaimConfirmation noName = new ClaimConfirmation("CLM-2026-000123", null, "jane@example.com", "THEFT",
                LocalDate.of(2026, 10, 15));

        RenderedEmail email = renderer.renderClaimConfirmation(noName);

        assertThat(email.textBody()).startsWith("Hello,");
        assertThat(email.htmlBody()).contains("Hello,").doesNotContain("Hello null");
    }
}
