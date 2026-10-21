package se.eplatform.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.eplatform.support.IntegrationTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Row level security on the contract levels (V19) holds on its own, without
 * the checks in ContractService.
 */
class ContractRowLevelSecurityTest extends IntegrationTest {

    static final UUID EXEMPELKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID GRANNKOMMUN = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final String APPLICATION = "00000000-0000-0000-000b-000000000001";

    UUID draftDefinition;
    UUID draftVersion;

    @BeforeEach
    void setUp() {
        draftDefinition = UUID.randomUUID();
        draftVersion = UUID.randomUUID();
        jdbc.update("INSERT INTO service_definitions (id, key, name, steward_organization_id) VALUES (?, ?, 'Utkast', ?)",
                draftDefinition, "se.rls-" + draftDefinition.toString().substring(0, 8), EXEMPELKOMMUN);
        jdbc.update("INSERT INTO service_definition_versions (id, definition_id, version, content) VALUES (?, ?, 1, '{}')",
                draftVersion, draftDefinition);
    }

    private static Connection asApplication(UUID organization) throws SQLException {
        Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "eplatform_app", APP_PASSWORD);
        app.setAutoCommit(false);
        if (organization != null) {
            try (Statement statement = app.createStatement()) {
                statement.execute("SELECT set_config('app.org_id', '" + organization + "', true)");
            }
        }
        return app;
    }

    private static long count(Connection app, String sql) throws SQLException {
        try (Statement statement = app.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void withoutContextOnlyPublishedDefinitionsAreVisible() throws Exception {
        try (Connection app = asApplication(null)) {
            assertThat(count(app, "SELECT COUNT(*) FROM service_definitions WHERE key = 'se.skolskjuts'")).isOne();
            assertThat(count(app, "SELECT COUNT(*) FROM service_definitions WHERE id = '" + draftDefinition + "'")).isZero();
            assertThat(count(app, "SELECT COUNT(*) FROM service_definition_versions WHERE status = 'DRAFT'")).isZero();
            for (String table : List.of("service_applications", "service_application_roles", "service_application_versions")) {
                assertThat(count(app, "SELECT COUNT(*) FROM " + table)).as(table).isZero();
            }
        }
    }

    @Test
    void anOrganisationSeesItsOwnDraftsAndApplicationsOnly() throws Exception {
        try (Connection app = asApplication(EXEMPELKOMMUN)) {
            assertThat(count(app, "SELECT COUNT(*) FROM service_definition_versions WHERE id = '" + draftVersion + "'")).isOne();
            assertThat(count(app, "SELECT COUNT(*) FROM service_applications WHERE id = '" + APPLICATION + "'")).isZero();
        }
        try (Connection app = asApplication(GRANNKOMMUN)) {
            assertThat(count(app, "SELECT COUNT(*) FROM service_definition_versions WHERE id = '" + draftVersion + "'")).isZero();
            assertThat(count(app, "SELECT COUNT(*) FROM service_applications WHERE id = '" + APPLICATION + "'")).isOne();
            assertThat(count(app, "SELECT COUNT(*) FROM service_application_versions WHERE application_id = '" + APPLICATION + "'"))
                    .isOne();
        }
    }

    @Test
    void writesAimedAtAnotherOrganisationAreRefused() throws Exception {
        try (Connection app = asApplication(GRANNKOMMUN); Statement statement = app.createStatement()) {
            // Not the steward: the draft is neither visible nor writable
            assertThat(statement.executeUpdate("UPDATE service_definition_versions SET content = '{\"x\":1}' WHERE id = '"
                    + draftVersion + "'")).isZero();
            // An application owned by someone else cannot be created
            assertThatThrownBy(() -> statement.execute("INSERT INTO service_applications (definition_id, name, owner_organization_id) " +
                    "SELECT id, 'Kapad', '" + EXEMPELKOMMUN + "' FROM service_definitions WHERE key = 'se.skolskjuts'"))
                    .hasMessageContaining("row-level security");
        }
        try (Connection app = asApplication(EXEMPELKOMMUN); Statement statement = app.createStatement()) {
            // Exempelkommun cannot take a role in Grannkommun's application
            assertThatThrownBy(() -> statement.execute("INSERT INTO service_application_roles (application_id, organization_id) " +
                    "VALUES ('" + APPLICATION + "', '" + EXEMPELKOMMUN + "')"))
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    void aDefinitionVersionThatCannotBeSeenCannotBeApplied() throws Exception {
        try (Connection app = asApplication(GRANNKOMMUN); Statement statement = app.createStatement()) {
            statement.execute("INSERT INTO service_applications (id, definition_id, name, owner_organization_id) VALUES " +
                    "('" + UUID.randomUUID() + "', '" + draftDefinition + "', 'Gömd', '" + GRANNKOMMUN + "')");
            assertThatThrownBy(() -> statement.execute("INSERT INTO service_application_versions (application_id, " +
                    "definition_version_id, version, content) SELECT id, '" + draftVersion + "', 1, '{}' " +
                    "FROM service_applications WHERE name = 'Gömd'"))
                    .hasMessageContaining("only apply a published definition version");
        }
    }
}
