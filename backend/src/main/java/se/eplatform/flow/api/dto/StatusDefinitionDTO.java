package se.eplatform.flow.api.dto;

import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.StatusType;

import java.util.UUID;

/**
 * DTO for StatusDefinition entity.
 */
public record StatusDefinitionDTO(
        UUID id,
        String name,
        String description,
        StatusType statusType,
        String color,
        String icon,
        boolean userCanEdit,
        boolean userCanDelete,
        boolean userCanMessage,
        boolean managerCanEdit,
        Integer handlingDays,
        Integer sortOrder
) {
    public static StatusDefinitionDTO from(StatusDefinition status) {
        return new StatusDefinitionDTO(
                status.getId(),
                status.getName(),
                status.getDescription(),
                status.getStatusType(),
                status.getColor(),
                status.getIcon(),
                status.isUserCanEdit(),
                status.isUserCanDelete(),
                status.isUserCanMessage(),
                status.isManagerCanEdit(),
                status.getHandlingDays(),
                status.getSortOrder()
        );
    }
}
