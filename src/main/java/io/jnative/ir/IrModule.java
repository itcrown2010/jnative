package io.jnative.ir;

import io.jnative.layout.ClassLayout;
import io.jnative.semantic.ClassMetadata;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IrModule {
    private final String name;
    private final List<IrFunction> functions = new ArrayList<>();
    private final List<IrValue.GlobalString> strings = new ArrayList<>();
    private final Map<String, ClassLayout> classLayouts = new LinkedHashMap<>();
    private final Map<String, VTable> vtables = new LinkedHashMap<>();
    private final Map<String, ClassMetadata> classMetadatas = new LinkedHashMap<>();
    private final Map<String, ClassMetadata> arrayMetadatas = new LinkedHashMap<>();

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

    public Map<String, VTable> vtables() {
        return vtables;
    }

    public Map<String, ClassMetadata> classMetadatas() {
        return classMetadatas;
    }

    public Map<String, ClassMetadata> arrayMetadatas() {
        return arrayMetadatas;
    }

    public void addClassLayout(ClassLayout layout) {
        classLayouts.put(layout.className(), layout);
    }

    public void addVTable(VTable vtable) {
        vtables.put(vtable.owner().name(), vtable);
    }

    public void addClassMetadata(ClassMetadata metadata) {
        classMetadatas.put(metadata.globalName(), metadata);
    }

    public void addArrayMetadata(ClassMetadata metadata) {
        arrayMetadatas.put(metadata.globalName(), metadata);
    }

    public void addFunction(IrFunction function) {
        functions.add(function);
    }

    public IrValue.GlobalString addString(String content) {
        String symbol = "str." + strings.size();
        IrValue.GlobalString gs = new IrValue.GlobalString(symbol, content);
        strings.add(gs);
        return gs;
    }
}
