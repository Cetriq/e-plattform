package se.eplatform.flow.domain;

/**
 * Semantic types for case statuses.
 */
public enum StatusType {
    DRAFT,
    SUBMITTED,
    IN_PROGRESS,
    WAITING_FOR_USER,
    WAITING_FOR_EXTERNAL,
    APPROVED,
    REJECTED,
    COMPLETED,
    CANCELLED,
    ARCHIVED
}
