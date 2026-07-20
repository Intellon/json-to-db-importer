package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionFactoryTest {

    private final ConnectionFactory factory =
            new ConnectionFactory(new DialectRegistry(List.of(new MssqlDialect())));

    private final DbConfig config = new DbConfig(DbType.MSSQL, "dbhost", 1433, "mydb", "sa", "secret");

    @Test
    void buildsReusableDataSourceFromTheDialectUrl() {
        DataSource dataSource = factory.create(config);

        assertThat(dataSource).isInstanceOf(SingleConnectionDataSource.class);
        SingleConnectionDataSource single = (SingleConnectionDataSource) dataSource;
        assertThat(single.getUrl()).isEqualTo(new MssqlDialect().buildJdbcUrl(config));
        assertThat(single.getUsername()).isEqualTo("sa");
        assertThat(single.getPassword()).isEqualTo("secret");
    }

    @Test
    void failsForATypeWithoutDialect() {
        ConnectionFactory withoutDialects = new ConnectionFactory(new DialectRegistry(List.of()));
        assertThatThrownBy(() -> withoutDialects.create(config))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
