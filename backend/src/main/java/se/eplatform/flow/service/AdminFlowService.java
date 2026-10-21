package se.eplatform.flow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.common.security.CurrentUser;
import se.eplatform.contract.ContractService;
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
    private final FlowCopier flowCopier;
    private final se.eplatform.flow.contract.PublicationService publicationService;
    private final jakarta.persistence.EntityManager entityManager;
    private final ContractService contractService;

    public AdminFlowService(
            FlowRepository flowRepository,
            StepRepository stepRepository,
            QueryDefinitionRepository queryDefinitionRepository,
            FlowTypeRepository flowTypeRepository,
            CategoryRepository categoryRepository,
            FlowCopier flowCopier,
            jakarta.persistence.EntityManager entityManager,
            se.eplatform.flow.contract.PublicationService publicationService,
            ContractService contractService) {
        this.publicationService = publicationService;
        this.contractService = contractService;
        this.flowCopier = flowCopier;
        this.entityManager = entityManager;
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
        return flowRepository.findAllWithRelations(CurrentUser.requireOrganizationId(), pageable);
    }

    /**
     * The flow, if it belongs to the organisation the user is working for.
     * Flows of other organisations are reported as not found.
     */
    private Flow ownFlow(UUID flowId) {
        UUID organizationId = CurrentUser.requireOrganizationId();
        return flowRepository.findById(flowId)
                .filter(flow -> organizationId.equals(flow.getOrganizationId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "E-tjänsten hittades inte"));
    }

    /**
     * Get a flow with all details for editing.
     */
    public Optional<Flow> getFlowForEdit(UUID id) {
        UUID organizationId = CurrentUser.requireOrganizationId();
        Optional<Flow> flowOpt = flowRepository.findByIdWithBasicRelations(id)
                .filter(flow -> organizationId.equals(flow.getOrganizationId()));
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
        flow.setOrganizationId(CurrentUser.requireOrganizationId());
        flow.setFamily(newFamily(name, flow.getOrganizationId()));
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

        Flow flow = ownDraft(id);

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
        // Only the organisation's own categories, and shared or own service types
        UUID organizationId = flow.getOrganizationId();
        if (typeId != null) {
            FlowType type = flowTypeRepository.findById(typeId)
                    .filter(t -> t.getOrganizationId() == null || organizationId.equals(t.getOrganizationId()))
                    .orElse(null);
            flow.setType(type);
        }
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .filter(c -> organizationId.equals(c.getOrganizationId()))
                    .orElse(null);
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
        Flow flow = ownFlow(id);
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
        Flow flow = ownFlow(id);
        if (flow.getStatus() != FlowStatus.DRAFT) {
            throw notEditable(flow);
        }
        // The version it replaces takes no new cases; its existing cases continue
        for (Flow other : flowRepository.findByFamilyIdOrderByVersionDesc(flow.getFamily().getId())) {
            if (other.getStatus() == FlowStatus.PUBLISHED && !other.getId().equals(flow.getId())) {
                other.supersede();
                flowRepository.save(other);
            }
        }
        // The service application it applies must still be published, and its fields must name its fields
        ContractService.Binding binding = requireValidLink(flow);
        // The documents of this version, with the manifest binding them; cases
        // created on it are bound to the manifest
        flow.setManifestHash(publicationService.publish(flow, java.time.Instant.now(), CurrentUser.requireId(), "publish",
                binding));
        flow.publish();
        flowRepository.save(flow);
        // With its parts loaded, for the response
        return getFlowForEdit(id).orElseThrow();
    }

    /**
     * Start a new draft version of an e-service, copied from this version. The
     * versions that are published stay as they are until the draft is published.
     */
    @Transactional
    public Flow createNewVersion(UUID id) {
        Flow original = getFlowForEdit(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "E-tjänsten hittades inte"));
        List<Flow> versions = flowRepository.findByFamilyIdOrderByVersionDesc(original.getFamily().getId());
        versions.stream().filter(v -> v.getStatus() == FlowStatus.DRAFT).findFirst().ifPresent(draft -> {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "FLOW_DRAFT_EXISTS: version " + draft.getVersion() + " är redan ett utkast (" + draft.getId() + ")");
        });
        int next = versions.stream().mapToInt(Flow::getVersion).max().orElse(0) + 1;
        Flow copy = flowCopier.copy(original, original.getName(), original.getFamily(), next);
        return getFlowForEdit(copy.getId()).orElseThrow();
    }

    /**
     * Let a draft version apply a published service application version (or
     * none), and name the definition or application field each of its fields
     * collects. Field keys not given are kept; a blank key removes it.
     * Without an application, no field has a key.
     */
    @Transactional
    public Flow linkContract(UUID flowId, UUID applicationVersionId, Map<UUID, String> fieldKeys) {
        Flow flow = ownDraft(flowId);
        Map<UUID, QueryDefinition> fields = new java.util.HashMap<>();
        flow.getSteps().forEach(step -> step.getQueryDefinitions().forEach(q -> fields.put(q.getId(), q)));
        List<String> unknown = new ArrayList<>();
        Map<UUID, String> keys = fieldKeys == null ? Map.of() : fieldKeys;
        keys.keySet().stream().filter(id -> !fields.containsKey(id)).sorted()
                .forEach(id -> unknown.add("$.fieldKeys." + id + ": not a field of this version"));
        if (!unknown.isEmpty()) throw new ContractService.InvalidDocument(unknown);

        flow.setApplicationVersionId(applicationVersionId);
        keys.forEach((id, key) -> fields.get(id).setFieldKey(key == null || key.isBlank() ? null : key.trim()));
        if (applicationVersionId == null) fields.values().forEach(q -> q.setFieldKey(null));
        requireValidLink(flow);
        flowRepository.save(flow);
        return getFlowForEdit(flowId).orElseThrow();
    }

    /** Field keys belong to the applied application version and name a field at most once; null if none is applied. */
    private ContractService.Binding requireValidLink(Flow flow) {
        if (flow.getApplicationVersionId() == null) return null;
        ContractService.Binding binding = contractService.binding(flow.getApplicationVersionId());
        List<String> errors = new ArrayList<>();
        Map<String, UUID> named = new java.util.HashMap<>();
        for (Step step : flow.getStepsSorted()) {
            for (QueryDefinition query : step.getQueryDefinitions()) {
                String key = query.getFieldKey();
                if (key == null) continue;
                if (!binding.fieldKeys().contains(key)) {
                    errors.add("$.fields." + query.getId() + ": " + key + " is not a field of " + binding.definitionKey()
                            + " or of its application");
                }
                UUID other = named.put(key, query.getId());
                if (other != null) {
                    errors.add("$.fields." + query.getId() + ": " + key + " is already collected by " + other);
                }
            }
        }
        if (!errors.isEmpty()) throw new ContractService.InvalidDocument(errors);
        return binding;
    }

    /** The flow, if it is the organisation's and still a draft; published versions are not changed. */
    private Flow ownDraft(UUID flowId) {
        Flow flow = ownFlow(flowId);
        if (flow.getStatus() != FlowStatus.DRAFT) {
            throw notEditable(flow);
        }
        return flow;
    }

    private static org.springframework.web.server.ResponseStatusException notEditable(Flow flow) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "FLOW_NOT_EDITABLE: version " + flow.getVersion() + " är " + flow.getStatus()
                + " och kan inte ändras. Skapa en ny version.");
    }

    private FlowFamily newFamily(String name, UUID organizationId) {
        FlowFamily family = new FlowFamily(name);
        family.setOrganizationId(organizationId);
        entityManager.persist(family);
        return family;
    }

    /**
     * Archive a flow.
     */
    @Transactional
    public Flow archiveFlow(UUID id) {
        Flow flow = ownFlow(id);
        flow.archive();
        flowRepository.save(flow);
        return getFlowForEdit(id).orElseThrow();
    }

    /**
     * Duplicate a flow.
     */
    @Transactional
    public Flow duplicateFlow(UUID id) {
        Flow original = getFlowForEdit(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));

        String name = original.getName() + " (kopia)";
        Flow copy = flowCopier.copy(original, name, newFamily(name, original.getOrganizationId()), 1);
        return getFlowForEdit(copy.getId()).orElseThrow();
    }

    // Step management

    /**
     * Add a step to a flow.
     */
    @Transactional
    public Step addStep(UUID flowId, String name, String description, Integer sortOrder) {
        Flow flow = ownDraft(flowId);

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
        ownDraft(flowId);
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
        ownDraft(flowId);
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
        Flow flow = ownDraft(flowId);

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
        ownDraft(flowId);

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
        ownDraft(flowId);

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
        ownDraft(flowId);
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
        ownDraft(flowId);
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
        ownDraft(flowId);
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
        ownDraft(flowId);
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
        ownDraft(flowId);
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
