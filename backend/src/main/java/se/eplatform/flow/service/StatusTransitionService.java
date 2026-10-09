package se.eplatform.flow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which status changes a flow allows (the status_transitions table).
 *
 * A flow without any transitions defined allows every change between its
 * non-draft statuses, so flows created in the admin UI keep working before
 * transitions can be edited there.
 */
@Service
public class StatusTransitionService {

    private final JdbcTemplate jdbc;

    public StatusTransitionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Transition(UUID toStatusId, boolean requiresComment) {}

    /**
     * Statuses a case may move to from its current status.
     */
    public List<Transition> allowedFrom(StatusDefinition current, List<StatusDefinition> flowStatuses) {
        if (!flowHasTransitions(flowStatuses)) {
            return flowStatuses.stream()
                    .filter(s -> s.getStatusType() != StatusType.DRAFT)
                    .filter(s -> current == null || !s.getId().equals(current.getId()))
                    .map(s -> new Transition(s.getId(), false))
                    .toList();
        }
        if (current == null) {
            return List.of();
        }
        return jdbc.query(
                "SELECT to_status_id, requires_comment FROM status_transitions WHERE from_status_id = ?",
                (rs, i) -> new Transition(rs.getObject("to_status_id", UUID.class), rs.getBoolean("requires_comment")),
                current.getId());
    }

    public Optional<Transition> find(StatusDefinition current, StatusDefinition target, List<StatusDefinition> flowStatuses) {
        return allowedFrom(current, flowStatuses).stream()
                .filter(t -> t.toStatusId().equals(target.getId()))
                .findFirst();
    }

    private boolean flowHasTransitions(List<StatusDefinition> flowStatuses) {
        if (flowStatuses.isEmpty()) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM status_transitions WHERE from_status_id = ANY(?)",
                Integer.class,
                (Object) flowStatuses.stream().map(StatusDefinition::getId).toArray(UUID[]::new));
        return count != null && count > 0;
    }
}
