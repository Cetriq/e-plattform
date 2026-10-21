package se.eplatform.flow.api.dto;

import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.FlowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Flow entity.
 */
public record FlowDTO(
        UUID id,
        String name,
        Integer version,
        String shortDescription,
        String longDescription,
        FlowStatus status,
        boolean enabled,
        boolean requireAuth,
        boolean requireSigning,
        String[] tags,
        UUID typeId,
        String typeName,
        UUID categoryId,
        String categoryName,
        UUID familyId,
        List<StepDTO> steps,
        Instant createdAt,
        Instant updatedAt,
        /** Gallringsfrist in months; null = bevaras. */
        Integer retentionMonths,
        UUID organizationId,
        /** The published service application version it applies (contract level 2); null = not on the levels. */
        UUID applicationVersionId
) {
    public static FlowDTO from(Flow flow) {
        return new FlowDTO(
                flow.getId(),
                flow.getName(),
                flow.getVersion(),
                flow.getShortDescription(),
                flow.getLongDescription(),
                flow.getStatus(),
                flow.isEnabled(),
                flow.isRequireAuth(),
                flow.isRequireSigning(),
                flow.getTags(),
                flow.getType() != null ? flow.getType().getId() : null,
                flow.getType() != null ? flow.getType().getName() : null,
                flow.getCategory() != null ? flow.getCategory().getId() : null,
                flow.getCategory() != null ? flow.getCategory().getName() : null,
                flow.getFamily() != null ? flow.getFamily().getId() : null,
                flow.getStepsSorted().stream().map(StepDTO::from).toList(),
                flow.getCreatedAt(),
                flow.getUpdatedAt(),
                flow.getRetentionMonths(),
                flow.getOrganizationId(),
                flow.getApplicationVersionId()
        );
    }

    /**
     * Summary version without steps.
     */
    public static FlowDTO summary(Flow flow) {
        return new FlowDTO(
                flow.getId(),
                flow.getName(),
                flow.getVersion(),
                flow.getShortDescription(),
                null,
                flow.getStatus(),
                flow.isEnabled(),
                flow.isRequireAuth(),
                flow.isRequireSigning(),
                flow.getTags(),
                flow.getType() != null ? flow.getType().getId() : null,
                flow.getType() != null ? flow.getType().getName() : null,
                flow.getCategory() != null ? flow.getCategory().getId() : null,
                flow.getCategory() != null ? flow.getCategory().getName() : null,
                flow.getFamily() != null ? flow.getFamily().getId() : null,
                null,
                flow.getCreatedAt(),
                flow.getUpdatedAt(),
                flow.getRetentionMonths(),
                flow.getOrganizationId(),
                flow.getApplicationVersionId()
        );
    }
}
