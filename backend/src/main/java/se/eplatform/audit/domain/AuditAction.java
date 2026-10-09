package se.eplatform.audit.domain;

/**
 * Actions recorded in the traceability log, each with its category.
 */
public enum AuditAction {
    // Authentication and security
    LOGIN_SUCCESS(AuditCategory.SECURITY),
    LOGIN_FAILURE(AuditCategory.SECURITY),
    DEMO_ACCOUNT_CREATED(AuditCategory.SECURITY),
    ACCESS_DENIED(AuditCategory.SECURITY),
    RATE_LIMIT_EXCEEDED(AuditCategory.SECURITY),

    // Reading personal data
    CASE_VIEW(AuditCategory.DATA_ACCESS),
    CASE_LIST(AuditCategory.DATA_ACCESS),
    CASE_SEARCH(AuditCategory.DATA_ACCESS),
    CASE_EXPORT_PDF(AuditCategory.DATA_ACCESS),
    MESSAGE_VIEW(AuditCategory.DATA_ACCESS),
    FILE_DOWNLOAD(AuditCategory.DATA_ACCESS),
    FILE_VIEW(AuditCategory.DATA_ACCESS),
    USER_LIST(AuditCategory.DATA_ACCESS),
    PROFILE_VIEW(AuditCategory.DATA_ACCESS),

    // Changing personal data
    CASE_CREATE(AuditCategory.DATA_CHANGE),
    CASE_UPDATE(AuditCategory.DATA_CHANGE),
    CASE_SUBMIT(AuditCategory.DATA_CHANGE),
    CASE_DELETE(AuditCategory.DATA_CHANGE),
    CASE_STATUS_CHANGE(AuditCategory.DATA_CHANGE),
    CASE_ASSIGN(AuditCategory.DATA_CHANGE),
    MESSAGE_SEND(AuditCategory.DATA_CHANGE),
    NOTE_ADD(AuditCategory.DATA_CHANGE),
    FILE_UPLOAD(AuditCategory.DATA_CHANGE),
    FILE_DELETE(AuditCategory.DATA_CHANGE),
    PROFILE_UPDATE(AuditCategory.DATA_CHANGE),

    // Administration
    FLOW_CHANGE(AuditCategory.ADMIN),
    CATEGORY_CHANGE(AuditCategory.ADMIN),
    USER_ROLE_CHANGE(AuditCategory.ADMIN),

    // Data protection
    AUDIT_VIEW(AuditCategory.PRIVACY),
    AUDIT_EXPORT(AuditCategory.PRIVACY),
    AUDIT_VERIFY(AuditCategory.PRIVACY),
    REGISTER_EXTRACT(AuditCategory.PRIVACY),
    USER_ERASED(AuditCategory.PRIVACY),
    CASE_PURGED(AuditCategory.PRIVACY),
    DRAFT_PURGED(AuditCategory.PRIVACY);

    private final AuditCategory category;

    AuditAction(AuditCategory category) {
        this.category = category;
    }

    public AuditCategory category() {
        return category;
    }
}
