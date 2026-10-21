package se.eplatform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.common.security.CurrentUser;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Service definitions (level 1) and organisations' applications of them
 * (level 2), with their versions.
 *
 * - A definition is maintained by its steward organisation. Its published
 *   versions can be read by every organisation's administrators; drafts only
 *   by the steward.
 * - An application is maintained by its owner organisation and read by the
 *   organisations that have a role in it. It applies one published definition
 *   version; applying a newer one is a new version of the application.
 * - Documents are checked against their JSON Schema when saved and published.
 *   A published version never changes; its content is hashed in canonical form.
 *
 * Composing a definition and an application into a publishable service, with
 * the checks of the change policy, is steg 4.
 */
@Service
@Transactional(readOnly = true)
public class ContractService {

    public static final String DEFINITION_VERSION_FORMAT = "eplatform.definition-version/1";
    public static final String APPLICATION_VERSION_FORMAT = "eplatform.application-version/1";
    public static final String PACKAGE_FORMAT = "eplatform.definition-package/1";

    private final JdbcTemplate jdbc;
    private final ContractDocumentValidator validator;
    private final ObjectMapper mapper = CanonicalJson.mapper();

    public ContractService(JdbcTemplate jdbc, ContractDocumentValidator validator) {
        this.jdbc = jdbc;
        this.validator = validator;
    }

    // ---------- Records ----------

    public record Version(UUID id, int version, String status, LocalDate validFrom, LocalDate validTo,
                          JsonNode legalBasis, JsonNode content, String contentHash, UUID definitionVersionId) {}

    public record Definition(UUID id, String key, String name, UUID stewardOrganizationId, List<Version> versions) {}

    public record Role(UUID organizationId, boolean responsible, boolean decidingAuthority,
                       boolean dataController, boolean participant) {}

    public record Application(UUID id, UUID definitionId, String name, UUID ownerOrganizationId,
                              List<Role> roles, List<Version> versions) {}

    public record Concept(String key, int version, JsonNode schema, String dataClass, String description) {}

    /**
     * A published application version as a version of an e-service applies it:
     * both level versions with their hashes, the legal validity they give
     * together, and the field keys the e-service's fields may name (the
     * definition's fields and the application's local fields).
     */
    public record Binding(UUID applicationId, UUID applicationVersionId, int applicationVersion, String applicationHash,
                          UUID definitionId, String definitionKey, UUID definitionVersionId, int definitionVersion,
                          String definitionHash, LocalDate validFrom, LocalDate validTo, Set<String> fieldKeys) {}

    /** Documents that do not follow their schema. */
    public static class InvalidDocument extends RuntimeException {
        private final List<String> errors;

        public InvalidDocument(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }

        public List<String> errors() {
            return errors;
        }
    }

    // ---------- Concepts ----------

    public List<Concept> concepts() {
        return jdbc.query("SELECT key, version, schema::text, data_class, description FROM concepts ORDER BY key, version",
                (rs, i) -> new Concept(rs.getString(1), rs.getInt(2), json(rs.getString(3)), rs.getString(4), rs.getString(5)));
    }

    // ---------- Definitions ----------

    public List<Definition> definitions() {
        UUID org = org();
        List<UUID> ids = jdbc.queryForList("""
                SELECT d.id FROM service_definitions d
                WHERE d.steward_organization_id = ?
                   OR EXISTS (SELECT 1 FROM service_definition_versions v WHERE v.definition_id = d.id AND v.status <> 'DRAFT')
                ORDER BY d.name""", UUID.class, org);
        return ids.stream().map(this::definitionView).toList();
    }

    public Definition definition(UUID id) {
        return definitionView(id);
    }

    @Transactional
    public Definition createDefinition(String key, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_definitions (id, key, name, steward_organization_id) VALUES (?, ?, ?, ?)",
                id, key, name, org());
        ObjectNode content = mapper.createObjectNode();
        content.put("format", ContractDocumentValidator.DEFINITION_FORMAT);
        for (String list : List.of("fields", "rules", "parameters", "extensionPoints", "decisionTypes")) {
            content.putArray(list);
        }
        jdbc.update("INSERT INTO service_definition_versions (definition_id, version, content) VALUES (?, 1, ?::jsonb)",
                id, content.toString());
        return definitionView(id);
    }

    @Transactional
    public Definition newDefinitionVersion(UUID id) {
        stewardOf(id);
        Map<String, Object> latest = jdbc.queryForMap("""
                SELECT version, status, content::text AS content, legal_basis::text AS legal_basis
                FROM service_definition_versions WHERE definition_id = ? ORDER BY version DESC LIMIT 1""", id);
        if ("DRAFT".equals(latest.get("status"))) {
            throw conflict("DRAFT_EXISTS: version " + latest.get("version") + " är redan ett utkast");
        }
        jdbc.update("INSERT INTO service_definition_versions (definition_id, version, content, legal_basis) " +
                    "VALUES (?, ?, ?::jsonb, ?::jsonb)",
                id, (Integer) latest.get("version") + 1, latest.get("content"), latest.get("legal_basis"));
        return definitionView(id);
    }

    @Transactional
    public Definition updateDefinitionDraft(UUID id, int version, JsonNode content, JsonNode legalBasis,
                                            LocalDate validFrom, LocalDate validTo) {
        stewardOf(id);
        requireDraft("service_definition_versions", "definition_id", id, version);
        requireValid(ContractDocumentValidator.DEFINITION_FORMAT, content);
        jdbc.update("UPDATE service_definition_versions SET content = ?::jsonb, legal_basis = ?::jsonb, valid_from = ?, " +
                    "valid_to = ? WHERE definition_id = ? AND version = ?",
                content.toString(), legalBasis == null ? "[]" : legalBasis.toString(), date(validFrom), date(validTo),
                id, version);
        return definitionView(id);
    }

    @Transactional
    public Definition publishDefinition(UUID id, int version) {
        stewardOf(id);
        requireDraft("service_definition_versions", "definition_id", id, version);
        Version draft = definitionVersions(id).stream().filter(v -> v.version() == version).findFirst().orElseThrow();
        requireValid(ContractDocumentValidator.DEFINITION_FORMAT, draft.content());
        requireConcepts(draft.content());
        String hash = CanonicalJson.hashOf(definitionVersionDocument(draft));
        jdbc.update("UPDATE service_definition_versions SET status = 'PUBLISHED', content_hash = ?, published_at = NOW(), " +
                    "published_by = ? WHERE definition_id = ? AND version = ?",
                hash, CurrentUser.requireId(), id, version);
        return definitionView(id);
    }

    /** A published definition version as a self-contained package, for moving it to another environment. */
    public Map<String, Object> exportDefinition(UUID id, int version) {
        Definition definition = definitionView(id);
        Version v = definition.versions().stream()
                .filter(x -> x.version() == version && !"DRAFT".equals(x.status()))
                .findFirst().orElseThrow(() -> notFound("Versionen hittades inte"));
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("format", PACKAGE_FORMAT);
        pkg.put("key", definition.key());
        pkg.put("name", definition.name());
        pkg.put("version", v.version());
        pkg.put("document", definitionVersionDocument(v));
        pkg.put("contentHash", v.contentHash());
        return pkg;
    }

    /**
     * Import a package: its hash is checked, and it becomes a published version
     * of a new definition stewarded by the importing organisation, with the
     * same content hash.
     */
    @Transactional
    public Definition importDefinition(JsonNode pkg) {
        if (!PACKAGE_FORMAT.equals(pkg.path("format").asText())) {
            throw new InvalidDocument(List.of("$.format: expected " + PACKAGE_FORMAT));
        }
        JsonNode document = pkg.path("document");
        String hash = CanonicalJson.hashOf(document);
        if (!hash.equals(pkg.path("contentHash").asText())) {
            throw new InvalidDocument(List.of("$.contentHash: does not match the document (" + hash + ")"));
        }
        if (!DEFINITION_VERSION_FORMAT.equals(document.path("format").asText())) {
            throw new InvalidDocument(List.of("$.document.format: expected " + DEFINITION_VERSION_FORMAT));
        }
        JsonNode content = document.path("content");
        requireValid(ContractDocumentValidator.DEFINITION_FORMAT, content);
        requireConcepts(content);

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_definitions (id, key, name, steward_organization_id) VALUES (?, ?, ?, ?)",
                id, pkg.path("key").asText(), pkg.path("name").asText(), org());
        jdbc.update("""
                INSERT INTO service_definition_versions (definition_id, version, status, valid_from, valid_to, legal_basis,
                    content, content_hash, published_at, published_by)
                VALUES (?, 1, 'PUBLISHED', ?, ?, ?::jsonb, ?::jsonb, ?, NOW(), ?)""",
                id, date(text(document, "validFrom")), date(text(document, "validTo")),
                document.path("legalBasis").toString(), content.toString(), hash, CurrentUser.requireId());
        return definitionView(id);
    }

    // ---------- Applications ----------

    public List<Application> applications() {
        UUID org = org();
        List<UUID> ids = jdbc.queryForList("""
                SELECT a.id FROM service_applications a
                WHERE a.owner_organization_id = ?
                   OR EXISTS (SELECT 1 FROM service_application_roles r WHERE r.application_id = a.id AND r.organization_id = ?)
                ORDER BY a.name""", UUID.class, org, org);
        return ids.stream().map(this::applicationView).toList();
    }

    public Application application(UUID id) {
        readableApplication(id);
        return applicationView(id);
    }

    /** Apply the latest published version of a definition; the creating organisation owns it and holds every role. */
    @Transactional
    public Application createApplication(UUID definitionId, String name) {
        definitionView(definitionId);
        UUID definitionVersion = latestPublishedDefinitionVersion(definitionId);
        UUID org = org();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_applications (id, definition_id, name, owner_organization_id) VALUES (?, ?, ?, ?)",
                id, definitionId, name, org);
        jdbc.update("INSERT INTO service_application_roles (application_id, organization_id, responsible, deciding_authority, " +
                    "data_controller, participant) VALUES (?, ?, true, true, true, true)", id, org);
        jdbc.update("INSERT INTO service_application_versions (application_id, definition_version_id, version, content) " +
                    "VALUES (?, ?, 1, ?::jsonb)", id, definitionVersion, defaultApplicationContent(definitionVersion).toString());
        return applicationView(id);
    }

    @Transactional
    public Application setRoles(UUID id, List<Role> roles) {
        ownerOf(id);
        long responsible = roles.stream().filter(Role::responsible).count();
        if (responsible != 1) {
            throw new InvalidDocument(List.of("$.roles: exactly one organisation must be responsible, found " + responsible));
        }
        for (Role role : roles) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM organizations WHERE id = ? AND kind <> 'PLATFORM'",
                    Integer.class, role.organizationId());
            if (exists == null || exists == 0) {
                throw new InvalidDocument(List.of("$.roles: unknown organisation " + role.organizationId()));
            }
            if (role.decidingAuthority() && !role.participant()) {
                throw new InvalidDocument(List.of("$.roles: a deciding authority must be a participant"));
            }
        }
        jdbc.update("DELETE FROM service_application_roles WHERE application_id = ?", id);
        for (Role role : roles) {
            jdbc.update("INSERT INTO service_application_roles (application_id, organization_id, responsible, deciding_authority, " +
                        "data_controller, participant) VALUES (?, ?, ?, ?, ?, ?)",
                    id, role.organizationId(), role.responsible(), role.decidingAuthority(), role.dataController(),
                    role.participant());
        }
        return applicationView(id);
    }

    /** A new draft, copied from the latest version, applying the given (or the latest) published definition version. */
    @Transactional
    public Application newApplicationVersion(UUID id, UUID definitionVersionId) {
        ownerOf(id);
        Map<String, Object> latest = jdbc.queryForMap("""
                SELECT a.definition_id, v.version, v.status, v.content::text AS content
                FROM service_applications a JOIN service_application_versions v ON v.application_id = a.id
                WHERE a.id = ? ORDER BY v.version DESC LIMIT 1""", id);
        if ("DRAFT".equals(latest.get("status"))) {
            throw conflict("DRAFT_EXISTS: version " + latest.get("version") + " är redan ett utkast");
        }
        UUID pinned = definitionVersionId != null ? definitionVersionId
                : latestPublishedDefinitionVersion((UUID) latest.get("definition_id"));
        Integer belongs = jdbc.queryForObject("SELECT COUNT(*) FROM service_definition_versions WHERE id = ? " +
                "AND definition_id = ? AND status = 'PUBLISHED'", Integer.class, pinned, latest.get("definition_id"));
        if (belongs == null || belongs == 0) {
            throw new InvalidDocument(List.of("$.definitionVersionId: not a published version of the definition"));
        }
        jdbc.update("INSERT INTO service_application_versions (application_id, definition_version_id, version, content) " +
                    "VALUES (?, ?, ?, ?::jsonb)", id, pinned, (Integer) latest.get("version") + 1, latest.get("content"));
        return applicationView(id);
    }

    @Transactional
    public Application updateApplicationDraft(UUID id, int version, JsonNode content, LocalDate validFrom, LocalDate validTo) {
        ownerOf(id);
        requireDraft("service_application_versions", "application_id", id, version);
        requireValid(ContractDocumentValidator.APPLICATION_FORMAT, content);
        jdbc.update("UPDATE service_application_versions SET content = ?::jsonb, valid_from = ?, valid_to = ? " +
                    "WHERE application_id = ? AND version = ?",
                content.toString(), date(validFrom), date(validTo), id, version);
        return applicationView(id);
    }

    /**
     * Publish an application version. Checked here: the schema, exactly one
     * responsible organisation, and that the applied definition version is
     * published. The checks against the definition's change policy come with
     * the composition in steg 4.
     */
    @Transactional
    public Application publishApplication(UUID id, int version) {
        ownerOf(id);
        requireDraft("service_application_versions", "application_id", id, version);
        Application application = applicationView(id);
        Version draft = application.versions().stream().filter(v -> v.version() == version).findFirst().orElseThrow();
        requireValid(ContractDocumentValidator.APPLICATION_FORMAT, draft.content());
        if (application.roles().stream().filter(Role::responsible).count() != 1) {
            throw new InvalidDocument(List.of("$.roles: exactly one organisation must be responsible"));
        }
        String definitionHash = jdbc.queryForObject("SELECT content_hash FROM service_definition_versions WHERE id = ? " +
                "AND status = 'PUBLISHED'", String.class, draft.definitionVersionId());
        String hash = CanonicalJson.hashOf(applicationVersionDocument(draft, definitionHash));
        jdbc.update("UPDATE service_application_versions SET status = 'PUBLISHED', content_hash = ?, published_at = NOW(), " +
                    "published_by = ? WHERE application_id = ? AND version = ?",
                hash, CurrentUser.requireId(), id, version);
        return applicationView(id);
    }

    // ---------- Documents that are hashed ----------

    public static Map<String, Object> definitionVersionDocument(Version v) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", DEFINITION_VERSION_FORMAT);
        validity(doc, v);
        doc.put("legalBasis", v.legalBasis());
        doc.put("content", v.content());
        return doc;
    }

    /** Absent rather than null, so the document hashes the same after a round trip through the API. */
    private static void validity(Map<String, Object> doc, Version v) {
        if (v.validFrom() != null) doc.put("validFrom", v.validFrom().toString());
        if (v.validTo() != null) doc.put("validTo", v.validTo().toString());
    }

    public static Map<String, Object> applicationVersionDocument(Version v, String definitionContentHash) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", APPLICATION_VERSION_FORMAT);
        doc.put("definitionContentHash", definitionContentHash);
        validity(doc, v);
        doc.put("content", v.content());
        return doc;
    }

    // ---------- Applied by e-service versions ----------

    /** The binding of a published application version the active organisation owns or has a role in; 404, or 409 if not published. */
    public Binding binding(UUID applicationVersionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id AS application_id, av.version AS application_version, av.status AS application_status,
                       av.content::text AS application_content, av.content_hash AS application_hash,
                       av.valid_from AS application_from, av.valid_to AS application_to,
                       d.id AS definition_id, d.key AS definition_key, dv.id AS definition_version_id,
                       dv.version AS definition_version, dv.content::text AS definition_content,
                       dv.content_hash AS definition_hash, dv.valid_from AS definition_from, dv.valid_to AS definition_to
                FROM service_application_versions av
                JOIN service_applications a ON a.id = av.application_id
                JOIN service_definition_versions dv ON dv.id = av.definition_version_id
                JOIN service_definitions d ON d.id = dv.definition_id
                WHERE av.id = ?""", applicationVersionId);
        if (rows.isEmpty()) throw notFound("Tillämpningsversionen hittades inte");
        Map<String, Object> row = rows.get(0);
        readableApplication((UUID) row.get("application_id"));
        if (!"PUBLISHED".equals(row.get("application_status"))) {
            throw conflict("APPLICATION_NOT_PUBLISHED: tillämpningsversionen är " + row.get("application_status"));
        }

        Set<String> keys = new TreeSet<>();
        json((String) row.get("definition_content")).path("fields").forEach(f -> keys.add(f.path("key").asText()));
        json((String) row.get("application_content")).path("extensions").forEach(point -> point.forEach(item -> {
            if ("field".equals(item.path("kind").asText())) keys.add(item.path("key").asText());
        }));
        return new Binding((UUID) row.get("application_id"), applicationVersionId, (Integer) row.get("application_version"),
                (String) row.get("application_hash"), (UUID) row.get("definition_id"), (String) row.get("definition_key"),
                (UUID) row.get("definition_version_id"), (Integer) row.get("definition_version"),
                (String) row.get("definition_hash"),
                later(local(row.get("definition_from")), local(row.get("application_from"))),
                earlier(local(row.get("definition_to")), local(row.get("application_to"))),
                Collections.unmodifiableSet(keys));
    }

    private static LocalDate local(Object value) {
        return value == null ? null : ((Date) value).toLocalDate();
    }

    /** Open (null) is the widest; validity is where both hold. */
    private static LocalDate later(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isBefore(b) ? a : b;
    }

    // ---------- Access ----------

    private static UUID org() {
        return CurrentUser.requireOrganizationId();
    }

    /** The definition as the active organisation may see it, or 404. */
    private Definition definitionView(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT key, name, steward_organization_id FROM service_definitions WHERE id = ?", id);
        if (rows.isEmpty()) throw notFound("Definitionen hittades inte");
        Map<String, Object> row = rows.get(0);
        boolean steward = org().equals(row.get("steward_organization_id"));
        List<Version> versions = definitionVersions(id).stream()
                .filter(v -> steward || !"DRAFT".equals(v.status()))
                .toList();
        if (versions.isEmpty() && !steward) throw notFound("Definitionen hittades inte");
        return new Definition(id, (String) row.get("key"), (String) row.get("name"),
                (UUID) row.get("steward_organization_id"), versions);
    }

    private void stewardOf(UUID definitionId) {
        Definition definition = definitionView(definitionId);
        if (!org().equals(definition.stewardOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Definitionen förvaltas av en annan organisation");
        }
    }

    private void readableApplication(UUID id) {
        Integer visible = jdbc.queryForObject("""
                SELECT COUNT(*) FROM service_applications a WHERE a.id = ? AND (a.owner_organization_id = ?
                    OR EXISTS (SELECT 1 FROM service_application_roles r WHERE r.application_id = a.id AND r.organization_id = ?))""",
                Integer.class, id, org(), org());
        if (visible == null || visible == 0) throw notFound("Tillämpningen hittades inte");
    }

    private void ownerOf(UUID id) {
        readableApplication(id);
        Integer owner = jdbc.queryForObject("SELECT COUNT(*) FROM service_applications WHERE id = ? AND owner_organization_id = ?",
                Integer.class, id, org());
        if (owner == null || owner == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tillämpningen ägs av en annan organisation");
        }
    }

    private Application applicationView(UUID id) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT definition_id, name, owner_organization_id FROM service_applications WHERE id = ?", id);
        List<Role> roles = jdbc.query("""
                SELECT organization_id, responsible, deciding_authority, data_controller, participant
                FROM service_application_roles WHERE application_id = ? ORDER BY organization_id""",
                (rs, i) -> new Role(rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
                        rs.getBoolean(5)), id);
        List<Version> versions = jdbc.query("""
                SELECT id, version, status, valid_from, valid_to, NULL AS legal_basis, content::text AS content, content_hash,
                       definition_version_id
                FROM service_application_versions WHERE application_id = ? ORDER BY version""", this::version, id);
        return new Application(id, (UUID) row.get("definition_id"), (String) row.get("name"),
                (UUID) row.get("owner_organization_id"), roles, versions);
    }

    private List<Version> definitionVersions(UUID definitionId) {
        return jdbc.query("""
                SELECT id, version, status, valid_from, valid_to, legal_basis::text AS legal_basis, content::text AS content,
                       content_hash, NULL::uuid AS definition_version_id
                FROM service_definition_versions WHERE definition_id = ? ORDER BY version""", this::version, definitionId);
    }

    private Version version(ResultSet rs, int i) throws SQLException {
        Date from = rs.getDate("valid_from");
        Date to = rs.getDate("valid_to");
        String legal = rs.getString("legal_basis");
        return new Version(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("status"),
                from == null ? null : from.toLocalDate(), to == null ? null : to.toLocalDate(),
                legal == null ? null : json(legal), json(rs.getString("content")), rs.getString("content_hash"),
                rs.getObject("definition_version_id", UUID.class));
    }

    private UUID latestPublishedDefinitionVersion(UUID definitionId) {
        List<UUID> published = jdbc.queryForList("SELECT id FROM service_definition_versions WHERE definition_id = ? " +
                "AND status = 'PUBLISHED' ORDER BY version DESC LIMIT 1", UUID.class, definitionId);
        if (published.isEmpty()) {
            throw conflict("NO_PUBLISHED_DEFINITION: definitionen har ingen publicerad version");
        }
        return published.get(0);
    }

    /** A new application starts with the definition's parameter defaults and no local content. */
    private ObjectNode defaultApplicationContent(UUID definitionVersionId) {
        JsonNode definition = json(jdbc.queryForObject(
                "SELECT content::text FROM service_definition_versions WHERE id = ?", String.class, definitionVersionId));
        ObjectNode content = mapper.createObjectNode();
        content.put("format", ContractDocumentValidator.APPLICATION_FORMAT);
        ObjectNode parameters = content.putObject("parameters");
        for (JsonNode parameter : definition.path("parameters")) {
            if (parameter.has("default")) parameters.set(parameter.get("key").asText(), parameter.get("default"));
        }
        content.putObject("extensions");
        return content;
    }

    private void requireDraft(String table, String column, UUID id, int version) {
        List<String> status = jdbc.queryForList("SELECT status FROM " + table + " WHERE " + column + " = ? AND version = ?",
                String.class, id, version);
        if (status.isEmpty()) throw notFound("Versionen hittades inte");
        if (!"DRAFT".equals(status.get(0))) {
            throw conflict("NOT_EDITABLE: version " + version + " är " + status.get(0) + " och kan inte ändras");
        }
    }

    private void requireValid(String format, JsonNode document) {
        List<String> errors = validator.validate(format, document);
        if (!errors.isEmpty()) throw new InvalidDocument(errors);
    }

    /** Every concept a definition's fields refer to must exist in that version. */
    private void requireConcepts(JsonNode content) {
        List<String> missing = new ArrayList<>();
        for (JsonNode field : content.path("fields")) {
            String[] ref = field.path("concept").asText().split("@");
            Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM concepts WHERE key = ? AND version = ?",
                    Integer.class, ref[0], Integer.parseInt(ref[1]));
            if (found == null || found == 0) missing.add("$.fields." + field.path("key").asText() + ": unknown concept "
                    + field.path("concept").asText());
        }
        if (!missing.isEmpty()) throw new InvalidDocument(missing);
    }

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static Date date(String value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
