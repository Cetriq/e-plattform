package se.eplatform.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import se.eplatform.support.IntegrationTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gallring of the traceability log only happens through the procedures in the
 * schema "retention", and leaves a chain that can still be verified.
 */
class RetentionProtectionTest extends IntegrationTest {

    static final String EXEMPELKOMMUN = "00000000-0000-0000-0000-000000000010";


    String security;

    @BeforeEach
    void setUp() throws Exception {
        security = loginAs(ComplianceTest.SECURITY);
    }

    @Test
    void theOldSessionSettingNoLongerAllowsDeleting() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT set_config('eplatform.audit_retention', 'on', false)");
                assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit_events"))
                        .hasMessageContaining("append-only");
            } finally {
                try (Statement reset = connection.createStatement()) {
                    reset.execute("RESET eplatform.audit_retention");
                }
            }
            return null;
        });
    }

    @Test
    void theRetentionRoleCanOnlyRunTheProcedures() throws SQLException {
        try (Connection retention = DriverManager.getConnection(jdbcUrl(), "eplatform_retention", RETENTION_PASSWORD);
             Statement statement = retention.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit_events"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE retention_rules SET keep_interval = INTERVAL '1 day'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.execute("SET ROLE eplatform_retention_owner"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void otherRolesCannotRunTheProcedures() throws SQLException {
        jdbc.execute("CREATE ROLE retention_probe LOGIN PASSWORD 'probe'");
        jdbc.execute("GRANT SELECT, DELETE ON audit_events TO retention_probe");
        try (Connection probe = DriverManager.getConnection(jdbcUrl(), "retention_probe", "probe");
             Statement statement = probe.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM retention.purge_audit_events()"))
                    .hasMessageContaining("permission denied");
            // Even with DELETE granted nothing goes: row level security shows the
            // role no entries, and the trigger refuses everyone but the procedures
            Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
            try {
                statement.executeUpdate("DELETE FROM audit_events");
            } catch (SQLException refused) {
                assertThat(refused.getMessage()).contains("append-only");
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class)).isGreaterThanOrEqualTo(before);
        } finally {
            jdbc.execute("REVOKE ALL ON audit_events FROM retention_probe");
            jdbc.execute("DROP ROLE retention_probe");
        }
    }

    @Test
    void gallringRemovesOnlyOldEntriesAndTheChainStaysVerifiable() throws Exception {
        loginAs(ComplianceTest.SECURITY);
        Timestamp marker = jdbc.queryForObject("SELECT now()", Timestamp.class);
        Thread.sleep(1100);
        loginAs(ComplianceTest.SECURITY);
        Integer youngBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE timestamp >= ?", Integer.class, marker);
        assertThat(youngBefore).isPositive();

        // Pretend the rule says "keep everything newer than the marker"
        jdbc.update("UPDATE retention_rules SET keep_interval = now() - ? WHERE data_type = 'audit_events'", marker);
        try {
            mvc.perform(as(platformSecurity(), post("/api/v1/security/retention/run")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.auditEntriesPurged").value(org.hamcrest.Matchers.greaterThan(0)));
        } finally {
            jdbc.update("UPDATE retention_rules SET keep_interval = INTERVAL '24 months' WHERE data_type = 'audit_events'");
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE timestamp < ?", Integer.class, marker))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE timestamp >= ?", Integer.class, marker))
                .isGreaterThanOrEqualTo(youngBefore);

        // In every chain, the first kept entry links to that chain's newest anchor
        for (String chain : jdbc.queryForList("SELECT DISTINCT chain_key FROM audit_chain_anchors", String.class)) {
            String first = jdbc.queryForObject(
                    "SELECT prev_hash FROM audit_events WHERE chain_key = ? ORDER BY seq LIMIT 1", String.class, chain);
            String anchored = jdbc.queryForObject(
                    "SELECT last_removed_hash FROM audit_chain_anchors WHERE chain_key = ? ORDER BY id DESC LIMIT 1",
                    String.class, chain);
            if (first != null) {
                assertThat(first).as(chain).isEqualTo(anchored);
            }
        }
        Map<String, Object> anchor = jdbc.queryForMap(
                "SELECT id, last_removed_hash FROM audit_chain_anchors WHERE chain_key = ? ORDER BY id DESC LIMIT 1",
                EXEMPELKOMMUN);

        mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.anchors").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(as(security, get("/api/v1/security/audit")).param("action", "RETENTION_RUN"))
                .andExpect(jsonPath("$.content[0].entityId").value(anchor.get("id").toString()));

        // Anchors can't be changed
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_chain_anchors SET rule = 'x'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_chain_anchors"))
                .hasMessageContaining("append-only");
    }

    @Test
    void removingAnEntryInTheMiddleIsDetected() throws Exception {
        loginAs(ComplianceTest.SECURITY);
        loginAs(ComplianceTest.SECURITY);
        Long middle = jdbc.queryForObject(
                "SELECT seq FROM audit_events ORDER BY seq DESC OFFSET 1 LIMIT 1", Long.class);
        Long next = jdbc.queryForObject("SELECT MIN(seq) FROM audit_events WHERE seq > ?", Long.class, middle);

        // Simulate someone with owner rights going around the protection
        // A regular table: the pool may hand out another connection for the restore
        jdbc.execute("CREATE TABLE removed_entry AS SELECT * FROM audit_events WHERE seq = " + middle);
        jdbc.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_append_only");
        try {
            jdbc.update("DELETE FROM audit_events WHERE seq = ?", middle);
            mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.firstBrokenSeq").value(next));
        } finally {
            jdbc.execute("INSERT INTO audit_events SELECT * FROM removed_entry");
            jdbc.execute("DROP TABLE removed_entry");
            jdbc.execute("ALTER TABLE audit_events ENABLE TRIGGER trg_audit_append_only");
        }
        mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void aChangedAnchorIsDetected() throws Exception {
        // Make sure there is an anchor to change
        jdbc.update("UPDATE retention_rules SET keep_interval = INTERVAL '1 microsecond' WHERE data_type = 'audit_events'");
        try {
            mvc.perform(as(platformSecurity(), post("/api/v1/security/retention/run"))).andExpect(status().isOk());
        } finally {
            jdbc.update("UPDATE retention_rules SET keep_interval = INTERVAL '24 months' WHERE data_type = 'audit_events'");
        }
        // Exempelkommun's chain, which its security officer verifies
        Long anchorId = jdbc.queryForObject("SELECT MAX(id) FROM audit_chain_anchors WHERE chain_key = ?", Long.class,
                EXEMPELKOMMUN);
        String rule = jdbc.queryForObject("SELECT rule FROM audit_chain_anchors WHERE id = ?", String.class, anchorId);

        jdbc.execute("ALTER TABLE audit_chain_anchors DISABLE TRIGGER trg_anchor_append_only");
        try {
            jdbc.update("UPDATE audit_chain_anchors SET row_count = row_count + 1 WHERE id = ?", anchorId);
            mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.firstBrokenAnchor").value(anchorId));
        } finally {
            jdbc.update("UPDATE audit_chain_anchors SET row_count = row_count - 1, rule = ? WHERE id = ?", rule, anchorId);
            jdbc.execute("ALTER TABLE audit_chain_anchors ENABLE TRIGGER trg_anchor_append_only");
        }
        mvc.perform(as(security, get("/api/v1/security/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true));
    }

    private static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }
}
