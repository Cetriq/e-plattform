package se.eplatform.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests against the full application and a real PostgreSQL
 * database (with all Flyway migrations and seed data).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String ADMIN = "admin@example.com";
    public static final String MANAGER = "handlaggare@example.com";
    public static final String CITIZEN = "medborgare@example.com";
    public static final String BUILDING_PERMIT_FLOW = "00000000-0000-0000-0004-000000000001";

    // One database for all test classes; started once per JVM
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Gallring logs in as this role; in real environments LOGIN and the password are set outside migrations. */
    public static final String RETENTION_PASSWORD = "retention-test";
    /** The application logs in as this role, bound by row level security (V14). */
    public static final String APP_PASSWORD = "app-test";

    static {
        POSTGRES.start();
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE eplatform_retention LOGIN PASSWORD '" + RETENTION_PASSWORD + "'");
            statement.execute("CREATE ROLE eplatform_app LOGIN PASSWORD '" + APP_PASSWORD + "'");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // The application as eplatform_app, so the tests run under row level
        // security; migrations as the database owner
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "eplatform_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("eplatform.retention.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("eplatform.retention.datasource.password", () -> RETENTION_PASSWORD);
        registry.add("eplatform.security.rate-limit.enabled", () -> "false");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    protected MockMvc mvc;

    /**
     * Direct database access for setting up and inspecting test data, as the
     * owner: it is not bound by row level security, unlike the application.
     */
    protected final org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    @Autowired
    protected ObjectMapper json;

    /** Log in as a persona and return the token. */
    protected String loginAs(String email) throws Exception {
        return login(Map.of("email", email)).get("token").asText();
    }

    public static final String PLATFORM = "00000000-0000-0000-0000-000000000012";

    /**
     * Ingrid Säkerhet working for the platform: the security function that reads
     * the platform's log (citizen accounts, logins, jobs) and runs gallring.
     */
    protected String platformSecurity() throws Exception {
        String token = loginAs("informationssakerhet@example.com");
        String response = mvc.perform(as(token, post("/api/v1/public/auth/organization"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("organizationId", PLATFORM))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    protected JsonNode login(Map<String, String> body) throws Exception {
        String response = mvc.perform(post("/api/v1/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    /** Create a new demo citizen and return the login response. */
    protected JsonNode newDemoCitizen(Map<String, String> body) throws Exception {
        String response = mvc.perform(post("/api/v1/public/auth/demo-citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    /** Create a draft case for the given user and return its id. */
    protected String createCase(String token) throws Exception {
        String response = mvc.perform(as(token, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", BUILDING_PERMIT_FLOW))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }
}
