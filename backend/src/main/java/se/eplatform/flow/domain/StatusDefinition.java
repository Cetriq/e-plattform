package se.eplatform.flow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A status definition for a flow.
 * Defines possible states a case can be in.
 */
@Entity
@Table(name = "status_definitions")
public class StatusDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private Flow flow;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "internal_description", columnDefinition = "TEXT")
    private String internalDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", nullable = false, length = 30)
    private StatusType statusType;

    @Column(length = 7)
    private String color;

    @Column(length = 100)
    private String icon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = Map.of();

    // Permissions
    @Column(name = "user_can_edit", nullable = false)
    private boolean userCanEdit = false;

    @Column(name = "user_can_delete", nullable = false)
    private boolean userCanDelete = false;

    @Column(name = "user_can_message", nullable = false)
    private boolean userCanMessage = true;

    @Column(name = "manager_can_edit", nullable = false)
    private boolean managerCanEdit = true;

    // SLA
    @Column(name = "handling_days")
    private Integer handlingDays;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public StatusDefinition() {}

    public StatusDefinition(String name, StatusType statusType) {
        this.name = name;
        this.statusType = statusType;
    }

    // Business methods

    /**
     * Check if this is a terminal status (case is closed).
     */
    public boolean isTerminal() {
        return statusType == StatusType.COMPLETED ||
               statusType == StatusType.CANCELLED ||
               statusType == StatusType.ARCHIVED;
    }

    /**
     * Check if this is an active status (case is being worked on).
     */
    public boolean isActive() {
        return statusType == StatusType.SUBMITTED ||
               statusType == StatusType.IN_PROGRESS ||
               statusType == StatusType.WAITING_FOR_USER ||
               statusType == StatusType.WAITING_FOR_EXTERNAL;
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Flow getFlow() {
        return flow;
    }

    public void setFlow(Flow flow) {
        this.flow = flow;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInternalDescription() {
        return internalDescription;
    }

    public void setInternalDescription(String internalDescription) {
        this.internalDescription = internalDescription;
    }

    public StatusType getStatusType() {
        return statusType;
    }

    public void setStatusType(StatusType statusType) {
        this.statusType = statusType;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public boolean isUserCanEdit() {
        return userCanEdit;
    }

    public void setUserCanEdit(boolean userCanEdit) {
        this.userCanEdit = userCanEdit;
    }

    public boolean isUserCanDelete() {
        return userCanDelete;
    }

    public void setUserCanDelete(boolean userCanDelete) {
        this.userCanDelete = userCanDelete;
    }

    public boolean isUserCanMessage() {
        return userCanMessage;
    }

    public void setUserCanMessage(boolean userCanMessage) {
        this.userCanMessage = userCanMessage;
    }

    public boolean isManagerCanEdit() {
        return managerCanEdit;
    }

    public void setManagerCanEdit(boolean managerCanEdit) {
        this.managerCanEdit = managerCanEdit;
    }

    public Integer getHandlingDays() {
        return handlingDays;
    }

    public void setHandlingDays(Integer handlingDays) {
        this.handlingDays = handlingDays;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
