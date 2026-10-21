package se.eplatform.flow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import se.eplatform.flow.domain.EvaluatorDefinition;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.FlowFamily;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.Step;
import se.eplatform.flow.repository.FlowRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copies a version of an e-service into a new draft: steps, fields,
 * conditions, statuses and status transitions, with every reference between
 * them pointing at the copies. Used for a new version of the same e-service
 * and for duplicating it into a new one.
 */
@Component
public class FlowCopier {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final FlowRepository flowRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FlowCopier(FlowRepository flowRepository, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.flowRepository = flowRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Must run in a transaction; the original's parts must be loaded. */
    public Flow copy(Flow original, String name, FlowFamily family, int version) {
        Flow copy = new Flow(name);
        copy.setFamily(family);
        copy.setVersion(version);
        copy.setOrganizationId(original.getOrganizationId());
        copy.setAccessScope(original.getAccessScope());
        copy.setApplicationVersionId(original.getApplicationVersionId());
        copy.setShortDescription(original.getShortDescription());
        copy.setLongDescription(original.getLongDescription());
        copy.setSubmittedMessage(original.getSubmittedMessage());
        copy.setRequireAuth(original.isRequireAuth());
        copy.setRequireSigning(original.isRequireSigning());
        copy.setSequentialSigning(original.isSequentialSigning());
        copy.setAllowSaveDraft(original.isAllowSaveDraft());
        copy.setAllowMultiple(original.isAllowMultiple());
        copy.setRetentionMonths(original.getRetentionMonths());
        copy.setExternalLink(original.getExternalLink());
        copy.setTags(original.getTags() != null ? original.getTags().clone() : null);
        copy.setType(original.getType());
        copy.setCategory(original.getCategory());

        Map<UUID, UUID> statusIds = new HashMap<>();
        Map<UUID, StatusDefinition> statuses = new HashMap<>();
        for (StatusDefinition status : original.getStatusDefinitions()) {
            StatusDefinition statusCopy = new StatusDefinition(status.getName(), status.getStatusType());
            statusCopy.setDescription(status.getDescription());
            statusCopy.setInternalDescription(status.getInternalDescription());
            statusCopy.setColor(status.getColor());
            statusCopy.setIcon(status.getIcon());
            statusCopy.setConfig(deepCopy(status.getConfig()));
            statusCopy.setUserCanEdit(status.isUserCanEdit());
            statusCopy.setUserCanDelete(status.isUserCanDelete());
            statusCopy.setUserCanMessage(status.isUserCanMessage());
            statusCopy.setManagerCanEdit(status.isManagerCanEdit());
            statusCopy.setHandlingDays(status.getHandlingDays());
            statusCopy.setSortOrder(status.getSortOrder());
            statusCopy.setFlow(copy);
            copy.getStatusDefinitions().add(statusCopy);
            statuses.put(status.getId(), statusCopy);
        }

        Map<UUID, QueryDefinition> queries = new HashMap<>();
        for (Step step : original.getStepsSorted()) {
            Step stepCopy = new Step(step.getName());
            stepCopy.setDescription(step.getDescription());
            stepCopy.setSortOrder(step.getSortOrder());
            stepCopy.setFlow(copy);
            copy.getSteps().add(stepCopy);

            for (QueryDefinition query : step.getQueryDefinitions()) {
                QueryDefinition queryCopy = new QueryDefinition(query.getName(), query.getQueryType());
                queryCopy.setDescription(query.getDescription());
                queryCopy.setHelpText(query.getHelpText());
                queryCopy.setPlaceholder(query.getPlaceholder());
                queryCopy.setConfig(deepCopy(query.getConfig()));
                queryCopy.setRequired(query.isRequired());
                queryCopy.setDefaultState(query.getDefaultState());
                queryCopy.setExportName(query.getExportName());
                queryCopy.setFieldKey(query.getFieldKey());
                queryCopy.setExportable(query.isExportable());
                queryCopy.setSortOrder(query.getSortOrder());
                queryCopy.setWidth(query.getWidth());
                queryCopy.setStep(stepCopy);
                stepCopy.getQueryDefinitions().add(queryCopy);
                queries.put(query.getId(), queryCopy);
            }
        }

        // The copies need their ids before conditions and transitions can point at them
        Flow saved = flowRepository.saveAndFlush(copy);
        statuses.forEach((originalId, statusCopy) -> statusIds.put(originalId, statusCopy.getId()));

        for (Step step : original.getSteps()) {
            for (QueryDefinition query : step.getQueryDefinitions()) {
                QueryDefinition source = queries.get(query.getId());
                for (EvaluatorDefinition evaluator : query.getEvaluators()) {
                    EvaluatorDefinition evaluatorCopy = new EvaluatorDefinition();
                    evaluatorCopy.setQuery(source);
                    evaluatorCopy.setName(evaluator.getName());
                    evaluatorCopy.setDescription(evaluator.getDescription());
                    evaluatorCopy.setEvaluatorType(evaluator.getEvaluatorType());
                    evaluatorCopy.setCondition(deepCopy(evaluator.getCondition()));
                    evaluatorCopy.setTargetQueryIds(evaluator.getTargetQueryIds() == null ? null
                            : evaluator.getTargetQueryIds().stream()
                                    .map(target -> queries.containsKey(target) ? queries.get(target).getId() : target)
                                    .toList());
                    evaluatorCopy.setTargetState(evaluator.getTargetState());
                    evaluatorCopy.setEnabled(evaluator.isEnabled());
                    evaluatorCopy.setSortOrder(evaluator.getSortOrder());
                    source.getEvaluators().add(evaluatorCopy);
                }
            }
        }
        saved = flowRepository.saveAndFlush(saved);

        // Status transitions are a plain table
        List<UUID> originalStatuses = List.copyOf(statusIds.keySet());
        if (!originalStatuses.isEmpty()) {
            jdbc.query("SELECT from_status_id, to_status_id, requires_comment, requires_signature " +
                       "FROM status_transitions WHERE from_status_id = ANY (?)",
                    statement -> statement.setArray(1, statement.getConnection()
                            .createArrayOf("uuid", originalStatuses.toArray())),
                    row -> {
                        UUID from = statusIds.get(row.getObject("from_status_id", UUID.class));
                        UUID to = statusIds.get(row.getObject("to_status_id", UUID.class));
                        if (from != null && to != null) {
                            jdbc.update("INSERT INTO status_transitions (from_status_id, to_status_id, requires_comment, " +
                                        "requires_signature) VALUES (?, ?, ?, ?)",
                                    from, to, row.getBoolean("requires_comment"), row.getBoolean("requires_signature"));
                        }
                    });
        }
        return saved;
    }

    private Map<String, Object> deepCopy(Map<String, Object> value) {
        return value == null ? null : objectMapper.convertValue(objectMapper.valueToTree(value), MAP);
    }
}
