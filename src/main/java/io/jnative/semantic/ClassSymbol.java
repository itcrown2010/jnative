package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.layout.ClassLayout;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Symbol for a class declaration, including inheritance, layout, and vtable.
 */
public final class ClassSymbol {
    private final String name;
    private final SourceLocation location;
    private final Map<String, FieldSymbol> declaredFields = new LinkedHashMap<>();
    private final Map<String, MethodSymbol> methodsByName = new LinkedHashMap<>();
    private final List<MethodSymbol> declaredMethods = new ArrayList<>();
    private final List<ConstructorSymbol> constructors = new ArrayList<>();
    private ClassLayout layout;
    private ClassSymbol superClass;
    private VTable vtable;
    private ClassMetadata metadata;
    private final boolean builtin;

    public ClassSymbol(String name, SourceLocation location, boolean builtin) {
        this.name = name;
        this.location = location;
        this.builtin = builtin;
        this.metadata = new ClassMetadata(this);
    }

    public String name() {
        return name;
    }

    public SourceLocation location() {
        return location;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public ClassType asType() {
        return new ClassType(this);
    }

    public Optional<ClassSymbol> superClass() {
        return Optional.ofNullable(superClass);
    }

    public void setSuperClass(ClassSymbol superClass) {
        this.superClass = superClass;
        metadata.setSuperMetadata(superClass != null ? superClass.metadata() : null);
    }

    public boolean isSubtypeOf(ClassSymbol other) {
        if (other == null) {
            return false;
        }
        ClassSymbol cursor = this;
        while (cursor != null) {
            if (cursor == other || cursor.name().equals(other.name())) {
                return true;
            }
            cursor = cursor.superClass;
        }
        return false;
    }

    public boolean addField(FieldSymbol field) {
        if (declaredFields.containsKey(field.name())) {
            return false;
        }
        // Hide inherited field with same name? Java allows shadowing in some cases;
        // 0.3: reject duplicate with inherited name for clarity.
        if (superClass != null && superClass.findField(field.name()).isPresent()) {
            return false;
        }
        declaredFields.put(field.name(), field);
        return true;
    }

    public Optional<FieldSymbol> findField(String fieldName) {
        FieldSymbol local = declaredFields.get(fieldName);
        if (local != null) {
            return Optional.of(local);
        }
        if (superClass != null) {
            return superClass.findField(fieldName);
        }
        return Optional.empty();
    }

    /** Fields declared directly on this class. */
    public List<FieldSymbol> fields() {
        return List.copyOf(declaredFields.values());
    }

    public List<FieldSymbol> declaredFields() {
        return fields();
    }

    public boolean addMethod(MethodSymbol method) {
        if (methodsByName.containsKey(method.name())) {
            return false;
        }
        methodsByName.put(method.name(), method);
        declaredMethods.add(method);
        return true;
    }

    public Optional<MethodSymbol> findMethod(String methodName) {
        MethodSymbol local = methodsByName.get(methodName);
        if (local != null) {
            return Optional.of(local);
        }
        if (superClass != null) {
            return superClass.findMethod(methodName);
        }
        return Optional.empty();
    }

    public Optional<MethodSymbol> findDeclaredMethod(String methodName) {
        return Optional.ofNullable(methodsByName.get(methodName));
    }

    public List<MethodSymbol> methods() {
        return List.copyOf(declaredMethods);
    }

    public List<MethodSymbol> declaredMethods() {
        return methods();
    }

    public void addConstructor(ConstructorSymbol ctor) {
        constructors.add(ctor);
    }

    public List<ConstructorSymbol> constructors() {
        return List.copyOf(constructors);
    }

    public ClassLayout layout() {
        return layout;
    }

    public void setLayout(ClassLayout layout) {
        this.layout = layout;
        metadata.setLayout(layout);
    }

    public VTable vtable() {
        return vtable;
    }

    public void setVtable(VTable vtable) {
        this.vtable = vtable;
        metadata.setVtable(vtable);
    }

    public ClassMetadata metadata() {
        return metadata;
    }
}
