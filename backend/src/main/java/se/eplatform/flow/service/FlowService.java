package se.eplatform.flow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.FlowStatus;
import se.eplatform.flow.repository.FlowRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FlowService {

    private final FlowRepository flowRepository;

    public FlowService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * Get all accessible (published & enabled) flows.
     */
    public List<Flow> getAccessibleFlows() {
        return flowRepository.findAllAccessible();
    }

    /**
     * Get published flows with pagination.
     */
    public Page<Flow> getPublishedFlows(Pageable pageable) {
        return flowRepository.findByStatusAndEnabledTrue(FlowStatus.PUBLISHED, pageable);
    }

    /**
     * Get a flow by ID with all details.
     * Uses Hibernate.initialize() to load nested collections.
     */
    public Optional<Flow> getFlow(UUID id) {
        Optional<Flow> flowOpt = flowRepository.findByIdWithBasicRelations(id);
        if (flowOpt.isEmpty()) {
            return Optional.empty();
        }

        Flow flow = flowOpt.get();
        // Initialize steps
        org.hibernate.Hibernate.initialize(flow.getSteps());
        // Initialize query definitions for each step
        for (var step : flow.getSteps()) {
            org.hibernate.Hibernate.initialize(step.getQueryDefinitions());
            // Initialize evaluators for each query
            for (var query : step.getQueryDefinitions()) {
                org.hibernate.Hibernate.initialize(query.getEvaluators());
            }
        }

        return flowOpt;
    }

    /**
     * Get flows by type.
     */
    public List<Flow> getFlowsByType(UUID typeId) {
        return flowRepository.findByTypeIdAndStatus(typeId, FlowStatus.PUBLISHED);
    }

    /**
     * Get flows by category.
     */
    public List<Flow> getFlowsByCategory(UUID categoryId) {
        return flowRepository.findByCategoryIdAndStatus(categoryId, FlowStatus.PUBLISHED);
    }

    /**
     * Search flows.
     */
    public Page<Flow> searchFlows(String query, Pageable pageable) {
        return flowRepository.searchPublished(query, pageable);
    }

    /**
     * Get all versions of a flow family.
     */
    public List<Flow> getFlowVersions(UUID familyId) {
        return flowRepository.findByFamilyIdOrderByVersionDesc(familyId);
    }

    /**
     * Get the latest published version of a flow family.
     */
    public Optional<Flow> getLatestPublishedVersion(UUID familyId) {
        return flowRepository.findLatestPublishedByFamilyId(familyId);
    }

    /**
     * Create a new flow.
     */
    @Transactional
    public Flow createFlow(Flow flow) {
        return flowRepository.save(flow);
    }

    /**
     * Update a flow.
     */
    @Transactional
    public Flow updateFlow(Flow flow) {
        return flowRepository.save(flow);
    }

    /**
     * Publish a flow.
     */
    @Transactional
    public Flow publishFlow(UUID id) {
        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
        flow.publish();
        return flowRepository.save(flow);
    }

    /**
     * Archive a flow.
     */
    @Transactional
    public Flow archiveFlow(UUID id) {
        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
        flow.archive();
        return flowRepository.save(flow);
    }

    /**
     * Create a new version of a flow.
     */
    @Transactional
    public Flow createNewVersion(UUID id) {
        Flow original = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
        Flow newVersion = original.createNewVersion();
        return flowRepository.save(newVersion);
    }

    /**
     * Delete a flow (only drafts).
     */
    @Transactional
    public void deleteFlow(UUID id) {
        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
        if (flow.getStatus() != FlowStatus.DRAFT) {
            throw new IllegalStateException("Can only delete draft flows");
        }
        flowRepository.delete(flow);
    }
}
