package se.eplatform.flow.api;

import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.web.Audited;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.api.dto.EvaluatorDTO;
import se.eplatform.flow.api.dto.FlowDTO;
import se.eplatform.flow.api.dto.QueryDefinitionDTO;
import se.eplatform.flow.api.dto.StepDTO;
import se.eplatform.flow.domain.*;
import se.eplatform.flow.service.AdminFlowService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin API for managing flows (e-services).
 */
@Tag(name = "Admin - E-tjänster", description = "Skapa och redigera e-tjänster, steg, fält och villkor")
@RestController
@RequestMapping("/api/v1/admin/flows")
public class AdminFlowController {

    private final AdminFlowService adminFlowService;

    public AdminFlowController(AdminFlowService adminFlowService) {
        this.adminFlowService = adminFlowService;
    }

    /**
     * Get all flows (including drafts) for admin view.
     */
    @GetMapping
    public Page<FlowDTO> getAllFlows(@PageableDefault(size = 20) Pageable pageable) {
        return adminFlowService.getAllFlows(pageable)
                .map(FlowDTO::summary);
    }

    /**
     * Get a flow with all details for editing.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlowDTO> getFlowForEdit(@PathVariable UUID id) {
        return adminFlowService.getFlowForEdit(id)
                .map(FlowDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new flow.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW")
    @PostMapping
    public ResponseEntity<FlowDTO> createFlow(@RequestBody CreateFlowRequest request) {
        Flow flow = adminFlowService.createFlow(
                request.name(),
                request.shortDescription(),
                request.longDescription(),
                request.typeId(),
                request.categoryId(),
                request.requireAuth(),
                request.requireSigning(),
                request.tags()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/admin/flows/" + flow.getId()))
                .body(FlowDTO.from(flow));
    }

    /**
     * Update flow metadata.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "id")
    @PutMapping("/{id}")
    public ResponseEntity<FlowDTO> updateFlow(
            @PathVariable UUID id,
            @RequestBody UpdateFlowRequest request) {
        Flow flow = adminFlowService.updateFlow(
                id,
                request.name(),
                request.shortDescription(),
                request.longDescription(),
                request.submittedMessage(),
                request.typeId(),
                request.categoryId(),
                request.requireAuth(),
                request.requireSigning(),
                request.sequentialSigning(),
                request.allowSaveDraft(),
                request.allowMultiple(),
                request.enabled(),
                request.externalLink(),
                request.tags(),
                request.retentionMonths()
        );
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Delete a flow (only drafts).
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable UUID id) {
        adminFlowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Publish a flow.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "id")
    @PostMapping("/{id}/publish")
    public ResponseEntity<FlowDTO> publishFlow(@PathVariable UUID id) {
        Flow flow = adminFlowService.publishFlow(id);
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Archive a flow.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "id")
    @PostMapping("/{id}/archive")
    public ResponseEntity<FlowDTO> archiveFlow(@PathVariable UUID id) {
        Flow flow = adminFlowService.archiveFlow(id);
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Duplicate a flow (create new version or copy).
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "id")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<FlowDTO> duplicateFlow(@PathVariable UUID id) {
        Flow flow = adminFlowService.duplicateFlow(id);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/flows/" + flow.getId()))
                .body(FlowDTO.from(flow));
    }

    // Step management

    /**
     * Add a step to a flow.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PostMapping("/{flowId}/steps")
    public ResponseEntity<StepDTO> addStep(
            @PathVariable UUID flowId,
            @RequestBody StepRequest request) {
        Step step = adminFlowService.addStep(
                flowId,
                request.name(),
                request.description(),
                request.sortOrder()
        );
        return ResponseEntity.ok(StepDTO.from(step));
    }

    /**
     * Update a step.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PutMapping("/{flowId}/steps/{stepId}")
    public ResponseEntity<StepDTO> updateStep(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @RequestBody StepRequest request) {
        Step step = adminFlowService.updateStep(
                flowId,
                stepId,
                request.name(),
                request.description(),
                request.sortOrder()
        );
        return ResponseEntity.ok(StepDTO.from(step));
    }

    /**
     * Delete a step.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @DeleteMapping("/{flowId}/steps/{stepId}")
    public ResponseEntity<Void> deleteStep(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId) {
        adminFlowService.deleteStep(flowId, stepId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder steps.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PutMapping("/{flowId}/steps/reorder")
    public ResponseEntity<Void> reorderSteps(
            @PathVariable UUID flowId,
            @RequestBody ReorderRequest request) {
        adminFlowService.reorderSteps(flowId, request.ids());
        return ResponseEntity.ok().build();
    }

    // Query definition management

    /**
     * Add a query definition to a step.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PostMapping("/{flowId}/steps/{stepId}/queries")
    public ResponseEntity<QueryDefinitionDTO> addQueryDefinition(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @RequestBody QueryDefinitionRequest request) {
        QueryDefinition query = adminFlowService.addQueryDefinition(
                flowId,
                stepId,
                request.name(),
                request.description(),
                request.helpText(),
                request.placeholder(),
                QueryType.valueOf(request.queryType()),
                request.config(),
                request.required(),
                request.defaultState() != null ? QueryState.valueOf(request.defaultState()) : QueryState.VISIBLE,
                request.sortOrder(),
                request.width()
        );
        return ResponseEntity.ok(QueryDefinitionDTO.from(query));
    }

    /**
     * Update a query definition.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PutMapping("/{flowId}/steps/{stepId}/queries/{queryId}")
    public ResponseEntity<QueryDefinitionDTO> updateQueryDefinition(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @PathVariable UUID queryId,
            @RequestBody QueryDefinitionRequest request) {
        QueryDefinition query = adminFlowService.updateQueryDefinition(
                flowId,
                stepId,
                queryId,
                request.name(),
                request.description(),
                request.helpText(),
                request.placeholder(),
                request.config(),
                request.required(),
                request.defaultState() != null ? QueryState.valueOf(request.defaultState()) : null,
                request.sortOrder(),
                request.width()
        );
        return ResponseEntity.ok(QueryDefinitionDTO.from(query));
    }

    /**
     * Delete a query definition.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @DeleteMapping("/{flowId}/steps/{stepId}/queries/{queryId}")
    public ResponseEntity<Void> deleteQueryDefinition(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @PathVariable UUID queryId) {
        adminFlowService.deleteQueryDefinition(flowId, stepId, queryId);
        return ResponseEntity.noContent().build();
    }

    // Conditions (evaluators)

    /**
     * Add a condition to a field: when the field's answer meets the
     * condition, the target fields are shown, required or hidden.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PostMapping("/{flowId}/queries/{queryId}/evaluators")
    public ResponseEntity<EvaluatorDTO> addEvaluator(
            @PathVariable UUID flowId,
            @PathVariable UUID queryId,
            @RequestBody EvaluatorRequest request) {
        EvaluatorDefinition evaluator = adminFlowService.addEvaluator(flowId, queryId,
                request.evaluatorType(), request.condition(), request.targetQueryIds(), request.targetState());
        return ResponseEntity.ok(EvaluatorDTO.from(evaluator));
    }

    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PutMapping("/{flowId}/queries/{queryId}/evaluators/{evaluatorId}")
    public ResponseEntity<EvaluatorDTO> updateEvaluator(
            @PathVariable UUID flowId,
            @PathVariable UUID queryId,
            @PathVariable UUID evaluatorId,
            @RequestBody EvaluatorRequest request) {
        EvaluatorDefinition evaluator = adminFlowService.updateEvaluator(flowId, queryId, evaluatorId,
                request.evaluatorType(), request.condition(), request.targetQueryIds(), request.targetState());
        return ResponseEntity.ok(EvaluatorDTO.from(evaluator));
    }

    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @DeleteMapping("/{flowId}/queries/{queryId}/evaluators/{evaluatorId}")
    public ResponseEntity<Void> deleteEvaluator(
            @PathVariable UUID flowId,
            @PathVariable UUID queryId,
            @PathVariable UUID evaluatorId) {
        adminFlowService.deleteEvaluator(flowId, queryId, evaluatorId);
        return ResponseEntity.noContent().build();
    }

    public record EvaluatorRequest(
            EvaluatorType evaluatorType,
            Map<String, Object> condition,
            List<UUID> targetQueryIds,
            QueryState targetState
    ) {}

    /**
     * Reorder query definitions within a step.
     */
    @Audited(value = AuditAction.FLOW_CHANGE, entity = "FLOW", idParam = "flowId")
    @PutMapping("/{flowId}/steps/{stepId}/queries/reorder")
    public ResponseEntity<Void> reorderQueries(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @RequestBody ReorderRequest request) {
        adminFlowService.reorderQueries(flowId, stepId, request.ids());
        return ResponseEntity.ok().build();
    }

    // Request records

    public record CreateFlowRequest(
            String name,
            String shortDescription,
            String longDescription,
            UUID typeId,
            UUID categoryId,
            Boolean requireAuth,
            Boolean requireSigning,
            String[] tags
    ) {}

    public record UpdateFlowRequest(
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
            /** Gallringsfrist in months; 0 = bevaras, omitted = unchanged. */
            Integer retentionMonths
    ) {}

    public record StepRequest(
            String name,
            String description,
            Integer sortOrder
    ) {}

    public record QueryDefinitionRequest(
            String name,
            String description,
            String helpText,
            String placeholder,
            String queryType,
            Map<String, Object> config,
            Boolean required,
            String defaultState,
            Integer sortOrder,
            String width
    ) {}

    public record ReorderRequest(List<UUID> ids) {}
}
