package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.ImportItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pre-import validation (spec section 4, step 2): keys are compared trimmed and
 * case-insensitively, because MSSQL default collations are case-insensitive —
 * a case-sensitive check would let MERGE silently overwrite the first row.
 */
public final class ImportValidator {

    private ImportValidator() {
    }

    public static Map<Integer, List<String>> validate(List<ImportItem> items) {
        Map<Integer, List<String>> issues = new HashMap<>();
        Map<String, Integer> firstIndexByTableAndKey = new HashMap<>();

        for (int i = 0; i < items.size(); i++) {
            ImportItem item = items.get(i);
            String key = item.fileKey() == null ? "" : item.fileKey().trim();
            if (key.isEmpty()) {
                issues.computeIfAbsent(i, k -> new ArrayList<>()).add("Key darf nicht leer sein");
                continue;
            }
            String composite = item.targetTable() + "|" + key.toLowerCase(Locale.ROOT);
            Integer first = firstIndexByTableAndKey.putIfAbsent(composite, i);
            if (first != null) {
                String msg = "Key-Konflikt (case-insensitiv) in Tabelle [" + item.targetTable() + "]: '" + key + "'";
                issues.computeIfAbsent(first, k -> new ArrayList<>()).add(msg);
                issues.computeIfAbsent(i, k -> new ArrayList<>()).add(msg);
            }
        }
        return issues;
    }
}
