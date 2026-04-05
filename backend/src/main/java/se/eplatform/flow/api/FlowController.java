package se.eplatform.flow.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.api.dto.FlowDTO;
import se.eplatform.flow.service.FlowService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    /**
     * Get all accessible flows (published & enabled).
     */
    @GetMapping
    public List<FlowDTO> getFlows() {
        return flowService.getAccessibleFlows().stream()
                .map(FlowDTO::summary)
                .toList();
    }

    /**
     * Get flows with pagination.
     */
    @GetMapping("/list")
    public Page<FlowDTO> getFlowsPaged(@PageableDefault(size = 20) Pageable pageable) {
        return flowService.getPublishedFlows(pageable)
                .map(FlowDTO::summary);
    }

    /**
     * Get a specific flow with all details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlowDTO> getFlow(@PathVariable UUID id) {
        return flowService.getFlow(id)
                .map(FlowDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get flows by type.
     */
    @GetMapping("/by-type/{typeId}")
    public List<FlowDTO> getFlowsByType(@PathVariable UUID typeId) {
        return flowService.getFlowsByType(typeId).stream()
                .map(FlowDTO::summary)
                .toList();
    }

    /**
     * Get flows by category.
     */
    @GetMapping("/by-category/{categoryId}")
    public List<FlowDTO> getFlowsByCategory(@PathVariable UUID categoryId) {
        return flowService.getFlowsByCategory(categoryId).stream()
                .map(FlowDTO::summary)
                .toList();
    }

    /**
     * Search flows.
     */
    @GetMapping("/search")
    public Page<FlowDTO> searchFlows(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return flowService.searchFlows(q, pageable)
                .map(FlowDTO::summary);
    }

    /**
     * Get all versions of a flow family.
     */
    @GetMapping("/family/{familyId}/versions")
    public List<FlowDTO> getFlowVersions(@PathVariable UUID familyId) {
        return flowService.getFlowVersions(familyId).stream()
                .map(FlowDTO::summary)
                .toList();
    }
}
