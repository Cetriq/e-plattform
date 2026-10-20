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
 *
 * Each organisation is data controller for what its e-services hold: it finds
 * the people with cases there (and its own staff), its extract contains its
 * cases, files and log, and its erasure removes its drafts. The platform is
 * data controller for the account itself: its extract is the account, the
 * memberships and the platform's log, and only it erases the account. People
 * outside the organisation's scope are reported as not found.
 */
@Service
public class PrivacyService {

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public PrivacyService(EntityManager entityManager, UserRepository userRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
    }

    /** Whose data the request concerns: one organisation's, or the platform's (the accounts). */
    record Scope(UUID organizationId) {
        static Scope current() {
            return new Scope(se.eplatform.common.security.CurrentUser.requireOrganizationId());
        }

        boolean platform() {
            return se.eplatform.user.domain.Organization.PLATFORM_ID.equals(organizationId);
        }
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
        Scope scope = Scope.current();
        var page = org.springframework.data.domain.PageRequest.of(0, 25);
        return (scope.platform() ? userRepository.search(q, page)
                                 : userRepository.searchInOrganization(q, scope.organizationId(), page))
                .stream()
                .map(user -> summarize(user, scope))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonSummary summary(UUID userId) {
        Scope scope = Scope.current();
        return summarize(requireUser(userId, scope), scope);
    }

    private PersonSummary summarize(User user, Scope scope) {
        long submitted = countCases(user.getId(), scope, true);
        long drafts = countCases(user.getId(), scope, false);
        return new PersonSummary(user.getId(), user.getFullName(), user.getEmail(),
                roles(user, scope), submitted, drafts, user.isActive());
    }

    /** Global roles, and the roles in the organisation (all of them for the platform). */
    private static List<String> roles(User user, Scope scope) {
        return java.util.stream.Stream.concat(user.getRoles().stream(), user.getMemberships().stream()
                        .filter(m -> scope.platform() || m.getOrganization().getId().equals(scope.organizationId()))
                        .flatMap(m -> m.getRoles().stream()))
                .map(Role::getName).distinct().sorted().toList();
    }

    private long countCases(UUID userId, Scope scope, boolean submitted) {
        return entityManager.createQuery("SELECT COUNT(c) FROM Case c WHERE c.createdBy.id = :id AND " +
                        (submitted ? "c.submittedAt IS NOT NULL" : "c.submittedAt IS NULL") +
                        " AND (:all = true OR c.organizationId = :org)", Long.class)
                .setParameter("id", userId)
                .setParameter("all", scope.platform())
                .setParameter("org", scope.organizationId())
                .getSingleResult();
    }

    /**
     * Everything the system holds about a person, as one document.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> registerExtract(UUID userId) {
        Scope scope = Scope.current();
        User user = requireUser(userId, scope);
        Map<String, Object> extract = new LinkedHashMap<>();
        extract.put("typ", "Registerutdrag enligt artikel 15 dataskyddsförordningen");
        extract.put("framtaget", Instant.now().toString());
        extract.put("personuppgiftsansvarig", entityManager.createQuery(
                        "SELECT o.name FROM Organization o WHERE o.id = :id", String.class)
                .setParameter("id", scope.organizationId()).getSingleResult());

        Map<String, Object> person = new LinkedHashMap<>();
        person.put("id", user.getId());
        person.put("namn", user.getFullName());
        person.put("fornamn", user.getFirstName());
        person.put("efternamn", user.getLastName());
        person.put("epost", user.getEmail());
        person.put("telefon", user.getPhone());
        person.put("roller", roles(user, scope));
        if (scope.platform()) {
            person.put("organisationer", user.getMemberships().stream()
                    .map(m -> m.getOrganization().getName()).sorted().toList());
            // Where the person's cases are, so a request about them can be passed on
            extract.put("arenden_finns_hos", entityManager.createQuery(
                            "SELECT DISTINCT o.name FROM Case c, Organization o " +
                            "WHERE c.createdBy.id = :id AND o.id = c.organizationId ORDER BY o.name", String.class)
                    .setParameter("id", userId).getResultList());
        }
        person.put("skapad", user.getCreatedAt());
        person.put("senastInloggad", user.getLastLoginAt());
        extract.put("person", person);

        List<Case> cases = entityManager.createQuery(
                        "SELECT DISTINCT c FROM Case c JOIN FETCH c.flow LEFT JOIN FETCH c.status " +
                        "WHERE c.createdBy.id = :id AND c.organizationId = :org ORDER BY c.createdAt", Case.class)
                .setParameter("id", userId)
                .setParameter("org", scope.organizationId())
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
                        "SELECT a FROM Attachment a WHERE a.uploadedBy = :id AND a.deleted = false " +
                        "AND a.organizationId = :org ORDER BY a.uploadedAt", Attachment.class)
                .setParameter("id", userId)
                .setParameter("org", scope.organizationId())
                .getResultList();
        extract.put("bilagor", files.stream().map(a -> Map.of(
                "filnamn", a.getOriginalFilename(),
                "typ", a.getContentType(),
                "storlek", a.getFileSize(),
                "uppladdad", a.getUploadedAt().toString())).toList());

        // Who has handled this person's data (art. 15.1 c, recipients), in this
        // organisation's log; the platform's log holds the account events
        var accessQuery = entityManager.createQuery(
                        "SELECT e FROM AuditEvent e WHERE e.subjectUserId = :id AND " +
                        (scope.platform() ? "e.organizationId IS NULL" : "e.organizationId = :org") +
                        " ORDER BY e.timestamp", AuditEvent.class)
                .setParameter("id", userId);
        if (!scope.platform()) {
            accessQuery.setParameter("org", scope.organizationId());
        }
        List<AuditEvent> access = accessQuery.getResultList();
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
    /**
     * @param accountErased true when the account itself would be anonymised (platform only)
     */
    public record ErasurePreview(
            PersonSummary person,
            boolean allowed,
            String notAllowedReason,
            long draftsToDelete,
            List<RetainedCase> retainedCases,
            boolean accountErased) {}

    public record RetainedCase(String referenceNumber, String flowName, String status,
                               Instant closedAt, Integer retentionMonths, Instant purgeAfter) {}

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ErasurePreview erasurePreview(UUID userId) {
        Scope scope = Scope.current();
        User user = requireUser(userId, scope);
        PersonSummary person = summarize(user, scope);
        String reason = staffReason(user, scope);

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT c.reference_number, f.name, s.name, c.completed_at, f.retention_months,
                       c.completed_at + make_interval(months => f.retention_months)
                FROM cases c JOIN flows f ON f.id = c.flow_id LEFT JOIN status_definitions s ON s.id = c.status_id
                WHERE c.created_by = :id AND c.submitted_at IS NOT NULL
                  AND (CAST(:all AS boolean) OR c.organization_id = :org)
                ORDER BY c.submitted_at
                """).setParameter("id", userId).setParameter("all", scope.platform())
                .setParameter("org", scope.organizationId()).getResultList();
        List<RetainedCase> retained = rows.stream().map(r -> new RetainedCase(
                (String) r[0], (String) r[1], (String) r[2],
                RetentionService.toInstant(r[3]),
                (Integer) r[4],
                RetentionService.toInstant(r[5]))).toList();

        return new ErasurePreview(person, reason == null, reason, person.drafts(), retained, scope.platform());
    }

    public record ErasureResult(long draftsDeleted, int casesRetained, boolean accountErased) {}

    /**
     * Erase what may be erased on request. An organisation deletes the drafts
     * in its e-services. The platform also deactivates the account and removes
     * its name and contact details, and deletes drafts everywhere. Submitted
     * cases stay until gallring.
     */
    @Transactional
    public ErasureResult erase(UUID userId) {
        Scope scope = Scope.current();
        User user = requireUser(userId, scope);
        String reason = staffReason(user, scope);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }

        int drafts = entityManager.createNativeQuery("DELETE FROM cases WHERE created_by = :id AND submitted_at IS NULL " +
                        "AND (CAST(:all AS boolean) OR organization_id = :org)")
                .setParameter("id", userId)
                .setParameter("all", scope.platform())
                .setParameter("org", scope.organizationId())
                .executeUpdate();
        long retained = countCases(userId, scope, true);
        if (!scope.platform()) {
            return new ErasureResult(drafts, (int) retained, false);
        }

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
        user.getMemberships().clear();
        userRepository.save(user);

        return new ErasureResult(drafts, (int) retained, true);
    }

    /** Staff accounts are handled through employment, not data subject requests. */
    private static String staffReason(User user, Scope scope) {
        boolean staff = user.getRoles().stream().anyMatch(r -> !"USER".equals(r.getName()))
                || user.getMemberships().stream().anyMatch(m ->
                        scope.platform() || m.getOrganization().getId().equals(scope.organizationId()));
        return staff ? "Kontot har en tjänsteroll och hanteras inte som en registrerads begäran" : null;
    }

    /** The person, if they are in the scope's data: cases there, or a member there. */
    private User requireUser(UUID userId, Scope scope) {
        return userRepository.findById(userId)
                .filter(user -> scope.platform() || inOrganization(userId, scope.organizationId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Personen hittades inte"));
    }

    private boolean inOrganization(UUID userId, UUID organizationId) {
        return entityManager.createQuery(
                        "SELECT COUNT(u) > 0 FROM User u WHERE u.id = :id AND (" +
                        "EXISTS (SELECT c FROM Case c WHERE c.createdBy = u AND c.organizationId = :org) OR " +
                        "EXISTS (SELECT m FROM OrganizationMembership m WHERE m.user = u AND m.organization.id = :org))",
                        Boolean.class)
                .setParameter("id", userId)
                .setParameter("org", organizationId)
                .getSingleResult();
    }
}
