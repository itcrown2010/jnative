package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

public final class VariableSymbol {
    public enum Kind {
        LOCAL,
        PARAMETER,
        FIELD_ALIAS // unused; fields use FieldSymbol
    }

    private final String name;
    private final JType type;
    private final Kind kind;
    private final SourceLocation location;

    public VariableSymbol(String name, JType type, Kind kind, SourceLocation location) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public JType type() {
        return type;
    }

    public Kind kind() {
        return kind;
    }

    public SourceLocation location() {
        return location;
    }
}
