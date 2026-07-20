package io.intellon.jsonimporter.service;

import java.util.regex.Pattern;

/**
 * Turns arbitrary folder names into safe SQL identifiers (spec section 5).
 * Rules: keep [A-Za-z0-9_], replace everything else with '_'; truncate to 128 chars.
 *
 * <p>Leading digits are kept: every identifier reaches the database bracket-quoted
 * ({@code [01_Login]}), and a delimited identifier may start with a digit. The
 * table therefore carries the folder name unchanged.
 *
 * <p>Precondition: {@code raw} must be non-null and non-empty — an empty result
 * would render as {@code []} and be rejected by the database.
 */
public final class IdentifierSanitizer {

    private static final int MAX_LENGTH = 128;
    private static final Pattern ILLEGAL = Pattern.compile("[^A-Za-z0-9_]");

    private IdentifierSanitizer() {
    }

    /**
     * @throws IllegalArgumentException if {@code raw} is null or empty
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Ordnername darf nicht leer sein");
        }
        String cleaned = ILLEGAL.matcher(raw).replaceAll("_");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }
}
