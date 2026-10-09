package se.eplatform.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import se.eplatform.support.IntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traceability, data subject rights, gallring and the security/operations roles.
 */
class ComplianceTest extends IntegrationTest {

    static final String SECURITY = "informationssakerhet@example.com";
    static final String OPERATIONS = "it-drift@example.com";
    static final String SUBMITTED = "00000000-0000-0000-0008-000000000002";

    @Autowired
    JdbcTemplate jdbc;

    String citizen;
    String citizenId;
    String security;
    String operations;
    String caseId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode login = newDemoCitizen(Map.of());
        citizen = login.get("token").asText();
        citizenId = login.get("user").get("id").asText();
        security = loginAs(SECURITY);
        operations = loginAs(OPERATIONS);
        caseId = createCase(citizen);
    }

    private void submit(String id) {
        jdbc.update("UPDATE cases SET submitted_at = NOW(), status_id = ? WHERE id = ?",
                UUID.fromString(SUBMITTED), UUID.fromString(id));
    }

    // ---------- Spårbarhet ----------

    @Test
    void readingACaseIsLoggedWithWhoseDataItWas() throws Exception {
        String other = newDemoCitizen(Map.of()).get("token").asText();
        mvc.perform(as(citizen, get("/api/v1/cases/" + caseId))).andExpect(status().isOk());
        mvc.perform(as(other, get("/api/v1/cases/" + caseId))).andExpect(status().isNotFound());

        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("subjectId", citizenId).param("action", "CASE_VIEW").param("entityId", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                // Newest first: the other citizen's denied attempt, then the owner's read
                .andExpect(jsonPath("$.content[0].outcome").value("DENIED"))
                .andExpect(jsonPath("$.content[0].responseStatus").value(404))
                .andExpect(jsonPath("$.content[1].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.content[1].userId").value(citizenId))
                .andExpect(jsonPath("$.content[1].category").value("DATA_ACCESS"));
    }

    @Test
    void loginsAndCreatedCasesAreLogged() throws Exception {
        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("subjectId", citizenId).param("action", "DEMO_ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("action", "CASE_CREATE").param("entityId", caseId))
                .andExpect(jsonPath("$.content[0].subjectUserId").value(citizenId));
    }

    @Test
    void theLogCannotBeChangedOrDeleted() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET details = 'ändrad'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events"))
                .hasMessageContaining("append-only");
    }

    @Test
    void changesMadeAroundTheProtectionAreDetected() throws Exception {
        mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true));

        Long seq = jdbc.queryForObject("SELECT MAX(seq) FROM audit_events", Long.class);
        String original = jdbc.queryForObject("SELECT COALESCE(details, '') FROM audit_events WHERE seq = ?", String.class, seq);
        // Simulate someone with database access bypassing the trigger
        jdbc.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_append_only");
        try {
            jdbc.update("UPDATE audit_events SET details = 'manipulerad' WHERE seq = ?", seq);
            mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.firstBrokenSeq").value(seq));
        } finally {
            jdbc.update("UPDATE audit_events SET details = NULLIF(?, '') WHERE seq = ?", original, seq);
            jdbc.execute("ALTER TABLE audit_events ENABLE TRIGGER trg_audit_append_only");
        }
        mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void auditLogCanBeExportedAsCsv() throws Exception {
        String csv = mvc.perform(as(security, get("/api/v1/security/audit/export"))
                        .param("subjectId", citizenId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sparbarhetslogg-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿nr;tid;");
        assertThat(csv).contains("DEMO_ACCOUNT_CREATED");

        // The export is itself logged
        mvc.perform(as(security, get("/api/v1/security/audit")).param("action", "AUDIT_EXPORT"))
                .andExpect(jsonPath("$.content[0].details").value(org.hamcrest.Matchers.containsString("CSV")));
    }

    // ---------- Roller ----------

    @Test
    void rolesOnlyReachTheirOwnArea() throws Exception {
        String manager = loginAs(MANAGER);
        String admin = loginAs(ADMIN);
        mvc.perform(as(manager, get("/api/v1/security/audit"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get("/api/v1/security/audit"))).andExpect(status().isForbidden());
        mvc.perform(as(operations, get("/api/v1/security/audit"))).andExpect(status().isForbidden());
        mvc.perform(as(security, get("/api/v1/ops/status"))).andExpect(status().isForbidden());
        // Neither role handles cases or administers e-services
        mvc.perform(as(security, get("/api/v1/cases"))).andExpect(status().isForbidden());
        mvc.perform(as(operations, get("/api/v1/cases"))).andExpect(status().isForbidden());
        mvc.perform(as(operations, get("/api/v1/admin/flows"))).andExpect(status().isForbidden());
        // Admins no longer see the traceability log through statistics
        mvc.perform(as(admin, get("/api/v1/admin/statistics/audit/recent"))).andExpect(status().isNotFound());

        mvc.perform(as(security, get("/api/v1/security/audit")).param("action", "ACCESS_DENIED"))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ---------- Den registrerades rättigheter ----------

    @Test
    void registerExtractContainsTheCaseAndWhoAccessedIt() throws Exception {
        mvc.perform(as(citizen, get("/api/v1/cases/" + caseId))).andExpect(status().isOk());

        String body = mvc.perform(as(security, get("/api/v1/security/people/" + citizenId + "/extract")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode extract = json.readTree(body);
        assertThat(extract.get("person").get("id").asText()).isEqualTo(citizenId);
        assertThat(extract.get("arenden").size()).isEqualTo(1);
        assertThat(extract.get("atkomstlogg").toString()).contains("CASE_VIEW");

        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("action", "REGISTER_EXTRACT").param("subjectId", citizenId))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void erasureRemovesDraftsAndAccountButKeepsSubmittedCases() throws Exception {
        String submitted = createCase(citizen);
        submit(submitted);

        mvc.perform(as(security, get("/api/v1/security/people/" + citizenId + "/erasure")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.draftsToDelete").value(1))
                .andExpect(jsonPath("$.retainedCases.length()").value(1));

        mvc.perform(as(security, post("/api/v1/security/people/" + citizenId + "/erase")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftsDeleted").value(1))
                .andExpect(jsonPath("$.casesRetained").value(1));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id = ?", Integer.class, UUID.fromString(caseId))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id = ?", Integer.class, UUID.fromString(submitted))).isOne();
        Map<String, Object> user = jdbc.queryForMap("SELECT email, first_name, phone, active FROM users WHERE id = ?", UUID.fromString(citizenId));
        assertThat(user.get("email").toString()).endsWith("@raderad.invalid");
        assertThat(user.get("first_name")).isEqualTo("Raderad");
        assertThat(user.get("active")).isEqualTo(false);

        // The erased person can no longer use their token
        mvc.perform(as(citizen, get("/api/v1/cases/user/" + citizenId))).andExpect(status().isUnauthorized());
    }

    @Test
    void staffAccountsAreNotErasedAsDataSubjectRequests() throws Exception {
        String managerId = login(Map.of("email", MANAGER)).get("user").get("id").asText();
        mvc.perform(as(security, get("/api/v1/security/people/" + managerId + "/erasure")))
                .andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(as(security, post("/api/v1/security/people/" + managerId + "/erase")))
                .andExpect(status().isConflict());
    }

    // ---------- Gallring ----------

    @Test
    void retentionPurgesClosedCasesAfterTheGallringsfristAndOldDrafts() throws Exception {
        String admin = loginAs(ADMIN);
        String copy = mvc.perform(as(admin, post("/api/v1/admin/flows/" + BUILDING_PERMIT_FLOW + "/duplicate")))
                .andReturn().getResponse().getContentAsString();
        String flowId = json.readTree(copy).get("id").asText();
        mvc.perform(as(admin, put("/api/v1/admin/flows/" + flowId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("retentionMonths", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionMonths").value(1));

        // A case on that e-service, closed two months ago
        String expired = createCase(citizen);
        jdbc.update("UPDATE cases SET flow_id = ?, submitted_at = NOW() - INTERVAL '3 months', " +
                    "completed_at = NOW() - INTERVAL '2 months' WHERE id = ?", UUID.fromString(flowId), UUID.fromString(expired));
        // A draft nobody has touched for half a year (the trigger normally keeps updated_at current)
        jdbc.execute("ALTER TABLE cases DISABLE TRIGGER trg_cases_updated_at");
        try {
            jdbc.update("UPDATE cases SET updated_at = NOW() - INTERVAL '180 days' WHERE id = ?", UUID.fromString(caseId));
        } finally {
            jdbc.execute("ALTER TABLE cases ENABLE TRIGGER trg_cases_updated_at");
        }

        mvc.perform(as(security, get("/api/v1/security/retention")))
                .andExpect(jsonPath("$.dueWithin30Days[?(@.caseId == '" + expired + "')]").exists());

        mvc.perform(as(security, post("/api/v1/security/retention/run")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.casesPurged").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.draftsPurged").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id IN (?, ?)", Integer.class,
                UUID.fromString(expired), UUID.fromString(caseId))).isZero();
        mvc.perform(as(security, get("/api/v1/security/audit"))
                        .param("action", "CASE_PURGED").param("entityId", expired))
                .andExpect(jsonPath("$.content[0].subjectUserId").value(citizenId))
                .andExpect(jsonPath("$.content[0].userId").value("system"));
    }

    // ---------- Drift ----------

    @Test
    void operationsSeeStatusAndTechnicalEventsWithoutPersonalData() throws Exception {
        mvc.perform(as(operations, get("/api/v1/ops/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[?(@.name == 'Databas')].status").value("UP"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.recentStartups.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        String events = mvc.perform(as(operations, get("/api/v1/ops/events")).param("size", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(events).doesNotContain("@example.com");
    }
}
