package se.eplatform.notification.domain;

/**
 * Types of notifications that can be sent.
 */
public enum NotificationType {
    // Case lifecycle
    CASE_SUBMITTED,
    CASE_STATUS_CHANGED,
    CASE_COMPLETED,
    CASE_REQUIRES_ACTION,

    // Messages
    NEW_MESSAGE_FROM_MANAGER,
    NEW_MESSAGE_FROM_CITIZEN,

    // Reminders
    CASE_REMINDER,
    INCOMPLETE_DRAFT_REMINDER
}
