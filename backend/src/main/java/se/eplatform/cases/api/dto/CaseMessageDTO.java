package se.eplatform.cases.api.dto;

import se.eplatform.cases.domain.ExternalMessage;

import java.time.Instant;
import java.util.UUID;

/**
 * A message between the citizen and the handläggare. For citizens the
 * author of staff messages is shown as "Handläggare" (data minimisation).
 */
public record CaseMessageDTO(
        UUID id,
        String message,
        boolean fromManager,
        Instant createdAt,
        String authorName,
        Instant readAt
) {
    public static CaseMessageDTO from(ExternalMessage msg, boolean forCitizen) {
        String author = msg.isFromManager() && forCitizen
                ? "Handläggare"
                : msg.getCreatedBy().getFullName();
        return new CaseMessageDTO(msg.getId(), msg.getMessage(), msg.isFromManager(),
                msg.getCreatedAt(), author, msg.getReadAt());
    }
}
