package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface type symbol — distinct from {@link ClassSymbol}.
 */
public final class InterfaceSymbol implements ReferenceTypeSymbol {
    private final String name;
    private String packageName = "";
    private Visibility visibility = Visibility.PUBLIC;
    private List<String> importTypes = List.of();
    private final SourceLocation location;
    private final boolean builtin;
    private final List<InterfaceSymbol> superInterfaces = new ArrayList<>();
    private final List<ParameterizedType> parameterizedSuperInterfaces = new ArrayList<>();
    private final List<TypeParameterSymbol> typeParameters = new ArrayList<>();
    private final Map<String, MethodSymbol> methodsByName = new LinkedHashMap<>();
    private final List<MethodSymbol> declaredMethods = new ArrayList<>();
    private final List<InterfaceMethodSlot> slots = new ArrayList<>();
    private final Set<ClassSymbol> implementors = new LinkedHashSet<>();
    private FunctionType functionType;
    private int interfaceId = -1;
    private final InterfaceMetadata metadata;

    public InterfaceSymbol(String name, SourceLocation location, boolean builtin) {
        this.name = name;
        this.location = location;
        this.builtin = builtin;
        this.metadata = new InterfaceMetadata(this);
    }

    @Override
    public String name() {
        return name;
    }

    public String packageName() {
        return packageName == null ? "" : packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    public Visibility visibility() {
        return visibility == null ? Visibility.PUBLIC : visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility == null ? Visibility.PUBLIC : visibility;
    }

    public List<String> importTypes() {
        return importTypes == null ? List.of() : importTypes;
    }

    public void setImportTypes(List<String> importTypes) {
        this.importTypes = importTypes == null ? List.of() : List.copyOf(importTypes);
    }

    public String simpleName() {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    @Override
    public SourceLocation location() {
        return location;
    }

    @Override
    public boolean isInterface() {
        return true;
    }

    @Override
    public JType asJType() {
        return asType();
    }

    public InterfaceType asType() {
        return new InterfaceType(this);
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public List<InterfaceSymbol> superInterfaces() {
        return List.copyOf(superInterfaces);
    }

    public void addSuperInterface(InterfaceSymbol iface) {
        if (iface != null && !superInterfaces.contains(iface)) {
            superInterfaces.add(iface);
        }
    }

    public List<ParameterizedType> parameterizedSuperInterfaces() {
        return List.copyOf(parameterizedSuperInterfaces);
    }

    public void setParameterizedSuperInterfaces(List<ParameterizedType> supers) {
        parameterizedSuperInterfaces.clear();
        if (supers != null) {
            parameterizedSuperInterfaces.addAll(supers);
        }
    }

    public List<TypeParameterSymbol> typeParameters() {
        return List.copyOf(typeParameters);
    }

    public void setTypeParameters(List<TypeParameterSymbol> params) {
        typeParameters.clear();
        if (params != null) {
            typeParameters.addAll(params);
        }
    }

    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }

    public boolean addMethod(MethodSymbol method) {
        if (methodsByName.containsKey(method.name())) {
            return false;
        }
        methodsByName.put(method.name(), method);
        declaredMethods.add(method);
        return true;
    }

    public Optional<MethodSymbol> findDeclaredMethod(String methodName) {
        return Optional.ofNullable(methodsByName.get(methodName));
    }

    public Optional<MethodSymbol> findMethod(String methodName) {
        MethodSymbol local = methodsByName.get(methodName);
        if (local != null) {
            return Optional.of(local);
        }
        for (InterfaceSymbol parent : superInterfaces) {
            Optional<MethodSymbol> found = parent.findMethod(methodName);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public List<MethodSymbol> declaredMethods() {
        return List.copyOf(declaredMethods);
    }

    public List<InterfaceMethodSlot> slots() {
        return List.copyOf(slots);
    }

    public void setSlots(List<InterfaceMethodSlot> slots) {
        this.slots.clear();
        this.slots.addAll(slots);
        for (InterfaceMethodSlot slot : slots) {
            slot.method().setInterfaceSlot(slot.slot());
        }
    }

    public FunctionType functionType() {
        return functionType;
    }

    public void setFunctionType(FunctionType functionType) {
        this.functionType = functionType;
    }

    public boolean isFunctional() {
        return functionType != null;
    }

    public int interfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(int interfaceId) {
        this.interfaceId = interfaceId;
        metadata.setInterfaceId(interfaceId);
    }

    public InterfaceMetadata metadata() {
        return metadata;
    }

    public Set<ClassSymbol> implementors() {
        return Set.copyOf(implementors);
    }

    public void addImplementor(ClassSymbol cls) {
        implementors.add(cls);
    }

    /** True if this interface is a subtype of {@code other} (including self). */
    public boolean isSubtypeOf(InterfaceSymbol other) {
        if (other == null) {
            return false;
        }
        if (this == other || name.equals(other.name())) {
            return true;
        }
        for (InterfaceSymbol parent : superInterfaces) {
            if (parent.isSubtypeOf(other)) {
                return true;
            }
        }
        return false;
    }

    /** All interfaces in the transitive extends closure, including self. */
    public List<InterfaceSymbol> transitiveClosure() {
        LinkedHashSet<InterfaceSymbol> out = new LinkedHashSet<>();
        collect(this, out);
        return List.copyOf(out);
    }

    private static void collect(InterfaceSymbol iface, Set<InterfaceSymbol> out) {
        if (!out.add(iface)) {
            return;
        }
        for (InterfaceSymbol parent : iface.superInterfaces) {
            collect(parent, out);
        }
    }
}
