/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.generic;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.Expression;
import io.jnative.ast.FieldAccessExpression;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.NewObjectExpression;
import io.jnative.ast.TypeName;
import io.jnative.semantic.ArrayType;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.mangle.NameMangler;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceType;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.ParameterizedType;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.semantic.TypeVariable;
import io.jnative.semantic.WildcardType;
import io.jnative.vtable.ITableBuilder;
import io.jnative.vtable.VTableBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers generic types to erased reference instantiations or primitive-specialized classes.
 * @author 张晓达
 */
public final class GenericLowering {

    public static final class Stats {
        private int erasedCount;
        private int specializedCount;
        private final List<String> specializedNames = new ArrayList<>();

        public int erasedCount() {
            return erasedCount;
        }

        public int specializedCount() {
            return specializedCount;
        }

        public List<String> specializedNames() {
            return List.copyOf(specializedNames);
        }
    }

    private final Stats stats = new Stats();

    public TypedCompilationUnit lower(TypedCompilationUnit typed, DiagnosticReporter reporter) {
        Map<String, ClassSymbol> classes = new LinkedHashMap<>(typed.classes());
        Map<Expression, JType> expressionTypes = new HashMap<>(typed.expressionTypes());
        Map<Expression, Object> resolvedSymbols = new HashMap<>(typed.resolvedSymbols());
        ClassSymbol objectClass = classes.get("Object");

        Set<ParameterizedType> seen = new LinkedHashSet<>();
        collectParameterizedTypes(expressionTypes, classes, seen);
        for (ClassSymbol cls : classes.values()) {
            if (cls.isBuiltin() || cls.isSpecialized()) {
                continue;
            }
            for (FieldSymbol field : cls.declaredFields()) {
                collectFromType(field.type(), classes, seen);
            }
        }

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            Set<ParameterizedType> batch = new LinkedHashSet<>(seen);
            for (ClassSymbol cls : classes.values()) {
                if (!cls.isSpecialized()) {
                    continue;
                }
                for (FieldSymbol field : cls.declaredFields()) {
                    collectFromType(field.type(), classes, batch);
                }
                for (MethodSymbol method : cls.declaredMethods()) {
                    collectFromType(method.returnType(), classes, batch);
                    for (ParameterSymbol p : method.parameters()) {
                        collectFromType(p.type(), classes, batch);
                    }
                }
                // new Foo<T>(...) inside specialized bodies → Foo$P_...
                collectNewObjectsFromOrigin(cls, typed, classes, batch);
            }
            for (ParameterizedType pt : batch) {
                if (!pt.needsPrimitiveSpecialization()) {
                    continue;
                }
                if (!(pt.raw() instanceof ClassSymbol origin)) {
                    continue;
                }
                String name = specializedName(origin, pt.typeArguments());
                if (classes.containsKey(name)) {
                    continue;
                }
                specializeClass(origin, pt.typeArguments(), classes);
                progressed = true;
                seen.add(pt);
            }
        }

        for (var entry : new ArrayList<>(expressionTypes.entrySet())) {
            JType lowered = lowerType(entry.getValue(), classes, objectClass);
            if (!entry.getValue().equals(lowered)) {
                if (entry.getValue() instanceof ParameterizedType && lowered instanceof ClassType) {
                    stats.erasedCount++;
                }
                expressionTypes.put(entry.getKey(), lowered);
            }
        }

        for (var entry : new ArrayList<>(resolvedSymbols.entrySet())) {
            if (entry.getKey() instanceof NewObjectExpression n) {
                JType inst = expressionTypes.get(n);
                if (inst instanceof ClassType ct && ct.classSymbol().isSpecialized()) {
                    ConstructorSymbol rawCtor = (ConstructorSymbol) entry.getValue();
                    ConstructorSymbol specCtor = matchConstructor(ct.classSymbol(), rawCtor);
                    if (specCtor != null) {
                        resolvedSymbols.put(n, specCtor);
                    }
                }
            } else if (entry.getKey() instanceof FieldAccessExpression f) {
                JType recv = expressionTypes.get(f.receiver());
                if (recv instanceof ClassType ct && ct.classSymbol().isSpecialized()) {
                    ct.classSymbol().findField(f.fieldName()).ifPresent(fs ->
                            resolvedSymbols.put(f, fs));
                }
            } else if (entry.getKey() instanceof io.jnative.ast.MethodCallExpression call
                    && entry.getValue() instanceof MethodSymbol ms) {
                Expression recvExpr = call.receiver();
                if (recvExpr != null) {
                    JType recv = expressionTypes.get(recvExpr);
                    if (recv instanceof ClassType ct && ct.classSymbol().isSpecialized()) {
                        ct.classSymbol().findMethod(ms.name()).ifPresent(spec ->
                                resolvedSymbols.put(call, spec));
                    }
                }
            }
        }

        return new TypedCompilationUnit(
                typed.unit(),
                typed.units(),
                Map.copyOf(classes),
                typed.interfaces(),
                typed.nativeStructs(),
                Map.copyOf(expressionTypes),
                Map.copyOf(resolvedSymbols),
                typed.callableBindings(),
                typed.arrayMetadatas(),
                typed.catchTypes(),
                typed.resourceCloseCalls(),
                typed.enhancedForDesugars(),
                typed.nativeLibraries(),
                typed.targetAbi());
    }

    public Stats stats() {
        return stats;
    }

    public String emitReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Generic lowering ===\n");
        sb.append("erased instantiations: ").append(stats.erasedCount).append('\n');
        sb.append("specialized classes: ").append(stats.specializedCount).append('\n');
        for (String name : stats.specializedNames()) {
            sb.append("  ").append(name).append('\n');
        }
        return sb.toString();
    }

    private void collectParameterizedTypes(
            Map<Expression, JType> expressionTypes,
            Map<String, ClassSymbol> classes,
            Set<ParameterizedType> out
    ) {
        for (JType type : expressionTypes.values()) {
            collectFromType(type, classes, out);
        }
    }

    private void collectFromType(JType type, Map<String, ClassSymbol> classes, Set<ParameterizedType> out) {
        if (type instanceof ParameterizedType pt) {
            out.add(pt);
            for (JType arg : pt.typeArguments()) {
                collectFromType(arg, classes, out);
            }
        } else if (type instanceof TypeVariable tv) {
            // leaf
        } else if (type instanceof io.jnative.semantic.ArrayType at) {
            collectFromType(at.elementType(), classes, out);
        }
    }

    private JType lowerType(JType type, Map<String, ClassSymbol> classes, ClassSymbol objectClass) {
        if (type instanceof ParameterizedType pt) {
            if (pt.needsPrimitiveSpecialization()) {
                if (pt.raw() instanceof ClassSymbol cs) {
                    ClassSymbol spec = classes.get(specializedName(cs, pt.typeArguments()));
                    return spec != null ? spec.asType() : ErrorType.INSTANCE;
                }
                // Interface primitive specialization: erase to raw interface for now;
                // specialized ABI is carried by implementors / call sites.
                return pt.rawType();
            }
            return pt.rawType();
        }
        // Keep TypeVariables so specialized class bodies can remap T[] / T.hashCode().
        // Erasure to Object happens in IrGenerator for non-specialized lowering.
        if (type instanceof TypeVariable) {
            return type;
        }
        if (type instanceof WildcardType) {
            return objectClass != null ? objectClass.asType() : ErrorType.INSTANCE;
        }
        if (type instanceof io.jnative.semantic.ArrayType at) {
            return new io.jnative.semantic.ArrayType(lowerType(at.elementType(), classes, objectClass));
        }
        return type;
    }

    private ClassSymbol specializeClass(
            ClassSymbol origin,
            List<JType> args,
            Map<String, ClassSymbol> classes
    ) {
        String name = specializedName(origin, args);
        ClassSymbol existing = classes.get(name);
        if (existing != null) {
            return existing;
        }

        // Register placeholder first so mutual deps (HashMap ↔ EntryIterator) do not recurse.
        ClassSymbol spec = new ClassSymbol(name, origin.location(), false);
        spec.setGenericOrigin(origin);
        spec.setSpecializationArgs(args);
        spec.setSpecialized(true);
        spec.setAbstract(origin.isAbstract());
        origin.superClass().ifPresent(spec::setSuperClass);
        spec.setImplementedInterfaces(origin.implementedInterfaces());
        classes.put(name, spec);

        TypeSubstitution sub = TypeSubstitution.zip(origin.typeParameters(), args);
        // Pre-specialize nested parameterized types so lowerType can resolve them.
        for (FieldSymbol field : origin.declaredFields()) {
            preSpecializeDeps(sub.apply(field.type()), classes);
        }
        for (MethodSymbol method : origin.declaredMethods()) {
            preSpecializeDeps(sub.apply(method.returnType()), classes);
            for (ParameterSymbol p : method.parameters()) {
                preSpecializeDeps(sub.apply(p.type()), classes);
            }
        }
        for (ConstructorSymbol ctor : origin.constructors()) {
            for (ParameterSymbol p : ctor.parameters()) {
                preSpecializeDeps(sub.apply(p.type()), classes);
            }
        }

        ClassSymbol objectClass = classes.get("Object");
        int index = 0;
        for (FieldSymbol field : origin.declaredFields()) {
            JType ft = lowerType(sub.apply(field.type()), classes, objectClass);
            FieldSymbol copy = new FieldSymbol(
                    spec, field.name(), ft, index++, field.isStatic(), field.isVolatile(), field.location());
            if (field.initializer() != null) {
                copy.setInitializer(field.initializer());
            }
            spec.addField(copy);
        }

        for (MethodSymbol method : origin.declaredMethods()) {
            List<ParameterSymbol> params = method.parameters().stream()
                    .map(p -> new ParameterSymbol(
                            p.name(), lowerType(sub.apply(p.type()), classes, objectClass), p.location()))
                    .toList();
            JType ret = lowerType(sub.apply(method.returnType()), classes, objectClass);
            String mangled = NameMangler.mangleMethod(
                    spec.name(), method.name(),
                    params.stream().map(ParameterSymbol::type).toList(),
                    method.isMain());
            MethodSymbol copy = new MethodSymbol(
                    spec, method.name(), ret, params,
                    method.isStatic(), method.isMain(), method.isNative(), method.isAbstract(),
                    mangled, method.location());
            copy.setDeclaredEffects(method.declaredEffects());
            copy.setInferredEffects(method.inferredEffects());
            copy.setEffectiveEffects(method.effectiveEffects());
            copy.setAsync(method.isAsync());
            copy.setLogicalReturnType(lowerType(sub.apply(method.logicalReturnType()), classes, objectClass));
            spec.addMethod(copy);
        }

        for (ConstructorSymbol ctor : origin.constructors()) {
            List<ParameterSymbol> params = ctor.parameters().stream()
                    .map(p -> new ParameterSymbol(
                            p.name(), lowerType(sub.apply(p.type()), classes, objectClass), p.location()))
                    .toList();
            ConstructorSymbol copy = new ConstructorSymbol(spec, params, ctor.isImplicit(), ctor.location());
            if (ctor.delegatesTo() != null) {
                copy.setDelegatesTo(ctor.delegatesTo());
                copy.setDelegatesToSuper(ctor.delegatesToSuper());
            }
            spec.addConstructor(copy);
        }

        spec.setLayout(ObjectLayoutBuilder.build(spec));
        spec.setVtable(VTableBuilder.build(spec));
        if (!spec.transitiveInterfaces().isEmpty()) {
            spec.setItables(ITableBuilder.buildAll(spec));
            for (var iface : spec.transitiveInterfaces()) {
                iface.addImplementor(spec);
            }
        }
        stats.specializedCount++;
        stats.specializedNames.add(name);
        return spec;
    }

    private void preSpecializeDeps(JType type, Map<String, ClassSymbol> classes) {
        if (type instanceof ParameterizedType pt) {
            for (JType arg : pt.typeArguments()) {
                preSpecializeDeps(arg, classes);
            }
            if (pt.needsPrimitiveSpecialization() && pt.raw() instanceof ClassSymbol origin) {
                specializeClass(origin, pt.typeArguments(), classes);
            }
        } else if (type instanceof ArrayType at) {
            preSpecializeDeps(at.elementType(), classes);
        }
    }

    private void collectNewObjectsFromOrigin(
            ClassSymbol specialized,
            TypedCompilationUnit typed,
            Map<String, ClassSymbol> classes,
            Set<ParameterizedType> out
    ) {
        ClassSymbol origin = specialized.genericOrigin();
        if (origin == null) {
            return;
        }
        ClassDeclaration decl = null;
        for (ClassDeclaration c : typed.unit().classes()) {
            if (c.name().equals(origin.name())) {
                decl = c;
                break;
            }
        }
        if (decl == null) {
            return;
        }
        TypeSubstitution sub = TypeSubstitution.zip(origin.typeParameters(), specialized.specializationArgs());
        for (MethodDeclaration method : decl.methods()) {
            if (method.body() == null) {
                continue;
            }
            collectNewObjectsInStatement(method.body(), sub, classes, out);
        }
    }

    private void collectNewObjectsInStatement(
            io.jnative.ast.Statement stmt,
            TypeSubstitution sub,
            Map<String, ClassSymbol> classes,
            Set<ParameterizedType> out
    ) {
        if (stmt instanceof io.jnative.ast.BlockStatement b) {
            for (var s : b.statements()) {
                collectNewObjectsInStatement(s, sub, classes, out);
            }
        } else if (stmt instanceof io.jnative.ast.ReturnStatement r && r.value() != null) {
            collectNewObjectsInExpression(r.value(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.ExpressionStatement e) {
            collectNewObjectsInExpression(e.expression(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.VariableDeclaration v && v.initializer() != null) {
            collectNewObjectsInExpression(v.initializer(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.IfStatement i) {
            collectNewObjectsInStatement(i.thenBranch(), sub, classes, out);
            if (i.elseBranch() != null) {
                collectNewObjectsInStatement(i.elseBranch(), sub, classes, out);
            }
        } else if (stmt instanceof io.jnative.ast.WhileStatement w) {
            collectNewObjectsInStatement(w.body(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.DoWhileStatement d) {
            collectNewObjectsInStatement(d.body(), sub, classes, out);
            collectNewObjectsInExpression(d.condition(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.ForStatement f) {
            if (f.initializer() != null) {
                collectNewObjectsInStatement(f.initializer(), sub, classes, out);
            }
            if (f.condition() != null) {
                collectNewObjectsInExpression(f.condition(), sub, classes, out);
            }
            collectNewObjectsInStatement(f.body(), sub, classes, out);
            for (Expression u : f.allUpdates()) {
                collectNewObjectsInExpression(u, sub, classes, out);
            }
        }
        else if (stmt instanceof io.jnative.ast.LabeledStatement l) {
            collectNewObjectsInStatement(l.body(), sub, classes, out);
        } else if (stmt instanceof io.jnative.ast.YieldStatement y) {
            collectNewObjectsInExpression(y.value(), sub, classes, out);
        }
        // BreakStatement / ContinueStatement: no expressions
    }

    private void collectNewObjectsInExpression(
            Expression expr,
            TypeSubstitution sub,
            Map<String, ClassSymbol> classes,
            Set<ParameterizedType> out
    ) {
        if (expr instanceof NewObjectExpression n && !n.typeArguments().isEmpty()) {
            ClassSymbol raw = classes.get(n.className());
            if (raw != null && raw.isGeneric()) {
                List<JType> args = new ArrayList<>();
                for (TypeName tn : n.typeArguments()) {
                    ClassSymbol objectClass = classes.get("Object");
                    // Resolve type arg names against substitution (T → int).
                    JType resolved = null;
                    for (var e : sub.asMap().entrySet()) {
                        if (e.getKey().name().equals(tn.name())) {
                            resolved = e.getValue();
                            break;
                        }
                    }
                    if (resolved == null) {
                        ClassSymbol c = classes.get(tn.name());
                        resolved = c != null ? c.asType() : (objectClass != null ? objectClass.asType() : ErrorType.INSTANCE);
                    }
                    args.add(resolved);
                }
                ParameterizedType pt = new ParameterizedType(raw, args);
                if (pt.needsPrimitiveSpecialization()) {
                    out.add(pt);
                }
            }
        }
    }

    public static String specializedName(ClassSymbol origin, List<JType> args) {
        StringBuilder sb = new StringBuilder(origin.name()).append("$P");
        for (JType arg : args) {
            sb.append('_').append(NameMangler.typeCode(arg));
        }
        return sb.toString();
    }

    private static ConstructorSymbol matchConstructor(ClassSymbol spec, ConstructorSymbol rawCtor) {
        int arity = rawCtor.parameters().size();
        for (ConstructorSymbol c : spec.constructors()) {
            if (c.parameters().size() == arity) {
                return c;
            }
        }
        return spec.constructors().isEmpty() ? null : spec.constructors().getFirst();
    }
}
