package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;
import java.util.Objects;

public final class MethodSymbol {
    private final ClassSymbol owner;
    private final String name;
    private final JType returnType;
    private final List<ParameterSymbol> parameters;
    private final boolean isStatic;
    private final boolean isMain;
    private final boolean isVirtual;
    private final String mangledName;
    private final SourceLocation location;
    private Integer vtableSlot;
    private MethodSymbol overrides;

    public MethodSymbol(
            ClassSymbol owner,
            String name,
            JType returnType,
            List<ParameterSymbol> parameters,
            boolean isStatic,
            boolean isMain,
            String mangledName,
            SourceLocation location
    ) {
        this.owner = owner;
        this.name = name;
        this.returnType = returnType;
        this.parameters = List.copyOf(parameters);
        this.isStatic = isStatic;
        this.isMain = isMain;
        this.isVirtual = !isStatic && !isMain;
        this.mangledName = mangledName;
        this.location = location;
    }

    public ClassSymbol owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public JType returnType() {
        return returnType;
    }

    public List<ParameterSymbol> parameters() {
        return parameters;
    }

    public List<JType> parameterTypes() {
        return parameters.stream().map(ParameterSymbol::type).toList();
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isMain() {
        return isMain;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public String mangledName() {
        return mangledName;
    }

    public SourceLocation location() {
        return location;
    }

    public Integer vtableSlot() {
        return vtableSlot;
    }

    public void setVtableSlot(Integer vtableSlot) {
        this.vtableSlot = vtableSlot;
    }

    public MethodSymbol overrides() {
        return overrides;
    }

    public void setOverrides(MethodSymbol overrides) {
        this.overrides = overrides;
    }

    public boolean sameSignatureAs(MethodSymbol other) {
        if (!Objects.equals(returnType, other.returnType)) {
            return false;
        }
        List<JType> a = parameterTypes();
        List<JType> b = other.parameterTypes();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!signatureTypeEquals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean signatureTypeEquals(JType a, JType b) {
        if (a == b) {
            return true;
        }
        if (a instanceof ClassType ca && b instanceof ClassType cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
            return signatureTypeEquals(aa.elementType(), ab.elementType());
        }
        return a.equals(b);
    }
}
