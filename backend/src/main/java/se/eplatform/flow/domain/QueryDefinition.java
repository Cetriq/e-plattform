package se.eplatform.flow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A form field definition within a step.
 */
@Entity
@Table(name = "query_definitions")
public class QueryDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    private Step step;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "help_text", columnDefinition = "TEXT")
    private String helpText;

    @Column(length = 500)
    private String placeholder;

    @Enumerated(EnumType.STRING)
    @Column(name = "query_type", nullable = false, length = 50)
    private QueryType queryType;

    /**
     * Type-specific configuration stored as JSON.
     * Examples:
     * - TEXT: {"maxLength": 100, "pattern": "..."}
     * - SELECT: {"options": [{"value": "x", "label": "X"}]}
     * - FILE: {"accept": ".pdf,.jpg", "maxSize": 10485760}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = Map.of();

    @Column(nullable = false)
    private boolean required = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_state", nullable = false, length = 20)
    private QueryState defaultState = QueryState.VISIBLE;

    @Column(name = "export_name")
    private String exportName;

    @Column(nullable = false)
    private boolean exportable = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(length = 20)
    private String width = "FULL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<EvaluatorDefinition> evaluators = new ArrayList<>();

    public QueryDefinition() {}

    public QueryDefinition(String name, QueryType queryType) {
        this.name = name;
        this.queryType = queryType;
    }

    // Business methods

    /**
     * Check if this is a layout element (non-input).
     */
    public boolean isLayoutElement() {
        return queryType == QueryType.HEADING ||
               queryType == QueryType.PARAGRAPH ||
               queryType == QueryType.DIVIDER;
    }

    /**
     * Check if this field accepts file uploads.
     */
    public boolean isFileField() {
        return queryType == QueryType.FILE || queryType == QueryType.IMAGE;
    }

    public void addEvaluator(EvaluatorDefinition evaluator) {
        evaluators.add(evaluator);
        evaluator.setQuery(this);
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
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

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public void setQueryType(QueryType queryType) {
        this.queryType = queryType;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public QueryState getDefaultState() {
        return defaultState;
    }

    public void setDefaultState(QueryState defaultState) {
        this.defaultState = defaultState;
    }

    public String getExportName() {
        return exportName;
    }

    public void setExportName(String exportName) {
        this.exportName = exportName;
    }

    public boolean isExportable() {
        return exportable;
    }

    public void setExportable(boolean exportable) {
        this.exportable = exportable;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<EvaluatorDefinition> getEvaluators() {
        return evaluators;
    }

    public void setEvaluators(List<EvaluatorDefinition> evaluators) {
        this.evaluators = evaluators;
    }
}
