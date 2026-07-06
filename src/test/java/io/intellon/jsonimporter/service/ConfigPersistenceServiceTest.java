package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.AppSettings;
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
    void saveNeverThrows() {
        // Pfad zeigt auf ein Verzeichnis, das nicht beschreibbar ist (Datei = existierendes Verzeichnis)
        ConfigPersistenceService service = new ConfigPersistenceService(dir.toString());
        AppSettings settings = new AppSettings("MSSQL", "h", 1, "d", "u", null);
        service.save(settings); // darf keine Exception werfen
        Optional<AppSettings> loaded = service.load();
        assertThat(loaded).isEmpty();
    }
}
