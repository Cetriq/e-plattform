package se.eplatform.flow.api.dto;

import se.eplatform.flow.domain.EvaluatorDefinition;
import se.eplatform.flow.domain.EvaluatorType;
import se.eplatform.flow.domain.QueryState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for EvaluatorDefinition entity.
 */
public record EvaluatorDTO(
        UUID id,
        EvaluatorType evaluatorType,
        Map<String, Object> condition,
        List<UUID> targetQueryIds,
        QueryState targetState
) {
    public static EvaluatorDTO from(EvaluatorDefinition evaluator) {
        return new EvaluatorDTO(
                evaluator.getId(),
                evaluator.getEvaluatorType(),
                evaluator.getCondition(),
                evaluator.getTargetQueryIds(),
                evaluator.getTargetState()
        );
    }
}
