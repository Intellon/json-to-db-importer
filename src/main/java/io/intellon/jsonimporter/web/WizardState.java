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

    public boolean isConnectionTested() {
        return connectionTested;
    }

    /**
     * Config and flag only ever move together: every later step reads the config
     * on the assumption that {@code connectionTested} vouches for it. Separate
     * setters made it possible to store one without the other.
     */
    public void markConnectionTested(DbConfig testedConfig) {
        this.dbConfig = testedConfig;
        this.connectionTested = true;
    }

    /**
     * Invalidates the connection. The config itself is kept on purpose, so the
     * form on /config can be pre-filled with the values that just failed.
     */
    public void markConnectionUntested() {
        this.connectionTested = false;
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
