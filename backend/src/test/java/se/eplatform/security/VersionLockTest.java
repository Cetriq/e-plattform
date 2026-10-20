package se.eplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import se.eplatform.support.IntegrationTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A published version of an e-service never changes, through the API or in
 * the database. Changes go into a new version; publishing it supersedes the
 * old one, whose cases continue on exactly what they were created on.
 */
class VersionLockTest extends IntegrationTest {

    /** A published e-service of its own, so the seed e-service other tests use is left alone. */
    private String publishedCopy(String admin) throws Exception {
        String copy = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + copy + "/publish"))).andExpect(status().isOk());
        return copy;
    }

    private TenantIsolationTest.AdminIds idsOf(String flowId) {
        Map<String, Object> evaluator = jdbc.queryForMap("""
                SELECT e.id, e.query_id, q.step_id FROM evaluator_definitions e
                JOIN query_definitions q ON q.id = e.query_id JOIN steps s ON s.id = q.step_id
                WHERE s.flow_id = ? LIMIT 1""", UUID.fromString(flowId));
        return new TenantIsolationTest.AdminIds(flowId, evaluator.get("step_id").toString(),
                evaluator.get("query_id").toString(), evaluator.get("id").toString(),
                "00000000-0000-0000-0002-000000000001", UUID.randomUUID().toString());
    }

    private String fingerprint(String flowId) {
        return jdbc.queryForObject("""
                SELECT f.name || f.version || f.status
                    || (SELECT string_agg(s.name || s.sort_order, ',' ORDER BY s.id) FROM steps s WHERE s.flow_id = f.id)
                    || (SELECT string_agg(q.name || q.required || q.sort_order, ',' ORDER BY q.id)
                        FROM query_definitions q JOIN steps s ON s.id = q.step_id WHERE s.flow_id = f.id)
                    || (SELECT count(*) FROM evaluator_definitions e JOIN query_definitions q ON q.id = e.query_id
                        JOIN steps s ON s.id = q.step_id WHERE s.flow_id = f.id)
                FROM flows f WHERE f.id = ?""", String.class, UUID.fromString(flowId));
    }

    @Test
    void everyChangeToAPublishedVersionIsRefused() throws Exception {
        String admin = loginAs(ADMIN);
        String flow = publishedCopy(admin);
        String before = fingerprint(flow);
        var ids = idsOf(flow);

        List<String> changes = TenantIsolationTest.ADMIN_ENDPOINTS.keySet().stream()
                .filter(key -> !key.startsWith("GET"))
                .filter(key -> key.contains("/admin/flows/"))
                .filter(key -> !key.endsWith("/publish") && !key.endsWith("/archive")
                        && !key.endsWith("/duplicate") && !key.endsWith("/versions")
                        && !key.equals("DELETE /api/v1/admin/flows/{id}"))
                .toList();
        assertThat(changes).hasSizeGreaterThanOrEqualTo(12);
        for (String change : changes) {
            var response = mvc.perform(as(admin, TenantIsolationTest.ADMIN_ENDPOINTS.get(change).apply(ids)))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(change).isEqualTo(409);
            assertThat(response.getContentAsString()).as(change).contains("FLOW_NOT_EDITABLE");
        }
        mvc.perform(as(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/admin/flows/" + flow))).andExpect(status().isConflict());
        assertThat(fingerprint(flow)).isEqualTo(before);
    }

    @Test
    void theDatabaseRefusesChangesToAPublishedVersionToo() throws Exception {
        String flow = publishedCopy(loginAs(ADMIN));
        UUID id = UUID.fromString(flow);
        var ids = idsOf(flow);
        assertThatThrownBy(() -> jdbc.update("UPDATE flows SET name = 'Ändrad' WHERE id = ?", id))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("UPDATE flows SET status = 'DRAFT' WHERE id = ?", id))
                .hasMessageContaining("cannot");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM flows WHERE id = ?", id))
                .hasMessageContaining("cannot be deleted");
        assertThatThrownBy(() -> jdbc.update("UPDATE query_definitions SET required = NOT required WHERE id = ?",
                UUID.fromString(ids.query()))).hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO steps (flow_id, name, sort_order) VALUES (?, 'Nytt', 99)", id))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM evaluator_definitions WHERE id = ?", UUID.fromString(ids.evaluator())))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("UPDATE status_definitions SET name = 'X' WHERE flow_id = ?", id))
                .hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM status_transitions WHERE from_status_id IN " +
                "(SELECT id FROM status_definitions WHERE flow_id = ?)", id)).hasMessageContaining("cannot be changed");
        // Allowed: it can be superseded or archived
        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id = ?", id);
    }

    @Test
    void aNewVersionIsAFullCopyWhoseReferencesPointAtItsOwnParts() throws Exception {
        String admin = loginAs(ADMIN);
        String v1 = publishedCopy(admin);
        JsonNode v2 = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + v1 + "/versions")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String v2id = v2.get("id").asText();
        assertThat(v2.get("version").asInt()).isEqualTo(2);
        assertThat(v2.get("status").asText()).isEqualTo("DRAFT");
        assertThat(v2.get("familyId").asText()).isEqualTo(
                jdbc.queryForObject("SELECT family_id::text FROM flows WHERE id = ?", String.class, UUID.fromString(v1)));

        String counts = """
                SELECT (SELECT count(*) FROM steps WHERE flow_id = f.id) || '/' ||
                       (SELECT count(*) FROM query_definitions q JOIN steps s ON s.id = q.step_id WHERE s.flow_id = f.id) || '/' ||
                       (SELECT count(*) FROM evaluator_definitions e JOIN query_definitions q ON q.id = e.query_id
                        JOIN steps s ON s.id = q.step_id WHERE s.flow_id = f.id) || '/' ||
                       (SELECT count(*) FROM status_definitions WHERE flow_id = f.id) || '/' ||
                       (SELECT count(*) FROM status_transitions t JOIN status_definitions d ON d.id = t.from_status_id
                        WHERE d.flow_id = f.id)
                FROM flows f WHERE f.id = ?""";
        String original = jdbc.queryForObject(counts, String.class, UUID.fromString(v1));
        assertThat(jdbc.queryForObject(counts, String.class, UUID.fromString(v2id))).isEqualTo(original);
        assertThat(original).doesNotContain("/0/").doesNotEndWith("/0");

        // Every condition of the copy targets fields of the copy, never of the original
        Integer foreignTargets = jdbc.queryForObject("""
                SELECT count(*) FROM evaluator_definitions e
                JOIN query_definitions q ON q.id = e.query_id JOIN steps s ON s.id = q.step_id
                CROSS JOIN LATERAL unnest(e.target_query_ids) AS target
                WHERE s.flow_id = ? AND target NOT IN
                    (SELECT q2.id FROM query_definitions q2 JOIN steps s2 ON s2.id = q2.step_id WHERE s2.flow_id = ?)""",
                Integer.class, UUID.fromString(v2id), UUID.fromString(v2id));
        assertThat(foreignTargets).isZero();

        // Only one draft at a time
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + v1 + "/versions")))
                .andExpect(status().isConflict());
    }

    @Test
    void publishingANewVersionSupersedesTheOldOneWhoseCasesContinue() throws Exception {
        String admin = loginAs(ADMIN);
        String v1 = publishedCopy(admin);
        var login = newDemoCitizen(Map.of());
        String citizen = login.get("token").asText();
        String oldCase = json.readTree(mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("flowId", v1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        String v2 = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + v1 + "/versions")))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(as(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/flows/" + v2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ansökan om bygglov, version 2\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(admin, post("/api/v1/admin/flows/" + v2 + "/publish"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM flows WHERE id = ?", String.class, UUID.fromString(v1)))
                .isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT flow_id FROM cases WHERE id = ?", UUID.class, UUID.fromString(oldCase)))
                .isEqualTo(UUID.fromString(v1));

        // The old case continues on the old version, which its owner can still read
        mvc.perform(as(citizen, get("/api/v1/cases/" + oldCase))).andExpect(status().isOk());
        mvc.perform(as(citizen, get("/api/v1/flows/" + v1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.not("Ansökan om bygglov, version 2")));
        // Nobody else sees it, and it takes no new cases; new cases go to version 2
        mvc.perform(get("/api/v1/flows/" + v1)).andExpect(status().isNotFound());
        mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("flowId", v1))))
                .andExpect(status().is4xxClientError());
        mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("flowId", v2))))
                .andExpect(status().isCreated());

        jdbc.update("UPDATE flows SET status = 'ARCHIVED', enabled = false WHERE id IN (?, ?)",
                UUID.fromString(v1), UUID.fromString(v2));
    }

    @Test
    void draftsAreNotPublic() throws Exception {
        String admin = loginAs(ADMIN);
        String draft = json.readTree(mvc.perform(as(admin, post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/flows/" + draft)).andExpect(status().isNotFound());
        mvc.perform(as(newDemoCitizen(Map.of()).get("token").asText(), get("/api/v1/flows/" + draft)))
                .andExpect(status().isNotFound());
        mvc.perform(as(loginAs("admin.grannkommun@example.com"), get("/api/v1/flows/" + draft)))
                .andExpect(status().isNotFound());
        mvc.perform(as(admin, get("/api/v1/flows/" + draft))).andExpect(status().isOk());
    }
}
