package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.ImportResult;
import io.intellon.jsonimporter.model.ScannedFile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.List;

/**
 * Per-session wizard state. Holds the tested connection (including password —
 * in memory only, never persisted; spec sections 3 and 8).
 */
@Component
@SessionScope
public class WizardState {

    private DbConfig dbConfig;
    private boolean connectionTested;
    private String folder;
    private List<ScannedFile> scannedFiles;
    private List<ImportResult> results;

    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public boolean isConnectionTested() {
        return connectionTested;
    }

    public void setConnectionTested(boolean connectionTested) {
        this.connectionTested = connectionTested;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public List<ScannedFile> getScannedFiles() {
        return scannedFiles;
    }

    public void setScannedFiles(List<ScannedFile> scannedFiles) {
        this.scannedFiles = scannedFiles;
    }

    public List<ImportResult> getResults() {
        return results;
    }

    public void setResults(List<ImportResult> results) {
        this.results = results;
    }
}
