package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Declared type parameter on a class, interface, or method.
 */
public final class TypeParameterSymbol {
    private final String name;
    private final ReferenceTypeSymbol ownerType;
    private final MethodSymbol ownerMethod;
    private final int index;
    private final SourceLocation location;
    private final List<JType> bounds = new ArrayList<>();

    public TypeParameterSymbol(
            String name,
            ReferenceTypeSymbol ownerType,
            MethodSymbol ownerMethod,
            int index,
            SourceLocation location
    ) {
        this.name = name;
        this.ownerType = ownerType;
        this.ownerMethod = ownerMethod;
        this.index = index;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public ReferenceTypeSymbol ownerType() {
        return ownerType;
    }

    public MethodSymbol ownerMethod() {
        return ownerMethod;
    }

    public int index() {
        return index;
    }

    public SourceLocation location() {
        return location;
    }

    public List<JType> bounds() {
        return List.copyOf(bounds);
    }

    public void setBounds(List<JType> bounds) {
        this.bounds.clear();
        if (bounds != null) {
            this.bounds.addAll(bounds);
        }
    }

    public void addBound(JType bound) {
        if (bound != null) {
            bounds.add(bound);
        }
    }

    public TypeVariable asType() {
        return new TypeVariable(this);
    }

    /** Erasure of this type variable (first bound, or Object). */
    public JType erasure(ClassSymbol objectClass) {
        if (!bounds.isEmpty()) {
            JType b = bounds.getFirst();
            if (b instanceof ParameterizedType pt) {
                return pt.rawType();
            }
            if (b instanceof TypeVariable tv) {
                return tv.symbol().erasure(objectClass);
            }
            return b;
        }
        return objectClass != null ? objectClass.asType() : ErrorType.INSTANCE;
    }
}
