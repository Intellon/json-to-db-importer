package io.intellon.jsonimporter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierSanitizerTest {

    @ParameterizedTest
    @CsvSource({
            "folderxxx,     folderxxx",
            "my-folder,     my_folder",
            "Kunden Daten,  Kunden_Daten",
            "über.ordner,   _ber_ordner",
            "a_b_c,         a_b_c",
            "'...',         ___",
    })
    void appliesSanitizingRules(String raw, String expected) {
        assertThat(IdentifierSanitizer.sanitize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "01_Login,      01_Login",
            "2024-data,     2024_data",
            "9,             9",
    })
    void keepsLeadingDigitsBecauseIdentifiersAreAlwaysBracketQuoted(String raw, String expected) {
        assertThat(IdentifierSanitizer.sanitize(raw)).isEqualTo(expected);
    }

    @Test
    void preservesCase() {
        assertThat(IdentifierSanitizer.sanitize("01_Login")).isEqualTo("01_Login");
    }

    @Test
    void truncatesTo128Characters() {
        String raw = "x".repeat(200);
        assertThat(IdentifierSanitizer.sanitize(raw)).hasSize(128);
    }

    @Test
    void truncatesDigitLeadingNameToo() {
        String raw = "9" + "x".repeat(200);
        String result = IdentifierSanitizer.sanitize(raw);
        assertThat(result).startsWith("9x").hasSize(128);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> IdentifierSanitizer.sanitize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leer");
    }

    @Test
    void rejectsEmptyStringBecauseItWouldProduceAnUnusableIdentifier() {
        assertThatThrownBy(() -> IdentifierSanitizer.sanitize(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leer");
    }
}
