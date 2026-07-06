package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.model.ImportItem;
import io.intellon.jsonimporter.model.ImportResult;
import io.intellon.jsonimporter.model.ImportStatus;
import io.intellon.jsonimporter.model.ScannedFile;
import io.intellon.jsonimporter.service.ImportService;
import io.intellon.jsonimporter.service.ImportValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ImportController {

    private final WizardState wizardState;
    private final ImportService importService;

    public ImportController(WizardState wizardState, ImportService importService) {
        this.wizardState = wizardState;
        this.importService = importService;
    }

    @PostMapping("/import")
    public String runImport(@RequestParam(name = "selected", required = false) List<Integer> selected,
                            HttpServletRequest request, Model model) {
        if (!wizardState.isConnectionTested()) {
            return "redirect:/config";
        }
        List<ScannedFile> files = wizardState.getScannedFiles();
        if (files == null || files.isEmpty()) {
            return "redirect:/files";
        }
        Set<Integer> selectedIdx = selected == null ? Set.of() : new LinkedHashSet<>(selected);

        // Eingetippte Keys aller Zeilen einsammeln (auch nicht angewählte, für Re-Render)
        Map<Integer, String> enteredKeys = new HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            String value = request.getParameter("key-" + i);
            if (value != null) {
                enteredKeys.put(i, value);
            }
        }

        // ImportItems nur für angewählte, importierbare Files bauen — Reihenfolge = Zeilenindex
        List<Integer> itemIndexes = new ArrayList<>();
        List<ImportItem> items = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            ScannedFile f = files.get(i);
            if (!selectedIdx.contains(i) || !f.importable()) {
                continue;
            }
            String key = enteredKeys.getOrDefault(i, f.defaultKey()).trim();
            itemIndexes.add(i);
            items.add(new ImportItem(f.absolutePath(), f.relativePath(), f.targetTable(), key));
        }

        Map<Integer, List<String>> itemIssues = ImportValidator.validate(items);
        if (!itemIssues.isEmpty()) {
            // Item-Indizes zurück auf Zeilenindizes mappen
            Map<Integer, List<String>> rowIssues = new HashMap<>();
            itemIssues.forEach((itemIdx, msgs) -> rowIssues.put(itemIndexes.get(itemIdx), msgs));
            model.addAttribute("folder", wizardState.getFolder());
            model.addAttribute("files", files);
            model.addAttribute("issues", rowIssues);
            model.addAttribute("enteredKeys", enteredKeys);
            model.addAttribute("selectedIdx", selectedIdx);
            return "files";
        }

        List<ImportResult> serviceResults = importService.run(wizardState.getDbConfig(), items);

        // Ergebnisliste über ALLE gescannten Files aufbauen (Spec §4 Schritt 3): Zeilen, die am
        // Import teilgenommen haben, bekommen das Service-Ergebnis; abgewählte importierbare Zeilen
        // und nicht importierbare Zeilen werden als SKIPPED synthetisiert, damit auf /result keine
        // Datei stillschweigend verschwindet.
        List<ImportResult> merged = new ArrayList<>(files.size());
        int servicePos = 0;
        for (int i = 0; i < files.size(); i++) {
            if (servicePos < itemIndexes.size() && itemIndexes.get(servicePos) == i) {
                merged.add(serviceResults.get(servicePos));
                servicePos++;
                continue;
            }
            ScannedFile f = files.get(i);
            String key = enteredKeys.getOrDefault(i, f.defaultKey()).trim();
            if (!f.importable()) {
                String msg = f.readError() ? "Lesefehler" : "ungültiges JSON";
                merged.add(new ImportResult(f.relativePath(), f.targetTable(), key, ImportStatus.SKIPPED, msg));
            } else {
                merged.add(new ImportResult(f.relativePath(), f.targetTable(), key, ImportStatus.SKIPPED, "abgewählt"));
            }
        }
        wizardState.setResults(merged);
        return "redirect:/result";
    }

    @GetMapping("/result")
    public String resultPage(Model model) {
        if (!wizardState.isConnectionTested()) {
            return "redirect:/config";
        }
        if (wizardState.getResults() == null) {
            return "redirect:/files";
        }
        var results = wizardState.getResults();
        model.addAttribute("results", results);
        model.addAttribute("countInserted", results.stream().filter(r -> r.status() == ImportStatus.INSERTED).count());
        model.addAttribute("countUpdated", results.stream().filter(r -> r.status() == ImportStatus.UPDATED).count());
        model.addAttribute("countSkipped", results.stream().filter(r -> r.status() == ImportStatus.SKIPPED).count());
        model.addAttribute("countError", results.stream().filter(r -> r.status() == ImportStatus.ERROR).count());
        return "result";
    }
}
