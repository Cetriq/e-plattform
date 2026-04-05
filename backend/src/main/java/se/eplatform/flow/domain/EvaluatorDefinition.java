package se.eplatform.flow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conditional rule for dynamic form behavior.
 * When the source query's value matches the condition,
 * the target queries' states are changed.
 */
@Entity
@Table(name = "evaluator_definitions")
public class EvaluatorDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id", nullable = false)
    private QueryDefinition query;

    @Column
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluator_type", nullable = false, length = 50)
    private EvaluatorType evaluatorType;

    /**
     * Condition configuration as JSON.
     * Examples:
     * - VALUE_EQUALS: {"value": "company"}
     * - VALUE_IN: {"values": ["a", "b", "c"]}
     * - REGEX_MATCH: {"pattern": "^[0-9]+$"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> condition;

    /**
     * IDs of queries that are affected when this evaluator triggers.
     */
    @Column(name = "target_query_ids", columnDefinition = "uuid[]")
    private List<UUID> targetQueryIds;

    /**
     * State to apply to target queries when condition is met.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_state", nullable = false, length = 20)
    private QueryState targetState;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EvaluatorDefinition() {}

    // Business methods

    /**
     * Evaluate if this evaluator's condition is met for the given value.
     */
    public boolean evaluate(Object value) {
        return switch (evaluatorType) {
            case VALUE_EQUALS -> {
                Object expected = condition.get("value");
                yield expected != null && expected.equals(value);
            }
            case VALUE_NOT_EQUALS -> {
                Object expected = condition.get("value");
                yield expected == null || !expected.equals(value);
            }
            case VALUE_IN -> {
                Object values = condition.get("values");
                if (values instanceof List<?> list) {
                    yield list.contains(value);
                }
                yield false;
            }
            case VALUE_NOT_IN -> {
                Object values = condition.get("values");
                if (values instanceof List<?> list) {
                    yield !list.contains(value);
                }
                yield true;
            }
            case IS_EMPTY -> value == null || value.toString().isBlank();
            case IS_NOT_EMPTY -> value != null && !value.toString().isBlank();
            case REGEX_MATCH -> {
                String pattern = (String) condition.get("pattern");
                yield value != null && pattern != null &&
                      value.toString().matches(pattern);
            }
            default -> false;
        };
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QueryDefinition getQuery() {
        return query;
    }

    public void setQuery(QueryDefinition query) {
        this.query = query;
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

    public EvaluatorType getEvaluatorType() {
        return evaluatorType;
    }

    public void setEvaluatorType(EvaluatorType evaluatorType) {
        this.evaluatorType = evaluatorType;
    }

    public Map<String, Object> getCondition() {
        return condition;
    }

    public void setCondition(Map<String, Object> condition) {
        this.condition = condition;
    }

    public List<UUID> getTargetQueryIds() {
        return targetQueryIds;
    }

    public void setTargetQueryIds(List<UUID> targetQueryIds) {
        this.targetQueryIds = targetQueryIds;
    }

    public QueryState getTargetState() {
        return targetState;
    }

    public void setTargetState(QueryState targetState) {
        this.targetState = targetState;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
