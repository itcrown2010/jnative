package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.layout.ClassLayout;
import io.jnative.vtable.ITable;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Symbol for a class declaration, including inheritance, layout, vtable, and itables.
 */
public final class ClassSymbol implements ReferenceTypeSymbol {
    private final String name;
    private String packageName = "";
    private Visibility visibility = Visibility.PUBLIC;
    private List<String> importTypes = List.of();
    private final SourceLocation location;
    private final Map<String, FieldSymbol> declaredFields = new LinkedHashMap<>();
    /** Overload sets keyed by simple method name (parameter arity/types distinguish members). */
    private final Map<String, List<MethodSymbol>> methodsByName = new LinkedHashMap<>();
    private final List<MethodSymbol> declaredMethods = new ArrayList<>();
    private final List<ConstructorSymbol> constructors = new ArrayList<>();
    private final List<InterfaceSymbol> implementedInterfaces = new ArrayList<>();
    private final List<ParameterizedType> implementedParameterizedInterfaces = new ArrayList<>();
    private final List<TypeParameterSymbol> typeParameters = new ArrayList<>();
    private final List<ITable> itables = new ArrayList<>();
    private ClassLayout layout;
    private ClassSymbol superClass;
    private ParameterizedType parameterizedSuper;
    private VTable vtable;
    private ClassMetadata metadata;
    private FunctionType functionType;
    private final boolean builtin;
    private boolean isAbstract;
    private boolean isFinal;
    /** Basic language enum (managed objects; not java.lang.Enum subclass). */
    private boolean isEnum;
    private List<String> enumConstants = List.of();
    /** True if this is a compiler-generated specialization (e.g. Box$P_i32). */
    private boolean specialized;
    private ClassSymbol genericOrigin;
    private List<JType> specializationArgs = List.of();

    public ClassSymbol(String name, SourceLocation location, boolean builtin) {
        this.name = name;
        this.location = location;
        this.builtin = builtin;
        this.metadata = new ClassMetadata(this);
    }

    @Override
    public String name() {
        return name;
    }

    /** Language package (empty = default). Not a package-manager coordinate. */
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
        return false;
    }

    @Override
    public JType asJType() {
        return asType();
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public void setEnum(boolean isEnum) {
        this.isEnum = isEnum;
    }

    public List<String> enumConstants() {
        return enumConstants;
    }

    public void setEnumConstants(List<String> enumConstants) {
        this.enumConstants = enumConstants == null ? List.of() : List.copyOf(enumConstants);
    }

    public int enumOrdinal(String constantName) {
        return enumConstants.indexOf(constantName);
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

    public ParameterizedType parameterizedSuper() {
        return parameterizedSuper;
    }

    public void setParameterizedSuper(ParameterizedType parameterizedSuper) {
        this.parameterizedSuper = parameterizedSuper;
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

    public boolean isSpecialized() {
        return specialized;
    }

    public void setSpecialized(boolean specialized) {
        this.specialized = specialized;
    }

    public ClassSymbol genericOrigin() {
        return genericOrigin;
    }

    public void setGenericOrigin(ClassSymbol genericOrigin) {
        this.genericOrigin = genericOrigin;
    }

    public List<JType> specializationArgs() {
        return specializationArgs;
    }

    public void setSpecializationArgs(List<JType> args) {
        this.specializationArgs = args == null ? List.of() : List.copyOf(args);
    }

    public List<InterfaceSymbol> implementedInterfaces() {
        return List.copyOf(implementedInterfaces);
    }

    public List<ParameterizedType> implementedParameterizedInterfaces() {
        return List.copyOf(implementedParameterizedInterfaces);
    }

    public void setImplementedParameterizedInterfaces(List<ParameterizedType> ifaces) {
        implementedParameterizedInterfaces.clear();
        if (ifaces != null) {
            implementedParameterizedInterfaces.addAll(ifaces);
        }
    }

    public void setImplementedInterfaces(List<InterfaceSymbol> ifaces) {
        implementedInterfaces.clear();
        LinkedHashSet<InterfaceSymbol> seen = new LinkedHashSet<>();
        for (InterfaceSymbol iface : ifaces) {
            if (iface != null && seen.add(iface)) {
                implementedInterfaces.add(iface);
            }
        }
    }

    public void addImplementedInterface(InterfaceSymbol iface) {
        if (iface != null && !implementedInterfaces.contains(iface)) {
            implementedInterfaces.add(iface);
        }
    }

    /** Direct + transitive interfaces. */
    public List<InterfaceSymbol> transitiveInterfaces() {
        LinkedHashSet<InterfaceSymbol> out = new LinkedHashSet<>();
        ClassSymbol cursor = this;
        while (cursor != null) {
            for (InterfaceSymbol iface : cursor.implementedInterfaces) {
                out.addAll(iface.transitiveClosure());
            }
            cursor = cursor.superClass;
        }
        return List.copyOf(out);
    }

    public boolean implementsInterface(InterfaceSymbol iface) {
        if (iface == null) {
            return false;
        }
        for (InterfaceSymbol t : transitiveInterfaces()) {
            if (t == iface || t.name().equals(iface.name())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSubtypeOf(ClassSymbol other) {
        if (other == null) {
            return false;
        }
        if ("Object".equals(other.name()) && !"Object".equals(name)) {
            return true;
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

    public List<FieldSymbol> fields() {
        return List.copyOf(declaredFields.values());
    }

    public List<FieldSymbol> declaredFields() {
        return fields();
    }

    public boolean addMethod(MethodSymbol method) {
        List<MethodSymbol> overloads = methodsByName.computeIfAbsent(method.name(), k -> new ArrayList<>());
        for (MethodSymbol existing : overloads) {
            if (existing.sameParameterSignatureAs(method)) {
                return false;
            }
        }
        overloads.add(method);
        declaredMethods.add(method);
        return true;
    }

    /**
     * First overload with this name in the inheritance chain (legacy single-match helpers).
     * Prefer {@link #findMethods(String)} + {@link CallableResolver} for calls.
     */
    public Optional<MethodSymbol> findMethod(String methodName) {
        List<MethodSymbol> local = methodsByName.get(methodName);
        if (local != null && !local.isEmpty()) {
            return Optional.of(local.get(0));
        }
        if (superClass != null) {
            return superClass.findMethod(methodName);
        }
        return Optional.empty();
    }

    public Optional<MethodSymbol> findMethod(String methodName, List<JType> parameterTypes) {
        for (MethodSymbol m : findMethods(methodName)) {
            if (m.sameParameterTypes(parameterTypes)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public List<MethodSymbol> findMethods(String methodName) {
        List<MethodSymbol> out = new ArrayList<>();
        List<MethodSymbol> local = methodsByName.get(methodName);
        if (local != null) {
            out.addAll(local);
        }
        if (superClass != null) {
            for (MethodSymbol parent : superClass.findMethods(methodName)) {
                boolean hidden = false;
                for (MethodSymbol child : out) {
                    if (child.sameParameterSignatureAs(parent)) {
                        hidden = true;
                        break;
                    }
                }
                if (!hidden) {
                    out.add(parent);
                }
            }
        }
        return out;
    }

    public Optional<MethodSymbol> findDeclaredMethod(String methodName) {
        List<MethodSymbol> local = methodsByName.get(methodName);
        if (local == null || local.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(local.get(0));
    }

    public List<MethodSymbol> findDeclaredMethods(String methodName) {
        List<MethodSymbol> local = methodsByName.get(methodName);
        if (local == null) {
            return List.of();
        }
        return List.copyOf(local);
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

    public List<ITable> itables() {
        return List.copyOf(itables);
    }

    public void setItables(List<ITable> itables) {
        this.itables.clear();
        this.itables.addAll(itables);
        metadata.setItables(this.itables);
    }

    public ClassMetadata metadata() {
        return metadata;
    }

    public FunctionType functionType() {
        return functionType;
    }

    public void setFunctionType(FunctionType functionType) {
        this.functionType = functionType;
    }
}
