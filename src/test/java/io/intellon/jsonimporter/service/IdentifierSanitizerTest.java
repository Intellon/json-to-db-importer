package io.intellon.jsonimporter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierSanitizerTest {

    @ParameterizedTest
    @CsvSource({
            "folderxxx,     folderxxx",
            "my-folder,     my_folder",
            "Kunden Daten,  Kunden_Daten",
            "über.ordner,   _ber_ordner",
            "a_b_c,         a_b_c",
            "2024-data,     t_2024_data",
            "'...',         ___",
    })
    void appliesSanitizingRules(String raw, String expected) {
        assertThat(IdentifierSanitizer.sanitize(raw)).isEqualTo(expected);
    }

    @Test
    void truncatesTo128Characters() {
        String raw = "x".repeat(200);
        assertThat(IdentifierSanitizer.sanitize(raw)).hasSize(128);
    }

    @Test
    void prefixThenTruncateStaysWithin128() {
        String raw = "9" + "x".repeat(200);
        String result = IdentifierSanitizer.sanitize(raw);
        assertThat(result).startsWith("t_9").hasSize(128);
    }
}
