package se.eplatform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import se.eplatform.flow.contract.ContractCanonicalizer;
import se.eplatform.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
 * Versions of e-services on the contract levels (steg 3b): a version applies
 * a published application version, its fields name the definition fields
 * they collect, and its manifest names both level versions with their hashes.
 */
class ContractLinkTest extends IntegrationTest {

    static final String GRANNE_ADMIN = "admin.grannkommun@example.com";
    static final UUID SCHOOL_TRANSPORT_FAMILY = UUID.fromString("00000000-0000-0000-0003-000000000011");
    static final UUID BUILDING_PERMIT_FAMILY = UUID.fromString("00000000-0000-0000-0003-000000000001");
    static final String GRANNE_APPLICATION_V1 = "00000000-0000-0000-000b-000000000101";
    static final String EXEMPEL_APPLICATION_V1 = "00000000-0000-0000-000b-000000000201";

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private UUID draftOf(UUID family) {
        return jdbc.queryForObject("SELECT id FROM flows WHERE family_id = ? AND status = 'DRAFT'", UUID.class, family);
    }

    private ResultActions link(String token, String flow, String applicationVersion, Map<String, String> keys) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("applicationVersionId", applicationVersion);
        request.put("fieldKeys", keys);
        return mvc.perform(as(token, put("/api/v1/admin/flows/" + flow + "/contract-link"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    /** A copy of Grannkommun's linked school transport draft, in a family of its own. */
    private String linkedCopy(String admin) throws Exception {
        return body(mvc.perform(as(admin, post("/api/v1/admin/flows/" + draftOf(SCHOOL_TRANSPORT_FAMILY) + "/duplicate")))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private List<UUID> fieldsOf(String flow) {
        return jdbc.queryForList("SELECT q.id FROM query_definitions q JOIN steps s ON s.id = q.step_id WHERE s.flow_id = ? " +
                "ORDER BY s.sort_order, q.sort_order", UUID.class, UUID.fromString(flow));
    }

    @Test
    void theExistingServicesHaveLinkedDraftsAndTheirPublishedVersionsAreUntouched() throws Exception {
        for (UUID family : List.of(SCHOOL_TRANSPORT_FAMILY, BUILDING_PERMIT_FAMILY)) {
            Map<String, Object> draft = jdbc.queryForMap("SELECT id, version, application_version_id, manifest_hash FROM flows " +
                    "WHERE family_id = ? AND status = 'DRAFT'", family);
            assertThat(draft.get("version")).isEqualTo(2);
            assertThat(draft.get("application_version_id")).isNotNull();
            assertThat(draft.get("manifest_hash")).isNull();
            // Every field of the draft names its definition field, and none of the published version's does
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_definitions q JOIN steps s ON s.id = q.step_id " +
                    "WHERE s.flow_id = ? AND q.field_key IS NULL", Integer.class, draft.get("id"))).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_definitions q JOIN steps s ON s.id = q.step_id " +
                    "JOIN flows f ON f.id = s.flow_id WHERE f.family_id = ? AND f.status = 'PUBLISHED' AND q.field_key IS NOT NULL",
                    Integer.class, family)).isZero();
        }
        // The building permit's condition was copied pointing at the copies of its fields
        UUID draft = draftOf(BUILDING_PERMIT_FAMILY);
        List<UUID> targets = jdbc.queryForList("""
                SELECT unnest(e.target_query_ids) FROM evaluator_definitions e JOIN query_definitions q ON q.id = e.query_id
                JOIN steps s ON s.id = q.step_id WHERE s.flow_id = ?""", UUID.class, draft);
        assertThat(targets).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_definitions q JOIN steps s ON s.id = q.step_id " +
                "WHERE s.flow_id = ? AND q.id = ANY (CAST(? AS uuid[]))", Integer.class, draft,
                "{" + String.join(",", targets.stream().map(UUID::toString).toList()) + "}")).isEqualTo(2);
        // The building permit's published version keeps its golden contract
        assertThat(jdbc.queryForObject("SELECT contract_hash FROM publication_manifests WHERE flow_id = ?", String.class,
                UUID.fromString(BUILDING_PERMIT_FLOW))).isEqualTo("a792e3266dad195f9a7657f2784298a75cff679bb31b3f5679f5e9c8b4d3da0e");

        JsonNode flow = body(mvc.perform(as(loginAs(ADMIN), get("/api/v1/admin/flows/" + draft))).andExpect(status().isOk()));
        assertThat(flow.get("applicationVersionId").asText()).isEqualTo(EXEMPEL_APPLICATION_V1);
        assertThat(flow.get("steps").get(0).get("queries").get(0).get("fieldKey").asText()).isEqualTo("sokande.typ");
    }

    @Test
    void publishingALinkedVersionWritesTheManifestWithBothLevels() throws Exception {
        String admin = loginAs(GRANNE_ADMIN);
        String flow = linkedCopy(admin);
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + flow + "/publish"))).andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("SELECT m.format, m.canonical, m.definition_version_id, " +
                "m.application_version_id, c.format AS contract_format, c.canonical AS contract FROM publication_manifests m " +
                "JOIN contract_snapshots c ON c.hash = m.contract_hash WHERE m.flow_id = ?", UUID.fromString(flow));
        assertThat(row.get("format")).isEqualTo(ContractCanonicalizer.MANIFEST_FORMAT_LEVELS);
        assertThat(row.get("contract_format")).isEqualTo(ContractCanonicalizer.CONTRACT_FORMAT_LEVELS);
        assertThat(row.get("application_version_id")).isEqualTo(UUID.fromString(GRANNE_APPLICATION_V1));

        JsonNode manifest = json.readTree((String) row.get("canonical"));
        Map<String, Object> levels = jdbc.queryForMap("""
                SELECT dv.id AS definition_version, dv.content_hash AS definition_hash, av.content_hash AS application_hash
                FROM service_application_versions av JOIN service_definition_versions dv ON dv.id = av.definition_version_id
                WHERE av.id = ?""", UUID.fromString(GRANNE_APPLICATION_V1));
        assertThat(manifest.get("definition").get("key").asText()).isEqualTo("se.skolskjuts");
        assertThat(manifest.get("definition").get("contentHash").asText()).isEqualTo(levels.get("definition_hash"));
        assertThat(manifest.get("application").get("contentHash").asText()).isEqualTo(levels.get("application_hash"));
        assertThat(row.get("definition_version_id")).isEqualTo(levels.get("definition_version"));
        assertThat(manifest.get("validFrom").isNull()).isTrue();
        assertThat(json.readTree((String) row.get("contract")).get("fields").get(0).get("fieldKey").asText())
                .isEqualTo("elev.namn");

        // A case on it is bound to that manifest and verifies
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        String caseId = body(mvc.perform(as(citizen, post("/api/v1/cases")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("flowId", flow)))).andExpect(status().isCreated())).get("id").asText();
        JsonNode contract = body(mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/contract"))).andExpect(status().isOk()));
        assertThat(contract.get("intact").asBoolean()).isTrue();
        assertThat(contract.get("manifest").get("application").get("versionId").asText()).isEqualTo(GRANNE_APPLICATION_V1);

        // Published: the link and the keys are locked
        assertThatThrownBy(() -> jdbc.update("UPDATE flows SET application_version_id = NULL WHERE id = ?", UUID.fromString(flow)))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("UPDATE query_definitions SET field_key = 'elev.skola' WHERE id = ?",
                fieldsOf(flow).get(0))).hasMessageContaining("cannot be changed");
        mvc.perform(as(admin, put("/api/v1/admin/flows/" + flow + "/contract-link")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationVersionId\":null}")).andExpect(status().isConflict());

        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id = ?", UUID.fromString(flow));
    }

    @Test
    void keysMustBeFieldsOfTheAppliedApplicationAndNameAFieldOnce() throws Exception {
        String admin = loginAs(GRANNE_ADMIN);
        String flow = linkedCopy(admin);
        List<UUID> fields = fieldsOf(flow);

        body(link(admin, flow, GRANNE_APPLICATION_V1, Map.of(fields.get(0).toString(), "finns.inte"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("is not a field of se.skolskjuts"))));
        link(admin, flow, GRANNE_APPLICATION_V1, Map.of(fields.get(1).toString(), "elev.namn"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("already collected by")));
        link(admin, flow, GRANNE_APPLICATION_V1, Map.of(UUID.randomUUID().toString(), "elev.namn"))
                .andExpect(status().isUnprocessableEntity());

        // A local field of the application can be named, and a blank key removes one
        JsonNode linked = body(link(admin, flow, GRANNE_APPLICATION_V1,
                Map.of(fields.get(4).toString(), "local.fardsatt", fields.get(3).toString(), " "))
                .andExpect(status().isOk()));
        assertThat(jdbc.queryForList("SELECT field_key FROM query_definitions WHERE id IN (?, ?) ORDER BY sort_order",
                String.class, fields.get(3), fields.get(4))).containsExactly(null, "local.fardsatt");
        assertThat(linked.get("applicationVersionId").asText()).isEqualTo(GRANNE_APPLICATION_V1);

        // Another organisation's application cannot be applied
        link(admin, flow, EXEMPEL_APPLICATION_V1, Map.of()).andExpect(status().isNotFound());

        // Without an application no field has a key
        link(admin, flow, null, Map.of()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM query_definitions q JOIN steps s ON s.id = q.step_id " +
                "WHERE s.flow_id = ? AND q.field_key IS NOT NULL", Integer.class, UUID.fromString(flow))).isZero();
        // And it publishes with the documents of format 1
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + flow + "/publish"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT format FROM publication_manifests WHERE flow_id = ?", String.class,
                UUID.fromString(flow))).isEqualTo(ContractCanonicalizer.MANIFEST_FORMAT);
        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id = ?", UUID.fromString(flow));
    }

    @Test
    void aVersionCannotBePublishedOnAnApplicationVersionThatWasRetired() throws Exception {
        String admin = loginAs(GRANNE_ADMIN);
        JsonNode application = body(mvc.perform(as(admin, post("/api/v1/admin/applications"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("definitionId", "00000000-0000-0000-000a-000000000001",
                        "name", "Skolskjuts, prov " + UUID.randomUUID().toString().substring(0, 6)))))
                .andExpect(status().isCreated()));
        String applicationId = application.get("id").asText();
        mvc.perform(as(admin, post("/api/v1/admin/applications/" + applicationId + "/versions/1/publish")))
                .andExpect(status().isOk());
        String version = application.get("versions").get(0).get("id").asText();

        String flow = linkedCopy(admin);
        link(admin, flow, version, Map.of()).andExpect(status().isOk());
        jdbc.update("UPDATE service_application_versions SET status = 'RETIRED' WHERE id = ?", UUID.fromString(version));
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + flow + "/publish")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM flows WHERE id = ?", String.class, UUID.fromString(flow)))
                .isEqualTo("DRAFT");
    }
}
