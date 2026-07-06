package io.intellon.jsonimporter.service;

import tools.jackson.databind.json.JsonMapper;
import io.intellon.jsonimporter.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final Path configFile;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ConfigPersistenceService(@Value("${importer.config-file}") String configFile) {
        this.configFile = Path.of(configFile);
    }

    public Optional<AppSettings> load() {
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(configFile.toFile(), AppSettings.class));
        } catch (Exception e) {
            log.warn("Config-Datei {} nicht lesbar, wird ignoriert: {}", configFile, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(AppSettings settings) {
        try {
            Files.createDirectories(configFile.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), settings);
        } catch (Exception e) {
            log.error("Config-Datei {} konnte nicht geschrieben werden: {}", configFile, e.getMessage());
        }
    }
}
