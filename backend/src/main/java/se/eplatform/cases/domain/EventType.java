package se.eplatform.cases.domain;

/**
 * Types of case events for audit logging.
 */
public enum EventType {
    CREATED,
    UPDATED,
    SUBMITTED,
    SAVED_DRAFT,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    MESSAGE_SENT,
    MESSAGE_READ,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    SIGNED,
    SIGNATURE_REQUESTED,
    PAYMENT_INITIATED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    EXPORTED,
    ARCHIVED,
    RESTORED,
    COMMENT_ADDED,
    COMMENT_DELETED
}
