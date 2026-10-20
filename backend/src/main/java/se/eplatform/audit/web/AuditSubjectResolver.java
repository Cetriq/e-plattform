package se.eplatform.audit.web;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Finds whose personal data an object holds: the owner of a case, the
 * uploader of a file, the user itself.
 */
@Component
public class AuditSubjectResolver {

    private final EntityManager entityManager;

    public AuditSubjectResolver(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public UUID subjectOf(String entityType, String entityId) {
        UUID id;
        try {
            id = UUID.fromString(entityId);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        if ("USER".equals(entityType)) {
            return id;
        }
        // A database function, because row level security hides a case from
        // someone who may not reach it, and a denied attempt must still say
        // whose data it concerned
        return (UUID) entityManager.createNativeQuery("SELECT audit_entity_subject(:type, :id)")
                .setParameter("type", entityType)
                .setParameter("id", id)
                .getSingleResult();
    }
}
