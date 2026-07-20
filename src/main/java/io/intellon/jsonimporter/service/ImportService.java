package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.db.ConnectionFactory;
import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.db.SqlDialect;
import io.intellon.jsonimporter.db.UpsertOutcome;
import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.ImportItem;
import io.intellon.jsonimporter.model.ImportResult;
import io.intellon.jsonimporter.model.ImportStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the import: one transaction per file (spec section 5), so a failing
 * file only rolls back itself. Files are re-read and re-validated at import
 * time; invalid ones are reported as SKIPPED (spec section 4, step 3).
 */
@Service
public class ImportService {

    private final ConnectionFactory connectionFactory;
    private final DialectRegistry dialectRegistry;

    public ImportService(ConnectionFactory connectionFactory, DialectRegistry dialectRegistry) {
        this.connectionFactory = connectionFactory;
        this.dialectRegistry = dialectRegistry;
    }

    public List<ImportResult> run(DbConfig config, List<ImportItem> items) {
        SqlDialect dialect = dialectRegistry.forType(config.dbType());
        DataSource dataSource = connectionFactory.create(config);
        try {
            Run run = new Run(dialect, new JdbcTemplate(dataSource),
                    new TransactionTemplate(new JdbcTransactionManager(dataSource)), new HashSet<>());
            List<ImportResult> results = new ArrayList<>(items.size());
            for (ImportItem item : items) {
                results.add(importOne(run, item));
            }
            return results;
        } finally {
            if (dataSource instanceof SingleConnectionDataSource single) {
                single.destroy();
            }
        }
    }

    /** Everything that stays constant across the files of one import run. */
    private record Run(SqlDialect dialect, JdbcTemplate jdbc, TransactionTemplate tx, Set<String> tablesEnsured) {
    }

    private ImportResult importOne(Run run, ImportItem item) {
        String key = item.fileKey() == null ? "" : item.fileKey().trim();
        try {
            String content = JsonFileReader.read(Path.of(item.absolutePath()));
            if (!JsonValidator.isValid(content)) {
                return new ImportResult(item.relativePath(), item.targetTable(), key,
                        ImportStatus.SKIPPED, "Dateiinhalt ist kein gültiges JSON (ungültig)");
            }
            UpsertOutcome outcome = run.tx().execute(status -> {
                if (!run.tablesEnsured().contains(item.targetTable())) {
                    run.dialect().createTableIfNotExists(run.jdbc(), item.targetTable());
                }
                return run.dialect().upsert(run.jdbc(), item.targetTable(), key, content);
            });
            run.tablesEnsured().add(item.targetTable());
            // Anything but a known outcome means the dialect misbehaved — reporting it as
            // "aktualisiert" would claim a write that may never have happened.
            ImportStatus resultStatus = switch (outcome) {
                case INSERTED -> ImportStatus.INSERTED;
                case UPDATED -> ImportStatus.UPDATED;
                case null -> throw new IllegalStateException("Upsert lieferte kein Ergebnis zurück");
            };
            return new ImportResult(item.relativePath(), item.targetTable(), key, resultStatus, "");
        } catch (Exception e) {
            return new ImportResult(item.relativePath(), item.targetTable(), key,
                    ImportStatus.ERROR, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
