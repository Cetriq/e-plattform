package se.eplatform.flow.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import se.eplatform.flow.domain.EvaluatorDefinition;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a version of an e-service into the documents that are published for
 * it, in canonical form (RFC 8785, JSON Canonicalization Scheme) and hashed
 * with SHA-256 over the UTF-8 bytes. Anyone with the documents can compute
 * the same hashes with any RFC 8785 implementation.
 *
 * - contract (eplatform.contract/1): what decides the outcome — fields and
 *   their validation, conditions, statuses and transitions
 * - projection (eplatform.projection/1): what the citizen is shown — steps,
 *   questions, help texts and messages
 * - manifest (eplatform.manifest/1): binds both to the published version
 *
 * Lists are in a fixed order (sort order, then id), so the same version always
 * gives the same documents.
 */
@Component
public class ContractCanonicalizer {

    public static final String CONTRACT_FORMAT = "eplatform.contract/1";
    public static final String PROJECTION_FORMAT = "eplatform.projection/1";
    public static final String MANIFEST_FORMAT = "eplatform.manifest/1";

    /** A canonical document and its hash. */
    public record Document(String format, String canonical, String hash) {}

    private static final Comparator<Step> STEPS =
            Comparator.comparing((Step s) -> nz(s.getSortOrder())).thenComparing(s -> s.getId().toString());
    private static final Comparator<QueryDefinition> FIELDS =
            Comparator.comparing((QueryDefinition q) -> nz(q.getSortOrder())).thenComparing(q -> q.getId().toString());
    private static final Comparator<StatusDefinition> STATUSES =
            Comparator.comparing((StatusDefinition s) -> nz(s.getSortOrder())).thenComparing(s -> s.getId().toString());

    // Its own mapper, with default settings: the documents must not depend on
    // how the application's JSON responses are configured (nulls are kept)
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public ContractCanonicalizer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The rules of a version. Must run in a transaction with the version's parts loadable. */
    public Document contract(Flow flow) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", CONTRACT_FORMAT);

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("organizationId", str(flow.getOrganizationId()));
        service.put("familyId", str(flow.getFamily().getId()));
        service.put("version", flow.getVersion());
        service.put("requireAuth", flow.isRequireAuth());
        service.put("requireSigning", flow.isRequireSigning());
        service.put("allowSaveDraft", flow.isAllowSaveDraft());
        service.put("allowMultiple", flow.isAllowMultiple());
        service.put("retentionMonths", flow.getRetentionMonths());
        service.put("accessScope", flow.getAccessScope());
        doc.put("service", service);

        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Step step : flow.getSteps().stream().sorted(STEPS).toList()) {
            for (QueryDefinition query : step.getQueryDefinitions().stream().sorted(FIELDS).toList()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("id", str(query.getId()));
                field.put("step", str(step.getId()));
                field.put("type", query.getQueryType().name());
                field.put("required", query.isRequired());
                field.put("defaultState", query.getDefaultState() == null ? null : query.getDefaultState().name());
                field.put("config", query.getConfig());
                field.put("exportName", query.getExportName());
                field.put("exportable", query.isExportable());
                fields.add(field);

                for (EvaluatorDefinition evaluator : query.getEvaluators().stream()
                        .sorted(Comparator.comparing((EvaluatorDefinition e) -> nz(e.getSortOrder()))
                                .thenComparing(e -> e.getId().toString())).toList()) {
                    Map<String, Object> condition = new LinkedHashMap<>();
                    condition.put("id", str(evaluator.getId()));
                    condition.put("source", str(query.getId()));
                    condition.put("type", evaluator.getEvaluatorType().name());
                    condition.put("condition", evaluator.getCondition());
                    condition.put("targets", evaluator.getTargetQueryIds() == null ? List.of()
                            : evaluator.getTargetQueryIds().stream().map(UUID::toString).sorted().toList());
                    condition.put("targetState", evaluator.getTargetState() == null ? null : evaluator.getTargetState().name());
                    condition.put("enabled", evaluator.isEnabled());
                    conditions.add(condition);
                }
            }
        }
        doc.put("fields", fields);
        doc.put("conditions", conditions);

        List<StatusDefinition> statusDefinitions = flow.getStatusDefinitions().stream().sorted(STATUSES).toList();
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (StatusDefinition status : statusDefinitions) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", str(status.getId()));
            s.put("name", status.getName());
            s.put("type", status.getStatusType().name());
            s.put("userCanEdit", status.isUserCanEdit());
            s.put("userCanDelete", status.isUserCanDelete());
            s.put("userCanMessage", status.isUserCanMessage());
            s.put("managerCanEdit", status.isManagerCanEdit());
            s.put("handlingDays", status.getHandlingDays());
            statuses.add(s);
        }
        doc.put("statuses", statuses);

        List<UUID> statusIds = statusDefinitions.stream().map(StatusDefinition::getId).toList();
        List<Map<String, Object>> transitions = statusIds.isEmpty() ? List.of() : jdbc.query(
                "SELECT from_status_id, to_status_id, requires_comment, requires_signature FROM status_transitions " +
                "WHERE from_status_id = ANY (?) ORDER BY from_status_id::text, to_status_id::text",
                statement -> statement.setArray(1, statement.getConnection().createArrayOf("uuid", statusIds.toArray())),
                (row, i) -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("from", row.getString("from_status_id"));
                    t.put("to", row.getString("to_status_id"));
                    t.put("requiresComment", row.getBoolean("requires_comment"));
                    t.put("requiresSignature", row.getBoolean("requires_signature"));
                    return t;
                });
        doc.put("transitions", transitions);
        return document(CONTRACT_FORMAT, doc);
    }

    /** What the citizen is shown in a version. */
    public Document projection(Flow flow) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", PROJECTION_FORMAT);
        doc.put("language", "sv");

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("name", flow.getName());
        service.put("shortDescription", flow.getShortDescription());
        service.put("longDescription", flow.getLongDescription());
        service.put("submittedMessage", flow.getSubmittedMessage());
        doc.put("service", service);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Step step : flow.getSteps().stream().sorted(STEPS).toList()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", str(step.getId()));
            s.put("name", step.getName());
            s.put("description", step.getDescription());
            List<Map<String, Object>> fields = new ArrayList<>();
            for (QueryDefinition query : step.getQueryDefinitions().stream().sorted(FIELDS).toList()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", str(query.getId()));
                f.put("name", query.getName());
                f.put("description", query.getDescription());
                f.put("helpText", query.getHelpText());
                f.put("placeholder", query.getPlaceholder());
                f.put("width", query.getWidth());
                fields.add(f);
            }
            s.put("fields", fields);
            steps.add(s);
        }
        doc.put("steps", steps);

        List<Map<String, Object>> statuses = new ArrayList<>();
        for (StatusDefinition status : flow.getStatusDefinitions().stream().sorted(STATUSES).toList()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", str(status.getId()));
            s.put("description", status.getDescription());
            s.put("color", status.getColor());
            statuses.add(s);
        }
        doc.put("statuses", statuses);
        return document(PROJECTION_FORMAT, doc);
    }

    /**
     * The manifest binding contract and projection to the version.
     * @param origin "publish", or "backfill" for versions published before manifests existed
     */
    public Document manifest(Flow flow, Document contract, Document projection, Instant publishedAt,
                             UUID publishedBy, String origin) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", MANIFEST_FORMAT);
        doc.put("origin", origin);
        doc.put("flowId", str(flow.getId()));
        doc.put("familyId", str(flow.getFamily().getId()));
        doc.put("version", flow.getVersion());
        doc.put("organizationId", str(flow.getOrganizationId()));
        doc.put("contractHash", contract.hash());
        doc.put("projectionHash", projection.hash());
        doc.put("publishedAt", publishedAt == null ? null : publishedAt.truncatedTo(ChronoUnit.MILLIS).toString());
        doc.put("publishedBy", str(publishedBy));
        // Legal validity is decided per definition in steg 3; until then it is open
        doc.put("validFrom", null);
        doc.put("validTo", null);
        return document(MANIFEST_FORMAT, doc);
    }

    /** Canonical form and SHA-256 of a stored document, to verify it. */
    public static String hashOf(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Document document(String format, Map<String, Object> doc) {
        try {
            String canonical = new JsonCanonicalizer(objectMapper.writeValueAsString(doc)).getEncodedString();
            return new Document(format, canonical, hashOf(canonical));
        } catch (IOException e) {
            throw new IllegalStateException("Could not canonicalize " + format, e);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
