package se.eplatform.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import se.eplatform.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every organisation has its own hash chain in the traceability log, and its
 * security officer searches and verifies only that. Events of no organisation
 * are in the platform's chain.
 */
class AuditChainsTest extends IntegrationTest {

    static final String EXEMPELKOMMUN = "00000000-0000-0000-0000-000000000010";
    static final String GRANNKOMMUN = "00000000-0000-0000-0000-000000000011";
    static final String SCHOOL_TRANSPORT_FLOW = "00000000-0000-0000-0004-000000000011";
    static final String GRANNE_SECURITY = "informationssakerhet.grannkommun@example.com";

    @Autowired
    JdbcTemplate jdbc;

    String citizen;
    String citizenId;
    String exempelCase;
    String grannCase;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode login = newDemoCitizen(Map.of());
        citizen = login.get("token").asText();
        citizenId = login.get("user").get("id").asText();
        exempelCase = createCase(citizen);
        grannCase = json.readTree(mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", SCHOOL_TRANSPORT_FLOW))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private long count(String token, String action, String entityId) throws Exception {
        return json.readTree(mvc.perform(as(token, get("/api/v1/security/audit"))
                        .param("action", action).param("entityId", entityId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("totalElements").asLong();
    }

    @Test
    void entriesGoToTheChainOfTheOrganisationTheyConcern() throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT chain_key || '/' || hash_version FROM audit_events WHERE action = 'CASE_CREATE' AND entity_id = ?",
                String.class, grannCase)).isEqualTo(GRANNKOMMUN + "/2");
        assertThat(jdbc.queryForObject(
                "SELECT chain_key FROM audit_events WHERE action = 'CASE_CREATE' AND entity_id = ?",
                String.class, exempelCase)).isEqualTo(EXEMPELKOMMUN);
        // The citizen's account belongs to no organisation
        assertThat(jdbc.queryForObject(
                "SELECT chain_key FROM audit_events WHERE action = 'DEMO_ACCOUNT_CREATED' AND subject_user_id = ?",
                String.class, UUID.fromString(citizenId))).isEqualTo("platform");
    }

    @Test
    void aSecurityOfficerSeesOnlyTheirOrganisationsEntries() throws Exception {
        String exempel = loginAs("informationssakerhet@example.com");
        String granne = loginAs(GRANNE_SECURITY);

        assertThat(count(exempel, "CASE_CREATE", exempelCase)).isOne();
        assertThat(count(exempel, "CASE_CREATE", grannCase)).isZero();
        assertThat(count(granne, "CASE_CREATE", grannCase)).isOne();
        assertThat(count(granne, "CASE_CREATE", exempelCase)).isZero();

        String csv = mvc.perform(as(granne, get("/api/v1/security/audit/export")).param("action", "CASE_CREATE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains(grannCase).doesNotContain(exempelCase);

        // Citizen accounts are for the platform's security function only
        mvc.perform(as(exempel, get("/api/v1/security/audit")).param("subjectId", citizenId).param("action", "DEMO_ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(platformSecurity(), get("/api/v1/security/audit")).param("subjectId", citizenId).param("action", "DEMO_ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anAttemptOnAnotherOrganisationsCaseIsInThatOrganisationsLog() throws Exception {
        jdbc.update("UPDATE cases c SET submitted_at = NOW(), status_id = s.id FROM status_definitions s " +
                    "WHERE c.id = ? AND s.flow_id = c.flow_id AND s.status_type = 'SUBMITTED'", UUID.fromString(exempelCase));
        mvc.perform(as(loginAs("handlaggare.grannkommun@example.com"), get("/api/v1/cases/" + exempelCase + "/manager")))
                .andExpect(status().isNotFound());

        String exempel = loginAs("informationssakerhet@example.com");
        mvc.perform(as(exempel, get("/api/v1/security/audit"))
                        .param("entityId", exempelCase).param("outcome", "DENIED"))
                .andExpect(jsonPath("$.content[0].userEmail").value("handlaggare.grannkommun@example.com"));
    }

    @Test
    void eachChainIsVerifiedOnItsOwn() throws Exception {
        String exempel = loginAs("informationssakerhet@example.com");
        String granne = loginAs(GRANNE_SECURITY);
        mvc.perform(as(granne, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.chains[0].chain").value(GRANNKOMMUN));
        mvc.perform(as(platformSecurity(), get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.chains[0].chain").value("platform"))
                .andExpect(jsonPath("$.chains[1].chain").value("global"));

        // Tampering with Grannkommun's chain breaks Grannkommun's, not Exempelkommun's
        Long seq = jdbc.queryForObject("SELECT MAX(seq) FROM audit_events WHERE chain_key = ?", Long.class, GRANNKOMMUN);
        String original = jdbc.queryForObject("SELECT COALESCE(details, '') FROM audit_events WHERE seq = ?", String.class, seq);
        jdbc.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_append_only");
        try {
            jdbc.update("UPDATE audit_events SET details = 'manipulerad' WHERE seq = ?", seq);
            mvc.perform(as(granne, get("/api/v1/security/audit/verify")))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.firstBrokenSeq").value(seq));
            mvc.perform(as(exempel, get("/api/v1/security/audit/verify")))
                    .andExpect(jsonPath("$.intact").value(true));
        } finally {
            jdbc.update("UPDATE audit_events SET details = NULLIF(?, '') WHERE seq = ?", original, seq);
            jdbc.execute("ALTER TABLE audit_events ENABLE TRIGGER trg_audit_append_only");
        }
        mvc.perform(as(granne, get("/api/v1/security/audit/verify"))).andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void onlyThePlatformStartsGallringAndEachOrganisationSeesItsOwnDueCases() throws Exception {
        mvc.perform(as(loginAs(GRANNE_SECURITY), post("/api/v1/security/retention/run"))).andExpect(status().isForbidden());

        jdbc.update("UPDATE flows SET retention_months = 1 WHERE id = ?", UUID.fromString(SCHOOL_TRANSPORT_FLOW));
        jdbc.update("UPDATE cases SET submitted_at = NOW() - INTERVAL '3 months', completed_at = NOW() - INTERVAL '2 months' " +
                    "WHERE id = ?", UUID.fromString(grannCase));
        try {
            mvc.perform(as(loginAs(GRANNE_SECURITY), get("/api/v1/security/retention")))
                    .andExpect(jsonPath("$.dueWithin30Days[?(@.caseId == '" + grannCase + "')]").exists());
            mvc.perform(as(loginAs("informationssakerhet@example.com"), get("/api/v1/security/retention")))
                    .andExpect(jsonPath("$.dueWithin30Days[?(@.caseId == '" + grannCase + "')]").doesNotExist());
        } finally {
            jdbc.update("UPDATE flows SET retention_months = NULL WHERE id = ?", UUID.fromString(SCHOOL_TRANSPORT_FLOW));
            jdbc.update("UPDATE cases SET completed_at = NULL WHERE id = ?", UUID.fromString(grannCase));
        }
    }
}
