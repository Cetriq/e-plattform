package se.eplatform.flow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import se.eplatform.flow.contract.ContractCanonicalizer;
import se.eplatform.flow.contract.ManifestBackfill;
import se.eplatform.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publishing stores the contract, the projection and the manifest binding
 * them; every case is bound to the manifest of its version and can be
 * verified against it.
 */
class ManifestTest extends IntegrationTest {

    @Autowired
    ManifestBackfill backfill;

    private String publishedCopy(String admin) throws Exception {
        String copy = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + copy + "/publish"))).andExpect(status().isOk());
        return copy;
    }

    private String createCase(String token, String flowId) throws Exception {
        return json.readTree(mvc.perform(as(token, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("flowId", flowId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode contract(String token, String caseId) throws Exception {
        return json.readTree(mvc.perform(as(token, get("/api/v1/cases/" + caseId + "/contract")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void publishingStoresTheDocumentsAndTheirHashesHold() throws Exception {
        String flow = publishedCopy(loginAs(ADMIN));
        Map<String, Object> m = jdbc.queryForMap("""
                SELECT m.hash, m.canonical AS manifest, c.hash AS ch, c.canonical AS contract, p.hash AS ph, p.canonical AS projection
                FROM flows f JOIN publication_manifests m ON m.hash = f.manifest_hash
                JOIN contract_snapshots c ON c.hash = m.contract_hash JOIN projection_snapshots p ON p.hash = m.projection_hash
                WHERE f.id = ?""", UUID.fromString(flow));
        assertThat(ContractCanonicalizer.hashOf((String) m.get("manifest"))).isEqualTo(m.get("hash"));
        assertThat(ContractCanonicalizer.hashOf((String) m.get("contract"))).isEqualTo(m.get("ch"));
        assertThat(ContractCanonicalizer.hashOf((String) m.get("projection"))).isEqualTo(m.get("ph"));

        JsonNode manifest = json.readTree((String) m.get("manifest"));
        assertThat(manifest.get("format").asText()).isEqualTo("eplatform.manifest/1");
        assertThat(manifest.get("origin").asText()).isEqualTo("publish");
        assertThat(manifest.get("flowId").asText()).isEqualTo(flow);
        assertThat(manifest.get("contractHash").asText()).isEqualTo(m.get("ch"));
        assertThat(manifest.get("publishedBy").asText()).isNotBlank();
        // The rules and what the citizen sees are separate documents
        assertThat((String) m.get("contract")).doesNotContain("Ansök om bygglov för nybyggnad");
        assertThat((String) m.get("projection")).contains("Sökande").doesNotContain("VALUE_EQUALS");
    }

    @Test
    void theFormatIsLockedByAGoldenFile() throws Exception {
        Map<String, Object> m = jdbc.queryForMap("""
                SELECT c.canonical AS contract, p.canonical AS projection FROM publication_manifests m
                JOIN contract_snapshots c ON c.hash = m.contract_hash JOIN projection_snapshots p ON p.hash = m.projection_hash
                WHERE m.flow_id = ?""", UUID.fromString(BUILDING_PERMIT_FLOW));
        String contract = new String(getClass().getResourceAsStream("/contracts/building-permit.contract.json").readAllBytes(),
                StandardCharsets.UTF_8);
        String projection = new String(getClass().getResourceAsStream("/contracts/building-permit.projection.json").readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(m.get("contract")).isEqualTo(contract);
        assertThat(m.get("projection")).isEqualTo(projection);
        assertThat(ContractCanonicalizer.hashOf(contract))
                .isEqualTo("a792e3266dad195f9a7657f2784298a75cff679bb31b3f5679f5e9c8b4d3da0e");
    }

    @Test
    void aCaseIsBoundToTheManifestOfItsVersionAndVerifies() throws Exception {
        String admin = loginAs(ADMIN);
        String v1 = publishedCopy(admin);
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        String oldCase = createCase(citizen, v1);

        String v2 = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + v1 + "/versions")))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + v2 + "/publish"))).andExpect(status().isOk());
        String newCase = createCase(citizen, v2);

        String m1 = jdbc.queryForObject("SELECT manifest_hash FROM flows WHERE id = ?", String.class, UUID.fromString(v1));
        String m2 = jdbc.queryForObject("SELECT manifest_hash FROM flows WHERE id = ?", String.class, UUID.fromString(v2));
        assertThat(m1).isNotEqualTo(m2);

        JsonNode old = contract(citizen, oldCase);
        assertThat(old.get("manifestHash").asText()).isEqualTo(m1);
        assertThat(old.get("intact").asBoolean()).isTrue();
        assertThat(old.get("backfilled").asBoolean()).isFalse();
        assertThat(old.get("manifest").get("version").asInt()).isEqualTo(1);
        assertThat(contract(citizen, newCase).get("manifestHash").asText()).isEqualTo(m2);
        // Staff of the organisation see it too; others do not
        assertThat(contract(loginAs(MANAGER), oldCase).get("intact").asBoolean()).isTrue();
        mvc.perform(as(loginAs("handlaggare.grannkommun@example.com"), get("/api/v1/cases/" + oldCase + "/contract")))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id IN (?, ?)",
                UUID.fromString(v1), UUID.fromString(v2));
    }

    @Test
    void tamperingWithAStoredDocumentIsDetected() throws Exception {
        String flow = publishedCopy(loginAs(ADMIN));
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        String caseId = createCase(citizen, flow);
        String contractHash = jdbc.queryForObject("SELECT m.contract_hash FROM flows f JOIN publication_manifests m " +
                "ON m.hash = f.manifest_hash WHERE f.id = ?", String.class, UUID.fromString(flow));

        assertThatThrownBy(() -> jdbc.update("UPDATE contract_snapshots SET canonical = canonical WHERE hash = ?", contractHash))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM publication_manifests WHERE flow_id = ?", UUID.fromString(flow)))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE flows SET manifest_hash = NULL WHERE id = ?", UUID.fromString(flow)))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("UPDATE cases SET manifest_hash = NULL WHERE id = ?", UUID.fromString(caseId)))
                .hasMessageContaining("cannot be changed");

        // Someone with owner rights going around the protection
        String original = jdbc.queryForObject("SELECT canonical FROM contract_snapshots WHERE hash = ?", String.class, contractHash);
        jdbc.execute("ALTER TABLE contract_snapshots DISABLE TRIGGER trg_contract_snapshots_append_only");
        try {
            jdbc.update("UPDATE contract_snapshots SET canonical = replace(canonical, '\"required\":true', '\"required\":false') " +
                        "WHERE hash = ?", contractHash);
            mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/contract")))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.documentsIntact").value(false))
                    .andExpect(jsonPath("$.manifestIntact").value(true));
        } finally {
            jdbc.update("UPDATE contract_snapshots SET canonical = ? WHERE hash = ?", original, contractHash);
            jdbc.execute("ALTER TABLE contract_snapshots ENABLE TRIGGER trg_contract_snapshots_append_only");
        }
        mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/contract"))).andExpect(jsonPath("$.intact").value(true));
        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id = ?", UUID.fromString(flow));
    }

    @Test
    void casesFromBeforeManifestsAreBoundAfterwardsAndMarked() throws Exception {
        // The seed e-service got its manifest when the application started
        assertThat(jdbc.queryForObject("SELECT m.canonical FROM flows f JOIN publication_manifests m ON m.hash = f.manifest_hash " +
                "WHERE f.id = ?", String.class, UUID.fromString(BUILDING_PERMIT_FLOW))).contains("\"origin\":\"backfill\"");

        // A case that has no manifest, as cases created before V16
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        String caseId = createCase(citizen);
        jdbc.execute("ALTER TABLE cases DISABLE TRIGGER trg_cases_manifest_fixed");
        try {
            jdbc.update("UPDATE cases SET manifest_hash = NULL WHERE id = ?", UUID.fromString(caseId));
        } finally {
            jdbc.execute("ALTER TABLE cases ENABLE TRIGGER trg_cases_manifest_fixed");
        }
        mvc.perform(as(citizen, get("/api/v1/cases/" + caseId + "/contract"))).andExpect(status().isNotFound());

        backfill.backfill();

        JsonNode bound = contract(citizen, caseId);
        assertThat(bound.get("backfilled").asBoolean()).isTrue();
        assertThat(bound.get("intact").asBoolean()).isTrue();
        assertThat(bound.get("manifest").get("origin").asText()).isEqualTo("backfill");
    }
}
