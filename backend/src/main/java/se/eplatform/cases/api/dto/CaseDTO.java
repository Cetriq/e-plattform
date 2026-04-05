package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.Priority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Case entity.
 */
public record CaseDTO(
        UUID id,
        String referenceNumber,
        UUID flowId,
        String flowName,
        UUID statusId,
        String statusName,
        String statusColor,
        Priority priority,
        Integer currentStepIndex,
        Integer totalSteps,
        String userDescription,
        boolean isDraft,
        boolean isCompleted,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant completedAt,
        List<QueryInstanceDTO> values
) {
    public static CaseDTO from(Case caseEntity) {
        return new CaseDTO(
                caseEntity.getId(),
                caseEntity.getReferenceNumber(),
                caseEntity.getFlow().getId(),
                caseEntity.getFlow().getName(),
                caseEntity.getStatus() != null ? caseEntity.getStatus().getId() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getName() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getColor() : null,
                caseEntity.getPriority(),
                caseEntity.getCurrentStepIndex(),
                caseEntity.getFlow().getSteps().size(),
                caseEntity.getUserDescription(),
                caseEntity.isDraft(),
                caseEntity.isCompleted(),
                caseEntity.getCreatedAt(),
                caseEntity.getUpdatedAt(),
                caseEntity.getSubmittedAt(),
                caseEntity.getCompletedAt(),
                caseEntity.getQueryInstances().stream()
                        .map(QueryInstanceDTO::from)
                        .toList()
        );
    }

    /**
     * Summary without values.
     */
    public static CaseDTO summary(Case caseEntity) {
        return new CaseDTO(
                caseEntity.getId(),
                caseEntity.getReferenceNumber(),
                caseEntity.getFlow().getId(),
                caseEntity.getFlow().getName(),
                caseEntity.getStatus() != null ? caseEntity.getStatus().getId() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getName() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getColor() : null,
                caseEntity.getPriority(),
                caseEntity.getCurrentStepIndex(),
                caseEntity.getFlow().getSteps().size(),
                caseEntity.getUserDescription(),
                caseEntity.isDraft(),
                caseEntity.isCompleted(),
                caseEntity.getCreatedAt(),
                caseEntity.getUpdatedAt(),
                caseEntity.getSubmittedAt(),
                caseEntity.getCompletedAt(),
                null
        );
    }
}
