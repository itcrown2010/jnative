package io.jnative.semantic;

/**
 * Typed native function pointer wrapping a {@link FunctionType} (ABI params/return, domain NATIVE).
 * Distinct from {@link NativePtrType} — not a data pointer.
 * @author 张晓达
 */
public final class NativeFunctionPtrType implements JType {
    private final FunctionType functionType;

    public NativeFunctionPtrType(FunctionType functionType) {
        this.functionType = functionType;
    }

    public static NativeFunctionPtrType of(FunctionType functionType) {
        return new NativeFunctionPtrType(functionType);
    }

    public FunctionType functionType() {
        return functionType;
    }

    @Override
    public String displayName() {
        return "NativeFn<" + functionType.displayName() + ">";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType) {
            return true;
        }
        if (!(other instanceof NativeFunctionPtrType nf)) {
            return false;
        }
        return functionType.equals(nf.functionType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NativeFunctionPtrType nf)) {
            return false;
        }
        return functionType.equals(nf.functionType);
    }

    @Override
    public int hashCode() {
        return functionType.hashCode();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
