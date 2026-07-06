package io.intellon.jsonimporter.model;

public record ScannedFile(
        String relativePath,
        String absolutePath,
        long sizeBytes,
        String targetTable,
        String defaultKey,
        boolean validJson,
        boolean readError) {

    public boolean importable() {
        return validJson && !readError;
    }
}
