package io.jnative.vtable;

import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.InterfaceMethodSlot;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.MethodSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds per-interface ITables for a concrete class.
 */
public final class ITableBuilder {

    private ITableBuilder() {
    }

    public static List<ITable> buildAll(ClassSymbol classSymbol) {
        List<ITable> tables = new ArrayList<>();
        for (InterfaceSymbol iface : classSymbol.transitiveInterfaces()) {
            tables.add(build(classSymbol, iface));
        }
        return tables;
    }

    public static ITable build(ClassSymbol classSymbol, InterfaceSymbol iface) {
        List<ITableEntry> entries = new ArrayList<>();
        for (InterfaceMethodSlot slot : iface.slots()) {
            MethodSymbol impl = resolveImplementation(classSymbol, slot.method())
                    .orElseThrow(() -> new IllegalStateException(
                            classSymbol.name() + " missing implementation for "
                                    + iface.name() + "." + slot.name()));
            entries.add(new ITableEntry(slot.slot(), impl));
        }
        return new ITable(classSymbol.name(), iface, entries);
    }

    public static Optional<MethodSymbol> resolveImplementation(ClassSymbol cls, MethodSymbol required) {
        Optional<MethodSymbol> found = cls.findMethod(required.name());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MethodSymbol ms = found.get();
        if (ms.isStatic() || ms.isAbstract()) {
            return Optional.empty();
        }
        if (!signaturesCompatible(ms, required)) {
            return Optional.empty();
        }
        return Optional.of(ms);
    }

    private static boolean signaturesCompatible(MethodSymbol impl, MethodSymbol required) {
        if (impl.parameterTypes().size() != required.parameterTypes().size()) {
            return false;
        }
        for (int i = 0; i < required.parameterTypes().size(); i++) {
            if (!erasedCompatible(required.parameterTypes().get(i), impl.parameterTypes().get(i))) {
                return false;
            }
        }
        return erasedCompatible(required.returnType(), impl.returnType())
                || required.returnType().isAssignableFrom(impl.returnType());
    }

    /**
     * Reference generics erase type variables to managed refs; allow concrete impl types
     * (e.g. {@code String map(String)} vs {@code T map(T)}).
     */
    private static boolean erasedCompatible(io.jnative.semantic.JType required, io.jnative.semantic.JType impl) {
        if (required instanceof io.jnative.semantic.TypeVariable) {
            return impl.isReference()
                    || impl instanceof io.jnative.semantic.TypeVariable
                    || impl instanceof io.jnative.semantic.PrimitiveType;
        }
        if (impl instanceof io.jnative.semantic.TypeVariable) {
            return required.isReference() || required instanceof io.jnative.semantic.PrimitiveType;
        }
        if (typeNameEquals(required, impl)) {
            return true;
        }
        return required.isAssignableFrom(impl) || impl.isAssignableFrom(required);
    }

    private static boolean typeNameEquals(io.jnative.semantic.JType a, io.jnative.semantic.JType b) {
        if (a == b || a.equals(b)) {
            return true;
        }
        if (a instanceof io.jnative.semantic.ClassType ca && b instanceof io.jnative.semantic.ClassType cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof io.jnative.semantic.InterfaceType ia && b instanceof io.jnative.semantic.InterfaceType ib) {
            return ia.name().equals(ib.name());
        }
        if (a instanceof io.jnative.semantic.PrimitiveType && b instanceof io.jnative.semantic.PrimitiveType) {
            return a == b;
        }
        if (a instanceof io.jnative.semantic.ArrayType aa && b instanceof io.jnative.semantic.ArrayType ab) {
            return typeNameEquals(aa.elementType(), ab.elementType());
        }
        if (a instanceof io.jnative.semantic.ParameterizedType pa
                && b instanceof io.jnative.semantic.ParameterizedType pb) {
            return pa.raw().name().equals(pb.raw().name());
        }
        if (a instanceof io.jnative.semantic.ParameterizedType pa) {
            return typeNameEquals(pa.rawType(), b);
        }
        if (b instanceof io.jnative.semantic.ParameterizedType pb) {
            return typeNameEquals(a, pb.rawType());
        }
        return false;
    }
}
