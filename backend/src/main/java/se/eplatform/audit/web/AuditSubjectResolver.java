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
        String jpql = switch (entityType) {
            case "CASE" -> "SELECT c.createdBy.id FROM Case c WHERE c.id = :id";
            case "FILE" -> "SELECT a.uploadedBy FROM Attachment a WHERE a.id = :id";
            case "USER" -> null;
            default -> "";
        };
        if (jpql == null) {
            return id;
        }
        if (jpql.isEmpty()) {
            return null;
        }
        List<UUID> result = entityManager.createQuery(jpql, UUID.class).setParameter("id", id).getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
}
