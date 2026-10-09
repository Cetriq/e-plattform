package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.CaseEvent;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standalone DTO for case events.
 *
 * The actor name is masked for citizen-facing responses: when the actor is a
 * handläggare (MANAGER) the name is replaced with the generic label
 * "Handläggare" to avoid exposing internal staff identities outside the
 * handläggar-vyn (dataminimering).
 */
public record CaseEventDTO(
        UUID id,
        String eventType,
        String description,
        Instant createdAt,
        String actorName,
        String actorRole,
        String oldStatusName,
        String newStatusName,
        String comment
) {

    public static CaseEventDTO forCitizen(CaseEvent event) {
        return build(event, true);
    }

    public static CaseEventDTO forManager(CaseEvent event) {
        return build(event, false);
    }

    private static CaseEventDTO build(CaseEvent event, boolean maskManagerName) {
        User actor = event.getCreatedBy();
        String role = null;
        String actorName = null;
        if (actor != null) {
            boolean isManager = actor.hasRole("MANAGER");
            boolean isAdmin = actor.hasRole("ADMIN");
            role = isAdmin ? "ADMIN" : (isManager ? "MANAGER" : "USER");
            if (maskManagerName && (isManager || isAdmin)) {
                actorName = "Handläggare";
            } else {
                actorName = actor.getFullName();
            }
        }

        Map<String, Object> data = event.getData() != null ? event.getData() : new HashMap<>();
        Object commentValue = data.get("comment");
        String comment = commentValue != null ? commentValue.toString() : null;

        return new CaseEventDTO(
                event.getId(),
                event.getEventType().name(),
                event.getDescription(),
                event.getCreatedAt(),
                actorName,
                role,
                event.getOldStatus() != null ? event.getOldStatus().getName() : null,
                event.getNewStatus() != null ? event.getNewStatus().getName() : null,
                comment
        );
    }
}
