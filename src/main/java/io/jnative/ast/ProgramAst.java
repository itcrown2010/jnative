package io.jnative.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Independently parsed compilation units for one program.
 * Types are analyzed across units; IR may consume a flattened class list.
 * @author 张晓达
 */
public final class ProgramAst {
    private final List<CompilationUnit> units;

    public ProgramAst(List<CompilationUnit> units) {
        this.units = List.copyOf(units == null ? List.of() : units);
    }

    public List<CompilationUnit> units() {
        return units;
    }

    /** Flatten class declarations for backends that still expect one unit. */
    public CompilationUnit flattened(String syntheticName) {
        List<ClassDeclaration> classes = new ArrayList<>();
        for (CompilationUnit u : units) {
            classes.addAll(u.classes());
        }
        String name = syntheticName == null || syntheticName.isBlank() ? "<program>" : syntheticName;
        return new CompilationUnit(name, classes,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                classes.isEmpty() ? io.jnative.diagnostics.SourceLocation.UNKNOWN
                        : classes.getFirst().location());
    }

    public Iterable<ClassDeclaration> allClasses() {
        List<ClassDeclaration> out = new ArrayList<>();
        for (CompilationUnit u : units) {
            out.addAll(u.classes());
        }
        return out;
    }
}
