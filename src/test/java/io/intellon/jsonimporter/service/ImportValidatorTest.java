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
}
