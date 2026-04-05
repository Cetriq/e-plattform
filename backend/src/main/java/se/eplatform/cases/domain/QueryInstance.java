package se.eplatform.cases.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.QueryState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An instance of a query (form field value) within a case.
 */
@Entity
@Table(name = "query_instances")
public class QueryInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_def_id", nullable = false)
    private QueryDefinition queryDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueryState state = QueryState.VISIBLE;

    /**
     * The value stored as JSON for flexibility.
     * Can be string, number, array (for multi-select), object, etc.
     * Stored as a Map to ensure proper JSON serialization.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> value;

    @Column(nullable = false)
    private boolean populated = false;

    @Column(nullable = false)
    private boolean validated = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "jsonb")
    private Map<String, Object> validationErrors;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public QueryInstance() {}

    public QueryInstance(QueryDefinition definition) {
        this.queryDefinition = definition;
        this.state = definition.getDefaultState();
    }

    // Business methods

    /**
     * Set the value and mark as populated.
     * Wraps the value in a Map to ensure proper JSON serialization.
     */
    public void setValue(Object value) {
        if (value == null) {
            this.value = null;
        } else {
            this.value = Map.of("v", value);
        }
        this.populated = value != null;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this field is required in its current state.
     */
    public boolean isRequired() {
        return state == QueryState.VISIBLE_REQUIRED;
    }

    /**
     * Check if this field is visible.
     */
    public boolean isVisible() {
        return state != QueryState.HIDDEN;
    }

    /**
     * Validate this field instance.
     */
    public boolean validate() {
        // Basic validation - can be extended
        if (isRequired() && !populated) {
            this.validated = false;
            return false;
        }
        this.validated = true;
        return true;
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

    public QueryDefinition getQueryDefinition() {
        return queryDefinition;
    }

    public void setQueryDefinition(QueryDefinition queryDefinition) {
        this.queryDefinition = queryDefinition;
    }

    public QueryState getState() {
        return state;
    }

    public void setState(QueryState state) {
        this.state = state;
    }

    /**
     * Get the unwrapped value.
     */
    public Object getValue() {
        if (value == null) {
            return null;
        }
        return value.get("v");
    }

    /**
     * Get the raw wrapped value (for JPA).
     */
    public Map<String, Object> getValueRaw() {
        return value;
    }

    public boolean isPopulated() {
        return populated;
    }

    public void setPopulated(boolean populated) {
        this.populated = populated;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    public Map<String, Object> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, Object> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
