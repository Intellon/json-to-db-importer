package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * One reused connection per import run (single-user tool, sequential import).
 * Callers must call destroy() on the returned SingleConnectionDataSource when done.
 */
@Component
public class ConnectionFactory {

    private final DialectRegistry dialectRegistry;

    public ConnectionFactory(DialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    public DataSource create(DbConfig config) {
        String url = dialectRegistry.forType(config.dbType()).buildJdbcUrl(config);
        return new SingleConnectionDataSource(url, config.username(), config.password(), true);
    }
}
