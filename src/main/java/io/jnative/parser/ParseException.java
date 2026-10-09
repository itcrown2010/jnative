package io.jnative.parser;

import io.jnative.diagnostics.SourceLocation;

/**
 * Internal parser control-flow exception. Converted into diagnostics.
 * @author 张晓达
 */
public final class ParseException extends RuntimeException {
    private final SourceLocation location;

    public ParseException(SourceLocation location, String message) {
        super(message);
        this.location = location;
    }

    public SourceLocation location() {
        return location;
    }
}
