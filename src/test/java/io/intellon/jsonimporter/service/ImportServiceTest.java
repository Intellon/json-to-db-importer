package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.db.ConnectionFactory;
import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.db.SqlDialect;
import io.intellon.jsonimporter.db.UpsertOutcome;
import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.model.ImportItem;
import io.intellon.jsonimporter.model.ImportResult;
import io.intellon.jsonimporter.model.ImportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    @TempDir
    Path dir;

    private final DbConfig config = new DbConfig(DbType.MSSQL, "h", 1433, "db", "u", "p");

    private Connection connection;
    private SqlDialect dialect;
    private ImportService service;

    @BeforeEach
    void setUp() {
        connection = mock(Connection.class);
        dialect = mock(SqlDialect.class);
        when(dialect.type()).thenReturn(DbType.MSSQL);

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.create(any())).thenReturn(new SingleConnectionDataSource(connection, true));

        DialectRegistry registry = mock(DialectRegistry.class);
        when(registry.forType(DbType.MSSQL)).thenReturn(dialect);

        service = new ImportService(connectionFactory, registry);
    }

    private ImportItem itemFor(String fileName, String content, String table, String key) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return new ImportItem(file.toString(), fileName, table, key);
    }

    @Test
    void importsValidFilesAndReportsOutcomePerItem() throws IOException {
        when(dialect.upsert(any(), eq("t1"), eq("a"), anyString())).thenReturn(UpsertOutcome.INSERTED);
        when(dialect.upsert(any(), eq("t1"), eq("b"), anyString())).thenReturn(UpsertOutcome.UPDATED);

        List<ImportResult> results = service.run(config, List.of(
                itemFor("a.json", "{\"a\":1}", "t1", "a"),
                itemFor("b.json", "{\"b\":2}", "t1", "b")));

        assertThat(results).extracting(ImportResult::status)
                .containsExactly(ImportStatus.INSERTED, ImportStatus.UPDATED);
        verify(dialect).createTableIfNotExists(any(), eq("t1"));
        verify(dialect).upsert(any(), eq("t1"), eq("a"), eq("{\"a\":1}"));
    }

    @Test
    void skipsFilesThatBecameInvalidSinceScan() throws IOException {
        List<ImportResult> results = service.run(config, List.of(
                itemFor("bad.json", "{broken", "t1", "bad")));
        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(ImportStatus.SKIPPED);
            assertThat(r.message()).contains("ungültig");
        });
    }

    @Test
    void isolatesFailuresPerItem() throws IOException, SQLException {
        when(dialect.upsert(any(), eq("ok"), anyString(), anyString())).thenReturn(UpsertOutcome.INSERTED);
        when(dialect.upsert(any(), eq("boom"), anyString(), anyString()))
                .thenThrow(new RuntimeException("Tabelle gesperrt"));

        List<ImportResult> results = service.run(config, List.of(
                itemFor("x.json", "{\"x\":1}", "boom", "x"),
                itemFor("y.json", "{\"y\":2}", "ok", "y")));

        assertThat(results).extracting(ImportResult::status)
                .containsExactly(ImportStatus.ERROR, ImportStatus.INSERTED);
        assertThat(results.get(0).message()).contains("Tabelle gesperrt");
        verify(connection, atLeastOnce()).rollback();
    }

    @Test
    void retriesTableCreationAfterRolledBackTransaction() throws IOException {
        when(dialect.upsert(any(), eq("t1"), eq("x"), anyString()))
                .thenThrow(new RuntimeException("Upsert fehlgeschlagen"));
        when(dialect.upsert(any(), eq("t1"), eq("y"), anyString())).thenReturn(UpsertOutcome.INSERTED);

        List<ImportResult> results = service.run(config, List.of(
                itemFor("x.json", "{\"x\":1}", "t1", "x"),
                itemFor("y.json", "{\"y\":2}", "t1", "y")));

        assertThat(results).extracting(ImportResult::status)
                .containsExactly(ImportStatus.ERROR, ImportStatus.INSERTED);
        verify(dialect, times(2)).createTableIfNotExists(any(), eq("t1"));
    }

    @Test
    void reportsMissingFileAsError() {
        List<ImportResult> results = service.run(config, List.of(
                new ImportItem(dir.resolve("gone.json").toString(), "gone.json", "t1", "gone")));
        assertThat(results).singleElement()
                .extracting(ImportResult::status).isEqualTo(ImportStatus.ERROR);
    }

    @Test
    void trimsKeysBeforeUpsert() throws IOException {
        when(dialect.upsert(any(), eq("t1"), eq("a"), anyString())).thenReturn(UpsertOutcome.INSERTED);
        List<ImportResult> results = service.run(config, List.of(
                itemFor("a.json", "{\"a\":1}", "t1", "  a  ")));
        verify(dialect).upsert(any(), eq("t1"), eq("a"), anyString());
        assertThat(results.get(0).fileKey()).isEqualTo("a");
    }
}
