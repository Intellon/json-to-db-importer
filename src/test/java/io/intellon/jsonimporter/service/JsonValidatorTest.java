package io.intellon.jsonimporter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JsonValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"a\":1}",
            "[1,2,3]",
            "{\"nested\":{\"list\":[{\"x\":1}]}}",
            "42",
            "\"text\"",
            "true",
            "null",
            "  {\"a\":1}  ",
    })
    void acceptsSingleCompleteJsonDocument(String content) {
        assertThat(JsonValidator.isValid(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "{\"a\":1} MÜLL",
            "{\"a\":1}{\"b\":2}",
            "{\"a\":1",
            "{a:1}",
            "[1,2,",
            "123abc",
    })
    void rejectsInvalidOrTrailingContent(String content) {
        assertThat(JsonValidator.isValid(content)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(JsonValidator.isValid(null)).isFalse();
    }
}
