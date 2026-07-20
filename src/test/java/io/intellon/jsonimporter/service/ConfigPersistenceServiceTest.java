package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.AppSettings;
import io.intellon.jsonimporter.model.DbConfig;
import io.intellon.jsonimporter.model.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPersistenceServiceTest {

    @TempDir
    Path dir;

    private ConfigPersistenceService service(String fileName) {
        return new ConfigPersistenceService(dir.resolve("sub").resolve(fileName).toString());
    }

    @Test
    void saveAndLoadRoundtrip() {
        ConfigPersistenceService service = service("config.json");
        AppSettings settings = new AppSettings("MSSQL", "dbhost", 1433, "mydb", "sa", "D:/data/json");
        service.save(settings);
        assertThat(service.load()).contains(settings);
    }

    @Test
    void loadReturnsEmptyWhenFileMissing() {
        assertThat(service("missing.json").load()).isEmpty();
    }

    @Test
    void loadReturnsEmptyOnCorruptFile() throws IOException {
        Path file = dir.resolve("sub").resolve("corrupt.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{not json");
        assertThat(service("corrupt.json").load()).isEmpty();
    }

    @Test
    void saveConnectionKeepsAlreadyPersistedLastFolder() {
        ConfigPersistenceService service = service("config.json");
        service.save(new AppSettings("MSSQL", "alt", 1, "alt", "alt", "D:/data/json"));

        service.saveConnection(new DbConfig(DbType.MSSQL, "neu", 1433, "neudb", "sa", "geheim"));

        assertThat(service.load()).contains(
                new AppSettings("MSSQL", "neu", 1433, "neudb", "sa", "D:/data/json"));
    }

    @Test
    void saveConnectionNeverPersistsThePassword() {
        ConfigPersistenceService service = service("config.json");
        service.saveConnection(new DbConfig(DbType.MSSQL, "h", 1433, "d", "sa", "streng-geheim"));

        Path file = dir.resolve("sub").resolve("config.json");
        assertThat(file).content().doesNotContain("streng-geheim");
    }

    @Test
    void saveLastFolderKeepsAlreadyPersistedConnectionFields() {
        ConfigPersistenceService service = service("config.json");
        service.save(new AppSettings("MSSQL", "dbhost", 1433, "mydb", "sa", "D:/alt"));

        service.saveLastFolder("D:/neu", new DbConfig(DbType.MSSQL, "ignoriert", 9, "ignoriert", "ignoriert", "p"));

        assertThat(service.load()).contains(
                new AppSettings("MSSQL", "dbhost", 1433, "mydb", "sa", "D:/neu"));
    }

    @Test
    void saveLastFolderFallsBackToTheTestedConnectionWithoutConfigFile() {
        ConfigPersistenceService service = service("config.json");

        service.saveLastFolder("D:/neu", new DbConfig(DbType.MSSQL, "dbhost", 1433, "mydb", "sa", "p"));

        assertThat(service.load()).contains(
                new AppSettings("MSSQL", "dbhost", 1433, "mydb", "sa", "D:/neu"));
    }

    @Test
    void saveNeverThrows() {
        // Pfad zeigt auf ein Verzeichnis, das nicht beschreibbar ist (Datei = existierendes Verzeichnis)
        ConfigPersistenceService service = new ConfigPersistenceService(dir.toString());
        AppSettings settings = new AppSettings("MSSQL", "h", 1, "d", "u", null);
        service.save(settings); // darf keine Exception werfen
        Optional<AppSettings> loaded = service.load();
        assertThat(loaded).isEmpty();
    }
}
