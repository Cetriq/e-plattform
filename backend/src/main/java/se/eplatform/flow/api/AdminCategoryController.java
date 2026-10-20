package se.eplatform.flow.api;

import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.web.Audited;
import org.springframework.http.ResponseEntity;
import se.eplatform.common.security.CurrentUser;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.domain.Category;
import se.eplatform.flow.domain.FlowType;
import se.eplatform.flow.repository.CategoryRepository;
import se.eplatform.flow.repository.FlowTypeRepository;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin API for managing categories and flow types.
 */
@Tag(name = "Admin - Kategorier", description = "Kategorier och tjänstetyper för e-tjänster")
@RestController
@RequestMapping("/api/v1/admin")
@Transactional(readOnly = true)
public class AdminCategoryController {

    private final FlowTypeRepository flowTypeRepository;
    private final CategoryRepository categoryRepository;

    public AdminCategoryController(FlowTypeRepository flowTypeRepository, CategoryRepository categoryRepository) {
        this.flowTypeRepository = flowTypeRepository;
        this.categoryRepository = categoryRepository;
    }

    // ==================
    // FLOW TYPES
    // ==================

    /**
     * Shared service types (no organisation) and the organisation's own, each
     * with the organisation's own categories.
     */
    @GetMapping("/flow-types")
    public List<FlowTypeDTO> getAllFlowTypes() {
        UUID org = CurrentUser.requireOrganizationId();
        return flowTypeRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .filter(type -> visible(type, org))
                .map(type -> FlowTypeDTO.from(type, org))
                .toList();
    }

    @GetMapping("/flow-types/{id}")
    public ResponseEntity<FlowTypeDTO> getFlowType(@PathVariable UUID id) {
        UUID org = CurrentUser.requireOrganizationId();
        return flowTypeRepository.findById(id)
                .filter(type -> visible(type, org))
                .map(type -> FlowTypeDTO.from(type, org))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a service type for the organisation.
     */
    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY")
    @PostMapping("/flow-types")
    @Transactional
    public ResponseEntity<FlowTypeDTO> createFlowType(@RequestBody FlowTypeRequest request) {
        UUID org = CurrentUser.requireOrganizationId();
        FlowType flowType = new FlowType(request.name());
        flowType.setOrganizationId(org);
        flowType.setDescription(request.description());
        flowType.setColor(request.color());
        flowType.setIcon(request.icon());
        flowType.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        FlowType saved = flowTypeRepository.save(flowType);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/flow-types/" + saved.getId()))
                .body(FlowTypeDTO.from(saved, org));
    }

    /**
     * Update one of the organisation's service types. Shared types are
     * changed through migrations, since every organisation uses them.
     */
    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY", idParam = "id")
    @PutMapping("/flow-types/{id}")
    @Transactional
    public ResponseEntity<FlowTypeDTO> updateFlowType(
            @PathVariable UUID id,
            @RequestBody FlowTypeRequest request) {
        UUID org = CurrentUser.requireOrganizationId();
        FlowType flowType = ownFlowType(id, org);
        flowType.setName(request.name());
        flowType.setDescription(request.description());
        flowType.setColor(request.color());
        flowType.setIcon(request.icon());
        if (request.sortOrder() != null) {
            flowType.setSortOrder(request.sortOrder());
        }
        return ResponseEntity.ok(FlowTypeDTO.from(flowTypeRepository.save(flowType), org));
    }

    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY", idParam = "id")
    @DeleteMapping("/flow-types/{id}")
    @Transactional
    public ResponseEntity<Void> deleteFlowType(@PathVariable UUID id) {
        flowTypeRepository.delete(ownFlowType(id, CurrentUser.requireOrganizationId()));
        return ResponseEntity.noContent().build();
    }

    // ==================
    // CATEGORIES
    // ==================

    /**
     * The organisation's categories.
     */
    @GetMapping("/categories")
    public List<CategoryDTO> getAllCategories() {
        UUID org = CurrentUser.requireOrganizationId();
        return categoryRepository.findAll()
                .stream()
                .filter(category -> org.equals(category.getOrganizationId()))
                .map(CategoryDTO::from)
                .toList();
    }

    @GetMapping("/flow-types/{flowTypeId}/categories")
    public List<CategoryDTO> getCategoriesByFlowType(@PathVariable UUID flowTypeId) {
        UUID org = CurrentUser.requireOrganizationId();
        return categoryRepository.findByFlowTypeIdOrderBySortOrderAsc(flowTypeId)
                .stream()
                .filter(category -> org.equals(category.getOrganizationId()))
                .map(CategoryDTO::from)
                .toList();
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryDTO> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(CategoryDTO.from(ownCategory(id, CurrentUser.requireOrganizationId())));
    }

    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY")
    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryRequest request) {
        UUID org = CurrentUser.requireOrganizationId();
        Category category = new Category(request.name());
        category.setOrganizationId(org);
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        if (request.flowTypeId() != null) {
            flowTypeRepository.findById(request.flowTypeId())
                    .filter(type -> visible(type, org))
                    .ifPresent(category::setFlowType);
        }

        Category saved = categoryRepository.save(category);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/categories/" + saved.getId()))
                .body(CategoryDTO.from(saved));
    }

    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY", idParam = "id")
    @PutMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable UUID id,
            @RequestBody CategoryRequest request) {
        UUID org = CurrentUser.requireOrganizationId();
        Category category = ownCategory(id, org);
        category.setName(request.name());
        category.setDescription(request.description());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.flowTypeId() != null) {
            flowTypeRepository.findById(request.flowTypeId())
                    .filter(type -> visible(type, org))
                    .ifPresent(category::setFlowType);
        }
        return ResponseEntity.ok(CategoryDTO.from(categoryRepository.save(category)));
    }

    @Audited(value = AuditAction.CATEGORY_CHANGE, entity = "CATEGORY", idParam = "id")
    @DeleteMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryRepository.delete(ownCategory(id, CurrentUser.requireOrganizationId()));
        return ResponseEntity.noContent().build();
    }

    // ---------- Organisation checks ----------

    /** Shared types and the organisation's own. */
    private static boolean visible(FlowType type, UUID org) {
        return type.getOrganizationId() == null || org.equals(type.getOrganizationId());
    }

    /** One of the organisation's own types; 403 for shared ones, 404 for other organisations'. */
    private FlowType ownFlowType(UUID id, UUID org) {
        FlowType type = flowTypeRepository.findById(id)
                .filter(t -> visible(t, org))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tjänsttypen hittades inte"));
        if (type.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Gemensamma tjänsttyper används av alla organisationer och ändras inte här");
        }
        return type;
    }

    private Category ownCategory(UUID id, UUID org) {
        return categoryRepository.findById(id)
                .filter(c -> org.equals(c.getOrganizationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategorin hittades inte"));
    }

    // ==================
    // DTOs
    // ==================

    public record FlowTypeDTO(
            UUID id,
            String name,
            String description,
            String color,
            String icon,
            Integer sortOrder,
            List<CategoryDTO> categories,
            /** Shared by every organisation; changed only through migrations. */
            boolean shared
    ) {
        /** With only the given organisation's categories. */
        public static FlowTypeDTO from(FlowType flowType, UUID org) {
            return new FlowTypeDTO(
                    flowType.getId(),
                    flowType.getName(),
                    flowType.getDescription(),
                    flowType.getColor(),
                    flowType.getIcon(),
                    flowType.getSortOrder(),
                    flowType.getCategories().stream()
                            .filter(category -> org.equals(category.getOrganizationId()))
                            .map(CategoryDTO::from)
                            .toList(),
                    flowType.getOrganizationId() == null
            );
        }
    }

    public record CategoryDTO(
            UUID id,
            String name,
            String description,
            Integer sortOrder,
            UUID flowTypeId,
            String flowTypeName
    ) {
        public static CategoryDTO from(Category category) {
            return new CategoryDTO(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    category.getSortOrder(),
                    category.getFlowType() != null ? category.getFlowType().getId() : null,
                    category.getFlowType() != null ? category.getFlowType().getName() : null
            );
        }
    }

    // ==================
    // Request records
    // ==================

    public record FlowTypeRequest(
            String name,
            String description,
            String color,
            String icon,
            Integer sortOrder
    ) {}

    public record CategoryRequest(
            String name,
            String description,
            Integer sortOrder,
            UUID flowTypeId
    ) {}
}
