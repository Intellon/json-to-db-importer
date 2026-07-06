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
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate tx = new TransactionTemplate(new JdbcTransactionManager(dataSource));
            List<ImportResult> results = new ArrayList<>(items.size());
            Set<String> tablesEnsured = new HashSet<>();
            for (ImportItem item : items) {
                results.add(importOne(dialect, jdbc, tx, item, tablesEnsured));
            }
            return results;
        } finally {
            if (dataSource instanceof SingleConnectionDataSource single) {
                single.destroy();
            }
        }
    }

    private ImportResult importOne(SqlDialect dialect, JdbcTemplate jdbc, TransactionTemplate tx, ImportItem item,
            Set<String> tablesEnsured) {
        String key = item.fileKey() == null ? "" : item.fileKey().trim();
        try {
            String content = JsonFileReader.read(Path.of(item.absolutePath()));
            if (!JsonValidator.isValid(content)) {
                return new ImportResult(item.relativePath(), item.targetTable(), key,
                        ImportStatus.SKIPPED, "Dateiinhalt ist kein gültiges JSON (ungültig)");
            }
            UpsertOutcome outcome = tx.execute(status -> {
                if (!tablesEnsured.contains(item.targetTable())) {
                    dialect.createTableIfNotExists(jdbc, item.targetTable());
                }
                return dialect.upsert(jdbc, item.targetTable(), key, content);
            });
            tablesEnsured.add(item.targetTable());
            ImportStatus resultStatus = outcome == UpsertOutcome.INSERTED ? ImportStatus.INSERTED : ImportStatus.UPDATED;
            return new ImportResult(item.relativePath(), item.targetTable(), key, resultStatus, "");
        } catch (Exception e) {
            return new ImportResult(item.relativePath(), item.targetTable(), key,
                    ImportStatus.ERROR, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
