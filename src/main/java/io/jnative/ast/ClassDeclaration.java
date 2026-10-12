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
public record ClassDeclaration(
        String name,
        String packageName,
        List<ImportDeclaration> imports,
        Visibility visibility,
        List<TypeParameterDeclaration> typeParameters,
        String superClassName,
        List<TypeName> superClassTypeArgs,
        List<TypeName> implementedInterfaces,
        List<FieldDeclaration> fields,
        List<MethodDeclaration> methods,
        boolean nativeStruct,
        boolean isInterface,
        boolean isAbstract,
        boolean isFinal,
        boolean isEnum,
        List<String> enumConstants,
        SourceLocation location
) implements AstNode {

    /** Legacy constructor used by older call sites / tests. */
    public ClassDeclaration(
            String name,
            String superClassName,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            SourceLocation location
    ) {
        this(name, "", List.of(), Visibility.PACKAGE, List.of(), superClassName, List.of(), List.of(),
                fields, methods, false, false, false, false, false, List.of(), location);
    }

    public ClassDeclaration(
            String name,
            String superClassName,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            SourceLocation location
    ) {
        this(name, "", List.of(), Visibility.PACKAGE, List.of(), superClassName, List.of(), List.of(),
                fields, methods, nativeStruct, false, false, false, false, List.of(), location);
    }

    /** 0.14-style: interface names as strings. */
    public ClassDeclaration(
            String name,
            String superClassName,
            List<String> implementedInterfaceNames,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            boolean isInterface,
            boolean isAbstract,
            SourceLocation location
    ) {
        this(name, "", List.of(), Visibility.PACKAGE, List.of(), superClassName, List.of(),
                implementedInterfaceNames.stream()
                        .map(n -> TypeName.ofClass(n, location))
                        .toList(),
                fields, methods, nativeStruct, isInterface, isAbstract, false, false, List.of(), location);
    }

    /** Pre-package constructor (type params + TypeName interfaces). */
    public ClassDeclaration(
            String name,
            List<TypeParameterDeclaration> typeParameters,
            String superClassName,
            List<TypeName> superClassTypeArgs,
            List<TypeName> implementedInterfaces,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            boolean isInterface,
            boolean isAbstract,
            SourceLocation location
    ) {
        this(name, "", List.of(), Visibility.PACKAGE, typeParameters, superClassName, superClassTypeArgs,
                implementedInterfaces, fields, methods, nativeStruct, isInterface, isAbstract,
                false, false, List.of(), location);
    }

    /** Full non-enum type declaration (pre-final flag). */
    public ClassDeclaration(
            String name,
            String packageName,
            List<ImportDeclaration> imports,
            Visibility visibility,
            List<TypeParameterDeclaration> typeParameters,
            String superClassName,
            List<TypeName> superClassTypeArgs,
            List<TypeName> implementedInterfaces,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            boolean isInterface,
            boolean isAbstract,
            SourceLocation location
    ) {
        this(name, packageName, imports, visibility, typeParameters, superClassName, superClassTypeArgs,
                implementedInterfaces, fields, methods, nativeStruct, isInterface, isAbstract,
                false, false, List.of(), location);
    }

    /** Full non-enum type declaration with final. */
    public ClassDeclaration(
            String name,
            String packageName,
            List<ImportDeclaration> imports,
            Visibility visibility,
            List<TypeParameterDeclaration> typeParameters,
            String superClassName,
            List<TypeName> superClassTypeArgs,
            List<TypeName> implementedInterfaces,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            boolean isInterface,
            boolean isAbstract,
            boolean isFinal,
            SourceLocation location
    ) {
        this(name, packageName, imports, visibility, typeParameters, superClassName, superClassTypeArgs,
                implementedInterfaces, fields, methods, nativeStruct, isInterface, isAbstract,
                isFinal, false, List.of(), location);
    }

    public static ClassDeclaration enumType(
            String name,
            String packageName,
            List<ImportDeclaration> imports,
            Visibility visibility,
            List<String> constants,
            SourceLocation location
    ) {
        return new ClassDeclaration(
                name, packageName, imports, visibility, List.of(), null, List.of(), List.of(),
                List.of(), List.of(), false, false, false, true, true,
                List.copyOf(constants), location);
    }

    public String qualifiedName() {
        if (packageName == null || packageName.isEmpty()) {
            return name;
        }
        return packageName + "." + name;
    }
}
