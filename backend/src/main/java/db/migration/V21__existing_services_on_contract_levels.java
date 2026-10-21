package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import se.eplatform.contract.CanonicalJson;
import se.eplatform.contract.ContractService;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moves the two existing e-services onto the contract levels.
 *
 * - Exempelkommun's building permit becomes a local definition stewarded by
 *   Exempelkommun itself (exempelkommun.bygglov), with Exempelkommun's
 *   application of it.
 * - Grannkommun's school transport already has its application of the
 *   national definition (V18).
 *
 * Each service gets a new draft version, copied from its latest published
 * version, that applies the application and names the definition field each
 * of its fields collects. The published versions and their manifests are left
 * exactly as they are; an administrator publishes the draft, and it is then
 * that the manifest naming both levels is written. Services that already have
 * a draft are left alone.
 */
public class V21__existing_services_on_contract_levels extends BaseJavaMigration {

    static final UUID EXEMPELKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID EXEMPEL_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000100");
    static final UUID DEFINITION = UUID.fromString("00000000-0000-0000-000a-000000000002");
    static final UUID DEFINITION_V1 = UUID.fromString("00000000-0000-0000-000a-000000000201");
    static final UUID APPLICATION = UUID.fromString("00000000-0000-0000-000b-000000000002");
    static final UUID APPLICATION_V1 = UUID.fromString("00000000-0000-0000-000b-000000000201");
    static final UUID GRANNKOMMUN_APPLICATION_V1 = UUID.fromString("00000000-0000-0000-000b-000000000101");

    static final UUID BUILDING_PERMIT_FAMILY = UUID.fromString("00000000-0000-0000-0003-000000000001");
    static final UUID SCHOOL_TRANSPORT_FAMILY = UUID.fromString("00000000-0000-0000-0003-000000000011");

    private static final ObjectMapper JSON = CanonicalJson.mapper();

    /** Field of the published version (by its id) to the definition field it collects. */
    static final Map<UUID, String> BUILDING_PERMIT_FIELDS = fields(
            "0006-000000000001", "sokande.typ",
            "0006-000000000002", "sokande.fornamn",
            "0006-000000000003", "sokande.efternamn",
            "0006-000000000004", "sokande.organisationsnamn",
            "0006-000000000005", "sokande.organisationsnummer",
            "0006-000000000006", "kontakt.epost",
            "0006-000000000007", "kontakt.telefon",
            "0006-000000000010", "fastighet.beteckning",
            "0006-000000000011", "byggplats.adress",
            "0006-000000000012", "byggplats.postnummer",
            "0006-000000000013", "byggplats.ort",
            "0006-000000000020", "atgard.typ",
            "0006-000000000021", "atgard.byggnadstyp",
            "0006-000000000022", "atgard.beskrivning",
            "0006-000000000023", "atgard.byggnadsarea",
            "0006-000000000024", "atgard.byggstart",
            "0006-000000000030", "handling.situationsplan",
            "0006-000000000031", "handling.planritning",
            "0006-000000000032", "handling.fasadritning",
            "0006-000000000033", "handling.ovriga");

    static final Map<UUID, String> SCHOOL_TRANSPORT_FIELDS = fields(
            "0006-000000001101", "elev.namn",
            "0006-000000001102", "elev.skola",
            "0006-000000001103", "elev.arskurs",
            "0006-000000001104", "resa.avstand",
            "0006-000000001105", "sarskilda.skal");

    @Override
    public void migrate(Context context) throws Exception {
        Connection db = context.getConnection();
        concepts(db);
        buildingPermit(db);
        linkedDraft(db, BUILDING_PERMIT_FAMILY, APPLICATION_V1, BUILDING_PERMIT_FIELDS);
        linkedDraft(db, SCHOOL_TRANSPORT_FAMILY, GRANNKOMMUN_APPLICATION_V1, SCHOOL_TRANSPORT_FIELDS);
    }

    private static void concepts(Connection db) throws Exception {
        String[][] concepts = {
            {"person.fornamn", "{\"type\":\"string\",\"maxLength\":100}", "PERSONAL", "En persons förnamn"},
            {"person.efternamn", "{\"type\":\"string\",\"maxLength\":100}", "PERSONAL", "En persons efternamn"},
            {"organisation.namn", "{\"type\":\"string\",\"maxLength\":200}", "PUBLIC", "En organisations namn"},
            {"organisation.organisationsnummer", "{\"type\":\"string\",\"pattern\":\"^[0-9]{6}-[0-9]{4}$\"}", "PUBLIC",
                "Organisationsnummer"},
            {"fastighet.beteckning", "{\"type\":\"string\",\"maxLength\":100}", "PUBLIC", "Fastighetsbeteckning"},
            {"adress.gatuadress", "{\"type\":\"string\",\"maxLength\":200}", "PERSONAL", "Gatuadress"},
            {"adress.postnummer", "{\"type\":\"string\",\"pattern\":\"^[0-9]{3} ?[0-9]{2}$\"}", "PERSONAL", "Postnummer"},
            {"adress.ort", "{\"type\":\"string\",\"maxLength\":100}", "PERSONAL", "Postort"},
            {"val.alternativ", "{\"type\":\"string\",\"maxLength\":100}", "PERSONAL",
                "Ett val ur tjänstens lista med alternativ"},
            {"yta.kvm", "{\"type\":\"number\",\"minimum\":0}", "PERSONAL", "Yta i kvadratmeter"},
            {"datum.dag", "{\"type\":\"string\",\"format\":\"date\"}", "PERSONAL", "Ett datum"},
            {"handling.fil", "{\"type\":\"string\"}", "PERSONAL", "En bifogad handling"},
        };
        for (String[] c : concepts) {
            update(db, "INSERT INTO concepts (key, version, schema, data_class, description) VALUES (?, 1, ?::jsonb, ?, ?)",
                    c[0], c[1], c[2], c[3]);
        }
    }

    /** Exempelkommun's own definition of the building permit, and its application of it. */
    private static void buildingPermit(Connection db) throws Exception {
        JsonNode legalBasis = JSON.readTree("[\"Plan- och bygglagen (2010:900) 9 kap.\", \"Förvaltningslagen (2017:900)\"]");
        JsonNode definition = JSON.readTree(BUILDING_PERMIT_DEFINITION);
        String definitionHash = CanonicalJson.hashOf(ContractService.definitionVersionDocument(new ContractService.Version(
                DEFINITION_V1, 1, "PUBLISHED", null, null, legalBasis, definition, null, null)));
        update(db, "INSERT INTO service_definitions (id, key, name, steward_organization_id) VALUES (?, ?, ?, ?)",
                DEFINITION, "exempelkommun.bygglov", "Bygglov (Exempelkommun)", EXEMPELKOMMUN);
        update(db, """
                INSERT INTO service_definition_versions (id, definition_id, version, status, legal_basis, content, content_hash,
                    published_at, published_by)
                VALUES (?, ?, 1, 'PUBLISHED', ?::jsonb, ?::jsonb, ?, NOW(), ?)""",
                DEFINITION_V1, DEFINITION, legalBasis.toString(), definition.toString(), definitionHash, EXEMPEL_ADMIN);

        JsonNode application = JSON.readTree("{\"format\": \"eplatform.application/1\", \"parameters\": {}, \"extensions\": {}}");
        String applicationHash = CanonicalJson.hashOf(ContractService.applicationVersionDocument(new ContractService.Version(
                APPLICATION_V1, 1, "PUBLISHED", null, null, null, application, null, DEFINITION_V1), definitionHash));
        update(db, "INSERT INTO service_applications (id, definition_id, name, owner_organization_id) VALUES (?, ?, ?, ?)",
                APPLICATION, DEFINITION, "Bygglov i Exempelkommun", EXEMPELKOMMUN);
        update(db, "INSERT INTO service_application_roles (application_id, organization_id, responsible, deciding_authority, " +
                   "data_controller, participant) VALUES (?, ?, true, true, true, true)", APPLICATION, EXEMPELKOMMUN);
        update(db, """
                INSERT INTO service_application_versions (id, application_id, definition_version_id, version, status, content,
                    content_hash, published_at, published_by)
                VALUES (?, ?, ?, 1, 'PUBLISHED', ?::jsonb, ?, NOW(), ?)""",
                APPLICATION_V1, APPLICATION, DEFINITION_V1, application.toString(), applicationHash, EXEMPEL_ADMIN);
    }

    /**
     * A new draft version of the family, copied from its latest published
     * version (as FlowCopier copies it), applying the application version.
     */
    private static void linkedDraft(Connection db, UUID family, UUID applicationVersion, Map<UUID, String> fieldKeys)
            throws Exception {
        if (exists(db, "SELECT 1 FROM flows WHERE family_id = ? AND status = 'DRAFT'", family)) return;
        UUID source = single(db, "SELECT id FROM flows WHERE family_id = ? AND status = 'PUBLISHED' " +
                                 "ORDER BY version DESC LIMIT 1", family);
        if (source == null) return;
        int next = count(db, "SELECT MAX(version) FROM flows WHERE family_id = ?", family) + 1;

        UUID draft = UUID.randomUUID();
        copy(db, "flows", source, draft, overrides(
                "version", next, "status", "DRAFT", "enabled", false, "publish_date", null, "unpublish_date", null,
                "manifest_hash", null, "application_version_id", applicationVersion, "created_at", now(),
                "updated_at", now()));

        Map<UUID, UUID> statuses = new HashMap<>();
        for (UUID status : ids(db, "SELECT id FROM status_definitions WHERE flow_id = ? ORDER BY sort_order, id", source)) {
            UUID statusCopy = UUID.randomUUID();
            copy(db, "status_definitions", status, statusCopy, overrides("flow_id", draft, "created_at", now()));
            statuses.put(status, statusCopy);
        }
        Map<UUID, UUID> queries = new HashMap<>();
        for (UUID step : ids(db, "SELECT id FROM steps WHERE flow_id = ? ORDER BY sort_order, id", source)) {
            UUID stepCopy = UUID.randomUUID();
            copy(db, "steps", step, stepCopy, overrides("flow_id", draft, "created_at", now()));
            for (UUID query : ids(db, "SELECT id FROM query_definitions WHERE step_id = ? ORDER BY sort_order, id", step)) {
                UUID queryCopy = UUID.randomUUID();
                copy(db, "query_definitions", query, queryCopy,
                        overrides("step_id", stepCopy, "field_key", fieldKeys.get(query), "created_at", now()));
                queries.put(query, queryCopy);
            }
        }
        for (Map.Entry<UUID, UUID> query : queries.entrySet()) {
            for (UUID evaluator : ids(db, "SELECT id FROM evaluator_definitions WHERE query_id = ?", query.getKey())) {
                List<UUID> targets = new ArrayList<>();
                try (PreparedStatement s = db.prepareStatement("SELECT target_query_ids FROM evaluator_definitions WHERE id = ?")) {
                    s.setObject(1, evaluator);
                    try (ResultSet rs = s.executeQuery()) {
                        rs.next();
                        Array array = rs.getArray(1);
                        if (array != null) {
                            for (Object target : (Object[]) array.getArray()) {
                                targets.add(queries.getOrDefault((UUID) target, (UUID) target));
                            }
                        }
                    }
                }
                copy(db, "evaluator_definitions", evaluator, UUID.randomUUID(), overrides(
                        "query_id", query.getValue(), "target_query_ids", db.createArrayOf("uuid", targets.toArray()),
                        "created_at", now()));
            }
        }
        try (PreparedStatement s = db.prepareStatement("SELECT from_status_id, to_status_id, requires_comment, " +
                "requires_signature FROM status_transitions WHERE from_status_id = ANY (?)")) {
            s.setArray(1, db.createArrayOf("uuid", statuses.keySet().toArray()));
            List<Object[]> transitions = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    UUID to = statuses.get(rs.getObject(2, UUID.class));
                    if (to != null) {
                        transitions.add(new Object[] {statuses.get(rs.getObject(1, UUID.class)), to,
                                rs.getBoolean(3), rs.getBoolean(4)});
                    }
                }
            }
            for (Object[] t : transitions) {
                update(db, "INSERT INTO status_transitions (from_status_id, to_status_id, requires_comment, " +
                           "requires_signature) VALUES (?, ?, ?, ?)", t);
            }
        }
    }

    /** Copy a row with a new id, every column taken over except those overridden. */
    private static void copy(Connection db, String table, UUID from, UUID to, Map<String, Object> overrides) throws Exception {
        List<String> columns = new ArrayList<>();
        Map<String, String> types = new HashMap<>();
        try (PreparedStatement s = db.prepareStatement("SELECT attname, format_type(atttypid, atttypmod) FROM pg_attribute " +
                "WHERE attrelid = ?::regclass AND attnum > 0 AND NOT attisdropped AND attname <> 'id' ORDER BY attnum")) {
            s.setString(1, table);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                    types.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        List<Object> args = new ArrayList<>();
        args.add(to);
        StringBuilder select = new StringBuilder("?");
        for (String column : columns) {
            if (overrides.containsKey(column)) {
                // Typed, so that a null is not taken for text
                select.append(", CAST(? AS ").append(types.get(column)).append(")");
                args.add(overrides.get(column));
            } else {
                select.append(", ").append(column);
            }
        }
        args.add(from);
        update(db, "INSERT INTO " + table + " (id, " + String.join(", ", columns) + ") SELECT " + select
                + " FROM " + table + " WHERE id = ?", args.toArray());
    }

    private static Map<String, Object> overrides(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static java.sql.Timestamp now() {
        return new java.sql.Timestamp(System.currentTimeMillis());
    }

    private static Map<UUID, String> fields(String... pairs) {
        Map<UUID, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(UUID.fromString("00000000-0000-0000-" + pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static List<UUID> ids(Connection db, String sql, UUID arg) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement s = db.prepareStatement(sql)) {
            s.setObject(1, arg);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) ids.add(rs.getObject(1, UUID.class));
            }
        }
        return ids;
    }

    private static UUID single(Connection db, String sql, UUID arg) throws Exception {
        List<UUID> ids = ids(db, sql, arg);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static boolean exists(Connection db, String sql, UUID arg) throws Exception {
        try (PreparedStatement s = db.prepareStatement(sql)) {
            s.setObject(1, arg);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int count(Connection db, String sql, UUID arg) throws Exception {
        try (PreparedStatement s = db.prepareStatement(sql)) {
            s.setObject(1, arg);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void update(Connection db, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    static final String BUILDING_PERMIT_DEFINITION = """
        {
          "format": "eplatform.definition/1",
          "purpose": "Pröva ansökan om bygglov, rivningslov eller marklov",
          "starts": ["CITIZEN", "STAFF"],
          "fields": [
            {"key": "sokande.typ", "concept": "val.alternativ@1", "required": true, "changePolicy": "immutable",
             "purpose": "Privatperson eller företag/organisation"},
            {"key": "sokande.fornamn", "concept": "person.fornamn@1", "required": true, "changePolicy": "immutable"},
            {"key": "sokande.efternamn", "concept": "person.efternamn@1", "required": true, "changePolicy": "immutable"},
            {"key": "sokande.organisationsnamn", "concept": "organisation.namn@1", "required": false,
             "changePolicy": "immutable"},
            {"key": "sokande.organisationsnummer", "concept": "organisation.organisationsnummer@1", "required": false,
             "changePolicy": "immutable"},
            {"key": "kontakt.epost", "concept": "kontakt.epost@1", "required": true, "changePolicy": "immutable"},
            {"key": "kontakt.telefon", "concept": "kontakt.telefon@1", "required": true, "changePolicy": "immutable"},
            {"key": "fastighet.beteckning", "concept": "fastighet.beteckning@1", "required": true,
             "changePolicy": "immutable"},
            {"key": "byggplats.adress", "concept": "adress.gatuadress@1", "required": true, "changePolicy": "immutable"},
            {"key": "byggplats.postnummer", "concept": "adress.postnummer@1", "required": true, "changePolicy": "immutable"},
            {"key": "byggplats.ort", "concept": "adress.ort@1", "required": true, "changePolicy": "immutable"},
            {"key": "atgard.typ", "concept": "val.alternativ@1", "required": true, "changePolicy": "immutable"},
            {"key": "atgard.byggnadstyp", "concept": "val.alternativ@1", "required": true, "changePolicy": "immutable"},
            {"key": "atgard.beskrivning", "concept": "text.fritext@1", "required": true, "changePolicy": "immutable"},
            {"key": "atgard.byggnadsarea", "concept": "yta.kvm@1", "required": true, "changePolicy": "immutable"},
            {"key": "atgard.byggstart", "concept": "datum.dag@1", "required": true, "changePolicy": "immutable"},
            {"key": "handling.situationsplan", "concept": "handling.fil@1", "required": true, "changePolicy": "immutable"},
            {"key": "handling.planritning", "concept": "handling.fil@1", "required": true, "changePolicy": "immutable"},
            {"key": "handling.fasadritning", "concept": "handling.fil@1", "required": true, "changePolicy": "immutable"},
            {"key": "handling.ovriga", "concept": "handling.fil@1", "required": false, "changePolicy": "immutable"}
          ],
          "rules": [
            {"key": "uppgifter.organisation", "kind": "completeness",
             "description": "Företag och organisationer anger namn och organisationsnummer",
             "when": {"field": "sokande.typ", "op": "eq", "value": "company"},
             "then": {"require": ["sokande.organisationsnamn", "sokande.organisationsnummer"]},
             "changePolicy": "immutable"}
          ],
          "parameters": [],
          "extensionPoints": [],
          "decisionTypes": [
            {"key": "beslut.bygglov", "kind": "HUMAN", "outcomes": ["BEVILJAT", "AVSLAGET"],
             "legalBasis": ["Plan- och bygglagen (2010:900) 9 kap. 30 §"], "changePolicy": "immutable"}
          ],
          "operations": [
            {"key": "start", "actors": ["CITIZEN", "STAFF"]},
            {"key": "submit", "actors": ["CITIZEN"], "requiresConfirmationBy": "CITIZEN"},
            {"key": "decide", "actors": ["STAFF"]}
          ]
        }
        """;
}
