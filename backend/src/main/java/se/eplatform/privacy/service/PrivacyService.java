package se.eplatform.privacy.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.CaseEvent;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.cases.domain.InternalMessage;
import se.eplatform.cases.domain.QueryInstance;
import se.eplatform.storage.domain.Attachment;
import se.eplatform.user.domain.Role;
import se.eplatform.user.domain.User;
import se.eplatform.user.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The data subject's rights: register extracts (GDPR art. 15 and 20) and
 * erasure (art. 17) within what the archives act allows.
 *
 * Submitted cases are allmänna handlingar. GDPR art. 17.3 b exempts them from
 * erasure on request; they are removed by gallring when the e-service's
 * gallringsfrist runs out (see {@link RetentionService}). What can be erased
 * on request is the account itself, its contact details and drafts that
 * were never submitted.
 */
@Service
public class PrivacyService {

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public PrivacyService(EntityManager entityManager, UserRepository userRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    public record PersonSummary(UUID id, String name, String email, List<String> roles,
                                long submittedCases, long drafts, boolean active) {}

    /**
     * Find people by name or e-mail.
     */
    @Transactional(readOnly = true)
    public List<PersonSummary> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return userRepository.search(q, org.springframework.data.domain.PageRequest.of(0, 25)).stream()
                .map(this::summarize)
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonSummary summary(UUID userId) {
        return summarize(requireUser(userId));
    }

    private PersonSummary summarize(User user) {
        long submitted = count("SELECT COUNT(c) FROM Case c WHERE c.createdBy.id = :id AND c.submittedAt IS NOT NULL", user.getId());
        long drafts = count("SELECT COUNT(c) FROM Case c WHERE c.createdBy.id = :id AND c.submittedAt IS NULL", user.getId());
        return new PersonSummary(user.getId(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).sorted().toList(), submitted, drafts, user.isActive());
    }

    /**
     * Everything the system holds about a person, as one document.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> registerExtract(UUID userId) {
        User user = requireUser(userId);
        Map<String, Object> extract = new LinkedHashMap<>();
        extract.put("typ", "Registerutdrag enligt artikel 15 dataskyddsförordningen");
        extract.put("framtaget", Instant.now().toString());

        Map<String, Object> person = new LinkedHashMap<>();
        person.put("id", user.getId());
        person.put("namn", user.getFullName());
        person.put("fornamn", user.getFirstName());
        person.put("efternamn", user.getLastName());
        person.put("epost", user.getEmail());
        person.put("telefon", user.getPhone());
        person.put("roller", user.getRoles().stream().map(Role::getName).sorted().toList());
        person.put("skapad", user.getCreatedAt());
        person.put("senastInloggad", user.getLastLoginAt());
        extract.put("person", person);

        List<Case> cases = entityManager.createQuery(
                        "SELECT DISTINCT c FROM Case c JOIN FETCH c.flow LEFT JOIN FETCH c.status " +
                        "WHERE c.createdBy.id = :id ORDER BY c.createdAt", Case.class)
                .setParameter("id", userId)
                .getResultList();
        List<Map<String, Object>> caseList = new ArrayList<>();
        for (Case c : cases) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("referensnummer", c.getReferenceNumber());
            entry.put("etjanst", c.getFlow().getName());
            entry.put("status", c.getStatus() != null ? c.getStatus().getName() : null);
            entry.put("skapat", c.getCreatedAt());
            entry.put("inskickat", c.getSubmittedAt());
            entry.put("avslutat", c.getCompletedAt());

            Map<String, Object> answers = new LinkedHashMap<>();
            for (QueryInstance qi : c.getQueryInstances()) {
                if (qi.isPopulated()) {
                    answers.put(qi.getQueryDefinition().getName(), qi.getValue());
                }
            }
            entry.put("svar", answers);

            entry.put("meddelanden", c.getExternalMessages().stream()
                    .sorted(Comparator.comparing(ExternalMessage::getCreatedAt))
                    .map(m -> Map.of(
                            "fran", m.isFromManager() ? "Handläggare" : "Du",
                            "tid", m.getCreatedAt().toString(),
                            "text", m.getMessage()))
                    .toList());
            // Internal notes are personal data too; whether they can be disclosed
            // is decided under the secrecy act (OSL) when the extract is handed out
            entry.put("interna_anteckningar", c.getInternalMessages().stream()
                    .sorted(Comparator.comparing(InternalMessage::getCreatedAt))
                    .map(m -> Map.of("tid", m.getCreatedAt().toString(), "text", m.getMessage()))
                    .toList());
            entry.put("handelser", c.getEvents().stream()
                    .sorted(Comparator.comparing(CaseEvent::getCreatedAt))
                    .map(e -> {
                        Map<String, Object> ev = new HashMap<>();
                        ev.put("tid", e.getCreatedAt().toString());
                        ev.put("handelse", e.getDescription() != null ? e.getDescription() : e.getEventType().name());
                        return ev;
                    })
                    .toList());
            caseList.add(entry);
        }
        extract.put("arenden", caseList);

        List<Attachment> files = entityManager.createQuery(
                        "SELECT a FROM Attachment a WHERE a.uploadedBy = :id AND a.deleted = false ORDER BY a.uploadedAt", Attachment.class)
                .setParameter("id", userId)
                .getResultList();
        extract.put("bilagor", files.stream().map(a -> Map.of(
                "filnamn", a.getOriginalFilename(),
                "typ", a.getContentType(),
                "storlek", a.getFileSize(),
                "uppladdad", a.getUploadedAt().toString())).toList());

        // Who has handled this person's data (art. 15.1 c, recipients)
        List<AuditEvent> access = entityManager.createQuery(
                        "SELECT e FROM AuditEvent e WHERE e.subjectUserId = :id ORDER BY e.timestamp", AuditEvent.class)
                .setParameter("id", userId)
                .getResultList();
        extract.put("atkomstlogg", access.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tid", e.getTimestamp().toString());
            row.put("vem", e.getUserId().equals(userId.toString()) ? "Du" : nameOrSystem(e));
            row.put("handling", e.getAction().name());
            row.put("utfall", e.getOutcome().name());
            return row;
        }).toList());

        return extract;
    }

    private static String nameOrSystem(AuditEvent e) {
        if ("system".equals(e.getUserId())) return "Systemet";
        return e.getUserName() != null ? e.getUserName() : e.getUserId();
    }

    /** What erasure on request would remove and what has to be kept. */
    public record ErasurePreview(
            PersonSummary person,
            boolean allowed,
            String notAllowedReason,
            long draftsToDelete,
            List<RetainedCase> retainedCases) {}

    public record RetainedCase(String referenceNumber, String flowName, String status,
                               Instant closedAt, Integer retentionMonths, Instant purgeAfter) {}

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ErasurePreview erasurePreview(UUID userId) {
        User user = requireUser(userId);
        PersonSummary person = summarize(user);
        String reason = staffReason(user);

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT c.reference_number, f.name, s.name, c.completed_at, f.retention_months,
                       c.completed_at + make_interval(months => f.retention_months)
                FROM cases c JOIN flows f ON f.id = c.flow_id LEFT JOIN status_definitions s ON s.id = c.status_id
                WHERE c.created_by = :id AND c.submitted_at IS NOT NULL ORDER BY c.submitted_at
                """).setParameter("id", userId).getResultList();
        List<RetainedCase> retained = rows.stream().map(r -> new RetainedCase(
                (String) r[0], (String) r[1], (String) r[2],
                RetentionService.toInstant(r[3]),
                (Integer) r[4],
                RetentionService.toInstant(r[5]))).toList();

        return new ErasurePreview(person, reason == null, reason, person.drafts(), retained);
    }

    public record ErasureResult(long draftsDeleted, int casesRetained) {}

    /**
     * Erase what may be erased on request: drafts are deleted, the account is
     * deactivated and its name and contact details removed. Submitted cases
     * stay until gallring.
     */
    @Transactional
    public ErasureResult erase(UUID userId) {
        User user = requireUser(userId);
        String reason = staffReason(user);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }

        int drafts = entityManager.createNativeQuery("DELETE FROM cases WHERE created_by = :id AND submitted_at IS NULL")
                .setParameter("id", userId)
                .executeUpdate();
        long retained = count("SELECT COUNT(c) FROM Case c WHERE c.createdBy.id = :id", userId);

        String tag = userId.toString().substring(0, 8);
        user.setEmail("raderad-" + tag + "@raderad.invalid");
        user.setUsername(null);
        user.setExternalId(null);
        user.setFirstName("Raderad");
        user.setLastName("användare");
        user.setDisplayName(null);
        user.setPhone(null);
        user.setSettings(Map.of());
        user.setActive(false);
        user.getRoles().clear();
        userRepository.save(user);

        return new ErasureResult(drafts, (int) retained);
    }

    /** Staff accounts are handled through employment, not data subject requests. */
    private static String staffReason(User user) {
        boolean staff = user.getRoles().stream().anyMatch(r -> !"USER".equals(r.getName()));
        return staff ? "Kontot har en tjänsteroll och hanteras inte som en registrerads begäran" : null;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private long count(String jpql, UUID id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
