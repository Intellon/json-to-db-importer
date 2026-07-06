package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.model.AppSettings;
import io.intellon.jsonimporter.service.ConfigPersistenceService;
import io.intellon.jsonimporter.service.FolderScanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Path;

@Controller
public class FilesController {

    private final WizardState wizardState;
    private final FolderScanService scanService;
    private final ConfigPersistenceService persistence;

    public FilesController(WizardState wizardState, FolderScanService scanService,
                           ConfigPersistenceService persistence) {
        this.wizardState = wizardState;
        this.scanService = scanService;
        this.persistence = persistence;
    }

    @GetMapping("/files")
    public String filesPage(Model model) {
        if (!wizardState.isConnectionTested()) {
            return "redirect:/config";
        }
        String folder = wizardState.getFolder();
        if (folder == null) {
            folder = persistence.load().map(AppSettings::lastFolder).orElse("");
        }
        model.addAttribute("folder", folder);
        model.addAttribute("files", wizardState.getScannedFiles());
        return "files";
    }

    @PostMapping("/files/scan")
    public String scan(@RequestParam("folder") String folder, Model model) {
        if (!wizardState.isConnectionTested()) {
            return "redirect:/config";
        }
        model.addAttribute("folder", folder);
        try {
            wizardState.setScannedFiles(scanService.scan(Path.of(folder.trim())));
            wizardState.setFolder(folder);
            persistence.load().ifPresentOrElse(
                    s -> persistence.save(new AppSettings(s.dbType(), s.host(), s.port(), s.database(), s.username(), folder)),
                    () -> {
                        var config = wizardState.getDbConfig();
                        persistence.save(new AppSettings(config.dbType().name(), config.host(), config.port(),
                                config.database(), config.username(), folder));
                    });
        } catch (Exception e) {
            model.addAttribute("errorMessage",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        model.addAttribute("files", wizardState.getScannedFiles());
        return "files";
    }
}
