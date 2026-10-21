package se.eplatform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import se.eplatform.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service definitions (level 1) maintained by a steward, applications
 * (level 2) owned by an organisation, both versioned, validated against their
 * schemas, and locked once published.
 */
class ContractTest extends IntegrationTest {

    static final String SCHOOL_TRANSPORT = "00000000-0000-0000-000a-000000000001";
    static final String GRANNKOMMUN_APPLICATION = "00000000-0000-0000-000b-000000000001";
    static final String STEWARD = "tjanstforvaltare@example.com";

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonBody(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object content) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(content));
    }

    private ObjectNode validDefinition() throws Exception {
        return (ObjectNode) json.readTree("""
                {"format": "eplatform.definition/1",
                 "fields": [{"key": "sokande.namn", "concept": "person.namn@1", "required": true, "changePolicy": "immutable"}],
                 "rules": [], "parameters": [], "extensionPoints": [],
                 "decisionTypes": [{"key": "beslut.ansokan", "kind": "HUMAN", "outcomes": ["BIFALL", "AVSLAG"], "legalBasis": []}]}""");
    }

    @Test
    void theNationalDefinitionAndGrannkommunsApplicationAreSeeded() throws Exception {
        String granne = loginAs("admin.grannkommun@example.com");
        JsonNode concepts = body(mvc.perform(as(granne, get("/api/v1/admin/concepts"))));
        assertThat(concepts.toString()).contains("person.personnummer").contains("SENSITIVE").contains("CONFIDENTIAL");

        // A published national definition is visible to every organisation's administrators
        JsonNode definition = body(mvc.perform(as(loginAs(ADMIN), get("/api/v1/admin/definitions/" + SCHOOL_TRANSPORT))));
        assertThat(definition.get("key").asText()).isEqualTo("se.skolskjuts");
        assertThat(definition.get("stewardOrganizationId").asText()).isEqualTo(PLATFORM);
        assertThat(definition.get("versions").get(0).get("status").asText()).isEqualTo("PUBLISHED");

        // Grannkommun's application, visible to Grannkommun only
        JsonNode application = body(mvc.perform(as(granne, get("/api/v1/admin/applications/" + GRANNKOMMUN_APPLICATION))));
        assertThat(application.get("versions").get(0).get("content").get("parameters").get("avstandsgrans").asInt()).isEqualTo(4);
        assertThat(application.get("roles").get(0).get("responsible").asBoolean()).isTrue();
        mvc.perform(as(loginAs(ADMIN), get("/api/v1/admin/applications/" + GRANNKOMMUN_APPLICATION)))
                .andExpect(status().isNotFound());

        // The stored hashes are the canonical hashes of the stored documents
        Map<String, Object> row = jdbc.queryForMap("SELECT content::text AS content, legal_basis::text AS legal, content_hash " +
                "FROM service_definition_versions WHERE definition_id = ?", UUID.fromString(SCHOOL_TRANSPORT));
        var version = new ContractService.Version(null, 1, "PUBLISHED", null, null, json.readTree((String) row.get("legal")),
                json.readTree((String) row.get("content")), null, null);
        assertThat(CanonicalJson.hashOf(ContractService.definitionVersionDocument(version))).isEqualTo(row.get("content_hash"));
    }

    @Test
    void aStewardMaintainsADefinitionThroughDraftsAndPublishedVersions() throws Exception {
        String steward = loginAs(STEWARD);
        String key = "se.test-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode created = body(mvc.perform(jsonBody(as(steward, post("/api/v1/admin/definitions")),
                Map.of("key", key, "name", "Testtjänst"))).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("versions").get(0).get("status").asText()).isEqualTo("DRAFT");

        // The schema decides what a definition can contain
        ObjectNode invalid = validDefinition();
        ((ObjectNode) invalid.get("fields").get(0)).put("changePolicy", "whatever");
        JsonNode errors = body(mvc.perform(jsonBody(as(steward, put("/api/v1/admin/definitions/" + id + "/versions/1")),
                Map.of("content", invalid))).andExpect(status().isUnprocessableEntity()));
        assertThat(errors.get("errors").toString()).contains("changePolicy");

        // A concept that does not exist stops the publication
        ObjectNode unknownConcept = validDefinition();
        ((ObjectNode) unknownConcept.get("fields").get(0)).put("concept", "finns.inte@1");
        mvc.perform(jsonBody(as(steward, put("/api/v1/admin/definitions/" + id + "/versions/1")),
                Map.of("content", unknownConcept))).andExpect(status().isOk());
        mvc.perform(as(steward, post("/api/v1/admin/definitions/" + id + "/versions/1/publish")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("unknown concept")));

        mvc.perform(jsonBody(as(steward, put("/api/v1/admin/definitions/" + id + "/versions/1")),
                Map.of("content", validDefinition(), "legalBasis", List.of("Förvaltningslagen (2017:900)"),
                        "validFrom", "2026-11-01"))).andExpect(status().isOk());
        JsonNode published = body(mvc.perform(as(steward, post("/api/v1/admin/definitions/" + id + "/versions/1/publish")))
                .andExpect(status().isOk()));
        JsonNode v1 = published.get("versions").get(0);
        assertThat(v1.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(v1.get("contentHash").asText()).hasSize(64);

        // Published: locked in the API and in the database
        mvc.perform(jsonBody(as(steward, put("/api/v1/admin/definitions/" + id + "/versions/1")),
                Map.of("content", validDefinition()))).andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.update("UPDATE service_definition_versions SET content = '{}' WHERE definition_id = ?",
                UUID.fromString(id))).hasMessageContaining("cannot be changed");

        // Changes go into a new draft version, copied from the published one
        JsonNode v2 = body(mvc.perform(as(steward, post("/api/v1/admin/definitions/" + id + "/versions")))
                .andExpect(status().isCreated())).get("versions").get(1);
        assertThat(v2.get("status").asText()).isEqualTo("DRAFT");
        assertThat(v2.get("content")).isEqualTo(v1.get("content"));

        // Only the steward changes it; others see the published version only
        String admin = loginAs(ADMIN);
        JsonNode seen = body(mvc.perform(as(admin, get("/api/v1/admin/definitions/" + id))));
        assertThat(seen.get("versions").size()).isEqualTo(1);
        mvc.perform(jsonBody(as(admin, put("/api/v1/admin/definitions/" + id + "/versions/2")),
                Map.of("content", validDefinition()))).andExpect(status().isForbidden());
    }

    @Test
    void anOrganisationAppliesADefinitionAndSharesItWithOthers() throws Exception {
        String admin = loginAs(ADMIN);
        JsonNode application = body(mvc.perform(jsonBody(as(admin, post("/api/v1/admin/applications")),
                Map.of("definitionId", SCHOOL_TRANSPORT, "name", "Skolskjuts i Exempelkommun"))).andExpect(status().isCreated()));
        String id = application.get("id").asText();
        // Starts from the definition's defaults, with the organisation holding every role
        assertThat(application.get("versions").get(0).get("content").get("parameters").get("avstandsgrans").asInt()).isEqualTo(3);
        assertThat(application.get("roles").size()).isEqualTo(1);

        // Exactly one responsible organisation; a deciding authority must take part
        String exempel = "00000000-0000-0000-0000-000000000010";
        String granne = "00000000-0000-0000-0000-000000000011";
        mvc.perform(jsonBody(as(admin, put("/api/v1/admin/applications/" + id + "/roles")), List.of(
                Map.of("organizationId", exempel, "responsible", true, "decidingAuthority", true, "dataController", true, "participant", true),
                Map.of("organizationId", granne, "responsible", true, "decidingAuthority", false, "dataController", false, "participant", true))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(jsonBody(as(admin, put("/api/v1/admin/applications/" + id + "/roles")), List.of(
                Map.of("organizationId", exempel, "responsible", true, "decidingAuthority", true, "dataController", true, "participant", true),
                Map.of("organizationId", granne, "responsible", false, "decidingAuthority", true, "dataController", false, "participant", true))))
                .andExpect(status().isOk());

        // A participant reads it but does not change it
        String granneAdmin = loginAs("admin.grannkommun@example.com");
        mvc.perform(as(granneAdmin, get("/api/v1/admin/applications/" + id))).andExpect(status().isOk());
        mvc.perform(as(granneAdmin, post("/api/v1/admin/applications/" + id + "/versions/1/publish")))
                .andExpect(status().isForbidden());

        // Content is checked against the schema
        mvc.perform(jsonBody(as(admin, put("/api/v1/admin/applications/" + id + "/versions/1")),
                Map.of("content", Map.of("format", "eplatform.application/1", "parameters", Map.of(),
                        "extensions", Map.of("lokala.uppgifter", List.of(Map.of("kind", "field", "key", "inte-lokal")))))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(jsonBody(as(admin, put("/api/v1/admin/applications/" + id + "/versions/1")),
                Map.of("content", Map.of("format", "eplatform.application/1", "parameters", Map.of("avstandsgrans", 5),
                        "extensions", Map.of()))))
                .andExpect(status().isOk());

        JsonNode published = body(mvc.perform(as(admin, post("/api/v1/admin/applications/" + id + "/versions/1/publish")))
                .andExpect(status().isOk()));
        assertThat(published.get("versions").get(0).get("contentHash").asText()).hasSize(64);
        assertThatThrownBy(() -> jdbc.update("UPDATE service_application_versions SET content = '{}' WHERE application_id = ?",
                UUID.fromString(id))).hasMessageContaining("cannot be changed");
    }

    @Test
    void aDefinitionMovesBetweenEnvironmentsWithTheSameHash() throws Exception {
        String steward = loginAs(STEWARD);
        JsonNode pkg = body(mvc.perform(as(steward, get("/api/v1/admin/definitions/" + SCHOOL_TRANSPORT + "/versions/1/export")))
                .andExpect(status().isOk()));
        String hash = pkg.get("contentHash").asText();

        ((ObjectNode) pkg).put("key", "se.skolskjuts-import-" + UUID.randomUUID().toString().substring(0, 8));
        JsonNode imported = body(mvc.perform(as(loginAs(ADMIN), post("/api/v1/admin/definitions/import"))
                        .contentType(MediaType.APPLICATION_JSON).content(pkg.toString()))
                .andExpect(status().isCreated()));
        assertThat(imported.get("stewardOrganizationId").asText()).isEqualTo("00000000-0000-0000-0000-000000000010");
        assertThat(imported.get("versions").get(0).get("contentHash").asText()).isEqualTo(hash);

        // A changed document no longer matches its hash
        ((ObjectNode) pkg.get("document").get("content")).put("purpose", "Ändrat syfte");
        ((ObjectNode) pkg).put("key", "se.skolskjuts-andrad");
        mvc.perform(as(loginAs(ADMIN), post("/api/v1/admin/definitions/import"))
                        .contentType(MediaType.APPLICATION_JSON).content(pkg.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("contentHash")));
    }

    @Test
    void anApplicationOnlyAppliesAPublishedDefinitionVersion() throws Exception {
        String steward = loginAs(STEWARD);
        JsonNode draftOnly = body(mvc.perform(jsonBody(as(steward, post("/api/v1/admin/definitions")),
                Map.of("key", "se.utkast-" + UUID.randomUUID().toString().substring(0, 8), "name", "Bara utkast"))));
        Map<String, String> early = Map.of("definitionId", draftOnly.get("id").asText(), "name", "För tidigt");
        mvc.perform(jsonBody(as(steward, post("/api/v1/admin/applications")), early)).andExpect(status().isConflict());
        // Others do not see a definition until it is published
        mvc.perform(jsonBody(as(loginAs(ADMIN), post("/api/v1/admin/applications")), early)).andExpect(status().isNotFound());

        UUID draftVersion = jdbc.queryForObject("SELECT id FROM service_definition_versions WHERE definition_id = ?",
                UUID.class, UUID.fromString(draftOnly.get("id").asText()));
        assertThatThrownBy(() -> jdbc.update("UPDATE service_application_versions SET definition_version_id = ? " +
                "WHERE application_id = ?", draftVersion, UUID.fromString(GRANNKOMMUN_APPLICATION)))
                .hasMessageContaining("cannot be changed");
    }
}
