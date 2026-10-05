package io.jnative.semantic;

import io.jnative.ast.*;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.mangle.NameMangler;
import io.jnative.vtable.VTableBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic analysis for JNative 0.4 (constructors, arrays, String, RTTI).
 */
public final class SemanticAnalyzer {
    private final DiagnosticReporter reporter;
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();
    private final Map<Expression, JType> expressionTypes = new HashMap<>();
    private final Map<Expression, Object> resolvedSymbols = new HashMap<>();
    private final Map<String, ClassMetadata> arrayMetadatas = new LinkedHashMap<>();
    private final Map<ConstructorSymbol, MethodDeclaration> constructorAst = new HashMap<>();
    private final Map<CatchClause, ClassSymbol> catchTypes = new HashMap<>();

    private Scope current;
    private ClassSymbol currentClass;
    private MethodSymbol currentMethod;
    private ConstructorSymbol currentConstructor;
    private ClassSymbol stringClass;
    private ClassSymbol objectClass;
    private ClassSymbol throwableClass;

    public SemanticAnalyzer(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }

    public TypedCompilationUnit analyze(CompilationUnit unit) {
        classes.clear();
        expressionTypes.clear();
        resolvedSymbols.clear();
        arrayMetadatas.clear();
        constructorAst.clear();
        catchTypes.clear();
        current = new Scope(null);
        currentClass = null;
        currentMethod = null;
        currentConstructor = null;

        installBuiltins();

        for (ClassDeclaration cls : unit.classes()) {
            declareClass(cls);
        }
        for (ClassDeclaration cls : unit.classes()) {
            linkSuperClass(cls);
        }
        detectInheritanceCycles(unit);

        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol != null) {
                registerFields(symbol, cls);
            }
        }
        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol != null) {
                registerMethods(symbol, cls);
                registerConstructors(symbol, cls);
            }
        }

        for (ClassSymbol symbol : topologicalClasses()) {
            if (!symbol.isBuiltin()) {
                symbol.setLayout(ObjectLayoutBuilder.build(symbol));
                symbol.setVtable(VTableBuilder.build(symbol));
            }
        }

        resolveConstructorDelegations(unit);
        detectConstructorCycles();

        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol == null) {
                continue;
            }
            currentClass = symbol;
            for (MethodDeclaration method : cls.methods()) {
                if (method.isConstructor()) {
                    analyzeConstructor(symbol, method);
                } else {
                    analyzeMethod(symbol, method);
                }
            }
        }
        currentClass = null;

        return new TypedCompilationUnit(
                unit,
                Map.copyOf(classes),
                Map.copyOf(expressionTypes),
                Map.copyOf(resolvedSymbols),
                Map.copyOf(arrayMetadatas),
                Map.copyOf(catchTypes)
        );
    }

    private void installBuiltins() {
        installStringBuiltin();
        installObjectHierarchy();
    }

    private void installObjectHierarchy() {
        objectClass = new ClassSymbol("Object", SourceLocation.UNKNOWN, true);
        classes.put("Object", objectClass);
        objectClass.setLayout(ObjectLayoutBuilder.build(objectClass));
        objectClass.setVtable(new io.jnative.vtable.VTable(objectClass, List.of()));

        // Create hierarchy first (no layouts yet), then add Throwable.message, then build layouts
        // top-down so subclasses inherit the message field size.
        throwableClass = new ClassSymbol("Throwable", SourceLocation.UNKNOWN, true);
        classes.put("Throwable", throwableClass);
        throwableClass.setSuperClass(objectClass);
        throwableClass.addConstructor(new ConstructorSymbol(throwableClass, List.of(), true, SourceLocation.UNKNOWN));

        ClassSymbol exceptionClass = newExceptionClass("Exception", throwableClass);
        ClassSymbol runtimeExceptionClass = newExceptionClass("RuntimeException", exceptionClass);

        FieldSymbol messageField = new FieldSymbol(
                throwableClass, "message", stringClass.asType(), 0, SourceLocation.UNKNOWN);
        throwableClass.addField(messageField);

        ParameterSymbol msgParam = new ParameterSymbol("message", stringClass.asType(), SourceLocation.UNKNOWN);
        ConstructorSymbol throwableStringCtor = new ConstructorSymbol(
                throwableClass, List.of(msgParam), false, SourceLocation.UNKNOWN);
        throwableClass.addConstructor(throwableStringCtor);
        ConstructorSymbol throwableDefault = throwableClass.constructors().getFirst();
        throwableDefault.setDelegatesTo(null);

        MethodSymbol getMessage = new MethodSymbol(
                throwableClass, "getMessage", stringClass.asType(), List.of(),
                false, false, NameMangler.mangleMethod(throwableClass.name(), "getMessage", List.of(), false),
                SourceLocation.UNKNOWN);
        throwableClass.addMethod(getMessage);

        ConstructorSymbol exceptionString = addStringSuperCtor(exceptionClass);
        ConstructorSymbol runtimeString = addStringSuperCtor(runtimeExceptionClass);
        exceptionString.setDelegatesTo(throwableStringCtor);
        exceptionString.setDelegatesToSuper(true);
        runtimeString.setDelegatesTo(exceptionString);
        runtimeString.setDelegatesToSuper(true);
        exceptionClass.constructors().getFirst().setDelegatesTo(throwableDefault);
        exceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        runtimeExceptionClass.constructors().getFirst().setDelegatesTo(exceptionClass.constructors().getFirst());
        runtimeExceptionClass.constructors().getFirst().setDelegatesToSuper(true);

        List<ClassSymbol> leaves = new ArrayList<>();
        for (String leaf : List.of(
                "NullPointerException", "ArrayIndexOutOfBoundsException", "NegativeArraySizeException",
                "ClassCastException", "ArithmeticException")) {
            ClassSymbol leafClass = newExceptionClass(leaf, runtimeExceptionClass);
            leafClass.constructors().getFirst().setDelegatesTo(runtimeExceptionClass.constructors().getFirst());
            leafClass.constructors().getFirst().setDelegatesToSuper(true);
            leaves.add(leafClass);
        }

        // Layouts must be built after message field exists, parents before children.
        for (ClassSymbol cls : List.of(
                throwableClass, exceptionClass, runtimeExceptionClass)) {
            cls.setLayout(ObjectLayoutBuilder.build(cls));
            cls.setVtable(VTableBuilder.build(cls));
        }
        for (ClassSymbol leaf : leaves) {
            leaf.setLayout(ObjectLayoutBuilder.build(leaf));
            leaf.setVtable(VTableBuilder.build(leaf));
        }
    }

    private ClassSymbol newExceptionClass(String name, ClassSymbol superClass) {
        ClassSymbol cls = new ClassSymbol(name, SourceLocation.UNKNOWN, true);
        classes.put(name, cls);
        cls.setSuperClass(superClass);
        cls.addConstructor(new ConstructorSymbol(cls, List.of(), true, SourceLocation.UNKNOWN));
        return cls;
    }

    private ConstructorSymbol addStringSuperCtor(ClassSymbol cls) {
        ParameterSymbol msgParam = new ParameterSymbol("message", stringClass.asType(), SourceLocation.UNKNOWN);
        ConstructorSymbol ctor = new ConstructorSymbol(cls, List.of(msgParam), false, SourceLocation.UNKNOWN);
        cls.addConstructor(ctor);
        return ctor;
    }

    private void installStringBuiltin() {
        stringClass = new ClassSymbol("String", SourceLocation.UNKNOWN, true);
        classes.put("String", stringClass);

        MethodSymbol length = new MethodSymbol(
                stringClass, "length", PrimitiveType.INT, List.of(),
                false, false, "_JN_String_length", SourceLocation.UNKNOWN);
        stringClass.addMethod(length);

        ParameterSymbol charAtParam = new ParameterSymbol("index", PrimitiveType.INT, SourceLocation.UNKNOWN);
        MethodSymbol charAt = new MethodSymbol(
                stringClass, "charAt", PrimitiveType.CHAR, List.of(charAtParam),
                false, false, "_JN_String_charAt_i32", SourceLocation.UNKNOWN);
        stringClass.addMethod(charAt);

        ParameterSymbol eqParam = new ParameterSymbol("other", stringClass.asType(), SourceLocation.UNKNOWN);
        MethodSymbol equals = new MethodSymbol(
                stringClass, "equals", PrimitiveType.BOOLEAN, List.of(eqParam),
                false, false, "_JN_String_equals_CString", SourceLocation.UNKNOWN);
        stringClass.addMethod(equals);

        stringClass.setLayout(ObjectLayoutBuilder.build(stringClass));
        stringClass.setVtable(new io.jnative.vtable.VTable(stringClass, List.of()));
    }

    private boolean isThrowableType(JType type) {
        if (!(type instanceof ClassType ct)) {
            return false;
        }
        return ct.classSymbol().isSubtypeOf(throwableClass);
    }

    private void declareClass(ClassDeclaration cls) {
        if (classes.containsKey(cls.name())) {
            reporter.error(cls.location(), "duplicate class '" + cls.name() + "'");
            return;
        }
        classes.put(cls.name(), new ClassSymbol(cls.name(), cls.location(), false));
    }

    private void linkSuperClass(ClassDeclaration cls) {
        ClassSymbol symbol = classes.get(cls.name());
        if (symbol == null || cls.superClassName() == null) {
            return;
        }
        if (cls.superClassName().equals(cls.name())) {
            reporter.error(cls.location(), "class '" + cls.name() + "' cannot extend itself");
            return;
        }
        ClassSymbol superSymbol = classes.get(cls.superClassName());
        if (superSymbol == null) {
            reporter.error(cls.location(), "undefined class '" + cls.superClassName() + "'");
            return;
        }
        if (superSymbol == stringClass) {
            reporter.error(cls.location(), "class '" + cls.name() + "' cannot extend String");
            return;
        }
        symbol.setSuperClass(superSymbol);
    }

    private void detectInheritanceCycles(CompilationUnit unit) {
        Map<ClassSymbol, Integer> color = new HashMap<>();
        for (ClassSymbol cls : classes.values()) {
            if (!cls.isBuiltin()) {
                color.put(cls, 0);
            }
        }
        for (ClassDeclaration decl : unit.classes()) {
            ClassSymbol cls = classes.get(decl.name());
            if (cls == null || cls.isBuiltin()) {
                continue;
            }
            if (color.getOrDefault(cls, 2) == 0) {
                dfsCycle(cls, color);
            }
        }
    }

    private void dfsCycle(ClassSymbol cls, Map<ClassSymbol, Integer> color) {
        color.put(cls, 1);
        cls.superClass().ifPresent(superClass -> {
            int c = color.getOrDefault(superClass, 2);
            if (c == 1) {
                reporter.error(cls.location(), "cyclic inheritance involving '" + cls.name() + "'");
                cls.setSuperClass(null);
            } else if (c == 0) {
                dfsCycle(superClass, color);
            }
        });
        color.put(cls, 2);
    }

    private List<ClassSymbol> topologicalClasses() {
        List<ClassSymbol> result = new ArrayList<>();
        Set<ClassSymbol> visiting = new HashSet<>();
        Set<ClassSymbol> visited = new HashSet<>();
        for (ClassSymbol cls : classes.values()) {
            if (!cls.isBuiltin()) {
                topoVisit(cls, visiting, visited, result);
            }
        }
        return result;
    }

    private void topoVisit(
            ClassSymbol cls,
            Set<ClassSymbol> visiting,
            Set<ClassSymbol> visited,
            List<ClassSymbol> result
    ) {
        if (visited.contains(cls) || cls.isBuiltin()) {
            return;
        }
        if (!visiting.add(cls)) {
            return;
        }
        cls.superClass().ifPresent(s -> topoVisit(s, visiting, visited, result));
        visiting.remove(cls);
        visited.add(cls);
        result.add(cls);
    }

    private void registerFields(ClassSymbol symbol, ClassDeclaration cls) {
        int index = 0;
        for (FieldDeclaration field : cls.fields()) {
            JType type = resolveTypeName(field.type(), false, field.location());
            if (type instanceof VoidType) {
                reporter.error(field.location(), "field cannot have type void");
                type = ErrorType.INSTANCE;
            }
            FieldSymbol fs = new FieldSymbol(symbol, field.name(), type, index, field.location());
            if (!symbol.addField(fs)) {
                if (symbol.superClass().flatMap(s -> s.findField(field.name())).isPresent()) {
                    reporter.error(field.location(),
                            "field '" + field.name() + "' is already declared in a superclass");
                } else {
                    reporter.error(field.location(), "duplicate field '" + field.name() + "'");
                }
            } else {
                index++;
            }
        }
    }

    private void registerMethods(ClassSymbol symbol, ClassDeclaration cls) {
        for (MethodDeclaration method : cls.methods()) {
            if (method.isConstructor()) {
                continue;
            }
            List<ParameterSymbol> params = new ArrayList<>();
            for (Parameter p : method.parameters()) {
                JType pt = resolveTypeName(p.type(), p.isArray(), p.location());
                params.add(new ParameterSymbol(p.name(), pt, p.location()));
            }
            JType returnType = resolveTypeName(method.returnType(), false, method.location());

            boolean isMain = method.name().equals("main")
                    && method.isStatic()
                    && returnType instanceof VoidType
                    && params.size() == 1
                    && params.getFirst().type() instanceof ArrayType at
                    && at.elementType() instanceof ClassType ct
                    && ct.name().equals("String");

            String mangled = NameMangler.mangleMethod(
                    symbol.name(), method.name(),
                    params.stream().map(ParameterSymbol::type).toList(),
                    isMain);

            MethodSymbol ms = new MethodSymbol(
                    symbol, method.name(), returnType, params,
                    method.isStatic(), isMain, mangled, method.location());

            Optional<MethodSymbol> inherited = symbol.superClass()
                    .flatMap(s -> s.findMethod(method.name()));
            if (inherited.isPresent()) {
                MethodSymbol parent = inherited.get();
                if (parent.isStatic() || ms.isStatic()) {
                    if (parent.isStatic() && ms.isStatic()) {
                        reporter.error(method.location(),
                                "method overloading/hiding of static '" + method.name()
                                        + "' is not supported");
                    } else {
                        reporter.error(method.location(),
                                "instance/static mismatch overriding '" + method.name() + "'");
                    }
                } else if (!ms.sameSignatureAs(parent)) {
                    reporter.error(method.location(),
                            "method '" + method.name()
                                    + "' does not correctly override superclass method");
                } else {
                    ms.setOverrides(parent);
                }
            }

            if (!symbol.addMethod(ms)) {
                reporter.error(method.location(),
                        "method overloading is not supported yet (duplicate '"
                                + method.name() + "')");
            }
        }
    }

    private void registerConstructors(ClassSymbol symbol, ClassDeclaration cls) {
        for (MethodDeclaration method : cls.methods()) {
            if (!method.isConstructor()) {
                continue;
            }
            if (!method.name().equals(cls.name())) {
                reporter.error(method.location(), "constructor name must match class name");
            }
            List<ParameterSymbol> params = new ArrayList<>();
            for (Parameter p : method.parameters()) {
                JType pt = resolveTypeName(p.type(), p.isArray(), p.location());
                params.add(new ParameterSymbol(p.name(), pt, p.location()));
            }
            // Check duplicate signatures
            for (ConstructorSymbol existing : symbol.constructors()) {
                if (existing.parameterTypes().equals(params.stream().map(ParameterSymbol::type).toList())) {
                    reporter.error(method.location(), "duplicate constructor");
                }
            }
            ConstructorSymbol ctor = new ConstructorSymbol(symbol, params, false, method.location());
            symbol.addConstructor(ctor);
            constructorAst.put(ctor, method);
        }
        if (symbol.constructors().isEmpty()) {
            ConstructorSymbol implicit = new ConstructorSymbol(
                    symbol, List.of(), true, cls.location());
            symbol.addConstructor(implicit);
        }
    }

    private void resolveConstructorDelegations(CompilationUnit unit) {
        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol == null) {
                continue;
            }
            for (ConstructorSymbol ctor : symbol.constructors()) {
                MethodDeclaration ast = constructorAst.get(ctor);
                if (ast == null) {
                    // implicit: call super() if superclass exists
                    linkImplicitSuper(ctor, symbol);
                    continue;
                }
                List<Statement> stmts = ast.body().statements();
                if (!stmts.isEmpty() && stmts.getFirst() instanceof ConstructorDelegationStatement del) {
                    // validate only first
                    for (int i = 1; i < stmts.size(); i++) {
                        if (stmts.get(i) instanceof ConstructorDelegationStatement d2) {
                            reporter.error(d2.location(),
                                    "constructor delegation must be the first statement");
                        }
                    }
                    List<JType> argTypes = new ArrayList<>();
                    // Args typed later in analyze; for resolution use preliminary types
                    // We'll resolve again during analyze — here resolve structure with placeholder
                    // Actually we need types: do a light resolve of arg expressions without full context?
                    // Defer full resolve to analyzeConstructor; here only mark needsDelegation.
                    // For cycle detection we need delegatesTo set. Resolve using expression analysis
                    // in a temporary constructor context.
                    currentClass = symbol;
                    currentConstructor = ctor;
                    current = new Scope(null);
                    current.declare(new Symbol.VariableBinding(
                            new VariableSymbol("this", symbol.asType(),
                                    VariableSymbol.Kind.PARAMETER, ctor.location())));
                    for (ParameterSymbol p : ctor.parameters()) {
                        current.declare(new Symbol.VariableBinding(
                                new VariableSymbol(p.name(), p.type(),
                                        VariableSymbol.Kind.PARAMETER, p.location())));
                    }
                    for (Expression arg : del.arguments()) {
                        argTypes.add(analyzeExpression(arg));
                    }
                    current = null;
                    currentConstructor = null;
                    currentClass = null;

                    List<CallableResolver.Candidate> candidates = new ArrayList<>();
                    ClassSymbol targetOwner = del.isSuper()
                            ? symbol.superClass().orElse(null)
                            : symbol;
                    if (targetOwner == null) {
                        reporter.error(del.location(), "no superclass for super(...)");
                        continue;
                    }
                    for (ConstructorSymbol c : targetOwner.constructors()) {
                        candidates.add(new CallableResolver.Candidate(c.parameterTypes(), c));
                    }
                    Object resolved = CallableResolver.resolve(candidates, argTypes);
                    if (!(resolved instanceof ConstructorSymbol target)) {
                        reporter.error(del.location(), "no matching constructor for delegation");
                    } else {
                        ctor.setDelegatesTo(target);
                        ctor.setDelegatesToSuper(del.isSuper());
                    }
                } else {
                    // Check no later delegation
                    for (Statement s : stmts) {
                        if (s instanceof ConstructorDelegationStatement d) {
                            reporter.error(d.location(),
                                    "constructor delegation must be the first statement");
                        }
                    }
                    linkImplicitSuper(ctor, symbol);
                }
            }
        }
    }

    private void linkImplicitSuper(ConstructorSymbol ctor, ClassSymbol symbol) {
        symbol.superClass().ifPresent(superClass -> {
            ConstructorSymbol noArg = null;
            for (ConstructorSymbol c : superClass.constructors()) {
                if (c.parameters().isEmpty()) {
                    noArg = c;
                    break;
                }
            }
            if (noArg == null) {
                reporter.error(ctor.location(),
                        "implicit super() not found; call super(...) explicitly");
            } else {
                ctor.setDelegatesTo(noArg);
                ctor.setDelegatesToSuper(true);
            }
        });
    }

    private void detectConstructorCycles() {
        for (ClassSymbol cls : classes.values()) {
            for (ConstructorSymbol ctor : cls.constructors()) {
                Set<ConstructorSymbol> seen = new HashSet<>();
                ConstructorSymbol cur = ctor;
                while (cur != null && cur.delegatesTo() != null && !cur.delegatesToSuper()) {
                    if (!seen.add(cur)) {
                        reporter.error(ctor.location(), "constructor delegation cycle");
                        break;
                    }
                    cur = cur.delegatesTo();
                    if (seen.contains(cur)) {
                        reporter.error(ctor.location(), "constructor delegation cycle");
                        break;
                    }
                }
            }
        }
    }

    private void analyzeConstructor(ClassSymbol owner, MethodDeclaration method) {
        ConstructorSymbol ctor = findCtor(owner, method);
        if (ctor == null) {
            return;
        }
        currentConstructor = ctor;
        currentMethod = null;
        current = new Scope(null);
        current.declare(new Symbol.VariableBinding(
                new VariableSymbol("this", owner.asType(),
                        VariableSymbol.Kind.PARAMETER, method.location())));
        for (ParameterSymbol p : ctor.parameters()) {
            VariableSymbol vs = new VariableSymbol(
                    p.name(), p.type(), VariableSymbol.Kind.PARAMETER, p.location());
            if (!current.declare(new Symbol.VariableBinding(vs))) {
                reporter.error(p.location(), "duplicate parameter '" + p.name() + "'");
            }
        }
        analyzeStatement(method.body());
        current = null;
        currentConstructor = null;
    }

    private ConstructorSymbol findCtor(ClassSymbol owner, MethodDeclaration method) {
        List<JType> pts = new ArrayList<>();
        for (Parameter p : method.parameters()) {
            pts.add(resolveTypeName(p.type(), p.isArray(), p.location()));
        }
        for (ConstructorSymbol c : owner.constructors()) {
            if (c.parameterTypes().equals(pts)) {
                return c;
            }
        }
        return null;
    }

    private void analyzeMethod(ClassSymbol owner, MethodDeclaration method) {
        Optional<MethodSymbol> opt = owner.findDeclaredMethod(method.name());
        if (opt.isEmpty()) {
            return;
        }
        currentMethod = opt.get();
        currentConstructor = null;
        current = new Scope(null);

        if (!currentMethod.isStatic()) {
            current.declare(new Symbol.VariableBinding(
                    new VariableSymbol("this", owner.asType(),
                            VariableSymbol.Kind.PARAMETER, method.location())));
        }
        for (ParameterSymbol p : currentMethod.parameters()) {
            VariableSymbol vs = new VariableSymbol(
                    p.name(), p.type(), VariableSymbol.Kind.PARAMETER, p.location());
            if (!current.declare(new Symbol.VariableBinding(vs))) {
                reporter.error(p.location(), "duplicate parameter '" + p.name() + "'");
            }
        }
        analyzeStatement(method.body());
        if (!(currentMethod.returnType() instanceof VoidType)
                && !(currentMethod.returnType() instanceof ErrorType)) {
            if (!hasReturn(method.body())) {
                reporter.error(method.location(),
                        "missing return statement in method '" + method.name() + "'");
            }
        }
        current = null;
        currentMethod = null;
    }

    private boolean hasReturn(Statement stmt) {
        return switch (stmt) {
            case ReturnStatement ignored -> true;
            case BlockStatement b -> b.statements().stream().anyMatch(this::hasReturn);
            case IfStatement i -> hasReturn(i.thenBranch())
                    && i.elseBranch() != null && hasReturn(i.elseBranch());
            case TryStatement t -> hasReturn(t.tryBlock())
                    || t.catches().stream().anyMatch(c -> hasReturn(c.body()))
                    || (t.finallyBlock() != null && hasReturn(t.finallyBlock()));
            default -> false;
        };
    }

    private void analyzeStatement(Statement stmt) {
        switch (stmt) {
            case BlockStatement b -> {
                Scope previous = current;
                current = new Scope(previous);
                for (Statement s : b.statements()) {
                    analyzeStatement(s);
                }
                current = previous;
            }
            case VariableDeclaration v -> analyzeVariableDeclaration(v);
            case ExpressionStatement e -> analyzeExpression(e.expression());
            case IfStatement i -> {
                expectBoolean(analyzeExpression(i.condition()), i.condition().location(), "if condition");
                analyzeStatement(i.thenBranch());
                if (i.elseBranch() != null) {
                    analyzeStatement(i.elseBranch());
                }
            }
            case WhileStatement w -> {
                expectBoolean(analyzeExpression(w.condition()), w.condition().location(), "while condition");
                analyzeStatement(w.body());
            }
            case ForStatement f -> {
                Scope previous = current;
                current = new Scope(previous);
                if (f.initializer() != null) {
                    analyzeStatement(f.initializer());
                }
                if (f.condition() != null) {
                    expectBoolean(analyzeExpression(f.condition()),
                            f.condition().location(), "for condition");
                }
                if (f.update() != null) {
                    analyzeExpression(f.update());
                }
                analyzeStatement(f.body());
                current = previous;
            }
            case ReturnStatement r -> analyzeReturn(r);
            case ConstructorDelegationStatement d -> {
                for (Expression arg : d.arguments()) {
                    analyzeExpression(arg);
                }
                // Target already resolved in resolveConstructorDelegations; store on a side map
                if (currentConstructor != null && currentConstructor.delegatesTo() != null) {
                    // Keep for IR via ConstructorSymbol.delegatesTo
                }
            }
            case EmptyStatement ignored -> {
            }
            case ThrowStatement t -> analyzeThrow(t);
            case TryStatement t -> analyzeTry(t);
        }
    }

    private void analyzeThrow(ThrowStatement t) {
        JType type = analyzeExpression(t.expression());
        if (!isThrowableType(type) && !(type instanceof ErrorType)) {
            reporter.error(t.location(), "throw expression must be a Throwable subtype");
        }
    }

    private void analyzeTry(TryStatement t) {
        analyzeStatement(t.tryBlock());
        List<ClassSymbol> seen = new ArrayList<>();
        for (CatchClause c : t.catches()) {
            JType catchType = resolveTypeName(c.exceptionType(), false, c.location());
            if (!(catchType instanceof ClassType ct)) {
                reporter.error(c.location(), "catch type must be a class");
                continue;
            }
            if (!isThrowableType(catchType)) {
                reporter.error(c.location(), "catch type must be a Throwable subtype");
            }
            for (ClassSymbol earlier : seen) {
                if (ct.classSymbol().isSubtypeOf(earlier)) {
                    reporter.error(c.location(), "unreachable catch clause");
                }
            }
            seen.add(ct.classSymbol());
            catchTypes.put(c, ct.classSymbol());
            Scope previous = current;
            current = new Scope(previous);
            VariableSymbol vs = new VariableSymbol(
                    c.variableName(), catchType, VariableSymbol.Kind.LOCAL, c.location());
            if (!current.declare(new Symbol.VariableBinding(vs))) {
                reporter.error(c.location(), "duplicate variable '" + c.variableName() + "'");
            }
            analyzeStatement(c.body());
            current = previous;
        }
        if (t.finallyBlock() != null) {
            analyzeStatement(t.finallyBlock());
        }
    }

    private void analyzeVariableDeclaration(VariableDeclaration v) {
        JType type = resolveTypeName(v.type(), v.isArray(), v.location());
        if (type instanceof VoidType) {
            reporter.error(v.location(), "cannot declare variable of type void");
            type = ErrorType.INSTANCE;
        }
        if (v.initializer() != null) {
            JType initType = analyzeExpression(v.initializer());
            if (!type.isAssignableFrom(initType)) {
                reporter.error(v.location(),
                        "incompatible types: " + initType + " cannot be converted to " + type);
            }
        }
        VariableSymbol vs = new VariableSymbol(
                v.name(), type, VariableSymbol.Kind.LOCAL, v.location());
        if (!current.declare(new Symbol.VariableBinding(vs))) {
            reporter.error(v.location(), "duplicate variable '" + v.name() + "'");
        }
    }

    private void analyzeReturn(ReturnStatement r) {
        if (currentConstructor != null) {
            if (r.value() != null) {
                reporter.error(r.location(), "constructors cannot return a value");
                analyzeExpression(r.value());
            }
            return;
        }
        if (currentMethod == null) {
            reporter.error(r.location(), "return outside method");
            return;
        }
        JType expected = currentMethod.returnType();
        if (r.value() == null) {
            if (!(expected instanceof VoidType)) {
                reporter.error(r.location(), "missing return value; expected " + expected);
            }
            return;
        }
        JType actual = analyzeExpression(r.value());
        if (expected instanceof VoidType) {
            reporter.error(r.location(), "cannot return a value from void method");
        } else if (!expected.isAssignableFrom(actual)) {
            reporter.error(r.location(),
                    "incompatible return type: " + actual + " cannot be converted to " + expected);
        }
    }

    private JType analyzeExpression(Expression expr) {
        JType type = switch (expr) {
            case LiteralExpression l -> typeOfLiteral(l);
            case NullLiteralExpression ignored -> NullType.INSTANCE;
            case ThisExpression t -> typeOfThis(t);
            case VariableExpression v -> typeOfVariable(v);
            case AssignmentExpression a -> typeOfAssignment(a);
            case BinaryExpression b -> typeOfBinary(b);
            case UnaryExpression u -> typeOfUnary(u);
            case MethodCallExpression m -> typeOfCall(m);
            case PrintlnExpression p -> typeOfPrintln(p);
            case GroupingExpression g -> analyzeExpression(g.expression());
            case NewObjectExpression n -> typeOfNew(n);
            case FieldAccessExpression f -> typeOfFieldAccess(f);
            case NewArrayExpression n -> typeOfNewArray(n);
            case ArrayAccessExpression a -> typeOfArrayAccess(a);
            case InstanceOfExpression i -> typeOfInstanceOf(i);
            case CastExpression c -> typeOfCast(c);
            case GcCollectExpression ignored -> VoidType.INSTANCE;
        };
        expressionTypes.put(expr, type);
        return type;
    }

    private JType typeOfLiteral(LiteralExpression l) {
        return switch (l.kind()) {
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case STRING -> stringClass.asType();
        };
    }

    private JType typeOfThis(ThisExpression t) {
        if (currentConstructor != null) {
            return currentClass.asType();
        }
        if (currentMethod == null || currentMethod.isStatic()) {
            reporter.error(t.location(), "'this' is not available in a static context");
            return ErrorType.INSTANCE;
        }
        return currentClass.asType();
    }

    private JType typeOfVariable(VariableExpression v) {
        Optional<Symbol> symbol = current.resolve(v.name());
        if (symbol.isPresent() && symbol.get() instanceof Symbol.VariableBinding vb) {
            resolvedSymbols.put(v, vb.variable());
            return vb.variable().type();
        }
        boolean inInstance = currentConstructor != null
                || (currentMethod != null && !currentMethod.isStatic());
        if (inInstance && currentClass != null) {
            Optional<FieldSymbol> field = currentClass.findField(v.name());
            if (field.isPresent()) {
                resolvedSymbols.put(v, field.get());
                return field.get().type();
            }
        }
        reporter.error(v.location(), "undefined variable '" + v.name() + "'");
        return ErrorType.INSTANCE;
    }

    private JType typeOfAssignment(AssignmentExpression a) {
        JType targetType = analyzeExpression(a.target());
        JType valueType = analyzeExpression(a.value());

        if (!(a.target() instanceof VariableExpression)
                && !(a.target() instanceof FieldAccessExpression)
                && !(a.target() instanceof ArrayAccessExpression)) {
            reporter.error(a.location(), "invalid assignment target");
            return ErrorType.INSTANCE;
        }
        if (a.target() instanceof VariableExpression ve && ve.name().equals("this")) {
            reporter.error(a.location(), "cannot assign to 'this'");
            return ErrorType.INSTANCE;
        }
        if (!targetType.isAssignableFrom(valueType)) {
            reporter.error(a.location(),
                    "incompatible types: " + valueType + " cannot be converted to " + targetType);
            return ErrorType.INSTANCE;
        }
        return targetType;
    }

    private JType typeOfBinary(BinaryExpression b) {
        JType left = analyzeExpression(b.left());
        JType right = analyzeExpression(b.right());
        return switch (b.operator()) {
            case ADD -> {
                if (isString(left) && isString(right)) {
                    yield stringClass.asType();
                }
                if (!left.isNumeric() || !right.isNumeric()) {
                    if (!(left instanceof ErrorType) && !(right instanceof ErrorType)) {
                        reporter.error(b.location(),
                                "operator + cannot be applied to " + left + ", " + right);
                    }
                    yield ErrorType.INSTANCE;
                }
                yield JType.binaryArithmeticResult(left, right);
            }
            case SUB, MUL, DIV, MOD -> {
                if (!left.isNumeric() || !right.isNumeric()) {
                    if (!(left instanceof ErrorType) && !(right instanceof ErrorType)) {
                        reporter.error(b.location(),
                                "operator " + b.operator() + " cannot be applied to "
                                        + left + ", " + right);
                    }
                    yield ErrorType.INSTANCE;
                }
                if (b.operator() == BinaryOp.MOD
                        && (left == PrimitiveType.DOUBLE || right == PrimitiveType.DOUBLE)) {
                    reporter.error(b.location(), "operator % cannot be applied to double");
                    yield ErrorType.INSTANCE;
                }
                yield JType.binaryArithmeticResult(left, right);
            }
            case LT, LE, GT, GE -> {
                if (!left.isNumeric() || !right.isNumeric()) {
                    if (!(left instanceof ErrorType) && !(right instanceof ErrorType)) {
                        reporter.error(b.location(),
                                "operator " + b.operator() + " cannot be applied to "
                                        + left + ", " + right);
                    }
                    yield ErrorType.INSTANCE;
                }
                yield PrimitiveType.BOOLEAN;
            }
            case EQ, NE -> {
                if (left instanceof ErrorType || right instanceof ErrorType) {
                    yield ErrorType.INSTANCE;
                }
                if (left.isReference() || right.isReference()
                        || left instanceof NullType || right instanceof NullType) {
                    if ((left.isReference() || left instanceof NullType)
                            && (right.isReference() || right instanceof NullType)) {
                        if (left instanceof NullType || right instanceof NullType
                                || left.isAssignableFrom(right) || right.isAssignableFrom(left)) {
                            yield PrimitiveType.BOOLEAN;
                        }
                    }
                    reporter.error(b.location(), "incomparable types: " + left + " and " + right);
                    yield ErrorType.INSTANCE;
                }
                if (!left.equals(right) && !(left.isNumeric() && right.isNumeric())) {
                    reporter.error(b.location(), "incomparable types: " + left + " and " + right);
                    yield ErrorType.INSTANCE;
                }
                yield PrimitiveType.BOOLEAN;
            }
            case AND, OR -> {
                expectBoolean(left, b.left().location(), "operand of " + b.operator());
                expectBoolean(right, b.right().location(), "operand of " + b.operator());
                yield PrimitiveType.BOOLEAN;
            }
        };
    }

    private boolean isString(JType t) {
        return t instanceof ClassType ct && ct.name().equals("String");
    }

    private JType typeOfUnary(UnaryExpression u) {
        JType operand = analyzeExpression(u.operand());
        return switch (u.operator()) {
            case NOT -> {
                expectBoolean(operand, u.location(), "operand of !");
                yield PrimitiveType.BOOLEAN;
            }
            case NEGATE, PLUS -> {
                if (!operand.isNumeric() && !(operand instanceof ErrorType)) {
                    reporter.error(u.location(), "unary operator cannot be applied to " + operand);
                    yield ErrorType.INSTANCE;
                }
                yield operand;
            }
        };
    }

    private JType typeOfCall(MethodCallExpression m) {
        for (Expression arg : m.arguments()) {
            analyzeExpression(arg);
        }
        if (m.receiver() != null) {
            JType recvType = analyzeExpression(m.receiver());
            if (!(recvType instanceof ClassType ct)) {
                if (!(recvType instanceof ErrorType)) {
                    reporter.error(m.location(),
                            "cannot invoke method on non-reference type " + recvType);
                }
                return ErrorType.INSTANCE;
            }
            Optional<MethodSymbol> method = ct.classSymbol().findMethod(m.name());
            if (method.isEmpty()) {
                reporter.error(m.location(),
                        "undefined method '" + m.name() + "' in class '" + ct.name() + "'");
                return ErrorType.INSTANCE;
            }
            MethodSymbol ms = method.get();
            if (ms.isStatic()) {
                reporter.error(m.location(),
                        "static method '" + m.name() + "' should be called without an instance");
            }
            checkArguments(m, ms);
            resolvedSymbols.put(m, ms);
            return ms.returnType();
        }

        if (currentClass != null) {
            Optional<MethodSymbol> method = currentClass.findMethod(m.name());
            if (method.isPresent()) {
                MethodSymbol ms = method.get();
                boolean inInstance = currentConstructor != null
                        || (currentMethod != null && !currentMethod.isStatic());
                if (!ms.isStatic() && !inInstance) {
                    reporter.error(m.location(),
                            "cannot call instance method '" + m.name() + "' from a static context");
                    return ErrorType.INSTANCE;
                }
                checkArguments(m, ms);
                resolvedSymbols.put(m, ms);
                return ms.returnType();
            }
        }

        MethodSymbol found = null;
        for (ClassSymbol cls : classes.values()) {
            Optional<MethodSymbol> method = cls.findDeclaredMethod(m.name());
            if (method.isPresent() && method.get().isStatic()) {
                if (found != null) {
                    reporter.error(m.location(), "ambiguous static method '" + m.name() + "'");
                    return ErrorType.INSTANCE;
                }
                found = method.get();
            }
        }
        if (found != null) {
            checkArguments(m, found);
            resolvedSymbols.put(m, found);
            return found.returnType();
        }
        reporter.error(m.location(), "undefined method '" + m.name() + "'");
        return ErrorType.INSTANCE;
    }

    private void checkArguments(MethodCallExpression m, MethodSymbol ms) {
        List<JType> expected = ms.parameterTypes();
        List<Expression> args = m.arguments();
        if (args.size() != expected.size()) {
            reporter.error(m.location(),
                    "method '" + m.name() + "' expects " + expected.size()
                            + " argument(s) but got " + args.size());
        }
        int n = Math.min(args.size(), expected.size());
        for (int i = 0; i < n; i++) {
            JType actual = expressionTypes.getOrDefault(args.get(i), ErrorType.INSTANCE);
            if (!expected.get(i).isAssignableFrom(actual)) {
                reporter.error(args.get(i).location(),
                        "argument " + (i + 1) + ": " + actual
                                + " cannot be converted to " + expected.get(i));
            }
        }
    }

    private JType typeOfPrintln(PrintlnExpression p) {
        JType arg = analyzeExpression(p.argument());
        if (!(arg instanceof PrimitiveType)
                && !(arg instanceof ClassType ct && ct.name().equals("String"))
                && !(arg instanceof ErrorType)) {
            reporter.error(p.location(), "println does not support type " + arg);
        }
        return VoidType.INSTANCE;
    }

    private JType typeOfNew(NewObjectExpression n) {
        ClassSymbol symbol = classes.get(n.className());
        if (symbol == null || symbol.isBuiltin()) {
            reporter.error(n.location(), "undefined class '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        List<JType> argTypes = new ArrayList<>();
        for (Expression arg : n.arguments()) {
            argTypes.add(analyzeExpression(arg));
        }
        List<CallableResolver.Candidate> candidates = new ArrayList<>();
        for (ConstructorSymbol c : symbol.constructors()) {
            candidates.add(new CallableResolver.Candidate(c.parameterTypes(), c));
        }
        Object resolved = CallableResolver.resolve(candidates, argTypes);
        if (!(resolved instanceof ConstructorSymbol ctor)) {
            reporter.error(n.location(),
                    "no matching constructor for " + n.className()
                            + "(" + NameMangler.describeSignature(argTypes) + ")");
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(n, ctor);
        return symbol.asType();
    }

    private JType typeOfNewArray(NewArrayExpression n) {
        JType element = resolveTypeName(n.elementType(), false, n.location());
        if (element instanceof VoidType || element instanceof ErrorType) {
            reporter.error(n.location(), "invalid array element type");
            return ErrorType.INSTANCE;
        }
        JType lenType = analyzeExpression(n.length());
        if (!lenType.isInteger() && !(lenType instanceof ErrorType)) {
            reporter.error(n.length().location(), "array length must be int-compatible");
        }
        ClassMetadata meta = arrayMetadataFor(element);
        resolvedSymbols.put(n, meta);
        return new ArrayType(element);
    }

    private JType typeOfArrayAccess(ArrayAccessExpression a) {
        JType arr = analyzeExpression(a.array());
        JType idx = analyzeExpression(a.index());
        if (!idx.isInteger() && !(idx instanceof ErrorType)) {
            reporter.error(a.index().location(), "array index must be int-compatible");
        }
        if (!(arr instanceof ArrayType at)) {
            if (!(arr instanceof ErrorType)) {
                reporter.error(a.location(), "array access on non-array type " + arr);
            }
            return ErrorType.INSTANCE;
        }
        return at.elementType();
    }

    private JType typeOfFieldAccess(FieldAccessExpression f) {
        JType recvType = analyzeExpression(f.receiver());
        if (recvType instanceof ArrayType && f.fieldName().equals("length")) {
            resolvedSymbols.put(f, ArrayLengthSymbol.INSTANCE);
            return PrimitiveType.INT;
        }
        if (!(recvType instanceof ClassType ct)) {
            if (!(recvType instanceof ErrorType) && !(recvType instanceof NullType)) {
                reporter.error(f.location(),
                        "cannot access field on non-reference type " + recvType);
            } else if (recvType instanceof NullType) {
                reporter.error(f.location(), "cannot access field on null");
            }
            return ErrorType.INSTANCE;
        }
        Optional<FieldSymbol> field = ct.classSymbol().findField(f.fieldName());
        if (field.isEmpty()) {
            reporter.error(f.location(),
                    "unknown field '" + f.fieldName() + "' in class '" + ct.name() + "'");
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(f, field.get());
        return field.get().type();
    }

    private JType typeOfInstanceOf(InstanceOfExpression i) {
        analyzeExpression(i.expression());
        JType target = resolveTypeName(i.type(), false, i.location());
        if (!(target instanceof ClassType ct)) {
            reporter.error(i.location(), "instanceof requires a class type");
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(i, ct.classSymbol().metadata());
        return PrimitiveType.BOOLEAN;
    }

    private JType typeOfCast(CastExpression c) {
        JType source = analyzeExpression(c.expression());
        JType target = resolveTypeName(c.type(), false, c.location());
        if (!(target instanceof ClassType targetCt)) {
            reporter.error(c.location(), "cast target must be a class type");
            return ErrorType.INSTANCE;
        }
        if (source instanceof NullType || source instanceof ErrorType) {
            resolvedSymbols.put(c, targetCt.classSymbol().metadata());
            return target;
        }
        if (!(source instanceof ClassType sourceCt)) {
            reporter.error(c.location(), "cannot cast " + source + " to " + target);
            return ErrorType.INSTANCE;
        }
        ClassSymbol s = sourceCt.classSymbol();
        ClassSymbol t = targetCt.classSymbol();
        if (!(s.isSubtypeOf(t) || t.isSubtypeOf(s))) {
            reporter.error(c.location(), "impossible cast from " + source + " to " + target);
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(c, t.metadata());
        return target;
    }

    private ClassMetadata arrayMetadataFor(JType element) {
        String key = NameMangler.arrayMetaName(element);
        return arrayMetadatas.computeIfAbsent(key, k ->
                new ClassMetadata(k, element.isReference(), element));
    }

    private void expectBoolean(JType type, SourceLocation location, String context) {
        if (type != PrimitiveType.BOOLEAN && !(type instanceof ErrorType)) {
            reporter.error(location, context + " must be boolean, found " + type);
        }
    }

    private JType resolveTypeName(TypeName typeName, boolean isArray, SourceLocation fallback) {
        SourceLocation loc = typeName.location() != null ? typeName.location() : fallback;
        JType base = switch (typeName.kind()) {
            case VOID -> VoidType.INSTANCE;
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case CHAR -> PrimitiveType.CHAR;
            case CLASS -> {
                ClassSymbol symbol = classes.get(typeName.name());
                if (symbol == null) {
                    reporter.error(loc, "undefined class '" + typeName.name() + "'");
                    yield ErrorType.INSTANCE;
                }
                yield symbol.asType();
            }
        };
        if (isArray) {
            if (base instanceof VoidType || base instanceof ErrorType) {
                reporter.error(loc, "invalid array type");
                return ErrorType.INSTANCE;
            }
            arrayMetadataFor(base);
            return new ArrayType(base);
        }
        return base;
    }
}
