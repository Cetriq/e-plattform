package se.eplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.common.tenant.TenantContext;
import se.eplatform.support.IntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Row level security (V14) is a backstop of its own: missing context shows
 * nothing, the application role cannot turn it off, and the context never
 * travels between requests on a pooled connection.
 */
class RowLevelSecurityTest extends IntegrationTest {

    static final UUID EXEMPELKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID GRANNKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final String SCHOOL_TRANSPORT_FLOW = "00000000-0000-0000-0004-000000000011";

    @Autowired
    DataSource applicationDataSource;

    @Autowired
    CaseRepository caseRepository;

    String exempelCase;
    String grannCase;

    @BeforeEach
    void setUp() throws Exception {
        String citizen = newDemoCitizen(Map.of()).get("token").asText();
        exempelCase = createCase(citizen);
        grannCase = json.readTree(mvc.perform(as(citizen, post("/api/v1/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flowId", SCHOOL_TRANSPORT_FLOW))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        for (String id : List.of(exempelCase, grannCase)) {
            jdbc.update("UPDATE cases c SET submitted_at = NOW(), status_id = s.id FROM status_definitions s " +
                        "WHERE c.id = ? AND s.flow_id = c.flow_id AND s.status_type = 'SUBMITTED'", UUID.fromString(id));
        }
    }

    private static Connection asApplication() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "eplatform_app", APP_PASSWORD);
    }

    private static long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void withoutContextTheApplicationRoleSeesNoCaseData() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cases", Long.class)).isPositive();
        try (Connection app = asApplication(); Statement statement = app.createStatement()) {
            for (String table : List.of("cases", "query_instances", "case_events", "internal_messages",
                    "external_messages", "attachments", "audit_events")) {
                assertThat(count(statement, "SELECT COUNT(*) FROM " + table)).as(table).isZero();
            }
        }
    }

    @Test
    void anOrganisationsContextShowsOnlyItsOwnCases() throws Exception {
        try (Connection app = asApplication(); Statement statement = app.createStatement()) {
            app.setAutoCommit(false);
            statement.execute("SELECT set_config('app.org_id', '" + GRANNKOMMUN + "', true)");
            assertThat(count(statement, "SELECT COUNT(*) FROM cases WHERE organization_id = '" + EXEMPELKOMMUN + "'")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM cases WHERE id = '" + grannCase + "'")).isOne();
            assertThat(count(statement, "SELECT COUNT(*) FROM query_instances q JOIN cases c ON c.id = q.case_id " +
                                        "WHERE c.organization_id = '" + EXEMPELKOMMUN + "'")).isZero();
            // An update aimed at another organisation's case changes nothing
            assertThat(statement.executeUpdate("UPDATE cases SET priority = 'HIGH' WHERE id = '" + exempelCase + "'")).isZero();
            app.rollback();
        }
    }

    @Test
    void aContextThatIsNotAnIdAbortsTheTransaction() throws Exception {
        try (Connection app = asApplication(); Statement statement = app.createStatement()) {
            app.setAutoCommit(false);
            statement.execute("SELECT set_config('app.org_id', 'exempelkommun', true)");
            assertThatThrownBy(() -> count(statement, "SELECT COUNT(*) FROM cases"))
                    .hasMessageContaining("invalid input syntax for type uuid");
            app.rollback();
        }
    }

    @Test
    void theApplicationRoleCannotTurnTheProtectionOff() throws Exception {
        try (Connection app = asApplication(); Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute("ALTER TABLE cases DISABLE ROW LEVEL SECURITY"))
                    .hasMessageContaining("must be owner");
            assertThatThrownBy(() -> statement.execute("ALTER TABLE cases NO FORCE ROW LEVEL SECURITY"))
                    .hasMessageContaining("must be owner");
            assertThatThrownBy(() -> statement.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_append_only"))
                    .hasMessageContaining("must be owner");
            assertThatThrownBy(() -> statement.execute("DROP POLICY app_cases ON cases"))
                    .hasMessageContaining("must be owner");
            assertThatThrownBy(() -> statement.execute("SET ROLE eplatform_retention_owner"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE retention_rules SET keep_interval = INTERVAL '1 day'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit_events"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM retention.purge_audit_events()"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void theDatabaseRefusesEvenWhenTheApplicationAsksForTheWrongOrganisation() {
        // Working for Grannkommun, but asking the staff query about Exempelkommun:
        // the application's own condition would allow it, row level security does not
        TenantContext.forOrganization(GRANNKOMMUN, () -> {
            assertThat(caseRepository.findById(UUID.fromString(exempelCase))).isEmpty();
            assertThat(caseRepository.isVisibleToStaff(UUID.fromString(exempelCase), EXEMPELKOMMUN, UUID.randomUUID(), 2))
                    .isFalse();
            assertThat(caseRepository.findAll()).allMatch(c -> GRANNKOMMUN.equals(c.getOrganizationId()));
            return null;
        });
    }

    @Test
    void noContextIsLeftOnPooledConnections() throws Exception {
        String manager = loginAs(MANAGER);
        mvc.perform(as(manager, get("/api/v1/cases/" + exempelCase + "/manager"))).andExpect(status().isOk());
        // A request that fails inside its transaction
        mvc.perform(as(manager, put("/api/v1/cases/" + exempelCase + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + UUID.randomUUID() + "\",\"comment\":\"x\"}"))
                .andExpect(status().is4xxClientError());

        // Take every connection the pool hands out and look at its settings
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                Connection connection = applicationDataSource.getConnection();
                held.add(connection);
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery(
                             "SELECT COALESCE(current_setting('app.org_id', true), ''), " +
                             "COALESCE(current_setting('app.user_id', true), ''), current_user")) {
                    rows.next();
                    assertThat(rows.getString(1)).as("org on connection %d", i).isEmpty();
                    assertThat(rows.getString(2)).as("user on connection %d", i).isEmpty();
                    assertThat(rows.getString(3)).isEqualTo("eplatform_app");
                }
            }
        } finally {
            for (Connection connection : held) connection.close();
        }
    }

    @Test
    void parallelRequestsFromTwoOrganisationsNeverSeeEachOthersCases() throws Exception {
        String exempel = loginAs(MANAGER);
        String granne = loginAs("handlaggare.grannkommun@example.com");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                boolean fromExempel = i % 2 == 0;
                Callable<String> call = () -> {
                    String body = mvc.perform(as(fromExempel ? exempel : granne, get("/api/v1/cases")).param("size", "500"))
                            .andReturn().getResponse().getContentAsString();
                    String forbidden = fromExempel ? grannCase : exempelCase;
                    String expected = fromExempel ? exempelCase : grannCase;
                    if (body.contains(forbidden)) return "leak: " + (fromExempel ? "Exempelkommun saw Grannkommun" : "Grannkommun saw Exempelkommun");
                    if (!body.contains(expected)) return "missing own case";
                    return "ok";
                };
                results.add(pool.submit(call));
            }
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("ok");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
