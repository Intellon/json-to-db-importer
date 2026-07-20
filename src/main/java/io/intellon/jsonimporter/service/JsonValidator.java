package io.intellon.jsonimporter.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Valid means: the content is exactly one complete JSON document (spec section 6).
 * Trailing content after the first document makes it invalid; top-level scalars
 * are valid per RFC 8259. Uses streaming parsing, so large files stay cheap.
 *
 * <p>Pathologically deep nesting cannot blow the stack here: parsing is iterative
 * and Jackson's own {@code StreamReadConstraints} cap the nesting depth, which
 * surfaces as a {@link JacksonException} like any other malformed input.
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
            // First token opened the document, skipChildren() consumes it completely.
            // A second token can therefore only be trailing content -> not exactly one document.
            parser.skipChildren();
            return parser.nextToken() == null;
        } catch (JacksonException e) {
            return false;
        }
    }
}
