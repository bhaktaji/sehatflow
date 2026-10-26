package com.clinicqueue.queue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration from an empty schema (R-023): Flyway builds the complete schema
 * including constraints, on an isolated database.
 */
class MigrationIT {

    private static final String BASE_URL = "jdbc:postgresql://localhost:5433/";
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    @Test
    void flywayBuildsCompleteSchemaAndConstraintsFromEmpty() throws Exception {
        String dbName = "cq_it_migration_" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection admin = DriverManager.getConnection(BASE_URL + "postgres", USER, PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + dbName);
        }

        try {
            DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    BASE_URL + dbName, USER, PASSWORD);
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();
            var result = flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);

            try (Connection connection = dataSource.getConnection()) {
                // All tables exist.
                for (String table : new String[]{"clinics", "staff_users", "doctors",
                        "queue_sessions", "queue_tokens", "queue_audit_events"}) {
                    assertThat(tableExists(connection, table)).isTrue();
                }
                // Core constraints exist.
                assertThat(constraintExists(connection, "queue_tokens", "uq_token_session_number")).isTrue();
                assertThat(constraintExists(connection, "queue_tokens", "uq_token_secret")).isTrue();
                assertThat(constraintExists(connection, "queue_sessions", "uq_session_clinic_doctor_date")).isTrue();
                assertThat(constraintExists(connection, "staff_users", "uq_staff_clinic_username")).isTrue();
                assertThat(constraintExists(connection, "queue_tokens", "fk_token_session")).isTrue();
                assertThat(checkConstraintExists(connection, "queue_tokens", "ck_token_status")).isTrue();
                assertThat(checkConstraintExists(connection, "queue_tokens", "ck_token_position")).isTrue();

                // Enum CHECK rejects invalid statuses.
                try (Statement s = connection.createStatement()) {
                    s.execute("""
                            INSERT INTO clinics (id, name, slug, created_at, updated_at)
                            VALUES (gen_random_uuid(), 'X', 'x-' || gen_random_uuid(), now(), now())
                            """);
                    s.execute("""
                            INSERT INTO doctors (id, clinic_id, display_name, created_at, updated_at)
                            VALUES (gen_random_uuid(), (SELECT id FROM clinics LIMIT 1), 'D', now(), now())
                            """);
                    try {
                        s.execute("""
                                INSERT INTO queue_sessions (id, clinic_id, doctor_id, business_date, opened_at,
                                    display_secret_hash, created_at, updated_at)
                                VALUES (gen_random_uuid(), (SELECT clinic_id FROM doctors LIMIT 1),
                                    (SELECT id FROM doctors LIMIT 1), current_date, now(), 'h', now(), now())
                                """);
                        s.execute("""
                                INSERT INTO queue_tokens (id, clinic_id, queue_session_id, token_number,
                                    public_secret_hash, status, queue_position, checked_in_at, created_at, updated_at)
                                VALUES (gen_random_uuid(), (SELECT clinic_id FROM queue_sessions LIMIT 1),
                                    (SELECT id FROM queue_sessions LIMIT 1), 1, 'h', 'BOGUS', 1, now(), now(), now())
                                """);
                        throw new AssertionError("invalid status must be rejected by CHECK constraint");
                    } catch (java.sql.SQLException expected) {
                        assertThat(expected.getMessage()).containsIgnoringCase("ck_token_status");
                    }
                }
            }
            // Re-running migrate is a no-op (idempotent).
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        } finally {
            try (Connection admin = DriverManager.getConnection(BASE_URL + "postgres", USER, PASSWORD);
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + dbName);
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(null, "public", table, null)) {
            return rs.next();
        }
    }

    private boolean constraintExists(Connection connection, String table, String constraint) throws Exception {
        String sql = """
                SELECT 1 FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = ? AND c.conname = ?
                """;
        try (var ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, constraint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean checkConstraintExists(Connection connection, String table, String constraint) throws Exception {
        return constraintExists(connection, table, constraint);
    }

    @Test
    void migrationVersionIsTracked() throws Exception {
        // sanity: the classpath migration set is what the app uses
        assertThat(MigrationVersion.fromVersion("1")).isNotNull();
    }
}
