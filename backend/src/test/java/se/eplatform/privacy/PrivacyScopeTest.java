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
 * Each organisation answers for the data its e-services hold; the platform
 * answers for the account.
 */
class PrivacyScopeTest extends IntegrationTest {

    static final String SCHOOL_TRANSPORT_FLOW = "00000000-0000-0000-0004-000000000011";

    @Autowired
    JdbcTemplate jdbc;

    String citizen;
    String citizenId;
    String exempelDraft;
    String grannDraft;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode login = newDemoCitizen(Map.of());
        citizen = login.get("token").asText();
        citizenId = login.get("user").get("id").asText();
        exempelDraft = createCase(citizen);
        grannDraft = json.readTree(mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", SCHOOL_TRANSPORT_FLOW))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        // Each organisation has read its case, so each has log entries about the citizen
        mvc.perform(as(citizen, get("/api/v1/cases/" + exempelDraft))).andExpect(status().isOk());
        mvc.perform(as(citizen, get("/api/v1/cases/" + grannDraft))).andExpect(status().isOk());
    }

    private JsonNode extract(String token) throws Exception {
        return json.readTree(mvc.perform(as(token, get("/api/v1/security/people/" + citizenId + "/extract")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String reference(String caseId) {
        return jdbc.queryForObject("SELECT reference_number FROM cases WHERE id = ?", String.class, UUID.fromString(caseId));
    }

    @Test
    void eachOrganisationsExtractHoldsOnlyItsOwnData() throws Exception {
        JsonNode exempel = extract(loginAs("informationssakerhet@example.com"));
        assertThat(exempel.get("personuppgiftsansvarig").asText()).isEqualTo("Exempelkommun");
        assertThat(exempel.get("arenden").toString()).contains(reference(exempelDraft)).doesNotContain(reference(grannDraft));
        assertThat(exempel.get("atkomstlogg").size()).isPositive();

        JsonNode granne = extract(loginAs("informationssakerhet.grannkommun@example.com"));
        assertThat(granne.get("personuppgiftsansvarig").asText()).isEqualTo("Grannkommun");
        assertThat(granne.get("arenden").toString()).contains(reference(grannDraft)).doesNotContain(reference(exempelDraft));

        // The platform holds the account and knows where the cases are, not what is in them
        JsonNode platform = extract(platformSecurity());
        assertThat(platform.get("arenden").size()).isZero();
        assertThat(platform.get("arenden_finns_hos").toString()).contains("Exempelkommun").contains("Grannkommun");
        assertThat(platform.get("atkomstlogg").toString()).contains("DEMO_ACCOUNT_CREATED");
    }

    @Test
    void anOrganisationErasesItsDraftsAndThePlatformTheAccount() throws Exception {
        mvc.perform(as(loginAs("informationssakerhet.grannkommun@example.com"), get("/api/v1/security/people/" + citizenId + "/erasure")))
                .andExpect(jsonPath("$.draftsToDelete").value(1))
                .andExpect(jsonPath("$.accountErased").value(false));
        mvc.perform(as(loginAs("informationssakerhet.grannkommun@example.com"), post("/api/v1/security/people/" + citizenId + "/erase")))
                .andExpect(jsonPath("$.draftsDeleted").value(1))
                .andExpect(jsonPath("$.accountErased").value(false));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id = ?", Integer.class, UUID.fromString(grannDraft))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id = ?", Integer.class, UUID.fromString(exempelDraft))).isOne();
        mvc.perform(as(citizen, get("/api/v1/cases/" + exempelDraft))).andExpect(status().isOk());

        mvc.perform(as(platformSecurity(), post("/api/v1/security/people/" + citizenId + "/erase")))
                .andExpect(jsonPath("$.draftsDeleted").value(1))
                .andExpect(jsonPath("$.accountErased").value(true));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, UUID.fromString(citizenId)))
                .endsWith("@raderad.invalid");
        mvc.perform(as(citizen, get("/api/v1/cases/" + exempelDraft))).andExpect(status().isUnauthorized());
    }
}
