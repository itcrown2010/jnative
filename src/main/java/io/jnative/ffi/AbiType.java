package io.jnative.ffi;

/**
 * Fixed-width ABI types. Never map Java {@code long} to C {@code long}.
 */
public enum AbiType {
    VOID("void", "void"),
    I8("i8", "int8"),
    U8("i8", "uint8"),
    I16("i16", "int16"),
    U16("i16", "uint16"),
    I32("i32", "int32"),
    U32("i32", "uint32"),
    I64("i64", "int64"),
    U64("i64", "uint64"),
    F32("float", "float"),
    F64("double", "double"),
    PTR("ptr", "pointer"),
    /** Platform size_t / ssize_t width — modeled as i64 on 64-bit targets. */
    SIZE("i64", "size"),
    /** Temporary C string (char*) produced from a JNative String for one call. */
    CSTRING("ptr", "cstring");

    private final String llvm;
    private final String display;

    AbiType(String llvm, String display) {
        this.llvm = llvm;
        this.display = display;
    }

    public String llvm() {
        return llvm;
    }

    public String displayName() {
        return display;
    }
}
