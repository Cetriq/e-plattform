package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.QueryInstance;
import se.eplatform.flow.domain.QueryState;

import java.util.UUID;

/**
 * DTO for QueryInstance entity.
 */
public record QueryInstanceDTO(
        UUID id,
        UUID queryDefinitionId,
        String queryName,
        QueryState state,
        Object value,
        boolean populated,
        boolean validated
) {
    public static QueryInstanceDTO from(QueryInstance instance) {
        return new QueryInstanceDTO(
                instance.getId(),
                instance.getQueryDefinition().getId(),
                instance.getQueryDefinition().getName(),
                instance.getState(),
                instance.getValue(),
                instance.isPopulated(),
                instance.isValidated()
        );
    }
}
