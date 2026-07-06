package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.db.SqlDialect;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.service.ConfigPersistenceService;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "importer.config-file=${java.io.tmpdir}/jsonimporter-test/config.json")
@AutoConfigureMockMvc
class FilesControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DialectRegistry dialectRegistry;

    @MockitoBean
    ConfigPersistenceService persistence;

    @TempDir
    Path folder;

    MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(persistence.load()).thenReturn(Optional.empty());
        SqlDialect dialect = mock(SqlDialect.class);
        when(dialectRegistry.forType(DbType.MSSQL)).thenReturn(dialect);
        session = new MockHttpSession();
        // Schritt 1 durchlaufen, damit die Session eine getestete Verbindung hat
        mvc.perform(post("/config/test").session(session)
                .param("dbType", "MSSQL").param("host", "h").param("port", "1433")
                .param("database", "d").param("username", "u").param("password", "p"))
                .andExpect(status().isOk());
    }

    @Test
    void redirectsToConfigWithoutTestedConnection() throws Exception {
        mvc.perform(get("/files")).andExpect(redirectedUrl("/config"));
    }

    @Test
    void showsFolderFormWhenConnectionTested() throws Exception {
        mvc.perform(get("/files").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"folder\"")));
    }

    @Test
    void scanListsFoundFilesWithTableAndKey() throws Exception {
        Path sub = Files.createDirectories(folder.resolve("folderxxx"));
        Files.writeString(sub.resolve("file_xyz.json"), "{\"a\":1}");
        Files.writeString(sub.resolve("invalid.json"), "{broken");

        mvc.perform(post("/files/scan").session(session).param("folder", folder.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("file_xyz.json")))
                .andExpect(content().string(containsString("folderxxx")))
                .andExpect(content().string(containsString("value=\"file_xyz\"")))
                .andExpect(content().string(containsString("Import starten")));
    }

    @Test
    void scanErrorIsShownOnPage() throws Exception {
        mvc.perform(post("/files/scan").session(session)
                        .param("folder", folder.resolve("nope").toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordner existiert nicht")));
    }

    @Test
    void blankFolderShowsValidationMessageAndDoesNotScan() throws Exception {
        mvc.perform(post("/files/scan").session(session).param("folder", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordnerpfad darf nicht leer sein")));
    }
}
