package io.jnative.semantic;

/**
 * Resolved Java type used by semantic analysis and lowering.
 * Primitive/reference/null/void are first-class; never encode types as bare Strings.
 */
public sealed interface JType
        permits PrimitiveType, ClassType, InterfaceType, ArrayType, NullType, VoidType, ErrorType,
        NativePtrType, NativeStructType, FunctionType, NativeFunctionPtrType,
        TypeVariable, ParameterizedType, WildcardType {

    String displayName();

    boolean isPrimitive();

    boolean isReference();

    boolean isNumeric();

    boolean isInteger();

    boolean isAssignableFrom(JType other);

    static JType binaryArithmeticResult(JType left, JType right) {
        if (left instanceof ErrorType || right instanceof ErrorType) {
            return ErrorType.INSTANCE;
        }
        if (!(left.isNumeric() && right.isNumeric())) {
            return ErrorType.INSTANCE;
        }
        if (left == PrimitiveType.DOUBLE || right == PrimitiveType.DOUBLE) {
            return PrimitiveType.DOUBLE;
        }
        if (left == PrimitiveType.LONG || right == PrimitiveType.LONG) {
            return PrimitiveType.LONG;
        }
        return PrimitiveType.INT;
    }
}
