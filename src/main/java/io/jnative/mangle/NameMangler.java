package io.jnative.mangle;

import io.jnative.semantic.ArrayType;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.FunctionType;
import io.jnative.semantic.InterfaceType;
import io.jnative.semantic.JType;
import io.jnative.semantic.ParameterizedType;
import io.jnative.semantic.TypeVariable;
import io.jnative.semantic.WildcardType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.NativeFunctionPtrType;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.NullType;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.VoidType;

import java.util.List;
import java.util.stream.Collectors;

public final class NameMangler {

    private NameMangler() {
    }

    public static String mangleMethod(String className, String methodName, List<JType> parameterTypes, boolean isMain) {
        if (isMain) {
            return "jn_main";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("_JN_").append(sanitize(className)).append('_').append(sanitize(methodName));
        if (!parameterTypes.isEmpty()) {
            for (JType t : parameterTypes) {
                sb.append('_').append(typeCode(t));
            }
        }
        return sb.toString();
    }

    public static String mangleConstructor(String className, List<JType> parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("_JN_").append(sanitize(className)).append("_init");
        for (JType t : parameterTypes) {
            sb.append('_').append(typeCode(t));
        }
        return sb.toString();
    }

    public static String mangleMethod(MethodSymbol method) {
        return method.mangledName();
    }

    public static String llvmStructName(String className) {
        return "class." + sanitize(className);
    }

    public static String llvmNativeStructName(String name) {
        return "struct." + sanitize(name);
    }

    public static String arrayMetaName(JType elementType) {
        return "array." + typeCode(elementType);
    }

    public static String typeCode(JType type) {
        return switch (type) {
            case PrimitiveType p -> switch (p) {
                case BYTE -> "i8";
                case SHORT -> "i16";
                case INT -> "i32";
                case LONG -> "i64";
                case FLOAT -> "f32";
                case DOUBLE -> "f64";
                case BOOLEAN -> "i1";
                case CHAR -> "i16";
            };
            case VoidType ignored -> "void";
            case NullType ignored -> "null";
            case ClassType c -> "C" + sanitize(c.name());
            case InterfaceType i -> "I" + sanitize(i.name());
            case ParameterizedType p -> "P" + typeCode(p.rawType()) + p.typeArguments().stream()
                    .map(NameMangler::typeCode).reduce("", (a, b) -> a + "_" + b);
            case TypeVariable t -> "T" + sanitize(t.name());
            case WildcardType ignored -> "W";
            case ArrayType a -> "A" + typeCode(a.elementType());
            case NativePtrType np -> "nptr_" + typeCode(np.pointee());
            case NativeFunctionPtrType nfp -> "nfn_" + typeCode(nfp.functionType().returnType());
            case FunctionType ft -> "fn_" + typeCode(ft.returnType());
            case NativeStructType ns -> "NS" + sanitize(ns.name());
            case ErrorType ignored -> "err";
        };
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    public static String describeSignature(List<JType> types) {
        return types.stream().map(JType::displayName).collect(Collectors.joining(","));
    }
}
