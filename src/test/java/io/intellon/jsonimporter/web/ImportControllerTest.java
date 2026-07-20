package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.db.SqlDialect;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.model.ImportResult;
import io.intellon.jsonimporter.model.ImportStatus;
import io.intellon.jsonimporter.service.ConfigPersistenceService;
import io.intellon.jsonimporter.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "importer.config-file=${java.io.tmpdir}/jsonimporter-test/config.json")
@AutoConfigureMockMvc
class ImportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DialectRegistry dialectRegistry;

    @MockitoBean
    ConfigPersistenceService persistence;

    @MockitoBean
    ImportService importService;

    @TempDir
    Path folder;

    MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(persistence.load()).thenReturn(Optional.empty());
        SqlDialect dialect = mock(SqlDialect.class);
        when(dialectRegistry.forType(DbType.MSSQL)).thenReturn(dialect);
        session = new MockHttpSession();
        mvc.perform(post("/config/test").session(session)
                .param("dbType", "MSSQL").param("host", "h").param("port", "1433")
                .param("database", "d").param("username", "u").param("password", "p"))
                .andExpect(status().isOk());
        // zwei valide Files scannen
        Path sub = Files.createDirectories(folder.resolve("folderxxx"));
        Files.writeString(sub.resolve("a.json"), "{\"a\":1}");
        Files.writeString(sub.resolve("b.json"), "{\"b\":2}");
        mvc.perform(post("/files/scan").session(session).param("folder", folder.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void importWithoutTestedConnectionRedirects() throws Exception {
        mvc.perform(post("/import")).andExpect(redirectedUrl("/config"));
    }

    @Test
    void runsImportForSelectedFilesAndRedirectsToResult() throws Exception {
        when(importService.run(any(), any())).thenReturn(List.of(
                new ImportResult("folderxxx/a.json", "folderxxx", "a", ImportStatus.INSERTED, ""),
                new ImportResult("folderxxx/b.json", "folderxxx", "b", ImportStatus.UPDATED, "")));

        mvc.perform(post("/import").session(session)
                        .param("selected", "0").param("selected", "1")
                        .param("key-0", " a ").param("key-1", "b"))
                .andExpect(redirectedUrl("/result"));

        verify(importService).run(any(), argThat(items ->
                items.size() == 2 && items.get(0).fileKey().equals("a")));

        mvc.perform(get("/result").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("neu angelegt")))
                .andExpect(content().string(containsString("aktualisiert")));
    }

    @Test
    void keyConflictRerendersFilesPageAndBlocksImport() throws Exception {
        mvc.perform(post("/import").session(session)
                        .param("selected", "0").param("selected", "1")
                        .param("key-0", "same").param("key-1", "SAME"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Key-Konflikt")));
        verify(importService, never()).run(any(), any());
    }

    @Test
    void emptyKeyRerendersFilesPageAndBlocksImport() throws Exception {
        mvc.perform(post("/import").session(session)
                        .param("selected", "0").param("selected", "1")
                        .param("key-0", "  ").param("key-1", "b"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Key darf nicht leer sein")));
        verify(importService, never()).run(any(), any());
    }

    @Test
    void resultWithoutRunRedirectsToFiles() throws Exception {
        mvc.perform(get("/result").session(session)).andExpect(redirectedUrl("/files"));
    }

    @Test
    void resultWithoutTestedConnectionRedirectsToConfig() throws Exception {
        mvc.perform(get("/result")).andExpect(redirectedUrl("/config"));
    }

    @Test
    void importWithoutScanRedirectsToFiles() throws Exception {
        // Frische Session: Verbindung getestet, aber noch kein Scan gelaufen.
        MockHttpSession freshSession = new MockHttpSession();
        mvc.perform(post("/config/test").session(freshSession)
                        .param("dbType", "MSSQL").param("host", "h").param("port", "1433")
                        .param("database", "d").param("username", "u").param("password", "p"))
                .andExpect(status().isOk());

        mvc.perform(post("/import").session(freshSession).param("selected", "0"))
                .andExpect(redirectedUrl("/files"));
        verify(importService, never()).run(any(), any());
    }

    @Test
    void resultPageListsAllScannedFilesIncludingSkippedOnes() throws Exception {
        // Drittes File (ungültiges JSON) zum bereits gescannten Ordner hinzufügen und neu scannen,
        // damit wir a.json (ausgewählt), b.json (abgewählt) und c_invalid.json (ungültig) haben.
        Path sub = folder.resolve("folderxxx");
        Files.writeString(sub.resolve("c_invalid.json"), "{invalid");
        mvc.perform(post("/files/scan").session(session).param("folder", folder.toString()))
                .andExpect(status().isOk());

        when(importService.run(any(), any())).thenReturn(List.of(
                new ImportResult("folderxxx/a.json", "folderxxx", "a", ImportStatus.INSERTED, "")));

        mvc.perform(post("/import").session(session)
                        .param("selected", "0")
                        .param("key-0", "a"))
                .andExpect(redirectedUrl("/result"));

        mvc.perform(get("/result").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("neu angelegt")))
                .andExpect(content().string(containsString("übersprungen")))
                .andExpect(content().string(containsString("abgewählt")))
                .andExpect(content().string(containsString("ungültiges JSON")))
                .andExpect(content().string(containsString("2 übersprungen")));
    }

    @Test
    void newScanClearsStaleResultsSoResultRedirectsToFiles() throws Exception {
        when(importService.run(any(), any())).thenReturn(List.of(
                new ImportResult("folderxxx/a.json", "folderxxx", "a", ImportStatus.INSERTED, "")));

        mvc.perform(post("/import").session(session)
                        .param("selected", "0")
                        .param("key-0", "a"))
                .andExpect(redirectedUrl("/result"));
        mvc.perform(get("/result").session(session)).andExpect(status().isOk());

        // Neuer erfolgreicher Scan muss die Ergebnisse aus dem vorherigen Lauf verwerfen.
        mvc.perform(post("/files/scan").session(session).param("folder", folder.toString()))
                .andExpect(status().isOk());

        mvc.perform(get("/result").session(session)).andExpect(redirectedUrl("/files"));
    }

    @Test
    void validationIssueIsAttachedToCorrectRowAmongInterleavedFiles() throws Exception {
        // Eigener Unterordner mit 3 Files in definierter Scan-Reihenfolge:
        // Index 0 = ungültiges JSON (nicht importierbar), Index 1 = ausgewählt mit leerem Key,
        // Index 2 = importierbar aber nicht ausgewählt.
        Path sub = Files.createDirectories(folder.resolve("case6"));
        Files.writeString(sub.resolve("0_invalid.json"), "{not valid");
        Files.writeString(sub.resolve("1_selected.json"), "{\"a\":1}");
        Files.writeString(sub.resolve("2_unselected.json"), "{\"b\":2}");

        mvc.perform(post("/files/scan").session(session).param("folder", sub.toString()))
                .andExpect(status().isOk());

        MvcResult result = mvc.perform(post("/import").session(session)
                        .param("selected", "1")
                        .param("key-1", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Key darf nicht leer sein")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int idxInvalidRow = body.indexOf("0_invalid.json");
        int idxSelectedRow = body.indexOf("1_selected.json");
        int idxUnselectedRow = body.indexOf("2_unselected.json");
        int idxIssue = body.indexOf("Key darf nicht leer sein");

        assertTrue(idxInvalidRow >= 0 && idxSelectedRow >= 0 && idxUnselectedRow >= 0 && idxIssue >= 0);
        assertTrue(idxIssue > idxSelectedRow && idxIssue < idxUnselectedRow,
                "Validierungsfehler muss an Zeile 'case6/1_selected.json' hängen, nicht an einer anderen Zeile");

        verify(importService, never()).run(any(), any());
    }
}
