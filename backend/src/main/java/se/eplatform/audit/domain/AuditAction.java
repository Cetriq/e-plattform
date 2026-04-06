package se.eplatform.audit.domain;

/**
 * Enumeration of auditable actions in the system.
 */
public enum AuditAction {
    // Authentication
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_REFRESH,

    // Cases
    CASE_CREATE,
    CASE_VIEW,
    CASE_UPDATE,
    CASE_SUBMIT,
    CASE_DELETE,
    CASE_STATUS_CHANGE,
    CASE_ASSIGN,
    CASE_EXPORT_PDF,

    // Messages
    MESSAGE_SEND,
    MESSAGE_VIEW,

    // Files
    FILE_UPLOAD,
    FILE_DOWNLOAD,
    FILE_DELETE,

    // Flows (Admin)
    FLOW_CREATE,
    FLOW_UPDATE,
    FLOW_DELETE,
    FLOW_PUBLISH,
    FLOW_UNPUBLISH,

    // Categories (Admin)
    CATEGORY_CREATE,
    CATEGORY_UPDATE,
    CATEGORY_DELETE,

    // Users (Admin)
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,
    USER_ROLE_CHANGE,

    // System
    SETTINGS_UPDATE,
    RATE_LIMIT_EXCEEDED,
    UNAUTHORIZED_ACCESS,
    INVALID_INPUT
}
