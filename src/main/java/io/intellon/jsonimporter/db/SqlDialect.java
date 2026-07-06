package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

/**
 * All database-specific SQL lives behind this interface (spec section 3).
 * Adding another database type means: new implementation + new DbType constant.
 */
public interface SqlDialect {

    DbType type();

    String buildJdbcUrl(DbConfig config);

    void testConnection(DbConfig config) throws SQLException;

    void createTableIfNotExists(JdbcTemplate jdbc, String tableName);

    UpsertOutcome upsert(JdbcTemplate jdbc, String tableName, String fileKey, String content);
}
