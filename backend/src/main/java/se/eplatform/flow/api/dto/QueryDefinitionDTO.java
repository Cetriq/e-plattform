package se.eplatform.flow.api.dto;

import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.QueryState;
import se.eplatform.flow.domain.QueryType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for QueryDefinition entity.
 */
public record QueryDefinitionDTO(
        UUID id,
        String name,
        String description,
        String helpText,
        String placeholder,
        QueryType queryType,
        Map<String, Object> config,
        boolean required,
        QueryState defaultState,
        Integer sortOrder,
        String width,
        List<EvaluatorDTO> evaluators
) {
    public static QueryDefinitionDTO from(QueryDefinition query) {
        return new QueryDefinitionDTO(
                query.getId(),
                query.getName(),
                query.getDescription(),
                query.getHelpText(),
                query.getPlaceholder(),
                query.getQueryType(),
                query.getConfig(),
                query.isRequired(),
                query.getDefaultState(),
                query.getSortOrder(),
                query.getWidth(),
                query.getEvaluators().stream()
                        .filter(e -> e.isEnabled())
                        .map(EvaluatorDTO::from)
                        .toList()
        );
    }
}
