package com.redrivermutual.notification.adapter.out.email;

import com.redrivermutual.notification.application.port.out.EmailDeliveryException;
import com.redrivermutual.notification.application.port.out.EmailSender;
import com.redrivermutual.notification.application.port.out.RenderedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Sends a multipart (plain text + HTML) email through Spring's {@link JavaMailSender}. */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${notification.email.from}") String from) {
        this.mailSender = mailSender;
        try {
            this.from = new InternetAddress(from, true);
        } catch (MessagingException e) {
            throw new IllegalArgumentException("notification.email.from is not a valid address", e);
        }
    }

    @Override
    public void send(String recipient, RenderedEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            // Strict parsing rejects anything that is not exactly one address.
            helper.setTo(new InternetAddress(recipient, true));
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), email.htmlBody());
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            // The cause can include the recipient address, so the message here names only the failure.
            throw new EmailDeliveryException("Email could not be sent: " + e.getClass().getSimpleName(), e);
        }
    }
}
