package se.eplatform.flow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.flow.domain.*;
import se.eplatform.flow.repository.CategoryRepository;
import se.eplatform.flow.repository.FlowRepository;
import se.eplatform.flow.repository.FlowTypeRepository;
import se.eplatform.flow.repository.StepRepository;
import se.eplatform.flow.repository.QueryDefinitionRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin service for managing flows (e-services).
 */
@Service
@Transactional(readOnly = true)
public class AdminFlowService {

    private final FlowRepository flowRepository;
    private final StepRepository stepRepository;
    private final QueryDefinitionRepository queryDefinitionRepository;
    private final FlowTypeRepository flowTypeRepository;
    private final CategoryRepository categoryRepository;

    public AdminFlowService(
            FlowRepository flowRepository,
            StepRepository stepRepository,
            QueryDefinitionRepository queryDefinitionRepository,
            FlowTypeRepository flowTypeRepository,
            CategoryRepository categoryRepository) {
        this.flowRepository = flowRepository;
        this.stepRepository = stepRepository;
        this.queryDefinitionRepository = queryDefinitionRepository;
        this.flowTypeRepository = flowTypeRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Get all flows (including drafts) for admin view.
     */
    public Page<Flow> getAllFlows(Pageable pageable) {
        return flowRepository.findAllWithRelations(pageable);
    }

    /**
     * Get a flow with all details for editing.
     */
    public Optional<Flow> getFlowForEdit(UUID id) {
        Optional<Flow> flowOpt = flowRepository.findByIdWithBasicRelations(id);
        if (flowOpt.isEmpty()) {
            return Optional.empty();
        }

        Flow flow = flowOpt.get();
        // Initialize steps
        org.hibernate.Hibernate.initialize(flow.getSteps());
        org.hibernate.Hibernate.initialize(flow.getStatusDefinitions());
        // Initialize query definitions for each step
        for (var step : flow.getSteps()) {
            org.hibernate.Hibernate.initialize(step.getQueryDefinitions());
            for (var query : step.getQueryDefinitions()) {
                org.hibernate.Hibernate.initialize(query.getEvaluators());
            }
        }

        return flowOpt;
    }

    /**
     * Create a new flow.
     */
    @Transactional
    public Flow createFlow(
            String name,
            String shortDescription,
            String longDescription,
            UUID typeId,
            UUID categoryId,
            Boolean requireAuth,
            Boolean requireSigning,
            String[] tags) {

        Flow flow = new Flow(name);
        flow.setShortDescription(shortDescription);
        flow.setLongDescription(longDescription);
        if (requireAuth != null) flow.setRequireAuth(requireAuth);
        if (requireSigning != null) flow.setRequireSigning(requireSigning);
        if (tags != null) flow.setTags(tags);

        // Create default status definitions
        createDefaultStatusDefinitions(flow);

        return flowRepository.save(flow);
    }

    /**
     * Update flow metadata.
     */
    @Transactional
    public Flow updateFlow(
            UUID id,
            String name,
            String shortDescription,
            String longDescription,
            String submittedMessage,
            UUID typeId,
            UUID categoryId,
            Boolean requireAuth,
            Boolean requireSigning,
            Boolean sequentialSigning,
            Boolean allowSaveDraft,
            Boolean allowMultiple,
            Boolean enabled,
            String externalLink,
            String[] tags,
            Integer retentionMonths) {

        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));

        if (name != null) flow.setName(name);
        if (shortDescription != null) flow.setShortDescription(shortDescription);
        if (longDescription != null) flow.setLongDescription(longDescription);
        if (submittedMessage != null) flow.setSubmittedMessage(submittedMessage);
        if (requireAuth != null) flow.setRequireAuth(requireAuth);
        if (requireSigning != null) flow.setRequireSigning(requireSigning);
        if (sequentialSigning != null) flow.setSequentialSigning(sequentialSigning);
        if (allowSaveDraft != null) flow.setAllowSaveDraft(allowSaveDraft);
        if (allowMultiple != null) flow.setAllowMultiple(allowMultiple);
        if (enabled != null) flow.setEnabled(enabled);
        if (externalLink != null) flow.setExternalLink(externalLink);
        if (tags != null) flow.setTags(tags);
        // 0 clears the gallringsfrist (cases are kept), null leaves it unchanged
        if (retentionMonths != null) flow.setRetentionMonths(retentionMonths > 0 ? retentionMonths : null);

        // Update type and category
        if (typeId != null) {
            FlowType type = flowTypeRepository.findById(typeId).orElse(null);
            flow.setType(type);
        }
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId).orElse(null);
            flow.setCategory(category);
        }

        Flow saved = flowRepository.save(flow);

        // Initialize lazy loaded relationships for response
        if (saved.getType() != null) {
            org.hibernate.Hibernate.initialize(saved.getType());
        }
        if (saved.getCategory() != null) {
            org.hibernate.Hibernate.initialize(saved.getCategory());
        }
        org.hibernate.Hibernate.initialize(saved.getSteps());
        for (var step : saved.getSteps()) {
            org.hibernate.Hibernate.initialize(step.getQueryDefinitions());
            for (var query : step.getQueryDefinitions()) {
                org.hibernate.Hibernate.initialize(query.getEvaluators());
            }
        }

        return saved;
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
     * Duplicate a flow.
     */
    @Transactional
    public Flow duplicateFlow(UUID id) {
        Flow original = getFlowForEdit(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));

        Flow copy = new Flow(original.getName() + " (kopia)");
        copy.setShortDescription(original.getShortDescription());
        copy.setLongDescription(original.getLongDescription());
        copy.setSubmittedMessage(original.getSubmittedMessage());
        copy.setRequireAuth(original.isRequireAuth());
        copy.setRequireSigning(original.isRequireSigning());
        copy.setSequentialSigning(original.isSequentialSigning());
        copy.setAllowSaveDraft(original.isAllowSaveDraft());
        copy.setAllowMultiple(original.isAllowMultiple());
        copy.setTags(original.getTags() != null ? original.getTags().clone() : null);
        copy.setType(original.getType());
        copy.setCategory(original.getCategory());

        // Copy status definitions
        for (StatusDefinition origStatus : original.getStatusDefinitions()) {
            StatusDefinition statusCopy = new StatusDefinition(
                    origStatus.getName(),
                    origStatus.getStatusType()
            );
            statusCopy.setDescription(origStatus.getDescription());
            statusCopy.setColor(origStatus.getColor());
            statusCopy.setIcon(origStatus.getIcon());
            statusCopy.setUserCanEdit(origStatus.isUserCanEdit());
            statusCopy.setUserCanDelete(origStatus.isUserCanDelete());
            statusCopy.setUserCanMessage(origStatus.isUserCanMessage());
            statusCopy.setManagerCanEdit(origStatus.isManagerCanEdit());
            statusCopy.setHandlingDays(origStatus.getHandlingDays());
            statusCopy.setSortOrder(origStatus.getSortOrder());
            statusCopy.setFlow(copy);
            copy.getStatusDefinitions().add(statusCopy);
        }

        // Copy steps
        for (Step origStep : original.getStepsSorted()) {
            Step stepCopy = new Step(origStep.getName());
            stepCopy.setDescription(origStep.getDescription());
            stepCopy.setSortOrder(origStep.getSortOrder());
            stepCopy.setFlow(copy);
            copy.getSteps().add(stepCopy);

            // Copy queries
            for (QueryDefinition origQuery : origStep.getQueryDefinitions()) {
                QueryDefinition queryCopy = new QueryDefinition(origQuery.getName(), origQuery.getQueryType());
                queryCopy.setDescription(origQuery.getDescription());
                queryCopy.setHelpText(origQuery.getHelpText());
                queryCopy.setPlaceholder(origQuery.getPlaceholder());
                queryCopy.setConfig(origQuery.getConfig());
                queryCopy.setRequired(origQuery.isRequired());
                queryCopy.setDefaultState(origQuery.getDefaultState());
                queryCopy.setExportName(origQuery.getExportName());
                queryCopy.setExportable(origQuery.isExportable());
                queryCopy.setSortOrder(origQuery.getSortOrder());
                queryCopy.setWidth(origQuery.getWidth());
                queryCopy.setStep(stepCopy);
                stepCopy.getQueryDefinitions().add(queryCopy);
            }
        }

        return flowRepository.save(copy);
    }

    // Step management

    /**
     * Add a step to a flow.
     */
    @Transactional
    public Step addStep(UUID flowId, String name, String description, Integer sortOrder) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + flowId));

        Step step = new Step(name);
        step.setDescription(description);
        step.setSortOrder(sortOrder != null ? sortOrder : flow.getSteps().size());
        flow.addStep(step);

        flowRepository.save(flow);
        return step;
    }

    /**
     * Update a step.
     */
    @Transactional
    public Step updateStep(UUID flowId, UUID stepId, String name, String description, Integer sortOrder) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));

        if (!step.getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        if (name != null) step.setName(name);
        if (description != null) step.setDescription(description);
        if (sortOrder != null) step.setSortOrder(sortOrder);

        return stepRepository.save(step);
    }

    /**
     * Delete a step.
     */
    @Transactional
    public void deleteStep(UUID flowId, UUID stepId) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));

        if (!step.getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        Flow flow = step.getFlow();
        flow.removeStep(step);
        flowRepository.save(flow);
    }

    /**
     * Reorder steps.
     */
    @Transactional
    public void reorderSteps(UUID flowId, List<UUID> stepIds) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + flowId));

        org.hibernate.Hibernate.initialize(flow.getSteps());

        for (int i = 0; i < stepIds.size(); i++) {
            UUID stepId = stepIds.get(i);
            flow.getSteps().stream()
                    .filter(s -> s.getId().equals(stepId))
                    .findFirst()
                    .ifPresent(step -> step.setSortOrder(stepIds.indexOf(step.getId())));
        }

        flowRepository.save(flow);
    }

    // Query definition management

    /**
     * Add a query definition to a step.
     */
    @Transactional
    public QueryDefinition addQueryDefinition(
            UUID flowId,
            UUID stepId,
            String name,
            String description,
            String helpText,
            String placeholder,
            QueryType queryType,
            Map<String, Object> config,
            Boolean required,
            QueryState defaultState,
            Integer sortOrder,
            String width) {

        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));

        if (!step.getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        QueryDefinition query = new QueryDefinition(name, queryType);
        query.setDescription(description);
        query.setHelpText(helpText);
        query.setPlaceholder(placeholder);
        if (config != null) query.setConfig(config);
        if (required != null) query.setRequired(required);
        if (defaultState != null) query.setDefaultState(defaultState);
        query.setSortOrder(sortOrder != null ? sortOrder : step.getQueryDefinitions().size());
        if (width != null) query.setWidth(width);
        query.setStep(step);

        step.getQueryDefinitions().add(query);
        stepRepository.save(step);

        return query;
    }

    /**
     * Update a query definition.
     */
    @Transactional
    public QueryDefinition updateQueryDefinition(
            UUID flowId,
            UUID stepId,
            UUID queryId,
            String name,
            String description,
            String helpText,
            String placeholder,
            Map<String, Object> config,
            Boolean required,
            QueryState defaultState,
            Integer sortOrder,
            String width) {

        QueryDefinition query = queryDefinitionRepository.findById(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found: " + queryId));

        if (!query.getStep().getId().equals(stepId)) {
            throw new IllegalArgumentException("Query does not belong to step");
        }
        if (!query.getStep().getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        if (name != null) query.setName(name);
        if (description != null) query.setDescription(description);
        if (helpText != null) query.setHelpText(helpText);
        if (placeholder != null) query.setPlaceholder(placeholder);
        if (config != null) query.setConfig(config);
        if (required != null) query.setRequired(required);
        if (defaultState != null) query.setDefaultState(defaultState);
        if (sortOrder != null) query.setSortOrder(sortOrder);
        if (width != null) query.setWidth(width);

        return queryDefinitionRepository.save(query);
    }

    /**
     * Delete a query definition.
     */
    @Transactional
    public void deleteQueryDefinition(UUID flowId, UUID stepId, UUID queryId) {
        QueryDefinition query = queryDefinitionRepository.findById(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found: " + queryId));

        if (!query.getStep().getId().equals(stepId)) {
            throw new IllegalArgumentException("Query does not belong to step");
        }
        if (!query.getStep().getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        Step step = query.getStep();
        step.getQueryDefinitions().remove(query);
        stepRepository.save(step);
    }

    /**
     * Reorder queries within a step.
     */
    @Transactional
    public void reorderQueries(UUID flowId, UUID stepId, List<UUID> queryIds) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));

        if (!step.getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Step does not belong to flow");
        }

        org.hibernate.Hibernate.initialize(step.getQueryDefinitions());

        for (int i = 0; i < queryIds.size(); i++) {
            UUID queryId = queryIds.get(i);
            final int order = i;
            step.getQueryDefinitions().stream()
                    .filter(q -> q.getId().equals(queryId))
                    .findFirst()
                    .ifPresent(query -> query.setSortOrder(order));
        }

        stepRepository.save(step);
    }

    // Helper methods

    private void createDefaultStatusDefinitions(Flow flow) {
        StatusDefinition draft = new StatusDefinition("Utkast", StatusType.DRAFT);
        draft.setColor("#6B7280");
        draft.setUserCanEdit(true);
        draft.setUserCanDelete(true);
        draft.setSortOrder(0);
        draft.setFlow(flow);
        flow.getStatusDefinitions().add(draft);

        StatusDefinition submitted = new StatusDefinition("Inskickad", StatusType.SUBMITTED);
        submitted.setColor("#3B82F6");
        submitted.setUserCanMessage(true);
        submitted.setSortOrder(1);
        submitted.setFlow(flow);
        flow.getStatusDefinitions().add(submitted);

        StatusDefinition inProgress = new StatusDefinition("Under handläggning", StatusType.IN_PROGRESS);
        inProgress.setColor("#F59E0B");
        inProgress.setManagerCanEdit(true);
        inProgress.setUserCanMessage(true);
        inProgress.setSortOrder(2);
        inProgress.setFlow(flow);
        flow.getStatusDefinitions().add(inProgress);

        StatusDefinition completed = new StatusDefinition("Avslutad", StatusType.COMPLETED);
        completed.setColor("#10B981");
        completed.setSortOrder(3);
        completed.setFlow(flow);
        flow.getStatusDefinitions().add(completed);
    }

    // Conditions (evaluators)

    /** Condition types that can be configured in the admin UI. */
    private static final Set<EvaluatorType> EDITABLE_EVALUATOR_TYPES = EnumSet.of(
            EvaluatorType.VALUE_EQUALS, EvaluatorType.VALUE_NOT_EQUALS,
            EvaluatorType.VALUE_IN, EvaluatorType.VALUE_NOT_IN,
            EvaluatorType.VALUE_CONTAINS, EvaluatorType.VALUE_NOT_CONTAINS,
            EvaluatorType.VALUE_GREATER_THAN, EvaluatorType.VALUE_LESS_THAN,
            EvaluatorType.VALUE_BETWEEN, EvaluatorType.REGEX_MATCH,
            EvaluatorType.IS_EMPTY, EvaluatorType.IS_NOT_EMPTY);

    /** Target states that make a field appear when the condition is met. */
    private static final Set<QueryState> SHOWING_STATES = EnumSet.of(QueryState.VISIBLE, QueryState.VISIBLE_REQUIRED);

    /**
     * Add a condition to a field: when its answer meets the condition, the
     * target fields get the target state.
     */
    @Transactional
    public EvaluatorDefinition addEvaluator(UUID flowId, UUID queryId, EvaluatorType type,
                                            Map<String, Object> condition, List<UUID> targetQueryIds,
                                            QueryState targetState) {
        QueryDefinition source = queryInFlow(flowId, queryId);
        validateEvaluator(source, type, targetQueryIds, targetState);

        EvaluatorDefinition evaluator = new EvaluatorDefinition();
        evaluator.setEvaluatorType(type);
        evaluator.setCondition(condition != null ? condition : Map.of());
        evaluator.setTargetQueryIds(new ArrayList<>(targetQueryIds));
        evaluator.setTargetState(targetState);
        evaluator.setSortOrder(source.getEvaluators().size());
        source.addEvaluator(evaluator);
        // The source is managed, so flushing persists the new condition through
        // the cascade on this very instance (save() would merge a copy) and
        // gives it its id before it is returned
        queryDefinitionRepository.flush();

        syncDefaultStates(source.getStep().getFlow(), new HashSet<>(targetQueryIds));
        return evaluator;
    }

    @Transactional
    public EvaluatorDefinition updateEvaluator(UUID flowId, UUID queryId, UUID evaluatorId, EvaluatorType type,
                                               Map<String, Object> condition, List<UUID> targetQueryIds,
                                               QueryState targetState) {
        QueryDefinition source = queryInFlow(flowId, queryId);
        EvaluatorDefinition evaluator = evaluatorOf(source, evaluatorId);
        validateEvaluator(source, type, targetQueryIds, targetState);

        Set<UUID> affected = new HashSet<>(evaluator.getTargetQueryIds());
        affected.addAll(targetQueryIds);

        evaluator.setEvaluatorType(type);
        evaluator.setCondition(condition != null ? condition : Map.of());
        evaluator.setTargetQueryIds(new ArrayList<>(targetQueryIds));
        evaluator.setTargetState(targetState);
        queryDefinitionRepository.save(source);

        syncDefaultStates(source.getStep().getFlow(), affected);
        return evaluator;
    }

    @Transactional
    public void deleteEvaluator(UUID flowId, UUID queryId, UUID evaluatorId) {
        QueryDefinition source = queryInFlow(flowId, queryId);
        EvaluatorDefinition evaluator = evaluatorOf(source, evaluatorId);
        Set<UUID> affected = new HashSet<>(evaluator.getTargetQueryIds());

        source.getEvaluators().remove(evaluator);
        queryDefinitionRepository.save(source);

        syncDefaultStates(source.getStep().getFlow(), affected);
    }

    private QueryDefinition queryInFlow(UUID flowId, UUID queryId) {
        QueryDefinition query = queryDefinitionRepository.findById(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found: " + queryId));
        if (!query.getStep().getFlow().getId().equals(flowId)) {
            throw new IllegalArgumentException("Query does not belong to flow");
        }
        return query;
    }

    private EvaluatorDefinition evaluatorOf(QueryDefinition source, UUID evaluatorId) {
        return source.getEvaluators().stream()
                .filter(e -> e.getId().equals(evaluatorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Condition not found: " + evaluatorId));
    }

    private void validateEvaluator(QueryDefinition source, EvaluatorType type, List<UUID> targetQueryIds,
                                   QueryState targetState) {
        if (type == null || !EDITABLE_EVALUATOR_TYPES.contains(type)) {
            throw new IllegalArgumentException("Villkorstypen stöds inte");
        }
        if (targetState == null) {
            throw new IllegalArgumentException("Välj vad som ska hända med fälten");
        }
        if (targetQueryIds == null || targetQueryIds.isEmpty()) {
            throw new IllegalArgumentException("Välj minst ett fält som villkoret påverkar");
        }
        Set<UUID> flowQueryIds = allQueries(source.getStep().getFlow()).stream()
                .map(QueryDefinition::getId)
                .collect(Collectors.toSet());
        for (UUID target : targetQueryIds) {
            if (target.equals(source.getId())) {
                throw new IllegalArgumentException("Ett fält kan inte styra sig självt");
            }
            if (!flowQueryIds.contains(target)) {
                throw new IllegalArgumentException("Fältet finns inte i e-tjänsten: " + target);
            }
        }
    }

    /**
     * Fields that a condition shows must start hidden, otherwise they are
     * always visible. Fields no condition shows any more start visible again.
     */
    private void syncDefaultStates(Flow flow, Set<UUID> affectedQueryIds) {
        List<QueryDefinition> queries = allQueries(flow);
        Set<UUID> shownByCondition = queries.stream()
                .flatMap(q -> q.getEvaluators().stream())
                .filter(e -> e.isEnabled() && SHOWING_STATES.contains(e.getTargetState()))
                .flatMap(e -> e.getTargetQueryIds().stream())
                .collect(Collectors.toSet());

        for (QueryDefinition query : queries) {
            if (!affectedQueryIds.contains(query.getId())) {
                continue;
            }
            if (shownByCondition.contains(query.getId())) {
                query.setDefaultState(QueryState.HIDDEN);
            } else if (query.getDefaultState() == QueryState.HIDDEN) {
                query.setDefaultState(QueryState.VISIBLE);
            }
            queryDefinitionRepository.save(query);
        }
    }

    private List<QueryDefinition> allQueries(Flow flow) {
        return flow.getSteps().stream()
                .flatMap(step -> step.getQueryDefinitions().stream())
                .toList();
    }
}
