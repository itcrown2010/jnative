package io.jnative.compiler;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.Expression;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.TypedCompilationUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IDE-facing semantic query surface over a frontend {@link TypedCompilationUnit}.
 * Does not depend on IR/LLVM.
 */
public final class SemanticModel {
    private final TypedCompilationUnit typed;
    private final long version;

    public SemanticModel(TypedCompilationUnit typed, long version) {
        this.typed = typed;
        this.version = version;
    }

    public long version() {
        return version;
    }

    public TypedCompilationUnit typed() {
        return typed;
    }

    public Optional<Object> symbolAt(String file, int line, int column) {
        Object best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ClassSymbol cls : typed.classes().values()) {
            if (cls.isBuiltin()) {
                continue;
            }
            int d = distance(cls.location(), file, line, column);
            if (d >= 0 && d < bestDist) {
                bestDist = d;
                best = cls;
            }
            for (MethodSymbol m : cls.declaredMethods()) {
                int md = distance(m.location(), file, line, column);
                if (md >= 0 && md < bestDist) {
                    bestDist = md;
                    best = m;
                }
            }
            for (FieldSymbol f : cls.declaredFields()) {
                int fd = distance(f.location(), file, line, column);
                if (fd >= 0 && fd < bestDist) {
                    bestDist = fd;
                    best = f;
                }
            }
        }
        for (InterfaceSymbol iface : typed.interfaces().values()) {
            int d = distance(iface.location(), file, line, column);
            if (d >= 0 && d < bestDist) {
                bestDist = d;
                best = iface;
            }
        }
        for (Map.Entry<Expression, Object> e : typed.resolvedSymbols().entrySet()) {
            int d = distance(e.getKey().location(), file, line, column);
            if (d >= 0 && d < bestDist) {
                bestDist = d;
                best = e.getValue();
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<JType> typeAt(String file, int line, int column) {
        Expression best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<Expression, JType> e : typed.expressionTypes().entrySet()) {
            int d = distance(e.getKey().location(), file, line, column);
            if (d >= 0 && d < bestDist) {
                bestDist = d;
                best = e.getKey();
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(typed.expressionTypes().get(best));
    }

    public Optional<TextRange> declarationOf(Object symbol) {
        if (symbol instanceof ClassSymbol c) {
            return Optional.of(TextRange.of(c.location()));
        }
        if (symbol instanceof InterfaceSymbol i) {
            return Optional.of(TextRange.of(i.location()));
        }
        if (symbol instanceof MethodSymbol m) {
            return Optional.of(TextRange.of(m.location()));
        }
        if (symbol instanceof FieldSymbol f) {
            return Optional.of(TextRange.of(f.location()));
        }
        return Optional.empty();
    }

    public List<TextRange> referencesOf(Object symbol) {
        List<TextRange> out = new ArrayList<>();
        declarationOf(symbol).ifPresent(out::add);
        for (Map.Entry<Expression, Object> e : typed.resolvedSymbols().entrySet()) {
            if (e.getValue() == symbol || sameSymbol(e.getValue(), symbol)) {
                out.add(TextRange.of(e.getKey().location()));
            }
        }
        return out;
    }

    public List<Object> membersOf(JType type) {
        List<Object> out = new ArrayList<>();
        if (type == null) {
            return out;
        }
        ClassSymbol cls = null;
        if (type instanceof ClassType ct) {
            cls = ct.classSymbol();
        } else {
            cls = typed.classes().get(type.toString());
        }
        if (cls == null) {
            return out;
        }
        out.addAll(cls.declaredFields());
        out.addAll(cls.declaredMethods());
        return out;
    }

    public List<Object> visibleSymbols(String file, int line, int column) {
        List<Object> out = new ArrayList<>();
        out.addAll(typed.classes().values());
        out.addAll(typed.interfaces().values());
        symbolAt(file, line, column).ifPresent(sym -> {
            if (sym instanceof ClassSymbol c) {
                out.addAll(c.declaredMethods());
                out.addAll(c.declaredFields());
            }
        });
        return out;
    }

    public List<ClassDeclaration> documentClasses() {
        if (typed.unit() == null) {
            return List.of();
        }
        return typed.unit().classes();
    }

    public Map<String, ClassSymbol> classes() {
        return new LinkedHashMap<>(typed.classes());
    }

    private static boolean sameSymbol(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        if (a instanceof ClassSymbol ca && b instanceof ClassSymbol cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof MethodSymbol ma && b instanceof MethodSymbol mb) {
            return ma.name().equals(mb.name())
                    && ma.owner() != null && mb.owner() != null
                    && ma.owner().name().equals(mb.owner().name());
        }
        return false;
    }

    /** Distance heuristic: same file, prefer same/near line. -1 if wrong file. */
    private static int distance(SourceLocation loc, String file, int line, int column) {
        if (loc == null) {
            return -1;
        }
        String lf = loc.file() == null ? "" : loc.file();
        String want = file == null ? "" : file;
        if (!want.isEmpty() && !lf.isEmpty() && !lf.equals(want) && !lf.endsWith(want) && !want.endsWith(lf)) {
            return -1;
        }
        int lineDelta = Math.abs(loc.line() - line);
        int colDelta = Math.abs(loc.column() - column);
        return lineDelta * 1000 + colDelta;
    }
}
