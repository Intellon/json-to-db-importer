package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class MssqlDialect implements SqlDialect {

    @Override
    public DbType type() {
        return DbType.MSSQL;
    }

    @Override
    public String buildJdbcUrl(DbConfig config) {
        return "jdbc:sqlserver://" + config.host() + ":" + config.port()
                + ";databaseName=" + config.database()
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=5";
    }

    @Override
    public void testConnection(DbConfig config) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                buildJdbcUrl(config), config.username(), config.password())) {
            if (!connection.isValid(5)) {
                throw new SQLException("Verbindung konnte nicht validiert werden");
            }
        }
    }

    @Override
    public void createTableIfNotExists(JdbcTemplate jdbc, String tableName) {
        jdbc.execute(buildCreateTableSql(tableName));
    }

    @Override
    public UpsertOutcome upsert(JdbcTemplate jdbc, String tableName, String fileKey, String content) {
        String action = jdbc.queryForObject(buildMergeSql(tableName), String.class, fileKey, content);
        return "INSERT".equalsIgnoreCase(action) ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
    }

    String buildCreateTableSql(String tableName) {
        return """
                IF OBJECT_ID(N'[%1$s]', N'U') IS NULL
                CREATE TABLE [%1$s] (
                    file_key    NVARCHAR(450)  NOT NULL PRIMARY KEY,
                    content     NVARCHAR(MAX)  NOT NULL,
                    imported_at DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()
                );
                """.formatted(tableName);
    }

    String buildMergeSql(String tableName) {
        return """
                MERGE [%1$s] WITH (HOLDLOCK) AS target
                USING (SELECT ? AS file_key, ? AS content) AS source
                    ON target.file_key = source.file_key
                WHEN MATCHED THEN UPDATE SET content = source.content, imported_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT (file_key, content) VALUES (source.file_key, source.content)
                OUTPUT $action;
                """.formatted(tableName);
    }
}
