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
        if (folder == null || folder.isBlank()) {
            model.addAttribute("errorMessage", "Ordnerpfad darf nicht leer sein");
            model.addAttribute("files", wizardState.getScannedFiles());
            return "files";
        }
        try {
            wizardState.setScannedFiles(scanService.scan(Path.of(folder.trim())));
            wizardState.setResults(null);
            wizardState.setFolder(folder);
        } catch (Exception e) {
            model.addAttribute("errorMessage",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            model.addAttribute("files", wizardState.getScannedFiles());
            return "files";
        }
        // Erst nach erfolgreichem Scan und außerhalb dessen try/catch: ein Schreibfehler
        // hier ist kein Scan-Fehler (er wird im Service geloggt und bricht nichts ab).
        persistence.saveLastFolder(folder, wizardState.getDbConfig());
        model.addAttribute("files", wizardState.getScannedFiles());
        return "files";
    }
}
