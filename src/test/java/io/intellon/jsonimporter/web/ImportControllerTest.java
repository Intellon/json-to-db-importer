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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
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
}
