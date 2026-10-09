package se.eplatform.audit.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One entry in the traceability log (spårbarhetslogg). Entries are only ever
 * added; the database refuses updates and deletes outside the retention job.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Order of the entry in the hash chain; assigned by the database. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(nullable = false)
    private Instant timestamp;

    /** The acting user, or "anonymous"/"system". */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_name")
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    /** The person whose personal data the event concerned, if any. */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_path")
    private String requestPath;

    @Column(name = "request_method")
    private String requestMethod;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(length = 64)
    private String hash;

    protected AuditEvent() {}

    public AuditEvent(AuditAction action, AuditOutcome outcome) {
        // PostgreSQL stores microseconds; truncate so the stored value is exactly
        // the one that was hashed (rounding could otherwise change the millisecond)
        this.timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.action = action;
        this.category = action.category();
        this.outcome = outcome;
    }

    /**
     * The fields covered by the hash, in a fixed order. Changing any of them
     * after the fact breaks the chain.
     */
    public String canonicalContent() {
        return String.join("|",
                String.valueOf(timestamp.toEpochMilli()),
                nz(userId), nz(action.name()), nz(outcome.name()),
                nz(entityType), nz(entityId),
                subjectUserId == null ? "" : subjectUserId.toString(),
                nz(details), nz(ipAddress), nz(requestMethod), nz(requestPath),
                responseStatus == null ? "" : responseStatus.toString());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // Getters and setters

    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public Instant getTimestamp() { return timestamp; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public AuditAction getAction() { return action; }
    public AuditCategory getCategory() { return category; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public UUID getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(UUID subjectUserId) { this.subjectUserId = subjectUserId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
