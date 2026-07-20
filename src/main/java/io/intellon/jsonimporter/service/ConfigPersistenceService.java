package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.AppSettings;
import io.intellon.jsonimporter.model.DbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists connection fields (without password) and the last folder to a local
 * JSON file. Spec section 8: missing/corrupt file is ignored, write errors are
 * logged and never interrupt the wizard flow.
 */
@Service
public class ConfigPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPersistenceService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Path configFile;

    public ConfigPersistenceService(@Value("${importer.config-file}") String configFile) {
        this.configFile = Path.of(configFile);
    }

    public Optional<AppSettings> load() {
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(configFile.toFile(), AppSettings.class));
        } catch (Exception e) {
            log.warn("Config-Datei {} nicht lesbar, wird ignoriert", configFile, e);
            return Optional.empty();
        }
    }

    public void save(AppSettings settings) {
        try {
            Files.createDirectories(configFile.toAbsolutePath().getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), settings);
        } catch (Exception e) {
            log.error("Config-Datei {} konnte nicht geschrieben werden", configFile, e);
        }
    }

    /**
     * Stores the connection fields (never the password) and keeps an already
     * persisted last folder.
     */
    public void saveConnection(DbConfig config) {
        String lastFolder = load().map(AppSettings::lastFolder).orElse(null);
        save(new AppSettings(config.dbType().name(), config.host(), config.port(),
                config.database(), config.username(), lastFolder));
    }

    /**
     * Stores the last scanned folder and keeps the already persisted connection
     * fields; without a config file yet, they are taken from the tested connection.
     */
    public void saveLastFolder(String folder, DbConfig testedConnection) {
        AppSettings current = load().orElseGet(() -> new AppSettings(
                testedConnection.dbType().name(), testedConnection.host(), testedConnection.port(),
                testedConnection.database(), testedConnection.username(), null));
        save(new AppSettings(current.dbType(), current.host(), current.port(),
                current.database(), current.username(), folder));
    }
}
