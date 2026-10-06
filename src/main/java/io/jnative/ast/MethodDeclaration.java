package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record MethodDeclaration(
        boolean isPublic,
        boolean isStatic,
        boolean isNative,
        boolean isConstructor,
        boolean isAbstract,
        List<TypeParameterDeclaration> typeParameters,
        TypeName returnType,
        boolean returnsArray,
        String name,
        List<Parameter> parameters,
        BlockStatement body,
        NativeAnnotation nativeAnnotation,
        EffectContract effectContract,
        SourceLocation location
) implements AstNode {
    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isConstructor,
            TypeName returnType,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            SourceLocation location
    ) {
        this(isPublic, isStatic, false, isConstructor, false, List.of(), returnType, false, name, parameters, body,
                null, null, location);
    }

    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isNative,
            boolean isConstructor,
            TypeName returnType,
            boolean returnsArray,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            NativeAnnotation nativeAnnotation,
            SourceLocation location
    ) {
        this(isPublic, isStatic, isNative, isConstructor, false, List.of(), returnType, returnsArray, name, parameters,
                body, nativeAnnotation, null, location);
    }

    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isNative,
            boolean isConstructor,
            TypeName returnType,
            boolean returnsArray,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            NativeAnnotation nativeAnnotation,
            EffectContract effectContract,
            SourceLocation location
    ) {
        this(isPublic, isStatic, isNative, isConstructor, false, List.of(), returnType, returnsArray, name, parameters,
                body, nativeAnnotation, effectContract, location);
    }

    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isNative,
            boolean isConstructor,
            boolean isAbstract,
            TypeName returnType,
            boolean returnsArray,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            NativeAnnotation nativeAnnotation,
            EffectContract effectContract,
            SourceLocation location
    ) {
        this(isPublic, isStatic, isNative, isConstructor, isAbstract, List.of(), returnType, returnsArray, name,
                parameters, body, nativeAnnotation, effectContract, location);
    }
}
