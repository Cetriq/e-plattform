package se.eplatform.cases.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import se.eplatform.common.domain.BaseEntity;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.Step;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.*;

/**
 * A case instance (submission of a flow/form).
 */
@Entity
@Table(name = "cases")
public class Case extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private Flow flow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private StatusDefinition status;

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private Step currentStep;

    @Column(name = "current_step_index", nullable = false)
    private Integer currentStepIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(name = "user_description", length = 500)
    private String userDescription;

    @Column(name = "manager_description", length = 500)
    private String managerDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // Relationships
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<QueryInstance> queryInstances = new LinkedHashSet<>();

    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<CaseEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<InternalMessage> internalMessages = new ArrayList<>();

    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ExternalMessage> externalMessages = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "case_owners",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> owners = new HashSet<>();

    public Case() {}

    // Factory method
    public static Case create(Flow flow, User creator) {
        Case newCase = new Case();
        newCase.setFlow(flow);
        newCase.setCreatedBy(creator);
        newCase.addOwner(creator);
        newCase.setCurrentStepIndex(0);
        List<Step> sortedSteps = flow.getStepsSorted();
        if (!sortedSteps.isEmpty()) {
            newCase.setCurrentStep(sortedSteps.get(0));
        }
        return newCase;
    }

    // Business methods

    /**
     * Submit this case for processing.
     */
    public void submit() {
        this.submittedAt = Instant.now();
        addEvent(CaseEvent.submitted(this));
    }

    /**
     * Check if this case is a draft (not submitted).
     */
    public boolean isDraft() {
        return submittedAt == null;
    }

    /**
     * Check if this case is completed.
     */
    public boolean isCompleted() {
        return completedAt != null;
    }

    /**
     * Move to the next step.
     */
    public boolean nextStep() {
        List<Step> steps = flow.getStepsSorted();
        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
            currentStep = steps.get(currentStepIndex);
            return true;
        }
        return false;
    }

    /**
     * Move to the previous step.
     */
    public boolean previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            currentStep = flow.getStepsSorted().get(currentStepIndex);
            return true;
        }
        return false;
    }

    /**
     * Change the status of this case.
     */
    public void changeStatus(StatusDefinition newStatus, User changedBy, String comment) {
        StatusDefinition oldStatus = this.status;
        this.status = newStatus;
        addEvent(CaseEvent.statusChanged(this, oldStatus, newStatus, changedBy, comment));

        if (newStatus.isTerminal()) {
            this.completedAt = Instant.now();
        }
    }

    public void addOwner(User owner) {
        owners.add(owner);
    }

    public void removeOwner(User owner) {
        owners.remove(owner);
    }

    public void addEvent(CaseEvent event) {
        events.add(event);
        event.setCaseEntity(this);
    }

    public void addQueryInstance(QueryInstance instance) {
        queryInstances.add(instance);
        instance.setCaseEntity(this);
    }

    // Getters and setters

    public Flow getFlow() {
        return flow;
    }

    public void setFlow(Flow flow) {
        this.flow = flow;
    }

    public StatusDefinition getStatus() {
        return status;
    }

    public void setStatus(StatusDefinition status) {
        this.status = status;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Step getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Step currentStep) {
        this.currentStep = currentStep;
    }

    public Integer getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(Integer currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getUserDescription() {
        return userDescription;
    }

    public void setUserDescription(String userDescription) {
        this.userDescription = userDescription;
    }

    public String getManagerDescription() {
        return managerDescription;
    }

    public void setManagerDescription(String managerDescription) {
        this.managerDescription = managerDescription;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Set<QueryInstance> getQueryInstances() {
        return queryInstances;
    }

    public void setQueryInstances(Set<QueryInstance> queryInstances) {
        this.queryInstances = queryInstances;
    }

    public List<CaseEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CaseEvent> events) {
        this.events = events;
    }

    public List<InternalMessage> getInternalMessages() {
        return internalMessages;
    }

    public List<ExternalMessage> getExternalMessages() {
        return externalMessages;
    }

    public Set<User> getOwners() {
        return owners;
    }

    public void setOwners(Set<User> owners) {
        this.owners = owners;
    }
}
