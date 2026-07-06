package io.intellon.jsonimporter.model;

public record ImportResult(String relativePath, String targetTable, String fileKey, ImportStatus status, String message) {
}
