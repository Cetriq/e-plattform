package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.Case;
import se.eplatform.user.domain.User;
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
        List<QueryInstanceDTO> values,
        /** Responsible handläggare; only filled in for staff. */
        UUID assignedToId,
        String assignedToName,
        /** Unread messages from the other party (staff for citizens, citizen for staff). */
        long unreadMessages
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
                        .toList(),
                null,
                null,
                0
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
                null,
                null,
                null,
                0
        );
    }

    /**
     * Summary for the staff case list, including the assigned handläggare.
     */
    public static CaseDTO staffSummary(Case caseEntity) {
        CaseDTO summary = summary(caseEntity);
        User assignee = caseEntity.getAssignedTo();
        return new CaseDTO(summary.id, summary.referenceNumber, summary.flowId, summary.flowName,
                summary.statusId, summary.statusName, summary.statusColor, summary.priority,
                summary.currentStepIndex, summary.totalSteps, summary.userDescription, summary.isDraft,
                summary.isCompleted, summary.createdAt, summary.updatedAt, summary.submittedAt,
                summary.completedAt, null,
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getFullName() : null,
                0);
    }

    public CaseDTO withUnreadMessages(long count) {
        return new CaseDTO(id, referenceNumber, flowId, flowName, statusId, statusName, statusColor,
                priority, currentStepIndex, totalSteps, userDescription, isDraft, isCompleted, createdAt,
                updatedAt, submittedAt, completedAt, values, assignedToId, assignedToName, count);
    }
}
