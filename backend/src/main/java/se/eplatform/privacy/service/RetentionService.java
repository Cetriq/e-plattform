package se.eplatform.privacy.service;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * The removal itself happens in the database procedures of the schema
 * "retention" (V9), called over a separate connection as the role
 * eplatform_retention. The periods are read from retention_rules by the
 * procedures; nothing here can shorten them. Every removal is recorded in the
 * traceability log.
 */
@Service
public class RetentionService {

    private final EntityManager entityManager;
    private final AuditService auditService;
    private final SystemEventService systemEvents;
    private final JdbcTemplate retention;

    public RetentionService(EntityManager entityManager, AuditService auditService, SystemEventService systemEvents,
                            @Value("${eplatform.retention.datasource.url}") String url,
                            @Value("${eplatform.retention.datasource.username}") String username,
                            @Value("${eplatform.retention.datasource.password:}") String password) {
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.systemEvents = systemEvents;
        // Gallring runs once a night, so a plain connection per run is enough
        this.retention = password.isBlank() ? null
                : new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    public record Result(int casesPurged, int draftsPurged, int auditEntriesPurged, int systemEventsPurged) {}

    /** A case whose gallringsfrist has run out (or will soon, for the overview). */
    public record DueCase(UUID caseId, String referenceNumber, UUID ownerId, String flowName, java.time.Instant purgeAfter,
                          UUID organizationId) {}

    @Scheduled(cron = "${eplatform.retention.cron:0 0 4 * * *}")
    public void scheduledRun() {
        run();
    }

    public Result run() {
        if (retention == null) {
            systemEvents.error("job:gallring", "Gallringen är inte konfigurerad", Map.of(
                    "saknas", "eplatform.retention.datasource.password"));
            throw new IllegalStateException("Retention is not configured (eplatform.retention.datasource.password)");
        }
        long started = System.currentTimeMillis();
        try {
            int[] cases = purgeCases();
            int audit = purgeAuditEntries();
            Integer events = retention.queryForObject("SELECT retention.purge_system_events()", Integer.class);
            Result result = new Result(cases[0], cases[1], audit, events == null ? 0 : events);
            systemEvents.info("job:gallring", "Gallring klar", Map.of(
                    "arenden", result.casesPurged(), "utkast", result.draftsPurged(), "loggposter", audit,
                    "systemhandelser", result.systemEventsPurged(),
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
                       c.completed_at + make_interval(months => f.retention_months) AS purge_after,
                       c.organization_id
                FROM cases c JOIN flows f ON f.id = c.flow_id
                WHERE f.retention_months IS NOT NULL AND c.completed_at IS NOT NULL
                  AND c.completed_at + make_interval(months => f.retention_months) < NOW() + make_interval(days => :within)
                ORDER BY purge_after
                """).setParameter("within", withinDays).getResultList();
        return rows.stream().map(r -> new DueCase(
                (UUID) r[0], (String) r[1], (UUID) r[2], (String) r[3], toInstant(r[4]), (UUID) r[5])).toList();
    }

    /** @return closed cases and drafts removed */
    private int[] purgeCases() {
        int[] counts = new int[2];
        retention.query("SELECT case_id, reference_number, owner_id, reason, organization_id FROM retention.purge_cases()", row -> {
            UUID caseId = row.getObject("case_id", UUID.class);
            UUID ownerId = row.getObject("owner_id", UUID.class);
            UUID organization = row.getObject("organization_id", UUID.class);
            if ("CLOSED".equals(row.getString("reason"))) {
                auditService.recordSystem(AuditService.Entry.of(AuditAction.CASE_PURGED)
                        .entity("CASE", caseId)
                        .organization(organization)
                        .subject(ownerId)
                        .details("Gallrat enligt gallringsfrist: " + row.getString("reference_number")));
                counts[0]++;
            } else {
                auditService.recordSystem(AuditService.Entry.of(AuditAction.DRAFT_PURGED)
                        .entity("CASE", caseId)
                        .organization(organization)
                        .subject(ownerId)
                        .details("Utkast äldre än " + getDraftDays() + " dagar"));
                counts[1]++;
            }
        });
        return counts;
    }

    /** Each chain is gallrad on its own; the run is logged in the chain it removed from. */
    private int purgeAuditEntries() {
        int[] total = new int[1];
        retention.query("SELECT chain, anchor_id, removed FROM retention.purge_audit_events()", row -> {
            String chain = row.getString("chain");
            int removed = row.getInt("removed");
            UUID organization = AuditService.PLATFORM_CHAIN.equals(chain) || AuditService.LEGACY_CHAIN.equals(chain)
                    ? null : UUID.fromString(chain);
            auditService.recordSystem(AuditService.Entry.of(AuditAction.RETENTION_RUN)
                    .entity("AUDIT_ANCHOR", row.getLong("anchor_id"))
                    .organization(organization)
                    .details("Gallrade " + removed + " loggposter i kedjan " + chain + " enligt regeln audit_events"));
            total[0] += removed;
        });
        return total[0];
    }

    /** Days a draft is kept, from retention_rules. */
    public int getDraftDays() {
        return ruleValue("SELECT CAST(EXTRACT(DAY FROM keep_interval) AS int) FROM retention_rules WHERE data_type = 'draft_cases'");
    }

    /** Months the traceability log is kept, from retention_rules. */
    public int getAuditMonths() {
        return ruleValue("SELECT CAST(EXTRACT(YEAR FROM keep_interval) * 12 + EXTRACT(MONTH FROM keep_interval) AS int) " +
                         "FROM retention_rules WHERE data_type = 'audit_events'");
    }

    private int ruleValue(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).intValue();
    }

    /** Native timestamptz values come back as Instant or OffsetDateTime depending on the driver path. */
    static java.time.Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant i) return i;
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        throw new IllegalArgumentException("Unexpected timestamp type " + value.getClass());
    }
}
