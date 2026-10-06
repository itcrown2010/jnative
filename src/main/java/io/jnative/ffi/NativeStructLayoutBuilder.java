package io.jnative.ffi;

import io.jnative.mangle.NameMangler;
import io.jnative.semantic.JType;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructSymbol;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.VoidType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link NativeStructLayout} using {@link TargetAbi} sizes/alignments.
 */
public final class NativeStructLayoutBuilder {

    private NativeStructLayoutBuilder() {
    }

    public static NativeStructLayout build(NativeStructSymbol symbol, TargetAbi abi) {
        List<NativeStructField> fields = new ArrayList<>();
        long offset = 0;
        int maxAlign = 1;

        for (var fs : symbol.declaredFields()) {
            AbiType abiType = toAbi(fs.type(), abi);
            int size = abi.sizeOf(abiType);
            int align = abi.alignOf(abiType);
            long aligned = alignUp(offset, align);
            if (aligned > offset) {
                fields.add(NativeStructField.padding(offset, (int) (aligned - offset)));
            }
            offset = aligned;
            fields.add(new NativeStructField(
                    fs.name(),
                    offset,
                    size,
                    align,
                    abiType,
                    abiType.llvm(),
                    false));
            fs.setOffset(offset);
            fs.setAbiType(abiType);
            offset += size;
            maxAlign = Math.max(maxAlign, align);
        }

        long size = alignUp(Math.max(offset, 1), Math.max(maxAlign, 1));
        if (offset < size) {
            fields.add(NativeStructField.padding(offset, (int) (size - offset)));
        }
        if (symbol.declaredFields().isEmpty()) {
            size = 1;
            maxAlign = 1;
        }

        return new NativeStructLayout(
                symbol.name(),
                size,
                maxAlign,
                fields,
                NameMangler.llvmNativeStructName(symbol.name()));
    }

    public static AbiType toAbi(JType type, TargetAbi abi) {
        if (type instanceof PrimitiveType p) {
            return abi.mapPrimitiveJavaType(p.displayName());
        }
        if (type instanceof NativePtrType) {
            return AbiType.PTR;
        }
        if (type instanceof NativeStructType ns) {
            // Inline nested struct — callers use layout size, not a single AbiType.
            // Represented as opaque for field ABI tag; layout builder uses nested size.
            NativeStructLayout nested = ns.symbol().layout();
            if (nested == null) {
                throw new IllegalStateException("nested struct layout not ready: " + ns.name());
            }
            return AbiType.PTR; // placeholder unused for nested; see buildNested
        }
        if (type instanceof VoidType) {
            throw new IllegalArgumentException("void cannot be a struct field");
        }
        throw new IllegalArgumentException("unsupported native struct field type: " + type);
    }

    /** Size/align for a field type, including nested NativeStruct. */
    public static int sizeOfField(JType type, TargetAbi abi) {
        if (type instanceof NativeStructType ns) {
            return (int) ns.symbol().layout().size();
        }
        return abi.sizeOf(toAbi(type, abi));
    }

    public static int alignOfField(JType type, TargetAbi abi) {
        if (type instanceof NativeStructType ns) {
            return ns.symbol().layout().alignment();
        }
        return abi.alignOf(toAbi(type, abi));
    }

    public static NativeStructLayout buildWithNested(NativeStructSymbol symbol, TargetAbi abi) {
        List<NativeStructField> fields = new ArrayList<>();
        long offset = 0;
        int maxAlign = 1;

        for (var fs : symbol.declaredFields()) {
            int size = sizeOfField(fs.type(), abi);
            int align = alignOfField(fs.type(), abi);
            AbiType abiType = fs.type() instanceof NativeStructType
                    ? AbiType.PTR // not used for GEP; llvm type comes from nested
                    : toAbi(fs.type(), abi);
            String llvm = fs.type() instanceof NativeStructType ns
                    ? "%" + ns.symbol().layout().llvmTypeName()
                    : abiType.llvm();

            long aligned = alignUp(offset, align);
            if (aligned > offset) {
                fields.add(NativeStructField.padding(offset, (int) (aligned - offset)));
            }
            offset = aligned;
            fields.add(new NativeStructField(
                    fs.name(),
                    offset,
                    size,
                    align,
                    abiType,
                    llvm,
                    false));
            fs.setOffset(offset);
            fs.setAbiType(abiType);
            offset += size;
            maxAlign = Math.max(maxAlign, align);
        }

        long size = alignUp(Math.max(offset, 1), Math.max(maxAlign, 1));
        if (offset < size) {
            fields.add(NativeStructField.padding(offset, (int) (size - offset)));
        }
        if (symbol.declaredFields().isEmpty()) {
            size = 1;
            maxAlign = 1;
        }

        return new NativeStructLayout(
                symbol.name(),
                size,
                maxAlign,
                fields,
                NameMangler.llvmNativeStructName(symbol.name()));
    }

    private static long alignUp(long value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        long mask = alignment - 1L;
        return (value + mask) & ~mask;
    }
}
