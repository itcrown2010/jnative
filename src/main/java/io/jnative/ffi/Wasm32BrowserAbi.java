package io.jnative.ffi;

/** wasm32-browser ABI: 32-bit pointers in linear memory. */
public final class Wasm32BrowserAbi implements TargetAbi {
    public static final Wasm32BrowserAbi INSTANCE = new Wasm32BrowserAbi();

    private Wasm32BrowserAbi() {
    }

    @Override
    public int pointerSize() {
        return 4;
    }

    @Override
    public int pointerAlign() {
        return 4;
    }

    @Override
    public AbiType sizeType() {
        return AbiType.I32;
    }

    @Override
    public AbiType mapPrimitiveJavaType(String javaTypeName) {
        return switch (javaTypeName) {
            case "void" -> AbiType.VOID;
            case "boolean", "byte" -> AbiType.I8;
            case "short", "char" -> AbiType.I16;
            case "int" -> AbiType.I32;
            case "long" -> AbiType.I64;
            case "float" -> AbiType.F32;
            case "double" -> AbiType.F64;
            case "NativePtr", "String" -> AbiType.PTR;
            default -> AbiType.PTR;
        };
    }

    @Override
    public int sizeOf(AbiType type) {
        return switch (type) {
            case VOID -> 0;
            case I8, U8 -> 1;
            case I16, U16 -> 2;
            case I32, U32, F32, SIZE -> 4;
            case I64, U64, F64 -> 8;
            case PTR, CSTRING -> pointerSize();
        };
    }

    @Override
    public int alignOf(AbiType type) {
        return switch (type) {
            case VOID -> 1;
            case I8, U8 -> 1;
            case I16, U16 -> 2;
            case I32, U32, F32, SIZE -> 4;
            case I64, U64, F64 -> 8;
            case PTR, CSTRING -> pointerAlign();
        };
    }
}
