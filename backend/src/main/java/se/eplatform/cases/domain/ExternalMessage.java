package se.eplatform.cases.domain;

import jakarta.persistence.*;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.UUID;

/**
 * An external message between the case owner and managers.
 */
@Entity
@Table(name = "external_messages")
public class ExternalMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "from_manager", nullable = false)
    private boolean fromManager = false;

    @Column(name = "system_message", nullable = false)
    private boolean systemMessage = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "read_at")
    private Instant readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "read_by")
    private User readBy;

    public ExternalMessage() {}

    public ExternalMessage(Case caseEntity, User author, String message, boolean fromManager) {
        this.caseEntity = caseEntity;
        this.createdBy = author;
        this.message = message;
        this.fromManager = fromManager;
    }

    // Factory methods

    public static ExternalMessage fromUser(Case caseEntity, User user, String message) {
        return new ExternalMessage(caseEntity, user, message, false);
    }

    public static ExternalMessage fromManager(Case caseEntity, User manager, String message) {
        return new ExternalMessage(caseEntity, manager, message, true);
    }

    public static ExternalMessage systemMessage(Case caseEntity, User systemUser, String message) {
        ExternalMessage msg = new ExternalMessage(caseEntity, systemUser, message, true);
        msg.setSystemMessage(true);
        return msg;
    }

    // Business methods

    public boolean isRead() {
        return readAt != null;
    }

    public void markAsRead(User reader) {
        if (this.readAt == null) {
            this.readAt = Instant.now();
            this.readBy = reader;
        }
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Case getCaseEntity() {
        return caseEntity;
    }

    public void setCaseEntity(Case caseEntity) {
        this.caseEntity = caseEntity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFromManager() {
        return fromManager;
    }

    public void setFromManager(boolean fromManager) {
        this.fromManager = fromManager;
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        this.systemMessage = systemMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public User getReadBy() {
        return readBy;
    }
}
