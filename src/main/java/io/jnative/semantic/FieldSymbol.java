package io.jnative.semantic;

import io.jnative.ast.Expression;
import io.jnative.diagnostics.SourceLocation;

/**
 * Field belonging to a class (instance or static).
 */
public final class FieldSymbol {
    private final ClassSymbol owner;
    private final String name;
    private final JType type;
    private final int declaredIndex;
    private final SourceLocation location;
    private final boolean isStatic;
    private final boolean isVolatile;
    /** LLVM struct GEP index (includes object header at 0). Set by layout builder. Unused for static. */
    private int gepIndex = -1;
    /** LLVM global symbol for static fields, e.g. {@code jn.static.Main.counter}. */
    private String staticGlobal;
    private Expression initializer;

    public FieldSymbol(ClassSymbol owner, String name, JType type, int declaredIndex, SourceLocation location) {
        this(owner, name, type, declaredIndex, false, false, location);
    }

    public FieldSymbol(
            ClassSymbol owner,
            String name,
            JType type,
            int declaredIndex,
            boolean isStatic,
            boolean isVolatile,
            SourceLocation location
    ) {
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.declaredIndex = declaredIndex;
        this.isStatic = isStatic;
        this.isVolatile = isVolatile;
        this.location = location;
        if (isStatic) {
            this.staticGlobal = "jn.static." + owner.name() + "." + name;
        }
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

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    public String staticGlobal() {
        return staticGlobal;
    }

    public Expression initializer() {
        return initializer;
    }

    public void setInitializer(Expression initializer) {
        this.initializer = initializer;
    }
}
