package se.eplatform.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.notification.config.EmailConfig;
import se.eplatform.notification.domain.NotificationType;
import se.eplatform.user.domain.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending notifications to users.
 * Handles email notifications for case lifecycle events.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailService emailService;
    private final EmailConfig emailConfig;

    public NotificationService(EmailService emailService, EmailConfig emailConfig) {
        this.emailService = emailService;
        this.emailConfig = emailConfig;
    }

    /**
     * Notify user when their case has been submitted.
     */
    public void notifyCaseSubmitted(Case caseEntity, User user) {
        if (user.getEmail() == null) {
            log.warn("Cannot send notification - user {} has no email", user.getId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName());
        variables.put("referenceNumber", caseEntity.getReferenceNumber());
        variables.put("flowName", caseEntity.getFlow().getName());
        variables.put("caseUrl", buildCaseUrl(caseEntity));

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "Ditt ärende har skickats in - " + caseEntity.getReferenceNumber(),
                "case-submitted",
                variables
        );

        log.info("Sent CASE_SUBMITTED notification for case {} to {}",
                caseEntity.getReferenceNumber(), user.getEmail());
    }

    /**
     * Notify user when case status has changed.
     */
    public void notifyCaseStatusChanged(Case caseEntity, User user, StatusDefinition oldStatus, StatusDefinition newStatus) {
        if (user.getEmail() == null) {
            log.warn("Cannot send notification - user {} has no email", user.getId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName());
        variables.put("referenceNumber", caseEntity.getReferenceNumber());
        variables.put("flowName", caseEntity.getFlow().getName());
        variables.put("oldStatus", oldStatus != null ? oldStatus.getName() : "Inskickad");
        variables.put("newStatus", newStatus.getName());
        variables.put("caseUrl", buildCaseUrl(caseEntity));

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "Status uppdaterad för ärende " + caseEntity.getReferenceNumber(),
                "case-status-changed",
                variables
        );

        log.info("Sent CASE_STATUS_CHANGED notification for case {} to {}",
                caseEntity.getReferenceNumber(), user.getEmail());
    }

    /**
     * Notify user when case is completed.
     */
    public void notifyCaseCompleted(Case caseEntity, User user) {
        if (user.getEmail() == null) {
            log.warn("Cannot send notification - user {} has no email", user.getId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName());
        variables.put("referenceNumber", caseEntity.getReferenceNumber());
        variables.put("flowName", caseEntity.getFlow().getName());
        variables.put("caseUrl", buildCaseUrl(caseEntity));

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "Ditt ärende är avslutat - " + caseEntity.getReferenceNumber(),
                "case-completed",
                variables
        );

        log.info("Sent CASE_COMPLETED notification for case {} to {}",
                caseEntity.getReferenceNumber(), user.getEmail());
    }

    /**
     * Notify user about new message from manager.
     */
    public void notifyNewMessageFromManager(Case caseEntity, User user, ExternalMessage message) {
        if (user.getEmail() == null) {
            log.warn("Cannot send notification - user {} has no email", user.getId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName());
        variables.put("referenceNumber", caseEntity.getReferenceNumber());
        variables.put("flowName", caseEntity.getFlow().getName());
        variables.put("messagePreview", truncateMessage(message.getMessage(), 200));
        variables.put("caseUrl", buildCaseUrl(caseEntity));

        emailService.sendTemplatedEmail(
                user.getEmail(),
                "Nytt meddelande i ärende " + caseEntity.getReferenceNumber(),
                "new-message",
                variables
        );

        log.info("Sent NEW_MESSAGE notification for case {} to {}",
                caseEntity.getReferenceNumber(), user.getEmail());
    }

    /**
     * Notify managers about new message from citizen.
     */
    public void notifyManagersNewMessage(Case caseEntity, User manager, ExternalMessage message) {
        if (manager.getEmail() == null) {
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("managerName", manager.getFirstName());
        variables.put("referenceNumber", caseEntity.getReferenceNumber());
        variables.put("flowName", caseEntity.getFlow().getName());
        variables.put("citizenName", message.getCreatedBy().getFullName());
        variables.put("messagePreview", truncateMessage(message.getMessage(), 200));
        variables.put("caseUrl", buildManagerCaseUrl(caseEntity));

        emailService.sendTemplatedEmail(
                manager.getEmail(),
                "Nytt meddelande i ärende " + caseEntity.getReferenceNumber(),
                "new-message-manager",
                variables
        );
    }

    private String buildCaseUrl(Case caseEntity) {
        return emailConfig.getBaseUrl() + "/citizen/cases/" + caseEntity.getId();
    }

    private String buildManagerCaseUrl(Case caseEntity) {
        return emailConfig.getBaseUrl() + "/manager/cases/" + caseEntity.getId();
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }
}
