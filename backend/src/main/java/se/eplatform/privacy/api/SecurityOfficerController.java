package se.eplatform.privacy.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.domain.AuditCategory;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.domain.AuditOutcome;
import se.eplatform.audit.repository.AuditEventRepository;
import se.eplatform.audit.service.AuditChainVerifier;
import se.eplatform.audit.web.AuditContext;
import se.eplatform.audit.web.Audited;
import se.eplatform.privacy.service.PrivacyService;
import se.eplatform.privacy.service.RetentionService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * For the information security and data protection role (SECURITY_OFFICER):
 * the traceability log, register extracts, erasure and gallring.
 * Every call is itself recorded in the traceability log.
 */
@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Informationssäkerhet & dataskydd", description = "Kräver rollen SECURITY_OFFICER.")
public class SecurityOfficerController {

    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    private final AuditEventRepository auditRepository;
    private final AuditChainVerifier chainVerifier;
    private final PrivacyService privacyService;
    private final RetentionService retentionService;
    private final ObjectMapper objectMapper;

    public SecurityOfficerController(AuditEventRepository auditRepository, AuditChainVerifier chainVerifier,
                                     PrivacyService privacyService, RetentionService retentionService,
                                     ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.chainVerifier = chainVerifier;
        this.privacyService = privacyService;
        this.retentionService = retentionService;
        this.objectMapper = objectMapper;
    }

    // ---------- Spårbarhetslogg ----------

    public record AuditEventDTO(
            long seq, Instant timestamp, String userId, String userName, String userEmail,
            AuditAction action, AuditCategory category, AuditOutcome outcome,
            String entityType, String entityId, UUID subjectUserId, String details,
            String ipAddress, String requestMethod, String requestPath, Integer responseStatus) {

        static AuditEventDTO from(AuditEvent e) {
            return new AuditEventDTO(e.getSeq(), e.getTimestamp(), e.getUserId(), e.getUserName(), e.getUserEmail(),
                    e.getAction(), e.getCategory(), e.getOutcome(), e.getEntityType(), e.getEntityId(),
                    e.getSubjectUserId(), e.getDetails(), e.getIpAddress(), e.getRequestMethod(),
                    e.getRequestPath(), e.getResponseStatus());
        }
    }

    @Operation(summary = "Sök i spårbarhetsloggen")
    @Audited(AuditAction.AUDIT_VIEW)
    @GetMapping("/audit")
    public Page<AuditEventDTO> searchAudit(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        AuditContext.details(describeFilter(userId, subjectId, action, category, outcome, entityId, from, to));
        return auditRepository.findAll(
                filter(userId, subjectId, action, category, outcome, entityId, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "seq")))
                .map(AuditEventDTO::from);
    }

    @Operation(summary = "Exportera spårbarhetsloggen", description = "CSV (standard) eller JSON, högst 50 000 rader.")
    @Audited(AuditAction.AUDIT_EXPORT)
    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAudit(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        List<AuditEventDTO> rows = auditRepository.findAll(
                        filter(userId, subjectId, action, category, outcome, entityId, from, to),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.ASC, "seq")))
                .map(AuditEventDTO::from)
                .getContent();
        AuditContext.details(rows.size() + " rader som " + format.toUpperCase() + "; "
                + describeFilter(userId, subjectId, action, category, outcome, entityId, from, to));

        String stamp = LocalDate.now(SWEDEN).toString();
        if ("json".equalsIgnoreCase(format)) {
            return download(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows),
                    "sparbarhetslogg-" + stamp + ".json", MediaType.APPLICATION_JSON);
        }
        return download(toCsv(rows), "sparbarhetslogg-" + stamp + ".csv", new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    @Operation(summary = "Kontrollera att loggen inte har ändrats", description = "Räknar om hashkedjan.")
    @Audited(AuditAction.AUDIT_VERIFY)
    @GetMapping("/audit/verify")
    public AuditChainVerifier.Result verifyAudit() {
        AuditChainVerifier.Result result = chainVerifier.verify();
        AuditContext.details(result.intact() ? result.verified() + " poster intakta"
                : "Bruten vid post " + result.firstBrokenSeq());
        return result;
    }

    // ---------- Registrerade ----------

    @Operation(summary = "Sök person", description = "På namn eller e-post, minst två tecken.")
    @Audited(AuditAction.USER_LIST)
    @GetMapping("/people")
    public List<PrivacyService.PersonSummary> searchPeople(@RequestParam String q) {
        return privacyService.search(q);
    }

    @Operation(summary = "Registerutdrag", description = "Allt systemet har om personen (artikel 15 och 20), som JSON.")
    @Audited(value = AuditAction.REGISTER_EXTRACT, entity = "USER", idParam = "userId")
    @GetMapping("/people/{userId}/extract")
    public ResponseEntity<byte[]> registerExtract(@PathVariable UUID userId) throws Exception {
        Map<String, Object> extract = privacyService.registerExtract(userId);
        return download(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(extract),
                "registerutdrag-" + userId.toString().substring(0, 8) + "-" + LocalDate.now(SWEDEN) + ".json",
                MediaType.APPLICATION_JSON);
    }

    @Operation(summary = "Förhandsgranska radering", description = "Vad som raderas och vad som måste bevaras.")
    @Audited(value = AuditAction.USER_LIST, entity = "USER", idParam = "userId")
    @GetMapping("/people/{userId}/erasure")
    public PrivacyService.ErasurePreview erasurePreview(@PathVariable UUID userId) {
        return privacyService.erasurePreview(userId);
    }

    @Operation(summary = "Radera enligt begäran", description = """
            Raderar utkast och kontaktuppgifter och avaktiverar kontot. Inskickade ärenden är
            allmänna handlingar och bevaras tills e-tjänstens gallringsfrist löper ut.
            """)
    @Audited(value = AuditAction.USER_ERASED, entity = "USER", idParam = "userId")
    @PostMapping("/people/{userId}/erase")
    public PrivacyService.ErasureResult erase(@PathVariable UUID userId) {
        PrivacyService.ErasureResult result = privacyService.erase(userId);
        AuditContext.details(result.draftsDeleted() + " utkast raderade, " + result.casesRetained() + " ärenden bevaras");
        return result;
    }

    // ---------- Gallring ----------

    public record RetentionOverview(int draftDays, int auditMonths, List<RetentionService.DueCase> dueWithin30Days) {}

    @Operation(summary = "Gallringsläge", description = "Inställningar och ärenden som gallras inom 30 dagar.")
    @GetMapping("/retention")
    public RetentionOverview retention() {
        return new RetentionOverview(retentionService.getDraftDays(), retentionService.getAuditMonths(),
                retentionService.dueCases(30));
    }

    @Operation(summary = "Kör gallring nu")
    @PostMapping("/retention/run")
    public RetentionService.Result runRetention() {
        return retentionService.run();
    }

    // ---------- Helpers ----------

    private static Specification<AuditEvent> filter(String userId, UUID subjectId, AuditAction action,
                                                    AuditCategory category, AuditOutcome outcome, String entityId,
                                                    LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null && !userId.isBlank()) p.add(cb.equal(root.get("userId"), userId));
            if (subjectId != null) p.add(cb.equal(root.get("subjectUserId"), subjectId));
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (category != null) p.add(cb.equal(root.get("category"), category));
            if (outcome != null) p.add(cb.equal(root.get("outcome"), outcome));
            if (entityId != null && !entityId.isBlank()) p.add(cb.equal(root.get("entityId"), entityId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from.atStartOfDay(SWEDEN).toInstant()));
            if (to != null) p.add(cb.lessThan(root.get("timestamp"), to.plusDays(1).atStartOfDay(SWEDEN).toInstant()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String describeFilter(Object... values) {
        String[] names = {"användare", "registrerad", "händelse", "kategori", "utfall", "objekt", "från", "till"};
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && !values[i].toString().isBlank()) parts.add(names[i] + "=" + values[i]);
        }
        return parts.isEmpty() ? "utan filter" : String.join(", ", parts);
    }

    private static byte[] toCsv(List<AuditEventDTO> rows) {
        StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel reads UTF-8
        csv.append("nr;tid;anvandare_id;anvandare;epost;handelse;kategori;utfall;objekttyp;objekt_id;registrerad_id;detaljer;ip;metod;sokvag;status\n");
        for (AuditEventDTO r : rows) {
            csv.append(String.join(";",
                    String.valueOf(r.seq()), r.timestamp().toString(), cell(r.userId()), cell(r.userName()),
                    cell(r.userEmail()), r.action().name(), r.category().name(), r.outcome().name(),
                    cell(r.entityType()), cell(r.entityId()),
                    r.subjectUserId() == null ? "" : r.subjectUserId().toString(),
                    cell(r.details()), cell(r.ipAddress()), cell(r.requestMethod()), cell(r.requestPath()),
                    r.responseStatus() == null ? "" : r.responseStatus().toString())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Quote a CSV cell and neutralise spreadsheet formulas (CSV injection). */
    private static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static ResponseEntity<byte[]> download(byte[] body, String filename, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
