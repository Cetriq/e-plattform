package se.eplatform.flow.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.api.dto.FlowDTO;
import se.eplatform.flow.api.dto.QueryDefinitionDTO;
import se.eplatform.flow.api.dto.StepDTO;
import se.eplatform.flow.domain.*;
import se.eplatform.flow.service.AdminFlowService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API for managing flows (e-services).
 */
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
                request.requireAuth(),
                request.requireSigning(),
                request.sequentialSigning(),
                request.allowSaveDraft(),
                request.allowMultiple(),
                request.enabled(),
                request.externalLink(),
                request.tags()
        );
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Delete a flow (only drafts).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable UUID id) {
        adminFlowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Publish a flow.
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<FlowDTO> publishFlow(@PathVariable UUID id) {
        Flow flow = adminFlowService.publishFlow(id);
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Archive a flow.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<FlowDTO> archiveFlow(@PathVariable UUID id) {
        Flow flow = adminFlowService.archiveFlow(id);
        return ResponseEntity.ok(FlowDTO.from(flow));
    }

    /**
     * Duplicate a flow (create new version or copy).
     */
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
    @DeleteMapping("/{flowId}/steps/{stepId}/queries/{queryId}")
    public ResponseEntity<Void> deleteQueryDefinition(
            @PathVariable UUID flowId,
            @PathVariable UUID stepId,
            @PathVariable UUID queryId) {
        adminFlowService.deleteQueryDefinition(flowId, stepId, queryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder query definitions within a step.
     */
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
            Boolean requireAuth,
            Boolean requireSigning,
            Boolean sequentialSigning,
            Boolean allowSaveDraft,
            Boolean allowMultiple,
            Boolean enabled,
            String externalLink,
            String[] tags
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
