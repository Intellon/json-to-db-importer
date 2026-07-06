package io.intellon.jsonimporter.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file as UTF-8 text and strips a leading byte order mark.
 * The returned string is stored in the database unchanged (spec section 5).
 */
public final class JsonFileReader {

    private JsonFileReader() {
    }

    public static String read(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        return content;
    }
}
