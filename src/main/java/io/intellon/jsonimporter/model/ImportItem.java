package io.intellon.jsonimporter.model;

public record ImportItem(String absolutePath, String relativePath, String targetTable, String fileKey) {
}
