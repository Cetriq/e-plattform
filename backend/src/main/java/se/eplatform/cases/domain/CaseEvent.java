package se.eplatform.cases.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An event in the case's history (audit log).
 */
@Entity
@Table(name = "case_events")
public class CaseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_status_id")
    private StatusDefinition oldStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_status_id")
    private StatusDefinition newStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(columnDefinition = "TEXT")
    private String description;

    public CaseEvent() {}

    // Factory methods

    public static CaseEvent created(Case caseEntity, User creator) {
        CaseEvent event = new CaseEvent();
        event.setCaseEntity(caseEntity);
        event.setEventType(EventType.CREATED);
        event.setCreatedBy(creator);
        event.setDescription("Ärende skapat");
        return event;
    }

    public static CaseEvent submitted(Case caseEntity) {
        CaseEvent event = new CaseEvent();
        event.setCaseEntity(caseEntity);
        event.setEventType(EventType.SUBMITTED);
        event.setCreatedBy(caseEntity.getCreatedBy());
        event.setDescription("Ärende inskickat");
        return event;
    }

    public static CaseEvent statusChanged(Case caseEntity, StatusDefinition oldStatus,
                                          StatusDefinition newStatus, User changedBy, String comment) {
        CaseEvent event = new CaseEvent();
        event.setCaseEntity(caseEntity);
        event.setEventType(EventType.STATUS_CHANGED);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setCreatedBy(changedBy);

        Map<String, Object> data = new HashMap<>();
        if (comment != null) {
            data.put("comment", comment);
        }
        event.setData(data);

        String desc = "Status ändrad";
        if (oldStatus != null && newStatus != null) {
            desc = "Status ändrad från " + oldStatus.getName() + " till " + newStatus.getName();
        }
        event.setDescription(desc);

        return event;
    }

    public static CaseEvent messageSent(Case caseEntity, User sender, boolean isExternal) {
        CaseEvent event = new CaseEvent();
        event.setCaseEntity(caseEntity);
        event.setEventType(EventType.MESSAGE_SENT);
        event.setCreatedBy(sender);
        event.setDescription(isExternal ? "Externt meddelande skickat" : "Internt meddelande skickat");
        return event;
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

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public StatusDefinition getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(StatusDefinition oldStatus) {
        this.oldStatus = oldStatus;
    }

    public StatusDefinition getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(StatusDefinition newStatus) {
        this.newStatus = newStatus;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
