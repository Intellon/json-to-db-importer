package io.intellon.jsonimporter.service;

import io.intellon.jsonimporter.model.ImportItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportValidatorTest {

    private ImportItem item(String table, String key) {
        return new ImportItem("C:/x/" + key + ".json", key + ".json", table, key);
    }

    @Test
    void acceptsDistinctKeys() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "a"), item("t1", "b"), item("t2", "a")));
        assertThat(issues).isEmpty();
    }

    @Test
    void rejectsEmptyAndWhitespaceKeys() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", ""), item("t1", "   "), item("t1", "ok")));
        assertThat(issues).containsOnlyKeys(0, 1);
    }

    @Test
    void flagsCaseInsensitiveConflictsOnBothRows() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "Foo"), item("t1", "foo"), item("t1", "bar")));
        assertThat(issues).containsOnlyKeys(0, 1);
    }

    @Test
    void sameKeyInDifferentTablesIsNoConflict() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "foo"), item("t2", "foo")));
        assertThat(issues).isEmpty();
    }

    @Test
    void comparesTrimmedKeys() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "foo "), item("t1", " foo")));
        assertThat(issues).containsOnlyKeys(0, 1);
    }

    @Test
    void flagsEveryRowOfAThreeWayConflict() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "foo"), item("t1", "FOO"), item("t1", "Foo")));

        assertThat(issues).containsOnlyKeys(0, 1, 2);
        // Die erste Zeile wird von jeder weiteren Kollision erneut markiert.
        assertThat(issues.get(0)).hasSize(2);
        assertThat(issues.get(1)).hasSize(1);
        assertThat(issues.get(2)).hasSize(1);
    }

    @Test
    void treatsNullKeyLikeAnEmptyKey() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                new ImportItem("C:/x/a.json", "a.json", "t1", null)));
        assertThat(issues).containsOnlyKeys(0);
        assertThat(issues.get(0)).containsExactly("Key darf nicht leer sein");
    }

    @Test
    void conflictMessageNamesTableAndKey() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("kunden", "Foo"), item("kunden", "foo")));
        // Beide Zeilen bekommen dieselbe Meldung; zitiert wird die Schreibweise der Kollisionszeile.
        assertThat(issues.get(0)).singleElement().asString()
                .contains("Key-Konflikt")
                .contains("case-insensitiv")
                .contains("[kunden]")
                .contains("'foo'");
        assertThat(issues.get(1)).isEqualTo(issues.get(0));
    }

    @Test
    void tableNamesAreNotConfusedByKeysContainingSeparators() {
        Map<Integer, List<String>> issues = ImportValidator.validate(List.of(
                item("t1", "a|b"), item("t1|a", "b")));
        assertThat(issues).isEmpty();
    }
}
