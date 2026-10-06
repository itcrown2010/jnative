package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record ClassDeclaration(
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
) implements AstNode {

    /** Legacy constructor used by older call sites / tests. */
    public ClassDeclaration(
            String name,
            String superClassName,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            SourceLocation location
    ) {
        this(name, List.of(), superClassName, List.of(), List.of(), fields, methods,
                false, false, false, location);
    }

    public ClassDeclaration(
            String name,
            String superClassName,
            List<FieldDeclaration> fields,
            List<MethodDeclaration> methods,
            boolean nativeStruct,
            SourceLocation location
    ) {
        this(name, List.of(), superClassName, List.of(), List.of(), fields, methods,
                nativeStruct, false, false, location);
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
        this(name, List.of(), superClassName, List.of(),
                implementedInterfaceNames.stream()
                        .map(n -> TypeName.ofClass(n, location))
                        .toList(),
                fields, methods, nativeStruct, isInterface, isAbstract, location);
    }
}
