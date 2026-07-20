package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.ScannedFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class FolderScanService {

    private static final String EXTENSION = ".json";

    public List<ScannedFile> scan(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("Ordner existiert nicht: " + normalizedRoot);
        }
        try (Stream<Path> walk = Files.walk(normalizedRoot)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .map(p -> toScannedFile(normalizedRoot, p))
                    .sorted(Comparator.comparing(ScannedFile::relativePath))
                    .toList();
        }
    }

    /**
     * A filesystem root ({@code D:\}, {@code /}) has no file name — such a folder
     * falls back to the fixed table name "root".
     */
    static String targetTableFor(Path folder) {
        Path folderName = folder.getFileName();
        return IdentifierSanitizer.sanitize(folderName == null ? "root" : folderName.toString());
    }

    private ScannedFile toScannedFile(Path root, Path file) {
        Path parent = file.getParent();
        Path folder = parent.equals(root) ? root : parent;
        String targetTable = targetTableFor(folder);

        String fileName = file.getFileName().toString();
        String defaultKey = fileName.substring(0, fileName.length() - EXTENSION.length());

        long size = 0;
        boolean validJson = false;
        boolean readError = false;
        try {
            size = Files.size(file);
            validJson = JsonValidator.isValid(JsonFileReader.read(file));
        } catch (IOException e) {
            readError = true;
        }
        return new ScannedFile(
                root.relativize(file).toString(),
                file.toString(),
                size,
                targetTable,
                defaultKey,
                validJson,
                readError);
    }
}
