package se.eplatform.flow.domain;

import jakarta.persistence.*;
import se.eplatform.common.domain.BaseEntity;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A flow/form definition. This is the main entity for e-services.
 */
@Entity
@Table(name = "flows")
public class Flow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private FlowFamily family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private FlowType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "long_description", columnDefinition = "TEXT")
    private String longDescription;

    @Column(name = "submitted_message", columnDefinition = "TEXT")
    private String submittedMessage;

    // Configuration
    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "require_auth", nullable = false)
    private boolean requireAuth = true;

    @Column(name = "require_signing", nullable = false)
    private boolean requireSigning = false;

    @Column(name = "sequential_signing", nullable = false)
    private boolean sequentialSigning = false;

    @Column(name = "allow_save_draft", nullable = false)
    private boolean allowSaveDraft = true;

    @Column(name = "allow_multiple", nullable = false)
    private boolean allowMultiple = true;

    // Publication
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlowStatus status = FlowStatus.DRAFT;

    @Column(name = "publish_date")
    private Instant publishDate;

    @Column(name = "unpublish_date")
    private Instant unpublishDate;

    @Column(name = "external_link", length = 1024)
    private String externalLink;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags = {};

    @Column(name = "created_by")
    private UUID createdBy;

    // Relationships
    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<Step> steps = new LinkedHashSet<>();

    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<StatusDefinition> statusDefinitions = new LinkedHashSet<>();

    public Flow() {}

    public Flow(String name) {
        this.name = name;
    }

    // Business methods

    /**
     * Publish this flow, making it available to users.
     */
    public void publish() {
        this.status = FlowStatus.PUBLISHED;
        this.enabled = true;
        this.publishDate = Instant.now();
    }

    /**
     * Archive this flow, hiding it from users.
     */
    public void archive() {
        this.status = FlowStatus.ARCHIVED;
        this.enabled = false;
    }

    /**
     * Create a new version of this flow.
     */
    public Flow createNewVersion() {
        Flow newVersion = new Flow(this.name);
        newVersion.setFamily(this.family);
        newVersion.setType(this.type);
        newVersion.setCategory(this.category);
        newVersion.setVersion(this.version + 1);
        newVersion.setShortDescription(this.shortDescription);
        newVersion.setLongDescription(this.longDescription);
        newVersion.setSubmittedMessage(this.submittedMessage);
        newVersion.setRequireAuth(this.requireAuth);
        newVersion.setRequireSigning(this.requireSigning);
        newVersion.setTags(this.tags.clone());
        // Steps would need to be deep-copied separately
        return newVersion;
    }

    /**
     * Check if this flow is currently published and accessible.
     */
    public boolean isAccessible() {
        if (!enabled || status != FlowStatus.PUBLISHED) {
            return false;
        }
        Instant now = Instant.now();
        if (publishDate != null && now.isBefore(publishDate)) {
            return false;
        }
        if (unpublishDate != null && now.isAfter(unpublishDate)) {
            return false;
        }
        return true;
    }

    // Step management

    public void addStep(Step step) {
        step.setSortOrder(steps.size());
        steps.add(step);
        step.setFlow(this);
    }

    public void removeStep(Step step) {
        steps.remove(step);
        step.setFlow(null);
        // Reorder remaining steps
        List<Step> sortedSteps = getStepsSorted();
        for (int i = 0; i < sortedSteps.size(); i++) {
            sortedSteps.get(i).setSortOrder(i);
        }
    }

    // Getters and setters

    public FlowFamily getFamily() {
        return family;
    }

    public void setFamily(FlowFamily family) {
        this.family = family;
    }

    public FlowType getType() {
        return type;
    }

    public void setType(FlowType type) {
        this.type = type;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public String getSubmittedMessage() {
        return submittedMessage;
    }

    public void setSubmittedMessage(String submittedMessage) {
        this.submittedMessage = submittedMessage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireAuth() {
        return requireAuth;
    }

    public void setRequireAuth(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public boolean isRequireSigning() {
        return requireSigning;
    }

    public void setRequireSigning(boolean requireSigning) {
        this.requireSigning = requireSigning;
    }

    public boolean isSequentialSigning() {
        return sequentialSigning;
    }

    public void setSequentialSigning(boolean sequentialSigning) {
        this.sequentialSigning = sequentialSigning;
    }

    public boolean isAllowSaveDraft() {
        return allowSaveDraft;
    }

    public void setAllowSaveDraft(boolean allowSaveDraft) {
        this.allowSaveDraft = allowSaveDraft;
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public FlowStatus getStatus() {
        return status;
    }

    public void setStatus(FlowStatus status) {
        this.status = status;
    }

    public Instant getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Instant publishDate) {
        this.publishDate = publishDate;
    }

    public Instant getUnpublishDate() {
        return unpublishDate;
    }

    public void setUnpublishDate(Instant unpublishDate) {
        this.unpublishDate = unpublishDate;
    }

    public String getExternalLink() {
        return externalLink;
    }

    public void setExternalLink(String externalLink) {
        this.externalLink = externalLink;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Set<Step> getSteps() {
        return steps;
    }

    public void setSteps(Set<Step> steps) {
        this.steps = steps;
    }

    /**
     * Get steps as a sorted list.
     */
    public List<Step> getStepsSorted() {
        return steps.stream()
                .sorted(Comparator.comparingInt(Step::getSortOrder))
                .collect(Collectors.toList());
    }

    public Set<StatusDefinition> getStatusDefinitions() {
        return statusDefinitions;
    }

    public void setStatusDefinitions(Set<StatusDefinition> statusDefinitions) {
        this.statusDefinitions = statusDefinitions;
    }

    /**
     * Get status definitions as a sorted list.
     */
    public List<StatusDefinition> getStatusDefinitionsSorted() {
        return statusDefinitions.stream()
                .sorted(Comparator.comparingInt(StatusDefinition::getSortOrder))
                .collect(Collectors.toList());
    }
}
