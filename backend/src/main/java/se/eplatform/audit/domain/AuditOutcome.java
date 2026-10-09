package se.eplatform.audit.domain;

public enum AuditOutcome {
    SUCCESS,
    /** Refused because the user lacked access (401/403, or 404 for other people's data). */
    DENIED,
    /** Rejected input or a server error. */
    FAILURE
}
