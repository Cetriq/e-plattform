package se.eplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import se.eplatform.auth.service.DemoCleanupJob;
import se.eplatform.support.IntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public demo: gated by an access code, and every citizen visitor gets
 * their own account that is removed after the retention period.
 */
@TestPropertySource(properties = {
        "eplatform.demo.access-code=hemlig-kod",
        "eplatform.demo.isolated-citizens=true",
        "eplatform.demo.retention-days=7"
})
class DemoLoginTest extends IntegrationTest {

    private static final String CODE = "hemlig-kod";

    @Autowired
    DemoCleanupJob cleanupJob;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void configTellsTheLoginPageWhatToShow() throws Exception {
        mvc.perform(get("/api/v1/public/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCodeRequired").value(true))
                .andExpect(jsonPath("$.isolatedCitizens").value(true));
    }

    @Test
    void loginRequiresTheAccessCode() throws Exception {
        mvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", MANAGER))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", MANAGER, "accessCode", "fel"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/auth/demo-citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        JsonNode response = login(Map.of("email", MANAGER, "accessCode", CODE));
        assertThat(response.get("token").asText()).isNotBlank();
    }

    @Test
    void sharedCitizenPersonaIsNotAvailable() throws Exception {
        mvc.perform(get("/api/v1/public/auth/test-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[*].email").value(not(hasItem(CITIZEN))))
                .andExpect(jsonPath("$.users[*].email").value(hasItem(MANAGER)));

        mvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", CITIZEN, "accessCode", CODE))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachVisitorGetsTheirOwnCitizenAccount() throws Exception {
        JsonNode first = newDemoCitizen(Map.of("accessCode", CODE));
        JsonNode second = newDemoCitizen(Map.of("accessCode", CODE));

        assertThat(first.get("user").get("id").asText()).isNotEqualTo(second.get("user").get("id").asText());
        assertThat(first.get("user").get("roles").toString()).contains("USER");

        String firstCase = createCase(first.get("token").asText());
        mvc.perform(as(second.get("token").asText(), get("/api/v1/cases/" + firstCase)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cleanupRemovesOldDemoCitizensAndTheirCases() throws Exception {
        JsonNode old = newDemoCitizen(Map.of("accessCode", CODE));
        JsonNode recent = newDemoCitizen(Map.of("accessCode", CODE));
        UUID oldId = UUID.fromString(old.get("user").get("id").asText());
        UUID recentId = UUID.fromString(recent.get("user").get("id").asText());
        String oldCase = createCase(old.get("token").asText());

        jdbc.update("UPDATE users SET created_at = NOW() - INTERVAL '8 days' WHERE id = ?", oldId);

        cleanupJob.removeExpiredDemoUsers();

        assertThat(count("users", oldId)).isZero();
        assertThat(count("cases", UUID.fromString(oldCase))).isZero();
        assertThat(count("users", recentId)).isOne();
        // Seeded personas are never removed
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, CITIZEN)).isOne();

        // A removed user's token stops working
        mvc.perform(as(old.get("token").asText(), get("/api/v1/cases/user/" + oldId)))
                .andExpect(status().isUnauthorized());
    }

    private int count(String table, UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    }
}
