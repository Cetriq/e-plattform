package se.eplatform.flow.api;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import se.eplatform.flow.domain.Category;
import se.eplatform.flow.domain.FlowType;
import se.eplatform.flow.repository.CategoryRepository;
import se.eplatform.flow.repository.FlowTypeRepository;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Admin API for managing categories and flow types.
 */
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
     * Get all flow types with their categories.
     */
    @GetMapping("/flow-types")
    public List<FlowTypeDTO> getAllFlowTypes() {
        return flowTypeRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(FlowTypeDTO::from)
                .toList();
    }

    /**
     * Get a single flow type.
     */
    @GetMapping("/flow-types/{id}")
    public ResponseEntity<FlowTypeDTO> getFlowType(@PathVariable UUID id) {
        return flowTypeRepository.findById(id)
                .map(FlowTypeDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new flow type.
     */
    @PostMapping("/flow-types")
    @Transactional
    public ResponseEntity<FlowTypeDTO> createFlowType(@RequestBody FlowTypeRequest request) {
        FlowType flowType = new FlowType(request.name());
        flowType.setDescription(request.description());
        flowType.setColor(request.color());
        flowType.setIcon(request.icon());
        flowType.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        FlowType saved = flowTypeRepository.save(flowType);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/flow-types/" + saved.getId()))
                .body(FlowTypeDTO.from(saved));
    }

    /**
     * Update a flow type.
     */
    @PutMapping("/flow-types/{id}")
    @Transactional
    public ResponseEntity<FlowTypeDTO> updateFlowType(
            @PathVariable UUID id,
            @RequestBody FlowTypeRequest request) {
        return flowTypeRepository.findById(id)
                .map(flowType -> {
                    flowType.setName(request.name());
                    flowType.setDescription(request.description());
                    flowType.setColor(request.color());
                    flowType.setIcon(request.icon());
                    if (request.sortOrder() != null) {
                        flowType.setSortOrder(request.sortOrder());
                    }
                    return flowTypeRepository.save(flowType);
                })
                .map(FlowTypeDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a flow type (only if it has no flows).
     */
    @DeleteMapping("/flow-types/{id}")
    @Transactional
    public ResponseEntity<Void> deleteFlowType(@PathVariable UUID id) {
        return flowTypeRepository.findById(id)
                .map(flowType -> {
                    // TODO: Check if any flows are using this type
                    flowTypeRepository.delete(flowType);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================
    // CATEGORIES
    // ==================

    /**
     * Get all categories.
     */
    @GetMapping("/categories")
    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryDTO::from)
                .toList();
    }

    /**
     * Get categories for a flow type.
     */
    @GetMapping("/flow-types/{flowTypeId}/categories")
    public List<CategoryDTO> getCategoriesByFlowType(@PathVariable UUID flowTypeId) {
        return categoryRepository.findByFlowTypeIdOrderBySortOrderAsc(flowTypeId)
                .stream()
                .map(CategoryDTO::from)
                .toList();
    }

    /**
     * Get a single category.
     */
    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryDTO> getCategory(@PathVariable UUID id) {
        return categoryRepository.findById(id)
                .map(CategoryDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new category.
     */
    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryRequest request) {
        Category category = new Category(request.name());
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        if (request.flowTypeId() != null) {
            flowTypeRepository.findById(request.flowTypeId())
                    .ifPresent(category::setFlowType);
        }

        Category saved = categoryRepository.save(category);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/categories/" + saved.getId()))
                .body(CategoryDTO.from(saved));
    }

    /**
     * Update a category.
     */
    @PutMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable UUID id,
            @RequestBody CategoryRequest request) {
        return categoryRepository.findById(id)
                .map(category -> {
                    category.setName(request.name());
                    category.setDescription(request.description());
                    if (request.sortOrder() != null) {
                        category.setSortOrder(request.sortOrder());
                    }
                    if (request.flowTypeId() != null) {
                        flowTypeRepository.findById(request.flowTypeId())
                                .ifPresent(category::setFlowType);
                    }
                    return categoryRepository.save(category);
                })
                .map(CategoryDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a category (only if it has no flows).
     */
    @DeleteMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        return categoryRepository.findById(id)
                .map(category -> {
                    // TODO: Check if any flows are using this category
                    categoryRepository.delete(category);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
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
            List<CategoryDTO> categories
    ) {
        public static FlowTypeDTO from(FlowType flowType) {
            return new FlowTypeDTO(
                    flowType.getId(),
                    flowType.getName(),
                    flowType.getDescription(),
                    flowType.getColor(),
                    flowType.getIcon(),
                    flowType.getSortOrder(),
                    flowType.getCategories().stream()
                            .map(CategoryDTO::from)
                            .toList()
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
