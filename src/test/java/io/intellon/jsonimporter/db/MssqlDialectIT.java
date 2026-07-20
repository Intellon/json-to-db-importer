package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.service.IdentifierSanitizer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MssqlDialectIT {

    @Container
    static final MSSQLServerContainer MSSQL =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    static final MssqlDialect dialect = new MssqlDialect();
    static DbConfig config;
    static SingleConnectionDataSource dataSource;
    static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        config = new DbConfig(DbType.MSSQL, MSSQL.getHost(), MSSQL.getMappedPort(1433),
                "master", MSSQL.getUsername(), MSSQL.getPassword());
        dataSource = new SingleConnectionDataSource(
                dialect.buildJdbcUrl(config), config.username(), config.password(), true);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void tearDown() {
        // Die eine wiederverwendete Verbindung schließen, bevor der Container stoppt.
        dataSource.destroy();
    }

    @Test
    void testConnectionSucceedsWithValidCredentials() throws SQLException {
        dialect.testConnection(config);
    }

    @Test
    void testConnectionFailsWithWrongPassword() {
        // Vom echten Passwort abgeleitet, damit es garantiert nicht zufällig stimmt.
        DbConfig wrong = new DbConfig(DbType.MSSQL, config.host(), config.port(),
                config.database(), config.username(), config.password() + "_falsch");
        assertThatThrownBy(() -> dialect.testConnection(wrong)).isInstanceOf(SQLException.class);
    }

    @Test
    void createTableIsIdempotent() {
        dialect.createTableIfNotExists(jdbc, "idempotent_test");
        dialect.createTableIfNotExists(jdbc, "idempotent_test");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.tables WHERE name = 'idempotent_test'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void upsertInsertsThenUpdates() {
        dialect.createTableIfNotExists(jdbc, "upsert_test");

        UpsertOutcome first = dialect.upsert(jdbc, "upsert_test", "file_xyz", "{\"v\":1}");
        assertThat(first).isEqualTo(UpsertOutcome.INSERTED);

        UpsertOutcome second = dialect.upsert(jdbc, "upsert_test", "file_xyz", "{\"v\":2}");
        assertThat(second).isEqualTo(UpsertOutcome.UPDATED);

        String content = jdbc.queryForObject(
                "SELECT content FROM [upsert_test] WHERE file_key = ?", String.class, "file_xyz");
        assertThat(content).isEqualTo("{\"v\":2}");
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM [upsert_test]", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void contentWithSpecialCharactersSurvivesUnchanged() {
        dialect.createTableIfNotExists(jdbc, "special_chars");
        String content = "{\"text\":\"Müller & Söhne <täglich> 'quoted' \\\"double\\\" 😀\",\n  \"line\":\"a\\nb\"}";
        dialect.upsert(jdbc, "special_chars", "special", content);
        String roundtrip = jdbc.queryForObject(
                "SELECT content FROM [special_chars] WHERE file_key = ?", String.class, "special");
        assertThat(roundtrip).isEqualTo(content);
    }

    @Test
    void sanitizedTableNamesWork() {
        String table = IdentifierSanitizer.sanitize("2024-kunden daten");
        assertThat(table).isEqualTo("2024_kunden_daten");
        dialect.createTableIfNotExists(jdbc, table);
        assertThat(dialect.upsert(jdbc, table, "k", "[]")).isEqualTo(UpsertOutcome.INSERTED);
    }

    @Test
    void tableNameStartingWithADigitIsCreatedUnderTheFolderName() {
        // Beweis gegen echtes MSSQL, dass das frühere 't_'-Präfix überflüssig war:
        // als [01_Login] geklammert ist der Bezeichner gültig.
        String table = IdentifierSanitizer.sanitize("01_Login");
        assertThat(table).isEqualTo("01_Login");

        dialect.createTableIfNotExists(jdbc, table);
        assertThat(dialect.upsert(jdbc, table, "k", "{\"a\":1}")).isEqualTo(UpsertOutcome.INSERTED);

        String actualName = jdbc.queryForObject(
                "SELECT name FROM sys.tables WHERE name = ?", String.class, "01_Login");
        assertThat(actualName).isEqualTo("01_Login");
    }

    @Test
    void largeContentRoundtrips() {
        dialect.createTableIfNotExists(jdbc, "large_content");
        String content = "[\"" + "x".repeat(1_000_000) + "\"]";
        dialect.upsert(jdbc, "large_content", "big", content);
        String roundtrip = jdbc.queryForObject(
                "SELECT content FROM [large_content] WHERE file_key = ?", String.class, "big");
        assertThat(roundtrip).hasSameSizeAs(content).isEqualTo(content);
    }
}
