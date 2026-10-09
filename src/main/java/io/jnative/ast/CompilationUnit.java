package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;
import java.util.Map;

/**
 * @author 张晓达
 */
public record CompilationUnit(
        String fileName,
        List<ClassDeclaration> classes,
        Map<String, List<MarkerAnnotation>> typeAnnotations,
        Map<String, List<MarkerAnnotation>> methodAnnotations,
        Map<String, List<MarkerAnnotation>> fieldAnnotations,
        SourceLocation location
) implements AstNode {
    public CompilationUnit(String fileName, List<ClassDeclaration> classes, SourceLocation location) {
        this(fileName, classes, Map.of(), Map.of(), Map.of(), location);
    }

    public CompilationUnit(
            String fileName,
            List<ClassDeclaration> classes,
            Map<String, List<MarkerAnnotation>> typeAnnotations,
            Map<String, List<MarkerAnnotation>> methodAnnotations,
            SourceLocation location
    ) {
        this(fileName, classes, typeAnnotations, methodAnnotations, Map.of(), location);
    }
}
