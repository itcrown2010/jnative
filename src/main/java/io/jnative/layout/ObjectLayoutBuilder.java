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
    public static final long HEADER_SIZE = 8;
    public static final int HEADER_ALIGNMENT = 8;
    public static final int POINTER_SIZE = 8;

    /** @deprecated use {@link #HEADER_SIZE} */
    public static final long DEFAULT_HEADER_SIZE = HEADER_SIZE;
    public static final int DEFAULT_HEADER_ALIGNMENT = HEADER_ALIGNMENT;

    private ObjectLayoutBuilder() {
    }

    public static ClassLayout build(ClassSymbol classSymbol) {
        return build(classSymbol, HEADER_SIZE, HEADER_ALIGNMENT);
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
            offset = parent.instanceSize();
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
            case ClassType ignored -> POINTER_SIZE;
            case ArrayType ignored -> POINTER_SIZE;
            case NativePtrType ignored -> POINTER_SIZE;
            case NativeFunctionPtrType ignored -> POINTER_SIZE;
            case FunctionType ignored -> POINTER_SIZE;
            case NativeStructType ns -> (int) ns.symbol().layout().size();
            case NullType ignored -> POINTER_SIZE;
            case VoidType ignored -> 0;
            case ErrorType ignored -> POINTER_SIZE;
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
            case ClassType ignored -> POINTER_SIZE;
            case ArrayType ignored -> POINTER_SIZE;
            case NativePtrType ignored -> POINTER_SIZE;
            case NativeFunctionPtrType ignored -> POINTER_SIZE;
            case FunctionType ignored -> POINTER_SIZE;
            case NativeStructType ns -> ns.symbol().layout().alignment();
            case NullType ignored -> POINTER_SIZE;
            case VoidType ignored -> 1;
            case ErrorType ignored -> POINTER_SIZE;
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
