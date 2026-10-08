package io.jnative.layout;

import io.jnative.mangle.NameMangler;
import io.jnative.semantic.ArrayType;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.FunctionType;
import io.jnative.semantic.JType;
import io.jnative.semantic.NativeFunctionPtrType;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.NullType;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.VoidType;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes aligned object layouts with a vtable header and inherited field prefix.
 */
public final class ObjectLayoutBuilder {

    /** Object header: ClassMetadata* (metadata holds vtable* for dispatch). */
    public static long HEADER_SIZE() {
        return LayoutConfig.headerSize();
    }

    public static int HEADER_ALIGNMENT() {
        return LayoutConfig.pointerAlign();
    }

    public static int POINTER_SIZE() {
        return LayoutConfig.pointerSize();
    }

    /** @deprecated use {@link #HEADER_SIZE()} */
    public static final long DEFAULT_HEADER_SIZE = 8;
    public static final int DEFAULT_HEADER_ALIGNMENT = 8;

    /** Compatibility constants — prefer {@link LayoutConfig}. */
    public static final long HEADER_SIZE = 8;
    public static final int HEADER_ALIGNMENT = 8;
    public static final int POINTER_SIZE = 8;

    private ObjectLayoutBuilder() {
    }

    public static ClassLayout build(ClassSymbol classSymbol) {
        return build(classSymbol, LayoutConfig.headerSize(), LayoutConfig.pointerAlign());
    }

    public static ClassLayout build(ClassSymbol classSymbol, long headerSize, int headerAlignment) {
        List<FieldLayout> layouts = new ArrayList<>();
        long offset;
        int gepIndex;
        int maxAlign = headerSize > 0 ? headerAlignment : 1;

        ClassSymbol superClass = classSymbol.superClass().orElse(null);
        if (superClass != null && superClass.layout() != null) {
            ClassLayout parent = superClass.layout();
            layouts.addAll(parent.fields());
            for (FieldLayout fl : parent.fields()) {
                maxAlign = Math.max(maxAlign, fl.alignment());
            }
            // Append after the last parent field byte (not padded instanceSize).
            // LLVM flat structs {…parent fields…, child} do not insert parent's
            // trailing alignment padding between parent and child members; debug
            // catalogs and GC offsets must match that GEP layout.
            if (parent.fields().isEmpty()) {
                offset = parent.headerSize();
            } else {
                FieldLayout last = parent.fields().get(parent.fields().size() - 1);
                offset = last.offset() + last.size();
            }
            gepIndex = parent.fieldGepBaseIndex() + parent.fields().size();
        } else {
            offset = headerSize;
            gepIndex = headerSize > 0 ? 1 : 0;
        }

        for (FieldSymbol field : classSymbol.declaredFields()) {
            if (field.isStatic()) {
                continue;
            }
            int size = sizeOf(field.type());
            int align = alignmentOf(field.type());
            offset = alignUp(offset, align);
            field.setGepIndex(gepIndex);
            layouts.add(new FieldLayout(
                    field.name(),
                    gepIndex,
                    offset,
                    size,
                    align,
                    field.isReference(),
                    llvmTypeOf(field.type())
            ));
            offset += size;
            maxAlign = Math.max(maxAlign, align);
            gepIndex++;
        }

        // Ensure inherited FieldSymbols keep gep indices from parent layout.
        if (superClass != null) {
            for (FieldLayout fl : layouts) {
                superClass.findField(fl.name()).ifPresent(fs -> {
                    if (fs.gepIndex() < 0) {
                        fs.setGepIndex(fl.index());
                    }
                });
            }
        }

        long instanceSize = alignUp(Math.max(offset, headerSize > 0 ? headerSize : 1), Math.max(maxAlign, 1));
        if (layouts.isEmpty() && headerSize == 0) {
            instanceSize = 1;
            maxAlign = 1;
        }

        return new ClassLayout(
                classSymbol.name(),
                headerSize,
                headerAlignment,
                layouts,
                instanceSize,
                maxAlign,
                NameMangler.llvmStructName(classSymbol.name())
        );
    }

    public static int sizeOf(JType type) {
        return switch (type) {
            case PrimitiveType p -> switch (p) {
                case BOOLEAN, BYTE -> 1;
                case CHAR, SHORT -> 2;
                case INT, FLOAT -> 4;
                case LONG, DOUBLE -> 8;
            };
            case ClassType ignored -> LayoutConfig.pointerSize();
            case io.jnative.semantic.InterfaceType ignored -> LayoutConfig.pointerSize();
            case io.jnative.semantic.ParameterizedType ignored -> LayoutConfig.pointerSize();
            case io.jnative.semantic.TypeVariable ignored -> LayoutConfig.pointerSize();
            case io.jnative.semantic.WildcardType ignored -> LayoutConfig.pointerSize();
            case ArrayType ignored -> LayoutConfig.pointerSize();
            case NativePtrType ignored -> LayoutConfig.pointerSize();
            case NativeFunctionPtrType ignored -> LayoutConfig.pointerSize();
            case FunctionType ignored -> LayoutConfig.pointerSize();
            case NativeStructType ns -> (int) ns.symbol().layout().size();
            case NullType ignored -> LayoutConfig.pointerSize();
            case VoidType ignored -> 0;
            case ErrorType ignored -> LayoutConfig.pointerSize();
        };
    }

    public static int alignmentOf(JType type) {
        return switch (type) {
            case PrimitiveType p -> switch (p) {
                case BOOLEAN, BYTE -> 1;
                case CHAR, SHORT -> 2;
                case INT, FLOAT -> 4;
                case LONG, DOUBLE -> 8;
            };
            case ClassType ignored -> LayoutConfig.pointerAlign();
            case io.jnative.semantic.InterfaceType ignored -> LayoutConfig.pointerAlign();
            case io.jnative.semantic.ParameterizedType ignored -> LayoutConfig.pointerAlign();
            case io.jnative.semantic.TypeVariable ignored -> LayoutConfig.pointerAlign();
            case io.jnative.semantic.WildcardType ignored -> LayoutConfig.pointerAlign();
            case ArrayType ignored -> LayoutConfig.pointerAlign();
            case NativePtrType ignored -> LayoutConfig.pointerAlign();
            case NativeFunctionPtrType ignored -> LayoutConfig.pointerAlign();
            case FunctionType ignored -> LayoutConfig.pointerAlign();
            case NativeStructType ns -> ns.symbol().layout().alignment();
            case NullType ignored -> LayoutConfig.pointerAlign();
            case VoidType ignored -> 1;
            case ErrorType ignored -> LayoutConfig.pointerAlign();
        };
    }

    public static String llvmTypeOf(JType type) {
        return switch (type) {
            case PrimitiveType p -> switch (p) {
                case BOOLEAN -> "i1";
                case BYTE -> "i8";
                case CHAR, SHORT -> "i16";
                case INT -> "i32";
                case LONG -> "i64";
                case FLOAT -> "float";
                case DOUBLE -> "double";
            };
            case ClassType ignored -> "ptr";
            case io.jnative.semantic.InterfaceType ignored -> "ptr";
            case io.jnative.semantic.ParameterizedType ignored -> "ptr";
            case io.jnative.semantic.TypeVariable ignored -> "ptr";
            case io.jnative.semantic.WildcardType ignored -> "ptr";
            case ArrayType ignored -> "ptr";
            case NativePtrType ignored -> "ptr";
            case NativeFunctionPtrType ignored -> "ptr";
            case FunctionType ignored -> "ptr";
            case NativeStructType ns -> "%" + ns.symbol().layout().llvmTypeName();
            case NullType ignored -> "ptr";
            case VoidType ignored -> "void";
            case ErrorType ignored -> "i32";
        };
    }

    private static long alignUp(long value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        long mask = alignment - 1L;
        return (value + mask) & ~mask;
    }
}
