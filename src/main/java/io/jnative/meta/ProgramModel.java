package io.jnative.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whole-program compile-time model exposed to processors. */
public final class ProgramModel {
    private final Map<String, ClassModel> typesByName;
    private final String sourceFile;
    private final String targetTriple;

    public ProgramModel(List<ClassModel> types, String sourceFile, String targetTriple) {
        Map<String, ClassModel> map = new LinkedHashMap<>();
        if (types != null) {
            for (ClassModel c : types) {
                map.put(c.simpleName(), c);
                map.put(c.typeId().binaryName(), c);
            }
        }
        this.typesByName = Collections.unmodifiableMap(map);
        this.sourceFile = sourceFile == null ? "" : sourceFile;
        this.targetTriple = targetTriple == null ? "native" : targetTriple;
    }

    public List<ClassModel> types() {
        List<ClassModel> out = new ArrayList<>();
        for (Map.Entry<String, ClassModel> e : typesByName.entrySet()) {
            if (e.getKey().equals(e.getValue().simpleName())) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    public ClassModel findType(String name) {
        return typesByName.get(name);
    }

    public String sourceFile() {
        return sourceFile;
    }

    public String targetTriple() {
        return targetTriple;
    }

    public List<ClassModel> findTypesAnnotatedWith(String annotationSimpleName) {
        List<ClassModel> out = new ArrayList<>();
        for (ClassModel c : types()) {
            if (c.hasAnnotation(annotationSimpleName)) {
                out.add(c);
            }
        }
        return out;
    }

    public List<MethodModel> findMethodsAnnotatedWith(String annotationSimpleName) {
        List<MethodModel> out = new ArrayList<>();
        for (ClassModel c : types()) {
            for (MethodModel m : c.methods()) {
                if (m.hasAnnotation(annotationSimpleName)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    public boolean isSubtype(TypeId type, TypeId superType) {
        if (type == null || superType == null) {
            return false;
        }
        if (type.equals(superType)) {
            return true;
        }
        ClassModel c = findType(type.simpleName());
        if (c == null) {
            return false;
        }
        if (c.superClass() != null && isSubtype(c.superClass(), superType)) {
            return true;
        }
        for (TypeId iface : c.interfaces()) {
            if (isSubtype(iface, superType)) {
                return true;
            }
        }
        return false;
    }
}
