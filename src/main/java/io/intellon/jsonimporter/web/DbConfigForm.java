package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DbConfigForm {

    @NotNull(message = "DB-Typ darf nicht leer sein")
    private DbType dbType = DbType.MSSQL;

    @NotBlank(message = "Host darf nicht leer sein")
    private String host;

    @NotNull(message = "Port darf nicht leer sein")
    @Min(value = 1, message = "Port muss zwischen 1 und 65535 liegen")
    @Max(value = 65535, message = "Port muss zwischen 1 und 65535 liegen")
    private Integer port = 1433;

    @NotBlank(message = "DB-Name darf nicht leer sein")
    private String database;

    @NotBlank(message = "User darf nicht leer sein")
    private String username;

    private String password = "";

    public DbConfig toDbConfig() {
        return new DbConfig(dbType, host, port, database, username, password);
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /** Keeps the empty-string invariant in one place: the field is never null. */
    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }
}
