package se.eplatform.flow.domain;

import jakarta.persistence.*;
import se.eplatform.common.domain.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups different versions of the same flow/form.
 */
@Entity
@Table(name = "flow_families")
public class FlowFamily extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String icon;

    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    private List<Flow> flows = new ArrayList<>();

    public FlowFamily() {}

    public FlowFamily(String name) {
        this.name = name;
    }

    // Getters and setters

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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public List<Flow> getFlows() {
        return flows;
    }

    public void setFlows(List<Flow> flows) {
        this.flows = flows;
    }

    public void addFlow(Flow flow) {
        flows.add(flow);
        flow.setFamily(this);
    }

    public void removeFlow(Flow flow) {
        flows.remove(flow);
        flow.setFamily(null);
    }

    /**
     * Get the latest published version of this flow family.
     */
    public Flow getLatestPublishedVersion() {
        return flows.stream()
                .filter(f -> f.getStatus() == FlowStatus.PUBLISHED)
                .findFirst()
                .orElse(null);
    }
}
