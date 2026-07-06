package io.intellon.jsonimporter.service;

/**
 * Turns arbitrary folder names into safe SQL identifiers (spec section 5).
 * Rules: keep [A-Za-z0-9_], replace everything else with '_';
 * prefix 't_' if the result starts with a digit; truncate to 128 chars.
 */
public final class IdentifierSanitizer {

    private static final int MAX_LENGTH = 128;

    private IdentifierSanitizer() {
    }

    public static String sanitize(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (!cleaned.isEmpty() && Character.isDigit(cleaned.charAt(0))) {
            cleaned = "t_" + cleaned;
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }
}
