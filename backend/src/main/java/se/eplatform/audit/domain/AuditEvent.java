package se.eplatform.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit event entity for tracking user actions.
 * Stores all significant actions for compliance and security monitoring.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "userId"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_entity", columnList = "entityType, entityId"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String userId;

    @Column
    private String userEmail;

    @Column
    private String userName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column
    private String entityType;

    @Column
    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column
    private String requestPath;

    @Column
    private String requestMethod;

    @Column
    private Integer responseStatus;

    @Column
    private Long durationMs;

    // Constructors
    public AuditEvent() {
        this.timestamp = Instant.now();
    }

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AuditEvent event = new AuditEvent();

        public Builder userId(String userId) {
            event.userId = userId;
            return this;
        }

        public Builder userEmail(String userEmail) {
            event.userEmail = userEmail;
            return this;
        }

        public Builder userName(String userName) {
            event.userName = userName;
            return this;
        }

        public Builder action(AuditAction action) {
            event.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            event.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            event.entityId = entityId;
            return this;
        }

        public Builder details(String details) {
            event.details = details;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            event.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            event.userAgent = userAgent;
            return this;
        }

        public Builder requestPath(String requestPath) {
            event.requestPath = requestPath;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            event.requestMethod = requestMethod;
            return this;
        }

        public Builder responseStatus(Integer responseStatus) {
            event.responseStatus = responseStatus;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            event.durationMs = durationMs;
            return this;
        }

        public AuditEvent build() {
            return event;
        }
    }

    // Getters
    public UUID getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public String getUserName() { return userName; }
    public AuditAction getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getRequestPath() { return requestPath; }
    public String getRequestMethod() { return requestMethod; }
    public Integer getResponseStatus() { return responseStatus; }
    public Long getDurationMs() { return durationMs; }
}
