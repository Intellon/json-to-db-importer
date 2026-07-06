package io.intellon.jsonimporter.model;

public record AppSettings(String dbType, String host, Integer port, String database, String username, String lastFolder) {
}
