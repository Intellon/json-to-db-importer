package io.intellon.jsonimporter.db;

import io.intellon.jsonimporter.model.DbType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DialectRegistry {

    private final Map<DbType, SqlDialect> dialects = new EnumMap<>(DbType.class);

    public DialectRegistry(List<SqlDialect> available) {
        for (SqlDialect dialect : available) {
            dialects.put(dialect.type(), dialect);
        }
    }

    public SqlDialect forType(DbType type) {
        SqlDialect dialect = dialects.get(type);
        if (dialect == null) {
            throw new IllegalArgumentException("Kein Dialekt für DB-Typ: " + type);
        }
        return dialect;
    }
}
