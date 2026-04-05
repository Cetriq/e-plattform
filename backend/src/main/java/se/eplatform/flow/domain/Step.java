package se.eplatform.flow.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A step/page in a flow form.
 */
@Entity
@Table(name = "steps")
public class Step {

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

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QueryDefinition> queryDefinitions = new ArrayList<>();

    public Step() {}

    public Step(String name) {
        this.name = name;
    }

    // Business methods

    public void addQueryDefinition(QueryDefinition queryDefinition) {
        queryDefinitions.add(queryDefinition);
        queryDefinition.setStep(this);
        queryDefinition.setSortOrder(queryDefinitions.size() - 1);
    }

    public void removeQueryDefinition(QueryDefinition queryDefinition) {
        queryDefinitions.remove(queryDefinition);
        queryDefinition.setStep(null);
        // Reorder remaining queries
        for (int i = 0; i < queryDefinitions.size(); i++) {
            queryDefinitions.get(i).setSortOrder(i);
        }
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<QueryDefinition> getQueryDefinitions() {
        return queryDefinitions;
    }

    public void setQueryDefinitions(List<QueryDefinition> queryDefinitions) {
        this.queryDefinitions = queryDefinitions;
    }
}
