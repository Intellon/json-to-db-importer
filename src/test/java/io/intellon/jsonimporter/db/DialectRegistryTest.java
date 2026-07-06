package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DialectRegistryTest {

    @Test
    void resolvesMssqlDialect() {
        DialectRegistry registry = new DialectRegistry(List.of(new MssqlDialect()));
        assertThat(registry.forType(DbType.MSSQL)).isInstanceOf(MssqlDialect.class);
    }
}
