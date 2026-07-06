package io.intellon.jsonimporter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFileReaderTest {

    @TempDir
    Path dir;

    @Test
    void readsUtf8ContentUnchanged() throws IOException {
        Path f = dir.resolve("a.json");
        String content = "{\"name\":\"Müller\",\"emoji\":\"😀\"}";
        Files.writeString(f, content, StandardCharsets.UTF_8);
        assertThat(JsonFileReader.read(f)).isEqualTo(content);
    }

    @Test
    void stripsLeadingBom() throws IOException {
        Path f = dir.resolve("bom.json");
        Files.writeString(f, "\uFEFF" + "{\"a\":1}", StandardCharsets.UTF_8);
        assertThat(JsonFileReader.read(f)).isEqualTo("{\"a\":1}");
    }

    @Test
    void throwsOnNonUtf8Bytes() throws IOException {
        Path f = dir.resolve("latin1.json");
        Files.write(f, new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xFC, '"', '}'}); // 0xFC = ü in Latin-1, invalid UTF-8
        assertThatThrownBy(() -> JsonFileReader.read(f)).isInstanceOf(IOException.class);
    }
}
