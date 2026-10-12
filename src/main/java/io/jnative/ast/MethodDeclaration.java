/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.Visibility;

import java.util.List;

/**
 * @author 张晓达
 */
public record MethodDeclaration(
        boolean isPublic,
        boolean isStatic,
        boolean isNative,
        boolean isConstructor,
        boolean isAbstract,
        boolean isAsync,
        boolean isFinal,
        List<TypeParameterDeclaration> typeParameters,
        TypeName returnType,
        boolean returnsArray,
        String name,
        List<Parameter> parameters,
        BlockStatement body,
        NativeAnnotation nativeAnnotation,
        EffectContract effectContract,
        Visibility visibility,
        SourceLocation location
) implements AstNode {
    public MethodDeclaration {
        if (visibility == null) {
            visibility = isPublic ? Visibility.PUBLIC : Visibility.PACKAGE;
        }
    }

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
        this(isPublic, isStatic, false, isConstructor, false, false, false, List.of(), returnType, false, name, parameters, body,
                null, null, isPublic ? Visibility.PUBLIC : Visibility.PACKAGE, location);
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
        this(isPublic, isStatic, isNative, isConstructor, false, false, false, List.of(), returnType, returnsArray, name, parameters,
                body, nativeAnnotation, null, isPublic ? Visibility.PUBLIC : Visibility.PACKAGE, location);
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
        this(isPublic, isStatic, isNative, isConstructor, false, false, false, List.of(), returnType, returnsArray, name, parameters,
                body, nativeAnnotation, effectContract, isPublic ? Visibility.PUBLIC : Visibility.PACKAGE, location);
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
        this(isPublic, isStatic, isNative, isConstructor, isAbstract, false, false, List.of(), returnType, returnsArray, name,
                parameters, body, nativeAnnotation, effectContract,
                isPublic ? Visibility.PUBLIC : Visibility.PACKAGE, location);
    }

    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isNative,
            boolean isConstructor,
            boolean isAbstract,
            boolean isAsync,
            List<TypeParameterDeclaration> typeParameters,
            TypeName returnType,
            boolean returnsArray,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            NativeAnnotation nativeAnnotation,
            EffectContract effectContract,
            SourceLocation location
    ) {
        this(isPublic, isStatic, isNative, isConstructor, isAbstract, isAsync, false, typeParameters, returnType, returnsArray,
                name, parameters, body, nativeAnnotation, effectContract,
                isPublic ? Visibility.PUBLIC : Visibility.PACKAGE, location);
    }

    /** Full constructor including visibility (pre-final flag). */
    public MethodDeclaration(
            boolean isPublic,
            boolean isStatic,
            boolean isNative,
            boolean isConstructor,
            boolean isAbstract,
            boolean isAsync,
            List<TypeParameterDeclaration> typeParameters,
            TypeName returnType,
            boolean returnsArray,
            String name,
            List<Parameter> parameters,
            BlockStatement body,
            NativeAnnotation nativeAnnotation,
            EffectContract effectContract,
            Visibility visibility,
            SourceLocation location
    ) {
        this(isPublic, isStatic, isNative, isConstructor, isAbstract, isAsync, false, typeParameters, returnType, returnsArray,
                name, parameters, body, nativeAnnotation, effectContract, visibility, location);
    }
}
