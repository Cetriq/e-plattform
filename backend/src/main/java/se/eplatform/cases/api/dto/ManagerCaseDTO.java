package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.CaseEvent;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.cases.domain.InternalMessage;
import se.eplatform.cases.domain.Priority;
import se.eplatform.flow.api.dto.StatusDefinitionDTO;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Extended DTO for Case entity with manager-specific data.
 */
public record ManagerCaseDTO(
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
        String managerDescription,
        boolean isDraft,
        boolean isCompleted,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant completedAt,
        String createdByName,
        List<QueryInstanceDTO> values,
        List<StatusDefinitionDTO> statusDefinitions,
        List<CaseEventDTO> events,
        List<InternalMessageDTO> internalMessages,
        List<ExternalMessageDTO> externalMessages,
        List<OwnerDTO> owners
) {
    public static ManagerCaseDTO from(Case caseEntity) {
        return new ManagerCaseDTO(
                caseEntity.getId(),
                caseEntity.getReferenceNumber(),
                caseEntity.getFlow().getId(),
                caseEntity.getFlow().getName(),
                caseEntity.getStatus() != null ? caseEntity.getStatus().getId() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getName() : null,
                caseEntity.getStatus() != null ? caseEntity.getStatus().getColor() : null,
                caseEntity.getPriority(),
                caseEntity.getCurrentStepIndex(),
                caseEntity.getFlow().getStepsSorted().size(),
                caseEntity.getUserDescription(),
                caseEntity.getManagerDescription(),
                caseEntity.isDraft(),
                caseEntity.isCompleted(),
                caseEntity.getCreatedAt(),
                caseEntity.getUpdatedAt(),
                caseEntity.getSubmittedAt(),
                caseEntity.getCompletedAt(),
                caseEntity.getCreatedBy().getFullName(),
                caseEntity.getQueryInstances().stream()
                        .map(QueryInstanceDTO::from)
                        .toList(),
                caseEntity.getFlow().getStatusDefinitionsSorted().stream()
                        .map(StatusDefinitionDTO::from)
                        .toList(),
                caseEntity.getEvents().stream()
                        .map(CaseEventDTO::from)
                        .toList(),
                caseEntity.getInternalMessages().stream()
                        .map(InternalMessageDTO::from)
                        .toList(),
                caseEntity.getExternalMessages().stream()
                        .map(ExternalMessageDTO::from)
                        .toList(),
                caseEntity.getOwners().stream()
                        .map(OwnerDTO::from)
                        .toList()
        );
    }

    // Nested DTOs

    public record CaseEventDTO(
            UUID id,
            String eventType,
            String description,
            Instant createdAt,
            UUID userId,
            String userName
    ) {
        public static CaseEventDTO from(CaseEvent event) {
            return new CaseEventDTO(
                    event.getId(),
                    event.getEventType().name(),
                    event.getDescription(),
                    event.getCreatedAt(),
                    event.getCreatedBy() != null ? event.getCreatedBy().getId() : null,
                    event.getCreatedBy() != null ? event.getCreatedBy().getFullName() : null
            );
        }
    }

    public record InternalMessageDTO(
            UUID id,
            String message,
            Instant createdAt,
            UUID userId,
            String userName
    ) {
        public static InternalMessageDTO from(InternalMessage msg) {
            return new InternalMessageDTO(
                    msg.getId(),
                    msg.getMessage(),
                    msg.getCreatedAt(),
                    msg.getCreatedBy().getId(),
                    msg.getCreatedBy().getFullName()
            );
        }
    }

    public record ExternalMessageDTO(
            UUID id,
            String message,
            boolean fromManager,
            Instant createdAt,
            UUID userId,
            String userName,
            Instant readAt
    ) {
        public static ExternalMessageDTO from(ExternalMessage msg) {
            return new ExternalMessageDTO(
                    msg.getId(),
                    msg.getMessage(),
                    msg.isFromManager(),
                    msg.getCreatedAt(),
                    msg.getCreatedBy().getId(),
                    msg.getCreatedBy().getFullName(),
                    msg.getReadAt()
            );
        }
    }

    public record OwnerDTO(UUID id, String name) {
        public static OwnerDTO from(User user) {
            return new OwnerDTO(user.getId(), user.getFullName());
        }
    }
}
