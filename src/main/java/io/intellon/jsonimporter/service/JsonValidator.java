package io.intellon.jsonimporter.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Valid means: the content is exactly one complete JSON document (spec section 6).
 * Trailing content after the first document makes it invalid; top-level scalars
 * are valid per RFC 8259. Uses streaming parsing, so large files stay cheap.
 */
public final class JsonValidator {

    private static final JsonFactory FACTORY = new JsonFactory();

    private JsonValidator() {
    }

    public static boolean isValid(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), content)) {
            if (parser.nextToken() == null) {
                return false;
            }
            parser.skipChildren();
            return parser.nextToken() == null;
        } catch (JacksonException e) {
            return false;
        }
    }
}
