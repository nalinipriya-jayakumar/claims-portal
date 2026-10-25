package com.redrivermutual.notification.adapter.out.email;

import com.redrivermutual.notification.application.port.out.EmailTemplateRenderer;
import com.redrivermutual.notification.application.port.out.RenderedEmail;
import com.redrivermutual.notification.domain.ClaimConfirmation;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the confirmation email from {@code templates/email/claim-confirmation.{html,txt}}.
 * HTML mode escapes every value, so names and other event fields cannot inject markup.
 */
@Component
public class ThymeleafEmailTemplateRenderer implements EmailTemplateRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.CANADA);

    private static final Map<String, String> LOSS_TYPE_LABELS = Map.of(
            "FIRE", "Fire",
            "WATER", "Water damage",
            "WIND_HAIL", "Wind or hail",
            "THEFT", "Theft",
            "VANDALISM", "Vandalism",
            "OTHER", "Other");

    private final TemplateEngine engine = new TemplateEngine();

    public ThymeleafEmailTemplateRenderer() {
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
    }

    @Override
    public RenderedEmail renderClaimConfirmation(ClaimConfirmation confirmation) {
        String subject = "We received your claim " + confirmation.claimNumber();

        Context context = new Context(Locale.CANADA);
        context.setVariable("subject", subject);
        context.setVariable("firstName", confirmation.customerFirstName());
        context.setVariable("claimNumber", confirmation.claimNumber());
        context.setVariable("lossType", LOSS_TYPE_LABELS.getOrDefault(confirmation.lossType(), "Other"));
        context.setVariable("dateOfLoss", DATE.format(confirmation.dateOfLoss()));

        return new RenderedEmail(
                subject,
                engine.process("claim-confirmation.txt", context),
                engine.process("claim-confirmation.html", context));
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setTemplateMode(mode);
        resolver.setResolvablePatterns(java.util.Set.of(pattern));
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        resolver.setOrder(order);
        return resolver;
    }
}
