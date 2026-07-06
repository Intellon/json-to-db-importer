package io.intellon.jsonimporter.model;

public record DbConfig(DbType dbType, String host, int port, String database, String username, String password) {
}
