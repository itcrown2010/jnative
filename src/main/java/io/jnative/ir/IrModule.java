package io.jnative.ir;

import io.jnative.ffi.NativeFunction;
import io.jnative.ffi.NativeStructLayout;
import io.jnative.layout.ClassLayout;
import io.jnative.semantic.ClassMetadata;
import io.jnative.semantic.InterfaceMetadata;
import io.jnative.vtable.ITable;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IrModule {
    private final String name;
    private final List<IrFunction> functions = new ArrayList<>();
    private final List<IrValue.GlobalString> strings = new ArrayList<>();
    private final Map<String, ClassLayout> classLayouts = new LinkedHashMap<>();
    private final Map<String, NativeStructLayout> nativeStructLayouts = new LinkedHashMap<>();
    private final Map<String, VTable> vtables = new LinkedHashMap<>();
    private final Map<String, ITable> itables = new LinkedHashMap<>();
    private final Map<String, ClassMetadata> classMetadatas = new LinkedHashMap<>();
    private final Map<String, InterfaceMetadata> interfaceMetadatas = new LinkedHashMap<>();
    private final Map<String, ClassMetadata> arrayMetadatas = new LinkedHashMap<>();
    private final Map<String, NativeFunction> nativeDeclarations = new LinkedHashMap<>();
    private final Set<String> nativeLibraries = new LinkedHashSet<>();
    private final List<StaticField> staticFields = new ArrayList<>();
    private final List<StaticFieldInit> staticInits = new ArrayList<>();
    private final IrDebugInfo debugInfo = new IrDebugInfo();

    public record StaticField(String globalName, IrType type, boolean isVolatile, boolean isReference) {
    }

    /** Initializer expression already lowered into a synthetic clinit fragment name, or null. */
    public record StaticFieldInit(String globalName, IrType type, IrValue constantOrNull, String initFunction) {
    }

    public IrModule(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<IrFunction> functions() {
        return functions;
    }

    public List<IrValue.GlobalString> strings() {
        return strings;
    }

    public Map<String, ClassLayout> classLayouts() {
        return classLayouts;
    }

    public Map<String, NativeStructLayout> nativeStructLayouts() {
        return nativeStructLayouts;
    }

    public Map<String, VTable> vtables() {
        return vtables;
    }

    public Map<String, ITable> itables() {
        return itables;
    }

    public Map<String, ClassMetadata> classMetadatas() {
        return classMetadatas;
    }

    public Map<String, InterfaceMetadata> interfaceMetadatas() {
        return interfaceMetadatas;
    }

    public Map<String, ClassMetadata> arrayMetadatas() {
        return arrayMetadatas;
    }

    public Map<String, NativeFunction> nativeDeclarations() {
        return nativeDeclarations;
    }

    public Set<String> nativeLibraries() {
        return nativeLibraries;
    }

    public void addClassLayout(ClassLayout layout) {
        classLayouts.put(layout.className(), layout);
    }

    public void addNativeStructLayout(NativeStructLayout layout) {
        nativeStructLayouts.put(layout.name(), layout);
    }

    public void addVTable(VTable vtable) {
        vtables.put(vtable.owner().name(), vtable);
    }

    public void addITable(ITable itable) {
        itables.put(itable.globalName(), itable);
    }

    public void addInterfaceMetadata(InterfaceMetadata metadata) {
        interfaceMetadatas.put(metadata.globalName(), metadata);
    }

    public void addClassMetadata(ClassMetadata metadata) {
        classMetadatas.put(metadata.globalName(), metadata);
    }

    public void addArrayMetadata(ClassMetadata metadata) {
        arrayMetadatas.put(metadata.globalName(), metadata);
    }

    public void addNativeDeclaration(NativeFunction function) {
        nativeDeclarations.putIfAbsent(function.symbol(), function);
        function.library().ifPresent(nativeLibraries::add);
    }

    public void addFunction(IrFunction function) {
        functions.add(function);
    }

    public void removeFunctions(java.util.Collection<String> names) {
        functions.removeIf(f -> names.contains(f.name()));
    }

    public IrFunction findFunction(String name) {
        for (IrFunction f : functions) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public IrValue.GlobalString addString(String content) {
        String symbol = "str." + strings.size();
        IrValue.GlobalString gs = new IrValue.GlobalString(symbol, content);
        strings.add(gs);
        return gs;
    }

    public List<StaticField> staticFields() {
        return staticFields;
    }

    public List<StaticFieldInit> staticInits() {
        return staticInits;
    }

    public void addStaticField(StaticField field) {
        staticFields.add(field);
    }

    public void addStaticInit(StaticFieldInit init) {
        staticInits.add(init);
    }

    public IrDebugInfo debugInfo() {
        return debugInfo;
    }
}
