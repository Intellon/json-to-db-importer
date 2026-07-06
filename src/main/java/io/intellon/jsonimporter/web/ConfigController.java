package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.model.AppSettings;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.service.ConfigPersistenceService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ConfigController {

    private final WizardState wizardState;
    private final DialectRegistry dialectRegistry;
    private final ConfigPersistenceService persistence;

    public ConfigController(WizardState wizardState, DialectRegistry dialectRegistry,
                            ConfigPersistenceService persistence) {
        this.wizardState = wizardState;
        this.dialectRegistry = dialectRegistry;
        this.persistence = persistence;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/config";
    }

    @GetMapping("/config")
    public String configPage(Model model) {
        DbConfigForm form = new DbConfigForm();
        if (wizardState.getDbConfig() != null) {
            var current = wizardState.getDbConfig();
            form.setDbType(current.dbType());
            form.setHost(current.host());
            form.setPort(current.port());
            form.setDatabase(current.database());
            form.setUsername(current.username());
        } else {
            persistence.load().ifPresent(settings -> {
                if (settings.dbType() != null) {
                    try {
                        form.setDbType(DbType.valueOf(settings.dbType()));
                    } catch (IllegalArgumentException e) {
                        // Spec §8: korrupte/unbekannte Werte werden ignoriert, Default bleibt bestehen.
                    }
                }
                form.setHost(settings.host());
                if (settings.port() != null) {
                    form.setPort(settings.port());
                }
                form.setDatabase(settings.database());
                form.setUsername(settings.username());
            });
        }
        model.addAttribute("form", form);
        model.addAttribute("dbTypes", DbType.values());
        model.addAttribute("connectionTested", wizardState.isConnectionTested());
        return "config";
    }

    @PostMapping("/config/test")
    public String testConnection(@Valid @ModelAttribute("form") DbConfigForm form,
                                 BindingResult binding, Model model) {
        model.addAttribute("dbTypes", DbType.values());
        model.addAttribute("connectionTested", wizardState.isConnectionTested());
        if (binding.hasErrors()) {
            return "config";
        }
        var config = form.toDbConfig();
        try {
            dialectRegistry.forType(config.dbType()).testConnection(config);
            wizardState.setDbConfig(config);
            wizardState.setConnectionTested(true);
            String lastFolder = persistence.load().map(AppSettings::lastFolder).orElse(null);
            persistence.save(new AppSettings(config.dbType().name(), config.host(), config.port(),
                    config.database(), config.username(), lastFolder));
            model.addAttribute("successMessage", "Verbindung erfolgreich hergestellt.");
            model.addAttribute("connectionTested", true);
        } catch (Exception e) {
            wizardState.setConnectionTested(false);
            model.addAttribute("connectionTested", false);
            model.addAttribute("errorMessage",
                    "Verbindung fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return "config";
    }
}
