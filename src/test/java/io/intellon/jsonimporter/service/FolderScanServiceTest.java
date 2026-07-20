package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.ScannedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderScanServiceTest {

    @TempDir
    Path root;

    private final FolderScanService service = new FolderScanService();

    private ScannedFile find(List<ScannedFile> files, String nameEnding) {
        return files.stream()
                .filter(f -> f.relativePath().replace('\\', '/').endsWith(nameEnding))
                .findFirst()
                .orElseThrow();
    }

    @BeforeEach
    void setUpTree() throws IOException {
        // root/direct.json                          -> table = sanitized root folder name
        // root/folder1/folderxxx/file_xyz.json      -> table = folderxxx
        // root/my-folder/DATA.JSON                  -> case-insensitive extension, table = my_folder
        // root/folder1/invalid.json                 -> invalid JSON
        // root/folder1/broken.json                  -> non-UTF-8 bytes -> readError
        // root/folder1/readme.txt                   -> ignored
        Files.writeString(root.resolve("direct.json"), "{\"a\":1}");
        Path xxx = Files.createDirectories(root.resolve("folder1/folderxxx"));
        Files.writeString(xxx.resolve("file_xyz.json"), "{\"b\":2}");
        Path my = Files.createDirectories(root.resolve("my-folder"));
        Files.writeString(my.resolve("DATA.JSON"), "[1,2]");
        Files.writeString(root.resolve("folder1/invalid.json"), "{broken");
        Files.write(root.resolve("folder1/broken.json"), new byte[] {'{', (byte) 0xFC, '}'});
        Files.writeString(root.resolve("folder1/readme.txt"), "no json", StandardCharsets.UTF_8);
    }

    @Test
    void findsOnlyJsonFilesRecursively() throws IOException {
        List<ScannedFile> files = service.scan(root);
        assertThat(files).hasSize(5);
        assertThat(files).noneMatch(f -> f.relativePath().endsWith(".txt"));
    }

    @Test
    void derivesTargetTableFromImmediateParentFolder() throws IOException {
        List<ScannedFile> files = service.scan(root);
        assertThat(find(files, "file_xyz.json").targetTable()).isEqualTo("folderxxx");
        assertThat(find(files, "DATA.JSON").targetTable()).isEqualTo("my_folder");
    }

    @Test
    void rootLevelFilesUseRootFolderName() throws IOException {
        List<ScannedFile> files = service.scan(root);
        String expected = IdentifierSanitizer.sanitize(root.getFileName().toString());
        assertThat(find(files, "direct.json").targetTable()).isEqualTo(expected);
    }

    @Test
    void defaultKeyIsFileNameWithoutExtension() throws IOException {
        List<ScannedFile> files = service.scan(root);
        assertThat(find(files, "file_xyz.json").defaultKey()).isEqualTo("file_xyz");
        assertThat(find(files, "DATA.JSON").defaultKey()).isEqualTo("DATA");
    }

    @Test
    void flagsInvalidJsonAndReadErrors() throws IOException {
        List<ScannedFile> files = service.scan(root);
        assertThat(find(files, "invalid.json").validJson()).isFalse();
        assertThat(find(files, "invalid.json").readError()).isFalse();
        assertThat(find(files, "broken.json").readError()).isTrue();
        assertThat(find(files, "file_xyz.json").importable()).isTrue();
        assertThat(find(files, "invalid.json").importable()).isFalse();
    }

    @Test
    void recordsSizeAndPaths() throws IOException {
        List<ScannedFile> files = service.scan(root);
        ScannedFile f = find(files, "file_xyz.json");
        assertThat(f.sizeBytes()).isEqualTo(Files.size(root.resolve("folder1/folderxxx/file_xyz.json")));
        assertThat(Path.of(f.absolutePath())).exists();
    }

    @Test
    void rejectsMissingFolder() {
        assertThatThrownBy(() -> service.scan(root.resolve("does-not-exist")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFilePassedInsteadOfFolder() {
        assertThatThrownBy(() -> service.scan(root.resolve("direct.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ordner existiert nicht");
    }

    @Test
    void filesystemRootWithoutNameFallsBackToRootTableName() {
        assertThat(FolderScanService.targetTableFor(Path.of(File.separator))).isEqualTo("root");
    }

    @Test
    void fileNamedOnlyDotJsonGetsEmptyDefaultKey() throws IOException {
        Path sub = Files.createDirectories(root.resolve("edge"));
        Files.writeString(sub.resolve(".json"), "{\"a\":1}");

        List<ScannedFile> files = service.scan(root);

        ScannedFile f = find(files, "/.json");
        assertThat(f.defaultKey()).isEmpty();
        assertThat(f.targetTable()).isEqualTo("edge");
        // Leerer Key wird erst beim Import geblockt (ImportValidator), nicht beim Scan.
        assertThat(f.importable()).isTrue();
    }
}
