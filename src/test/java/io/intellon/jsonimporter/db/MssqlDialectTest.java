package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlDialectTest {

    private final MssqlDialect dialect = new MssqlDialect();

    @Test
    void buildsJdbcUrlWithEncryptionDefaults() {
        DbConfig config = new DbConfig(DbType.MSSQL, "dbhost", 1433, "mydb", "sa", "secret");
        assertThat(dialect.buildJdbcUrl(config)).isEqualTo(
                "jdbc:sqlserver://dbhost:1433;databaseName=mydb;encrypt=true;trustServerCertificate=true;loginTimeout=5");
    }

    @Test
    void createTableSqlIsIdempotentAndBracketed() {
        String sql = dialect.buildCreateTableSql("folderxxx");
        assertThat(sql).contains("IF OBJECT_ID(N'[folderxxx]', N'U') IS NULL");
        assertThat(sql).contains("CREATE TABLE [folderxxx]");
        assertThat(sql).contains("file_key    NVARCHAR(450)  NOT NULL PRIMARY KEY");
        assertThat(sql).contains("content     NVARCHAR(MAX)  NOT NULL");
        assertThat(sql).contains("imported_at DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()");
    }

    @Test
    void mergeSqlUpsertsWithHoldlockAndOutputsAction() {
        String sql = dialect.buildMergeSql("folderxxx");
        assertThat(sql).contains("MERGE [folderxxx] WITH (HOLDLOCK) AS target");
        assertThat(sql).contains("USING (SELECT ? AS file_key, ? AS content) AS source");
        assertThat(sql).contains("WHEN MATCHED THEN UPDATE SET content = source.content, imported_at = SYSUTCDATETIME()");
        assertThat(sql).contains("WHEN NOT MATCHED THEN INSERT (file_key, content) VALUES (source.file_key, source.content)");
        assertThat(sql).contains("OUTPUT $action;");
    }

    @Test
    void reportsMssqlType() {
        assertThat(dialect.type()).isEqualTo(DbType.MSSQL);
    }
}
