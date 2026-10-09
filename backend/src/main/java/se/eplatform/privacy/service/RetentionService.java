package se.eplatform.privacy.service;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.service.AuditService;
import se.eplatform.ops.service.SystemEventService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gallring: removes data whose retention period has run out.
 *
 * - Submitted cases are allmänna handlingar and are only removed when their
 *   e-service has a gallringsfrist (flows.retention_months), counted from
 *   when the case was closed. E-services without one keep their cases.
 * - Drafts that were never submitted are removed after a number of days.
 * - The traceability log and the system log are kept for a fixed period.
 *
 * Every removal is recorded in the traceability log.
 */
@Service
public class RetentionService {

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final AuditService auditService;
    private final SystemEventService systemEvents;
    private final int draftDays;
    private final int auditMonths;
    private final int systemEventDays;

    public RetentionService(EntityManager entityManager, TransactionTemplate transactions,
                            AuditService auditService, SystemEventService systemEvents,
                            @Value("${eplatform.retention.draft-days:90}") int draftDays,
                            @Value("${eplatform.retention.audit-months:24}") int auditMonths,
                            @Value("${eplatform.retention.system-event-days:90}") int systemEventDays) {
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.auditService = auditService;
        this.systemEvents = systemEvents;
        this.draftDays = draftDays;
        this.auditMonths = auditMonths;
        this.systemEventDays = systemEventDays;
    }

    public record Result(int casesPurged, int draftsPurged, int auditEntriesPurged, int systemEventsPurged) {}

    /** A case whose gallringsfrist has run out (or will soon, for the overview). */
    public record DueCase(UUID caseId, String referenceNumber, UUID ownerId, String flowName, java.time.Instant purgeAfter) {}

    @Scheduled(cron = "${eplatform.retention.cron:0 0 4 * * *}")
    public void scheduledRun() {
        run();
    }

    public Result run() {
        long started = System.currentTimeMillis();
        try {
            int cases = purgeExpiredCases();
            int drafts = purgeExpiredDrafts();
            int audit = transactions.execute(status -> purgeAuditEntries());
            int events = transactions.execute(status -> entityManager
                    .createNativeQuery("DELETE FROM system_events WHERE timestamp < NOW() - make_interval(days => :days)")
                    .setParameter("days", systemEventDays)
                    .executeUpdate());
            Result result = new Result(cases, drafts, audit, events);
            systemEvents.info("job:gallring", "Gallring klar", Map.of(
                    "arenden", cases, "utkast", drafts, "loggposter", audit, "systemhandelser", events,
                    "durationMs", System.currentTimeMillis() - started));
            return result;
        } catch (RuntimeException e) {
            systemEvents.error("job:gallring", "Gallringen misslyckades", Map.of("exception", e.getClass().getName()));
            throw e;
        }
    }

    /** Cases due for gallring now. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<DueCase> dueCases(int withinDays) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT c.id, c.reference_number, c.created_by, f.name,
                       c.completed_at + make_interval(months => f.retention_months) AS purge_after
                FROM cases c JOIN flows f ON f.id = c.flow_id
                WHERE f.retention_months IS NOT NULL AND c.completed_at IS NOT NULL
                  AND c.completed_at + make_interval(months => f.retention_months) < NOW() + make_interval(days => :within)
                ORDER BY purge_after
                """).setParameter("within", withinDays).getResultList();
        return rows.stream().map(r -> new DueCase(
                (UUID) r[0], (String) r[1], (UUID) r[2], (String) r[3], toInstant(r[4]))).toList();
    }

    private int purgeExpiredCases() {
        int count = 0;
        for (DueCase due : dueCases(0)) {
            transactions.executeWithoutResult(status -> entityManager
                    .createNativeQuery("DELETE FROM cases WHERE id = :id")
                    .setParameter("id", due.caseId())
                    .executeUpdate());
            auditService.recordSystem(AuditService.Entry.of(AuditAction.CASE_PURGED)
                    .entity("CASE", due.caseId())
                    .subject(due.ownerId())
                    .details("Gallrat enligt gallringsfrist: " + due.referenceNumber()));
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int purgeExpiredDrafts() {
        List<Object[]> drafts = entityManager.createNativeQuery("""
                SELECT id, created_by FROM cases
                WHERE submitted_at IS NULL AND updated_at < NOW() - make_interval(days => :days)
                """).setParameter("days", draftDays).getResultList();
        for (Object[] draft : drafts) {
            transactions.executeWithoutResult(status -> entityManager
                    .createNativeQuery("DELETE FROM cases WHERE id = :id")
                    .setParameter("id", draft[0])
                    .executeUpdate());
            auditService.recordSystem(AuditService.Entry.of(AuditAction.DRAFT_PURGED)
                    .entity("CASE", draft[0])
                    .subject((UUID) draft[1])
                    .details("Utkast äldre än " + draftDays + " dagar"));
        }
        return drafts.size();
    }

    private int purgeAuditEntries() {
        // The log is append-only; this setting lets this transaction remove expired entries
        entityManager.createNativeQuery("SELECT set_config('eplatform.audit_retention', 'on', true)").getSingleResult();
        return entityManager.createNativeQuery(
                        "DELETE FROM audit_events WHERE timestamp < NOW() - make_interval(months => :months)")
                .setParameter("months", auditMonths)
                .executeUpdate();
    }

    /** Native timestamptz values come back as Instant or OffsetDateTime depending on the driver path. */
    static java.time.Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant i) return i;
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        throw new IllegalArgumentException("Unexpected timestamp type " + value.getClass());
    }

    public int getDraftDays() { return draftDays; }
    public int getAuditMonths() { return auditMonths; }
}
