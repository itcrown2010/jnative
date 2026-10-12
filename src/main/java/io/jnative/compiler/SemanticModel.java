/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.compiler;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.Expression;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.semantic.VariableSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IDE-facing semantic query surface over a frontend {@link TypedCompilationUnit}.
 * Does not depend on IR/LLVM.
 * @author 张晓达
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
            return rangeOrEmpty(c.location());
        }
        if (symbol instanceof InterfaceSymbol i) {
            return rangeOrEmpty(i.location());
        }
        if (symbol instanceof MethodSymbol m) {
            return rangeOrEmpty(m.location());
        }
        if (symbol instanceof ConstructorSymbol ctor) {
            return rangeOrEmpty(ctor.location());
        }
        if (symbol instanceof FieldSymbol f) {
            return rangeOrEmpty(f.location());
        }
        if (symbol instanceof VariableSymbol v) {
            return rangeOrEmpty(v.location());
        }
        if (symbol instanceof ParameterSymbol p) {
            return rangeOrEmpty(p.location());
        }
        return Optional.empty();
    }

    public List<TextRange> referencesOf(Object symbol) {
        List<TextRange> out = new ArrayList<>();
        declarationOf(symbol).ifPresent(out::add);
        for (Map.Entry<Expression, Object> e : typed.resolvedSymbols().entrySet()) {
            if (e.getValue() == symbol || sameIdentity(e.getValue(), symbol)) {
                out.add(TextRange.of(e.getKey().location()));
            }
        }
        return out;
    }

    private static Optional<TextRange> rangeOrEmpty(SourceLocation loc) {
        if (loc == null || loc.file() == null || loc.file().isBlank()
                || "<unknown>".equals(loc.file()) || "<stdlib>".equals(loc.file())) {
            return Optional.empty();
        }
        if (loc.line() <= 0) {
            return Optional.empty();
        }
        return Optional.of(TextRange.of(loc));
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

    /**
     * Identity for references: prefer object identity, then FQN + method signature.
     * Does not collapse overloads that only share a name.
     */
    static boolean sameIdentity(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b || a.equals(b)) {
            return true;
        }
        if (a instanceof ClassSymbol ca && b instanceof ClassSymbol cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof InterfaceSymbol ia && b instanceof InterfaceSymbol ib) {
            return ia.name().equals(ib.name());
        }
        if (a instanceof MethodSymbol ma && b instanceof MethodSymbol mb) {
            if (!ma.name().equals(mb.name())) {
                return false;
            }
            if (ma.owner() == null || mb.owner() == null
                    || !ma.owner().name().equals(mb.owner().name())) {
                return false;
            }
            if (ma.parameters().size() != mb.parameters().size()) {
                return false;
            }
            for (int i = 0; i < ma.parameters().size(); i++) {
                JType ta = ma.parameters().get(i).type();
                JType tb = mb.parameters().get(i).type();
                if (ta == null || tb == null || !ta.toString().equals(tb.toString())) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof ConstructorSymbol ca && b instanceof ConstructorSymbol cb) {
            if (ca.owner() == null || cb.owner() == null
                    || !ca.owner().name().equals(cb.owner().name())) {
                return false;
            }
            if (ca.parameters().size() != cb.parameters().size()) {
                return false;
            }
            for (int i = 0; i < ca.parameters().size(); i++) {
                JType ta = ca.parameters().get(i).type();
                JType tb = cb.parameters().get(i).type();
                if (ta == null || tb == null || !ta.toString().equals(tb.toString())) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof FieldSymbol fa && b instanceof FieldSymbol fb) {
            return fa.name().equals(fb.name())
                    && fa.owner() != null && fb.owner() != null
                    && fa.owner().name().equals(fb.owner().name());
        }
        if (a instanceof VariableSymbol va && b instanceof VariableSymbol vb) {
            return va == vb || (va.name().equals(vb.name())
                    && va.location() != null && vb.location() != null
                    && va.location().equals(vb.location()));
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
