package se.eplatform.flow.api.dto;

import se.eplatform.flow.domain.Step;

import java.util.List;
import java.util.UUID;

/**
 * DTO for Step entity.
 */
public record StepDTO(
        UUID id,
        String name,
        String description,
        Integer sortOrder,
        List<QueryDefinitionDTO> queries
) {
    public static StepDTO from(Step step) {
        return new StepDTO(
                step.getId(),
                step.getName(),
                step.getDescription(),
                step.getSortOrder(),
                step.getQueryDefinitions().stream()
                        .map(QueryDefinitionDTO::from)
                        .toList()
        );
    }
}
