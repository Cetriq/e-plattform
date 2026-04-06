package se.eplatform.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import se.eplatform.notification.config.EmailConfig;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailConfig emailConfig;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine, EmailConfig emailConfig) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.emailConfig = emailConfig;
    }

    /**
     * Send an HTML email using a Thymeleaf template.
     */
    public void sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send to: {} with subject: {}", to, subject);
            logEmailContent(templateName, variables);
            return;
        }

        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("baseUrl", emailConfig.getBaseUrl());

            String htmlContent = templateEngine.process("email/" + templateName, context);

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Email sent to: {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to: {} with subject: {}", to, subject, e);
        }
    }

    /**
     * Send a plain HTML email.
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(emailConfig.getFromAddress());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * Send a simple text email.
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send to: {} with subject: {} text: {}", to, subject, text);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(emailConfig.getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);

            mailSender.send(message);
            log.info("Simple email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send simple email to: {}", to, e);
        }
    }

    private void logEmailContent(String templateName, Map<String, Object> variables) {
        log.debug("Template: {}", templateName);
        variables.forEach((key, value) -> log.debug("  {}: {}", key, value));
    }
}
