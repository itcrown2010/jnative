package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

/**
 * Instance field belonging to a class.
 */
public final class FieldSymbol {
    private final ClassSymbol owner;
    private final String name;
    private final JType type;
    private final int declaredIndex;
    private final SourceLocation location;
    /** LLVM struct GEP index (includes object header at 0). Set by layout builder. */
    private int gepIndex = -1;

    public FieldSymbol(ClassSymbol owner, String name, JType type, int declaredIndex, SourceLocation location) {
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.declaredIndex = declaredIndex;
        this.location = location;
    }

    public ClassSymbol owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public JType type() {
        return type;
    }

    /** Index among fields declared on this class (not GEP index). */
    public int index() {
        return declaredIndex;
    }

    public int gepIndex() {
        return gepIndex;
    }

    public void setGepIndex(int gepIndex) {
        this.gepIndex = gepIndex;
    }

    public SourceLocation location() {
        return location;
    }

    public boolean isReference() {
        return type.isReference();
    }
}
