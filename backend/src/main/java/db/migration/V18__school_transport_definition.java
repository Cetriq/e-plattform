package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import se.eplatform.contract.CanonicalJson;
import se.eplatform.contract.ContractService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Seeds the concept vocabulary, a steward for the platform, the national
 * definition of school transport and Grannkommun's application of it.
 *
 * A Java migration, so the published versions get their content hash from
 * exactly the same canonicalization the application uses.
 */
public class V18__school_transport_definition extends BaseJavaMigration {

    static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000012");
    static final UUID GRANNKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID STEWARD = UUID.fromString("00000000-0000-0000-0000-000000000120");
    static final UUID DEFINITION = UUID.fromString("00000000-0000-0000-000a-000000000001");
    static final UUID DEFINITION_V1 = UUID.fromString("00000000-0000-0000-000a-000000000101");
    static final UUID APPLICATION = UUID.fromString("00000000-0000-0000-000b-000000000001");
    static final UUID APPLICATION_V1 = UUID.fromString("00000000-0000-0000-000b-000000000101");

    private static final ObjectMapper JSON = CanonicalJson.mapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection db = context.getConnection();
        concepts(db);
        steward(db);

        JsonNode legalBasis = JSON.readTree("[\"Skollagen (2010:800) 10 kap. 32 §\", \"Förvaltningslagen (2017:900)\"]");
        JsonNode definition = JSON.readTree(DEFINITION_CONTENT);
        String definitionHash = CanonicalJson.hashOf(ContractService.definitionVersionDocument(new ContractService.Version(
                DEFINITION_V1, 1, "PUBLISHED", null, null, legalBasis, definition, null, null)));
        update(db, "INSERT INTO service_definitions (id, key, name, steward_organization_id) VALUES (?, ?, ?, ?)",
                DEFINITION, "se.skolskjuts", "Skolskjuts", PLATFORM);
        update(db, """
                INSERT INTO service_definition_versions (id, definition_id, version, status, legal_basis, content, content_hash,
                    published_at, published_by)
                VALUES (?, ?, 1, 'PUBLISHED', ?::jsonb, ?::jsonb, ?, NOW(), ?)""",
                DEFINITION_V1, DEFINITION, legalBasis.toString(), definition.toString(), definitionHash, STEWARD);

        JsonNode application = JSON.readTree(APPLICATION_CONTENT);
        String applicationHash = CanonicalJson.hashOf(ContractService.applicationVersionDocument(new ContractService.Version(
                APPLICATION_V1, 1, "PUBLISHED", null, null, null, application, null, DEFINITION_V1), definitionHash));
        update(db, "INSERT INTO service_applications (id, definition_id, name, owner_organization_id) VALUES (?, ?, ?, ?)",
                APPLICATION, DEFINITION, "Skolskjuts i Grannkommun", GRANNKOMMUN);
        update(db, "INSERT INTO service_application_roles (application_id, organization_id, responsible, deciding_authority, " +
                   "data_controller, participant) VALUES (?, ?, true, true, true, true)", APPLICATION, GRANNKOMMUN);
        update(db, """
                INSERT INTO service_application_versions (id, application_id, definition_version_id, version, status, content,
                    content_hash, published_at, published_by)
                VALUES (?, ?, ?, 1, 'PUBLISHED', ?::jsonb, ?, NOW(), ?)""",
                APPLICATION_V1, APPLICATION, DEFINITION_V1, application.toString(), applicationHash,
                UUID.fromString("00000000-0000-0000-0000-000000000110"));
    }

    private static void concepts(Connection db) throws Exception {
        String[][] concepts = {
            {"person.namn", "{\"type\":\"string\",\"maxLength\":200}", "PERSONAL", "En persons för- och efternamn"},
            {"person.personnummer", "{\"type\":\"string\",\"pattern\":\"^[0-9]{8}-[0-9]{4}$\"}", "PERSONAL",
                "Personnummer med sekelsiffror"},
            {"person.skyddad-identitet", "{\"type\":\"boolean\"}", "CONFIDENTIAL",
                "Personen har skyddade personuppgifter (sekretessmarkering eller skyddad folkbokföring)"},
            {"adress.folkbokforing", "{\"type\":\"string\",\"maxLength\":300}", "PERSONAL", "Folkbokföringsadress"},
            {"kontakt.epost", "{\"type\":\"string\",\"format\":\"email\"}", "PERSONAL", "E-postadress"},
            {"kontakt.telefon", "{\"type\":\"string\",\"maxLength\":30}", "PERSONAL", "Telefonnummer"},
            {"skola.namn", "{\"type\":\"string\",\"maxLength\":200}", "PUBLIC", "En skolas namn"},
            {"elev.arskurs", "{\"enum\":[\"F\",\"1-3\",\"4-6\",\"7-9\"]}", "PERSONAL", "Elevens årskurs"},
            {"avstand.km", "{\"type\":\"number\",\"minimum\":0}", "PERSONAL", "Avstånd i kilometer"},
            {"halsa.funktionsnedsattning", "{\"type\":\"string\",\"maxLength\":2000}", "SENSITIVE",
                "Uppgift om funktionsnedsättning (uppgift om hälsa, GDPR art. 9)"},
            {"text.fritext", "{\"type\":\"string\",\"maxLength\":4000}", "PERSONAL", "Fri text som kan innehålla personuppgifter"},
        };
        for (String[] c : concepts) {
            update(db, "INSERT INTO concepts (key, version, schema, data_class, description) VALUES (?, 1, ?::jsonb, ?, ?)",
                    c[0], c[1], c[2], c[3]);
        }
    }

    /** Tora Förvaltare: administers the platform's national definitions. */
    private static void steward(Connection db) throws Exception {
        update(db, "INSERT INTO users (id, email, username, first_name, last_name, display_name, organization_id, active, " +
                   "email_verified) VALUES (?, 'tjanstforvaltare@example.com', 'tjanstforvaltare', 'Tora', 'Förvaltare', " +
                   "'Tora Förvaltare', ?, true, true)", STEWARD, PLATFORM);
        UUID member = UUID.fromString("00000000-0000-0000-0009-000000000120");
        update(db, "INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)", member, STEWARD, PLATFORM);
        update(db, "INSERT INTO organization_member_roles (member_id, role_id) VALUES (?, ?), (?, ?)",
                member, UUID.fromString("00000000-0000-0000-0000-000000000001"),   // ADMIN
                member, UUID.fromString("00000000-0000-0000-0000-000000000004"));  // FLOW_EDITOR
    }

    private static void update(Connection db, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    static final String DEFINITION_CONTENT = """
        {
          "format": "eplatform.definition/1",
          "purpose": "Pröva elevens rätt till kostnadsfri skolskjuts",
          "starts": ["CITIZEN", "STAFF"],
          "fields": [
            {"key": "elev.namn", "concept": "person.namn@1", "required": true, "changePolicy": "immutable"},
            {"key": "elev.personnummer", "concept": "person.personnummer@1", "required": true, "changePolicy": "immutable",
             "purpose": "Identifiera eleven och kontrollera folkbokföringen"},
            {"key": "elev.skola", "concept": "skola.namn@1", "required": true, "changePolicy": "immutable"},
            {"key": "elev.arskurs", "concept": "elev.arskurs@1", "required": true, "changePolicy": "immutable"},
            {"key": "resa.avstand", "concept": "avstand.km@1", "required": true, "changePolicy": "immutable",
             "purpose": "Kortaste gångavstånd mellan hemmet och skolan"},
            {"key": "sarskilda.skal", "concept": "text.fritext@1", "required": false, "changePolicy": "immutable",
             "purpose": "Trafikförhållanden, funktionsnedsättning eller annan särskild omständighet"}
          ],
          "rules": [
            {"key": "ratt.avstand", "kind": "eligibility",
             "description": "Rätt till skolskjuts när avståndet är längre än kommunens avståndsgräns",
             "when": {"field": "resa.avstand", "op": "gt", "parameter": "avstandsgrans"},
             "then": {"eligible": true},
             "changePolicy": "locally_configurable", "parameters": ["avstandsgrans"],
             "legalBasis": ["Skollagen (2010:800) 10 kap. 32 §"]},
            {"key": "ratt.sarskilda-skal", "kind": "eligibility",
             "description": "Särskilda skäl prövas alltid av en handläggare",
             "when": {"field": "sarskilda.skal", "op": "notEmpty"},
             "then": {"manualAssessment": true},
             "changePolicy": "immutable",
             "legalBasis": ["Skollagen (2010:800) 10 kap. 32 §"]}
          ],
          "parameters": [
            {"key": "avstandsgrans", "type": "number", "unit": "km", "min": 2, "max": 6, "default": 3, "required": true,
             "description": "Kortaste gångavstånd för rätt till skolskjuts"}
          ],
          "extensionPoints": [
            {"key": "lokala.uppgifter", "allows": ["field", "visibility-rule", "information"],
             "description": "Uppgifter kommunen behöver för att planera skjutsen, utan påverkan på rätten"}
          ],
          "decisionTypes": [
            {"key": "beslut.skolskjuts", "kind": "HUMAN", "outcomes": ["BEVILJAD", "DELVIS_BEVILJAD", "AVSLAGEN"],
             "legalBasis": ["Skollagen (2010:800) 10 kap. 32 §"], "reconsideration": "beslut.omprovning",
             "changePolicy": "immutable"},
            {"key": "beslut.omprovning", "kind": "HUMAN", "outcomes": ["ANDRAT", "OANDRAT"],
             "legalBasis": ["Förvaltningslagen (2017:900) 38 §"], "changePolicy": "immutable"}
          ],
          "operations": [
            {"key": "start", "actors": ["CITIZEN", "STAFF"]},
            {"key": "submit", "actors": ["CITIZEN", "AGENT"], "requiresConfirmationBy": "CITIZEN"},
            {"key": "decide", "actors": ["STAFF"]}
          ]
        }
        """;

    static final String APPLICATION_CONTENT = """
        {
          "format": "eplatform.application/1",
          "parameters": {"avstandsgrans": 4},
          "extensions": {
            "lokala.uppgifter": [
              {"kind": "field", "key": "local.fardsatt", "concept": "text.fritext@1", "required": false,
               "description": "Önskemål om busskort eller taxi"},
              {"kind": "information", "key": "local.information",
               "text": "Grannkommun beviljar skolskjuts när gångavståndet är längre än 4 km."}
            ]
          },
          "aiPolicy": {"features": ["summary", "completeness"], "maxDataClass": "PERSONAL"}
        }
        """;
}
