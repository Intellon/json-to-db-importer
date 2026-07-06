package io.intellon.jsonimporter.web;

import io.intellon.jsonimporter.db.DialectRegistry;
import io.intellon.jsonimporter.db.SqlDialect;
import io.intellon.jsonimporter.model.AppSettings;
import io.intellon.jsonimporter.model.DbType;
import io.intellon.jsonimporter.service.ConfigPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "importer.config-file=${java.io.tmpdir}/jsonimporter-test/config.json")
@AutoConfigureMockMvc
class ConfigControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DialectRegistry dialectRegistry;

    @MockitoBean
    ConfigPersistenceService persistence;

    SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = mock(SqlDialect.class);
        when(dialectRegistry.forType(DbType.MSSQL)).thenReturn(dialect);
        when(persistence.load()).thenReturn(Optional.empty());
    }

    @Test
    void rootRedirectsToConfig() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/config"));
    }

    @Test
    void configPageShowsFormWithDefaults() throws Exception {
        mvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"host\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"1433\"")));
    }

    @Test
    void successfulTestStoresConfigAndSavesSettings() throws Exception {
        mvc.perform(post("/config/test")
                        .param("dbType", "MSSQL")
                        .param("host", "dbhost")
                        .param("port", "1433")
                        .param("database", "mydb")
                        .param("username", "sa")
                        .param("password", "secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verbindung erfolgreich")));
        verify(dialect).testConnection(any());
        verify(persistence).save(new AppSettings("MSSQL", "dbhost", 1433, "mydb", "sa", null));
    }

    @Test
    void failedTestShowsDriverMessage() throws Exception {
        doThrow(new SQLException("Login failed for user 'sa'"))
                .when(dialect).testConnection(any());
        mvc.perform(post("/config/test")
                        .param("dbType", "MSSQL")
                        .param("host", "dbhost")
                        .param("port", "1433")
                        .param("database", "mydb")
                        .param("username", "sa")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Login failed for user")));
    }

    @Test
    void missingRequiredFieldsShowValidationErrors() throws Exception {
        mvc.perform(post("/config/test")
                        .param("dbType", "MSSQL")
                        .param("host", "")
                        .param("port", "1433")
                        .param("database", "")
                        .param("username", "")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("darf nicht leer sein")));
    }
}
