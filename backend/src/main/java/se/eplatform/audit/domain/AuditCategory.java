package se.eplatform.audit.domain;

/**
 * Kind of event in the traceability log.
 */
public enum AuditCategory {
    /** Logins, denied access, rate limiting. */
    SECURITY,
    /** Someone read personal data (a case, a file, a register extract). */
    DATA_ACCESS,
    /** Personal data was created, changed or deleted. */
    DATA_CHANGE,
    /** Configuration of e-services, users and roles. */
    ADMIN,
    /** Data protection actions: extracts, erasure, gallring, log exports. */
    PRIVACY
}
