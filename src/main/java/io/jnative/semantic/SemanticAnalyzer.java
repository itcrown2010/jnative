package io.jnative.semantic;

import io.jnative.ast.*;
import io.jnative.ast.ImportDeclaration;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.generic.TypeInferenceEngine;
import io.jnative.generic.TypeSubstitution;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.mangle.NameMangler;
import io.jnative.ffi.AbiType;
import io.jnative.ffi.CallingConvention;
import io.jnative.ffi.NativeFunction;
import io.jnative.ffi.NativeSignature;
import io.jnative.ffi.NativeStructLayoutBuilder;
import io.jnative.ffi.PointerProvenance;
import io.jnative.ffi.TargetAbi;
import io.jnative.iface.FunctionalInterfaceAnalyzer;
import io.jnative.iface.InterfaceSlotBuilder;
import io.jnative.vtable.ITableBuilder;
import io.jnative.vtable.VTableBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic analysis for JNative 0.8 (native data model, constructors, arrays, String, RTTI).
 * @author 张晓达
 */
public final class SemanticAnalyzer {
    private final DiagnosticReporter reporter;
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();
    private final Map<String, InterfaceSymbol> interfaces = new LinkedHashMap<>();
    private final Map<String, NativeStructSymbol> nativeStructs = new LinkedHashMap<>();
    /** Simple type name → FQNs (for same-package / ambiguity diagnostics). */
    private final Map<String, List<String>> typesBySimpleName = new HashMap<>();
    private final Map<InterfaceSymbol, ClassSymbol> interfaceAdapters = new LinkedHashMap<>();
    private final Map<Expression, JType> expressionTypes = new HashMap<>();
    private final Map<Expression, Object> resolvedSymbols = new HashMap<>();
    private final Map<Expression, CallableBinding> callableBindings = new HashMap<>();
    private final Map<Expression, PointerProvenance> provenances = new HashMap<>();
    private final Map<String, ClassMetadata> arrayMetadatas = new LinkedHashMap<>();
    private final Map<ConstructorSymbol, MethodDeclaration> constructorAst = new HashMap<>();
    private final Map<CatchClause, ClassSymbol> catchTypes = new HashMap<>();
    private final Map<TryStatement, List<MethodCallExpression>> resourceCloseCalls = new HashMap<>();
    private final Map<EnhancedForStatement, EnhancedForDesugar> enhancedForDesugars = new HashMap<>();

    private Scope current;
    private ClassSymbol currentClass;
    /** Active package/imports for type resolution (class or interface declaration). */
    private String resolvePackage = "";
    private List<String> resolveImports = List.of();
    private MethodSymbol currentMethod;
    private ConstructorSymbol currentConstructor;
    private ClassSymbol stringClass;
    private ClassSymbol futureClass;
    private ClassSymbol objectClass;
    private ClassSymbol throwableClass;
    private ClassSymbol nativeMemoryClass;
    private ClassSymbol nativeClass;
    private boolean allowingStackNativeNew;
    private final TargetAbi targetAbi = TargetAbi.forHost();
    private final LinkedHashMap<String, String> nativeLibraries = new LinkedHashMap<>();
    private JType expectedSamType;
    /**
     * Nested lambda analysis frames (innermost at head). Captures propagate outward so
     * an inner lambda using an enclosing local also forces the outer closure env to hold it.
     */
    private final Deque<LambdaAnalysisFrame> lambdaStack = new ArrayDeque<>();
    private int envClassCounter;
    private int lambdaCounter;
    private int callbackCounter;

    private static final class LambdaAnalysisFrame {
        final Scope bodyScope;
        final LinkedHashSet<VariableSymbol> captured = new LinkedHashSet<>();

        LambdaAnalysisFrame(Scope bodyScope) {
            this.bodyScope = bodyScope;
        }
    }
    /** Only one `main(String[])` per CU may be the process entry (`jn_main`). */
    private boolean entryMainAssigned;
    private ClassSymbol runnableClass;
    private ClassSymbol nativeCallbackHandleClass;
    private final Deque<Map<String, TypeParameterSymbol>> typeParamScopes = new ArrayDeque<>();
    private boolean instanceofContext;
    /** Nested loop depth for {@code continue}. */
    private int loopDepth;
    /** Nested switch depth; {@code break} allowed when {@code loopDepth + switchDepth > 0}. */
    private int switchDepth;
    /** Definite-assignment environment for the current method/constructor body. */
    private DaEnv daEnv = new DaEnv();
    /** Whether the current statement position is reachable for DA purposes. */
    private boolean daReachable = true;
    /** When true, variable reads skip definite-assignment checks (assignment LHS). */
    private boolean skippingDaRead;
    /** Break exit DA states for the innermost loop/switch. */
    private final Deque<List<DaEnv>> breakDaExits = new ArrayDeque<>();
    /** Locals declared in the current block (removed from DA env on exit). */
    private final Deque<List<VariableSymbol>> blockDaLocals = new ArrayDeque<>();
    /** Locals declared inside each loop nest (blank final may be assigned once per iteration). */
    private final Deque<Set<VariableSymbol>> loopDeclaredLocals = new ArrayDeque<>();

    public SemanticAnalyzer(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }

    public TypedCompilationUnit analyze(CompilationUnit unit) {
        return analyze(List.of(unit));
    }

    /**
     * Project-level analysis over independently parsed compilation units.
     * File order must not affect resolution; declaration order within a type still matters for locals.
     */
    public TypedCompilationUnit analyze(List<CompilationUnit> units) {
        List<CompilationUnit> unitList = units == null || units.isEmpty()
                ? List.of()
                : List.copyOf(units);
        classes.clear();
        interfaces.clear();
        interfaceAdapters.clear();
        expressionTypes.clear();
        resolvedSymbols.clear();
        callableBindings.clear();
        arrayMetadatas.clear();
        constructorAst.clear();
        catchTypes.clear();
        resourceCloseCalls.clear();
        enhancedForDesugars.clear();
        nativeStructs.clear();
        typesBySimpleName.clear();
        provenances.clear();
        current = new Scope(null);
        currentClass = null;
        currentMethod = null;
        currentConstructor = null;
        allowingStackNativeNew = false;
        typeParamScopes.clear();
        instanceofContext = false;

        envClassCounter = 0;
        lambdaCounter = 0;
        callbackCounter = 0;
        CompilationUnit probe = unitList.isEmpty()
                ? new CompilationUnit("<empty>", List.of(), SourceLocation.UNKNOWN)
                : unitList.getFirst();
        installBuiltins(probe);
        // File.readBytes / argv need these metadatas present for the runtime.
        arrayMetadataFor(PrimitiveType.BYTE);
        arrayMetadataFor(stringClass.asType());
        nativeLibraries.clear();
        nativeStructs.clear();
        provenances.clear();

        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                if (cls.isInterface()) {
                    declareInterface(cls);
                } else if (cls.nativeStruct()) {
                    declareNativeStruct(cls);
                } else {
                    declareClass(cls);
                }
            }
        }
        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                beginResolve(cls);
                if (cls.isInterface()) {
                    linkSuperInterfaces(cls);
                } else if (!cls.nativeStruct()) {
                    currentClass = classOf(cls);
                    linkSuperClass(cls);
                    linkImplements(cls);
                }
                endResolve();
            }
        }
        detectInheritanceCycles(unitList);

        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                if (!cls.nativeStruct()) {
                    continue;
                }
                NativeStructSymbol symbol = nativeStructs.get(cls.qualifiedName());
                if (symbol != null) {
                    registerNativeStructFields(symbol, cls);
                }
            }
        }
        detectRecursiveNativeStructs();
        buildNativeStructLayouts();

        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                if (cls.nativeStruct() || cls.isInterface()) {
                    continue;
                }
                ClassSymbol symbol = classOf(cls);
                if (symbol != null) {
                    beginResolve(cls);
                    currentClass = symbol;
                    registerFields(symbol, cls);
                    endResolve();
                }
            }
        }
        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                if (cls.nativeStruct()) {
                    continue;
                }
                beginResolve(cls);
                if (cls.isInterface()) {
                    InterfaceSymbol symbol = interfaceOf(cls);
                    if (symbol != null) {
                        registerInterfaceMethods(symbol, cls);
                    }
                } else {
                    ClassSymbol symbol = classOf(cls);
                    if (symbol != null) {
                        currentClass = symbol;
                        if (cls.isEnum()) {
                            registerEnumSynthetics(symbol, cls);
                        } else {
                            registerMethods(symbol, cls);
                            registerConstructors(symbol, cls);
                        }
                    }
                }
                endResolve();
            }
        }

        int nextInterfaceId = 0;
        for (InterfaceSymbol iface : topologicalInterfaces()) {
            InterfaceSlotBuilder.buildSlots(iface);
            FunctionalInterfaceAnalyzer.analyze(iface);
            iface.setInterfaceId(nextInterfaceId++);
        }

        for (ClassSymbol symbol : topologicalClasses()) {
            if (!symbol.isBuiltin()) {
                symbol.setLayout(ObjectLayoutBuilder.build(symbol));
                symbol.setVtable(VTableBuilder.build(symbol));
            }
        }

        for (CompilationUnit unit : unitList) {
            verifyAbstractAndInterfaceCompleteness(unit);
        }
        if (!reporter.hasErrors()) {
            for (ClassSymbol symbol : classes.values()) {
                if (symbol.isBuiltin() || symbol.isAbstract()) {
                    continue;
                }
                if (symbol.transitiveInterfaces().isEmpty()) {
                    continue;
                }
                symbol.setItables(ITableBuilder.buildAll(symbol));
                for (InterfaceSymbol iface : symbol.transitiveInterfaces()) {
                    iface.addImplementor(symbol);
                }
            }
        }

        for (CompilationUnit unit : unitList) {
            resolveConstructorDelegations(unit);
        }
        detectConstructorCycles();

        for (CompilationUnit unit : unitList) {
            for (ClassDeclaration cls : unit.classes()) {
                if (cls.nativeStruct() || cls.isInterface()) {
                    continue;
                }
                ClassSymbol symbol = classOf(cls);
                if (symbol == null) {
                    continue;
                }
                beginResolve(cls);
                currentClass = symbol;
                analyzeStaticFieldInitializers(symbol);
                Map<String, Integer> overloadOrdinal = new HashMap<>();
                for (MethodDeclaration method : cls.methods()) {
                    if (method.isConstructor()) {
                        analyzeConstructor(symbol, method);
                    } else {
                        int idx = overloadOrdinal.merge(method.name(), 1, Integer::sum) - 1;
                        analyzeMethod(symbol, method, idx);
                    }
                }
                endResolve();
            }
        }

        CompilationUnit flat = new ProgramAst(unitList).flattened("<program>");
        return new TypedCompilationUnit(
                flat,
                unitList,
                Map.copyOf(classes),
                Map.copyOf(interfaces),
                Map.copyOf(nativeStructs),
                Map.copyOf(expressionTypes),
                Map.copyOf(resolvedSymbols),
                Map.copyOf(callableBindings),
                Map.copyOf(arrayMetadatas),
                Map.copyOf(catchTypes),
                Map.copyOf(resourceCloseCalls),
                Map.copyOf(enhancedForDesugars),
                List.copyOf(nativeLibraries.keySet()),
                targetAbi
        );
    }

    private void installBuiltins(CompilationUnit unit) {
        installObjectBuiltin();
        installStringBuiltin();
        installExceptionHierarchy();
        installCallableBuiltins();
        installFfiBuiltins();
        boolean hasStdlibFuture = unit.classes().stream()
                .anyMatch(c -> !c.isInterface() && !c.nativeStruct() && "Future".equals(c.name()));
        if (!hasStdlibFuture) {
            installAsyncBuiltins();
        }
    }

    /** Object with stable identity hashCode + reference-equality equals (0.16). */
    private void installObjectBuiltin() {
        objectClass = new ClassSymbol("Object", SourceLocation.UNKNOWN, true);
        classes.put("Object", objectClass);
        objectClass.addConstructor(new ConstructorSymbol(
                objectClass, List.of(), true, SourceLocation.UNKNOWN));

        ParameterSymbol equalsOther = new ParameterSymbol(
                "other", objectClass.asType(), SourceLocation.UNKNOWN);
        MethodSymbol equals = new MethodSymbol(
                objectClass, "equals", PrimitiveType.BOOLEAN, List.of(equalsOther),
                false, false,
                NameMangler.mangleMethod("Object", "equals", List.of(objectClass.asType()), false),
                SourceLocation.UNKNOWN);
        objectClass.addMethod(equals);

        MethodSymbol hashCode = new MethodSymbol(
                objectClass, "hashCode", PrimitiveType.INT, List.of(),
                false, false,
                NameMangler.mangleMethod("Object", "hashCode", List.of(), false),
                SourceLocation.UNKNOWN);
        objectClass.addMethod(hashCode);

        objectClass.setLayout(ObjectLayoutBuilder.build(objectClass));
        objectClass.setVtable(VTableBuilder.build(objectClass));
    }

    private void installExceptionHierarchy() {
        // Create hierarchy first (no layouts yet), then add Throwable.message + cause (both PTR),
        // then build layouts top-down so subclasses inherit field sizes.
        throwableClass = new ClassSymbol("Throwable", SourceLocation.UNKNOWN, true);
        classes.put("Throwable", throwableClass);
        throwableClass.setSuperClass(objectClass);
        throwableClass.addConstructor(new ConstructorSymbol(throwableClass, List.of(), true, SourceLocation.UNKNOWN));

        ClassSymbol exceptionClass = newExceptionClass("Exception", throwableClass);
        ClassSymbol runtimeExceptionClass = newExceptionClass("RuntimeException", exceptionClass);
        ClassSymbol ioExceptionClass = newExceptionClass("IOException", exceptionClass);
        ClassSymbol fileNotFoundClass = newExceptionClass("FileNotFoundException", ioExceptionClass);
        ClassSymbol socketExceptionClass = newExceptionClass("SocketException", ioExceptionClass);
        ClassSymbol connectExceptionClass = newExceptionClass("ConnectException", socketExceptionClass);
        ClassSymbol unknownHostClass = newExceptionClass("UnknownHostException", socketExceptionClass);
        ClassSymbol socketTimeoutClass = newExceptionClass("SocketTimeoutException", socketExceptionClass);

        FieldSymbol messageField = new FieldSymbol(
                throwableClass, "message", stringClass.asType(), 0, SourceLocation.UNKNOWN);
        throwableClass.addField(messageField);
        FieldSymbol causeField = new FieldSymbol(
                throwableClass, "cause", throwableClass.asType(), 1, SourceLocation.UNKNOWN);
        throwableClass.addField(causeField);
        FieldSymbol suppressedField = new FieldSymbol(
                throwableClass, "suppressed", new ArrayType(throwableClass.asType()),
                2, SourceLocation.UNKNOWN);
        throwableClass.addField(suppressedField);
        // Ensure Throwable[] metadata exists for getSuppressed / runtime helpers.
        arrayMetadataFor(throwableClass.asType());

        ParameterSymbol msgParam = new ParameterSymbol("message", stringClass.asType(), SourceLocation.UNKNOWN);
        ConstructorSymbol throwableStringCtor = new ConstructorSymbol(
                throwableClass, List.of(msgParam), false, SourceLocation.UNKNOWN);
        throwableClass.addConstructor(throwableStringCtor);
        ParameterSymbol causeParam = new ParameterSymbol("cause", throwableClass.asType(), SourceLocation.UNKNOWN);
        ConstructorSymbol throwableMsgCauseCtor = new ConstructorSymbol(
                throwableClass, List.of(
                        new ParameterSymbol("message", stringClass.asType(), SourceLocation.UNKNOWN),
                        causeParam),
                false, SourceLocation.UNKNOWN);
        throwableClass.addConstructor(throwableMsgCauseCtor);
        ConstructorSymbol throwableDefault = throwableClass.constructors().getFirst();
        throwableDefault.setDelegatesTo(null);

        MethodSymbol getMessage = new MethodSymbol(
                throwableClass, "getMessage", stringClass.asType(), List.of(),
                false, false, NameMangler.mangleMethod(throwableClass.name(), "getMessage", List.of(), false),
                SourceLocation.UNKNOWN);
        throwableClass.addMethod(getMessage);
        MethodSymbol getCause = new MethodSymbol(
                throwableClass, "getCause", throwableClass.asType(), List.of(),
                false, false, NameMangler.mangleMethod(throwableClass.name(), "getCause", List.of(), false),
                SourceLocation.UNKNOWN);
        throwableClass.addMethod(getCause);
        ParameterSymbol suppressedParam = new ParameterSymbol(
                "exception", throwableClass.asType(), SourceLocation.UNKNOWN);
        MethodSymbol addSuppressed = new MethodSymbol(
                throwableClass, "addSuppressed", VoidType.INSTANCE, List.of(suppressedParam),
                false, false,
                NameMangler.mangleMethod(throwableClass.name(), "addSuppressed",
                        List.of(throwableClass.asType()), false),
                SourceLocation.UNKNOWN);
        throwableClass.addMethod(addSuppressed);
        MethodSymbol getSuppressed = new MethodSymbol(
                throwableClass, "getSuppressed", new ArrayType(throwableClass.asType()), List.of(),
                false, false,
                NameMangler.mangleMethod(throwableClass.name(), "getSuppressed", List.of(), false),
                SourceLocation.UNKNOWN);
        throwableClass.addMethod(getSuppressed);

        ConstructorSymbol exceptionString = addStringSuperCtor(exceptionClass);
        ConstructorSymbol runtimeString = addStringSuperCtor(runtimeExceptionClass);
        ConstructorSymbol ioString = addStringSuperCtor(ioExceptionClass);
        ConstructorSymbol fnfString = addStringSuperCtor(fileNotFoundClass);
        ConstructorSymbol socketString = addStringSuperCtor(socketExceptionClass);
        ConstructorSymbol connectString = addStringSuperCtor(connectExceptionClass);
        ConstructorSymbol unknownHostString = addStringSuperCtor(unknownHostClass);
        ConstructorSymbol socketTimeoutString = addStringSuperCtor(socketTimeoutClass);
        exceptionString.setDelegatesTo(throwableStringCtor);
        exceptionString.setDelegatesToSuper(true);
        runtimeString.setDelegatesTo(exceptionString);
        runtimeString.setDelegatesToSuper(true);
        ioString.setDelegatesTo(exceptionString);
        ioString.setDelegatesToSuper(true);
        fnfString.setDelegatesTo(ioString);
        fnfString.setDelegatesToSuper(true);
        socketString.setDelegatesTo(ioString);
        socketString.setDelegatesToSuper(true);
        connectString.setDelegatesTo(socketString);
        connectString.setDelegatesToSuper(true);
        unknownHostString.setDelegatesTo(socketString);
        unknownHostString.setDelegatesToSuper(true);
        socketTimeoutString.setDelegatesTo(socketString);
        socketTimeoutString.setDelegatesToSuper(true);

        ConstructorSymbol exceptionMsgCause = addMessageCauseCtor(exceptionClass);
        ConstructorSymbol runtimeMsgCause = addMessageCauseCtor(runtimeExceptionClass);
        ConstructorSymbol ioMsgCause = addMessageCauseCtor(ioExceptionClass);
        exceptionMsgCause.setDelegatesTo(throwableMsgCauseCtor);
        exceptionMsgCause.setDelegatesToSuper(true);
        runtimeMsgCause.setDelegatesTo(exceptionMsgCause);
        runtimeMsgCause.setDelegatesToSuper(true);
        ioMsgCause.setDelegatesTo(exceptionMsgCause);
        ioMsgCause.setDelegatesToSuper(true);
        exceptionClass.constructors().getFirst().setDelegatesTo(throwableDefault);
        exceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        runtimeExceptionClass.constructors().getFirst().setDelegatesTo(exceptionClass.constructors().getFirst());
        runtimeExceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        ioExceptionClass.constructors().getFirst().setDelegatesTo(exceptionClass.constructors().getFirst());
        ioExceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        fileNotFoundClass.constructors().getFirst().setDelegatesTo(ioExceptionClass.constructors().getFirst());
        fileNotFoundClass.constructors().getFirst().setDelegatesToSuper(true);
        socketExceptionClass.constructors().getFirst().setDelegatesTo(ioExceptionClass.constructors().getFirst());
        socketExceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        connectExceptionClass.constructors().getFirst().setDelegatesTo(socketExceptionClass.constructors().getFirst());
        connectExceptionClass.constructors().getFirst().setDelegatesToSuper(true);
        unknownHostClass.constructors().getFirst().setDelegatesTo(socketExceptionClass.constructors().getFirst());
        unknownHostClass.constructors().getFirst().setDelegatesToSuper(true);
        socketTimeoutClass.constructors().getFirst().setDelegatesTo(socketExceptionClass.constructors().getFirst());
        socketTimeoutClass.constructors().getFirst().setDelegatesToSuper(true);

        List<ClassSymbol> leaves = new ArrayList<>();
        for (String leaf : List.of(
                "NullPointerException", "ArrayIndexOutOfBoundsException", "NegativeArraySizeException",
                "ClassCastException", "ArithmeticException", "IllegalArgumentException",
                "IllegalStateException", "IllegalMonitorStateException",
                "IndexOutOfBoundsException", "NoSuchElementException", "ConcurrentModificationException")) {
            ClassSymbol leafClass = newExceptionClass(leaf, runtimeExceptionClass);
            leafClass.constructors().getFirst().setDelegatesTo(runtimeExceptionClass.constructors().getFirst());
            leafClass.constructors().getFirst().setDelegatesToSuper(true);
            ConstructorSymbol leafString = addStringSuperCtor(leafClass);
            leafString.setDelegatesTo(runtimeString);
            leafString.setDelegatesToSuper(true);
            leaves.add(leafClass);
        }

        // Layouts must be built after message/cause fields exist, parents before children.
        for (ClassSymbol cls : List.of(
                throwableClass, exceptionClass, runtimeExceptionClass, ioExceptionClass, fileNotFoundClass,
                socketExceptionClass, connectExceptionClass, unknownHostClass, socketTimeoutClass)) {
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

    private ConstructorSymbol addMessageCauseCtor(ClassSymbol cls) {
        ParameterSymbol msgParam = new ParameterSymbol("message", stringClass.asType(), SourceLocation.UNKNOWN);
        ParameterSymbol causeParam = new ParameterSymbol("cause", throwableClass.asType(), SourceLocation.UNKNOWN);
        ConstructorSymbol ctor = new ConstructorSymbol(
                cls, List.of(msgParam, causeParam), false, SourceLocation.UNKNOWN);
        cls.addConstructor(ctor);
        return ctor;
    }

    private void installStringBuiltin() {
        stringClass = new ClassSymbol("String", SourceLocation.UNKNOWN, true);
        classes.put("String", stringClass);
        stringClass.setSuperClass(objectClass);

        MethodSymbol length = new MethodSymbol(
                stringClass, "length", PrimitiveType.INT, List.of(),
                false, false, true, "_JN_String_length", SourceLocation.UNKNOWN);
        length.setFfiIntrinsic(true);
        stringClass.addMethod(length);

        MethodSymbol utf8Length = new MethodSymbol(
                stringClass, "utf8Length", PrimitiveType.INT, List.of(),
                false, false, true, "_JN_String_utf8Length", SourceLocation.UNKNOWN);
        utf8Length.setFfiIntrinsic(true);
        stringClass.addMethod(utf8Length);

        ParameterSymbol charAtParam = new ParameterSymbol("index", PrimitiveType.INT, SourceLocation.UNKNOWN);
        MethodSymbol charAt = new MethodSymbol(
                stringClass, "charAt", PrimitiveType.CHAR, List.of(charAtParam),
                false, false, true, "_JN_String_charAt_i32", SourceLocation.UNKNOWN);
        charAt.setFfiIntrinsic(true);
        stringClass.addMethod(charAt);

        ParameterSymbol eqParam = new ParameterSymbol("other", objectClass.asType(), SourceLocation.UNKNOWN);
        MethodSymbol equals = new MethodSymbol(
                stringClass, "equals", PrimitiveType.BOOLEAN, List.of(eqParam),
                false, false,
                NameMangler.mangleMethod("String", "equals", List.of(objectClass.asType()), false),
                SourceLocation.UNKNOWN);
        objectClass.findMethod("equals").ifPresent(equals::setOverrides);
        stringClass.addMethod(equals);

        MethodSymbol hashCode = new MethodSymbol(
                stringClass, "hashCode", PrimitiveType.INT, List.of(),
                false, false,
                NameMangler.mangleMethod("String", "hashCode", List.of(), false),
                SourceLocation.UNKNOWN);
        objectClass.findMethod("hashCode").ifPresent(hashCode::setOverrides);
        stringClass.addMethod(hashCode);

        // General-purpose String helpers (0.8.3) — not HTTP-specific.
        JType stringType = stringClass.asType();
        JType byteArray = new ArrayType(PrimitiveType.BYTE);
        addStringIntrinsic("indexOf", PrimitiveType.INT, List.of(
                new ParameterSymbol("needle", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("indexOfFrom", PrimitiveType.INT, List.of(
                new ParameterSymbol("needle", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("from", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addStringIntrinsic("substring", stringType, List.of(
                new ParameterSymbol("begin", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("end", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addStringIntrinsic("substringFrom", stringType, List.of(
                new ParameterSymbol("begin", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addStringIntrinsic("startsWith", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("prefix", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("endsWith", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("suffix", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("equalsIgnoreCase", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("other", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("trim", stringType, List.of());
        addStringIntrinsic("getBytes", byteArray, List.of());
        addStringIntrinsic("toLowerCase", stringType, List.of());
        addStringIntrinsic("isEmpty", PrimitiveType.BOOLEAN, List.of());
        addStringIntrinsic("contains", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("needle", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("lastIndexOf", PrimitiveType.INT, List.of(
                new ParameterSymbol("needle", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("lastIndexOfFrom", PrimitiveType.INT, List.of(
                new ParameterSymbol("needle", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("from", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addStringIntrinsic("compareTo", PrimitiveType.INT, List.of(
                new ParameterSymbol("other", stringType, SourceLocation.UNKNOWN)));
        addStringIntrinsic("toUpperCase", stringType, List.of());
        addStringIntrinsic("replace", stringType, List.of(
                new ParameterSymbol("target", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("replacement", stringType, SourceLocation.UNKNOWN)));
        // Literal delimiter split (NOT Java regex). See docs/language-support.md / String contract.
        addStringIntrinsic("split", new ArrayType(stringType), List.of(
                new ParameterSymbol("delimiter", stringType, SourceLocation.UNKNOWN)));

        MethodSymbol join = new MethodSymbol(
                stringClass, "join", stringType,
                List.of(
                        new ParameterSymbol("delimiter", stringType, SourceLocation.UNKNOWN),
                        new ParameterSymbol("elements", new ArrayType(stringType), SourceLocation.UNKNOWN)),
                true, false, true,
                NameMangler.mangleMethod("String", "join",
                        List.of(stringType, new ArrayType(stringType)), false),
                SourceLocation.UNKNOWN);
        join.setFfiIntrinsic(true);
        stringClass.addMethod(join);

        MethodSymbol fromBytes = new MethodSymbol(
                stringClass, "fromBytes", stringType,
                List.of(new ParameterSymbol("bytes", byteArray, SourceLocation.UNKNOWN)),
                true, false, true, "_JN_String_fromBytes", SourceLocation.UNKNOWN);
        fromBytes.setFfiIntrinsic(true);
        stringClass.addMethod(fromBytes);

        MethodSymbol valueOf = new MethodSymbol(
                stringClass, "valueOf", stringType,
                List.of(new ParameterSymbol("value", PrimitiveType.INT, SourceLocation.UNKNOWN)),
                true, false, true, "_JN_String_valueOf_i32", SourceLocation.UNKNOWN);
        valueOf.setFfiIntrinsic(true);
        stringClass.addMethod(valueOf);

        stringClass.setLayout(ObjectLayoutBuilder.build(stringClass));
        stringClass.setVtable(VTableBuilder.build(stringClass));
    }

    private void addStringIntrinsic(String name, JType ret, List<ParameterSymbol> params) {
        String mangled = NameMangler.mangleMethod(
                "String", name, params.stream().map(ParameterSymbol::type).toList(), false);
        MethodSymbol ms = new MethodSymbol(
                stringClass, name, ret, params, false, false, true, mangled, SourceLocation.UNKNOWN);
        ms.setFfiIntrinsic(true);
        stringClass.addMethod(ms);
    }

    private void installFfiBuiltins() {
        // NativePtr is a type name (NativePtrType), not an instantiable class.
        ClassSymbol np = new ClassSymbol("NativePtr", SourceLocation.UNKNOWN, true);
        classes.put("NativePtr", np);
        np.setLayout(ObjectLayoutBuilder.build(np));
        np.setVtable(new io.jnative.vtable.VTable(np, List.of()));

        // Builtin Native: addressOf / loadInt / storeInt / allocate handled in typeOfCall.
        nativeClass = new ClassSymbol("Native", SourceLocation.UNKNOWN, true);
        classes.put("Native", nativeClass);
        nativeClass.setLayout(ObjectLayoutBuilder.build(nativeClass));
        nativeClass.setVtable(new io.jnative.vtable.VTable(nativeClass, List.of()));

        nativeMemoryClass = new ClassSymbol("NativeMemory", SourceLocation.UNKNOWN, true);
        classes.put("NativeMemory", nativeMemoryClass);

        addFfiIntrinsic(nativeMemoryClass, "allocate", NativePtrType.VOID,
                List.of(new ParameterSymbol("size", PrimitiveType.LONG, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "free", VoidType.INSTANCE,
                List.of(new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "getInt", PrimitiveType.INT,
                List.of(
                        new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN),
                        new ParameterSymbol("offset", PrimitiveType.LONG, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "putInt", VoidType.INSTANCE,
                List.of(
                        new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN),
                        new ParameterSymbol("offset", PrimitiveType.LONG, SourceLocation.UNKNOWN),
                        new ParameterSymbol("value", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "getPtr", NativePtrType.VOID,
                List.of(
                        new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN),
                        new ParameterSymbol("offset", PrimitiveType.LONG, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "putPtr", VoidType.INSTANCE,
                List.of(
                        new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN),
                        new ParameterSymbol("offset", PrimitiveType.LONG, SourceLocation.UNKNOWN),
                        new ParameterSymbol("value", NativePtrType.VOID, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(nativeMemoryClass, "fromCString", stringClass.asType(),
                List.of(new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN)));

        nativeMemoryClass.setLayout(ObjectLayoutBuilder.build(nativeMemoryClass));
        nativeMemoryClass.setVtable(new io.jnative.vtable.VTable(nativeMemoryClass, List.of()));

        installFileBuiltins();
        installNetworkBuiltins();
        installBytesBuiltin();
        installThreadBuiltins();
        installAsyncBuiltins();
    }

    private void installAsyncBuiltins() {
        futureClass = new ClassSymbol("Future", SourceLocation.UNKNOWN, true);
        classes.put("Future", futureClass);
        futureClass.setSuperClass(objectClass);
        futureClass.setTypeParameters(List.of(
                new TypeParameterSymbol("T", futureClass, null, 0, SourceLocation.UNKNOWN)));
        futureClass.addField(new FieldSymbol(
                futureClass, "state", PrimitiveType.INT, 0, SourceLocation.UNKNOWN));
        futureClass.addField(new FieldSymbol(
                futureClass, "result", objectClass.asType(), 1, SourceLocation.UNKNOWN));
        futureClass.addConstructor(new ConstructorSymbol(futureClass, List.of(), true, SourceLocation.UNKNOWN));

        addFfiIntrinsic(futureClass, "completed", futureClass.asType(), List.of(
                new ParameterSymbol("value", objectClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "pending", futureClass.asType(), List.of());
        addFfiIntrinsic(futureClass, "failed", futureClass.asType(), List.of(
                new ParameterSymbol("error", objectClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "isComplete", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "getState", PrimitiveType.INT, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "getCompleted", objectClass.asType(), List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "getError", objectClass.asType(), List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "tryComplete", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN),
                new ParameterSymbol("value", objectClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "tryFail", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN),
                new ParameterSymbol("error", objectClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "tryCancel", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN)));
        addFfiIntrinsic(futureClass, "awaitCooperative", VoidType.INSTANCE, List.of(
                new ParameterSymbol("future", futureClass.asType(), SourceLocation.UNKNOWN),
                new ParameterSymbol("frame", objectClass.asType(), SourceLocation.UNKNOWN),
                new ParameterSymbol("resumeState", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("resumeFn", objectClass.asType(), SourceLocation.UNKNOWN)));
        // Runtime state: 0=PENDING 1=SUCCESS 2=FAILED 3=CANCELLED (see AsyncRuntimeEmitter)

        futureClass.setLayout(ObjectLayoutBuilder.build(futureClass));
        futureClass.setVtable(new io.jnative.vtable.VTable(futureClass, List.of()));
    }

    private void installCallableBuiltins() {
        FunctionType voidFn = new FunctionType(List.of(), VoidType.INSTANCE, FunctionType.Domain.MANAGED);
        runnableClass = installSam("Runnable", "run", voidFn, "jnative_invoke_void");

        FunctionType intUnary = new FunctionType(
                List.of(PrimitiveType.INT), PrimitiveType.INT, FunctionType.Domain.MANAGED);
        installSam("IntUnaryFunction", "apply", intUnary, "jnative_invoke_i32_i32");
        installSam("IntFunction", "apply", intUnary, "jnative_invoke_i32_i32");

        FunctionType intBinary = new FunctionType(
                List.of(PrimitiveType.INT, PrimitiveType.INT), PrimitiveType.INT, FunctionType.Domain.MANAGED);
        installSam("IntBinaryFunction", "apply", intBinary, "jnative_invoke_i32_i32_i32");

        // Consumer / Comparator / Function live in collections stdlib as generic interfaces (0.16).

        FunctionType intCompare = new FunctionType(
                List.of(NativePtrType.VOID, NativePtrType.VOID),
                PrimitiveType.INT, FunctionType.Domain.NATIVE);
        installSam("IntCompare", "compare", intCompare, "jnative_invoke_i32_ptr_ptr");

        // HttpHandler SAM for stdlib HTTP routing (HttpServerRequest/HttpResponse resolved at use site).
        FunctionType httpHandler = new FunctionType(
                List.of(objectClass.asType()), objectClass.asType(), FunctionType.Domain.MANAGED);
        installSam("HttpHandler", "handle", httpHandler, "jnative_invoke_ptr_ptr");

        nativeCallbackHandleClass = new ClassSymbol("NativeCallbackHandle", SourceLocation.UNKNOWN, true);
        classes.put("NativeCallbackHandle", nativeCallbackHandleClass);
        nativeCallbackHandleClass.setSuperClass(objectClass);
        nativeCallbackHandleClass.addField(new FieldSymbol(
                nativeCallbackHandleClass, "handle", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        nativeCallbackHandleClass.addField(new FieldSymbol(
                nativeCallbackHandleClass, "retained", objectClass.asType(), 1, SourceLocation.UNKNOWN));
        nativeCallbackHandleClass.addField(new FieldSymbol(
                nativeCallbackHandleClass, "slot", PrimitiveType.INT, 2, SourceLocation.UNKNOWN));
        addInstanceFfiIntrinsic(nativeCallbackHandleClass, "pointer",
                NativeFunctionPtrType.of(voidFn), List.of());
        addInstanceFfiIntrinsic(nativeCallbackHandleClass, "close", VoidType.INSTANCE, List.of());
        nativeCallbackHandleClass.setLayout(ObjectLayoutBuilder.build(nativeCallbackHandleClass));
        nativeCallbackHandleClass.setVtable(new io.jnative.vtable.VTable(nativeCallbackHandleClass, List.of()));
    }

    private ClassSymbol installSam(String name, String methodName, FunctionType ft, String invokeRuntime) {
        ClassSymbol sam = new ClassSymbol(name, SourceLocation.UNKNOWN, true);
        classes.put(name, sam);
        sam.setSuperClass(objectClass);
        sam.setFunctionType(ft);
        sam.addField(new FieldSymbol(sam, "code", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        sam.addField(new FieldSymbol(sam, "env", objectClass.asType(), 1, SourceLocation.UNKNOWN));
        List<ParameterSymbol> params = new ArrayList<>();
        for (int i = 0; i < ft.parameterTypes().size(); i++) {
            params.add(new ParameterSymbol("p" + i, ft.parameterTypes().get(i), SourceLocation.UNKNOWN));
        }
        String mangled = NameMangler.mangleMethod(name, methodName, ft.parameterTypes(), false);
        MethodSymbol ms = new MethodSymbol(
                sam, methodName, ft.returnType(), params, false, false, true, mangled, SourceLocation.UNKNOWN);
        ms.setFfiIntrinsic(true);
        ms.setSamInvokeRuntime(invokeRuntime);
        sam.addMethod(ms);
        sam.setLayout(ObjectLayoutBuilder.build(sam));
        sam.setVtable(new io.jnative.vtable.VTable(sam, List.of()));
        return sam;
    }

    private void installThreadBuiltins() {
        ClassSymbol thread = new ClassSymbol("Thread", SourceLocation.UNKNOWN, true);
        classes.put("Thread", thread);
        thread.setSuperClass(objectClass);
        thread.addField(new FieldSymbol(thread, "nativeId", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        thread.addField(new FieldSymbol(thread, "target", runnableClass.asType(), 1, SourceLocation.UNKNOWN));
        thread.addConstructor(new ConstructorSymbol(thread, List.of(), true, SourceLocation.UNKNOWN));
        thread.addConstructor(new ConstructorSymbol(thread, List.of(
                new ParameterSymbol("target", runnableClass.asType(), SourceLocation.UNKNOWN)),
                false, SourceLocation.UNKNOWN));
        MethodSymbol run = new MethodSymbol(
                thread, "run", VoidType.INSTANCE, List.of(),
                false, false, false, NameMangler.mangleMethod("Thread", "run", List.of(), false),
                SourceLocation.UNKNOWN);
        thread.addMethod(run);
        addInstanceFfiIntrinsic(thread, "start", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(thread, "join", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(thread, "id", PrimitiveType.LONG, List.of());
        addFfiIntrinsic(thread, "sleep", VoidType.INSTANCE, List.of(
                new ParameterSymbol("ms", PrimitiveType.LONG, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(thread, "currentThread", thread.asType(), List.of());
        thread.setLayout(ObjectLayoutBuilder.build(thread));
        thread.setVtable(VTableBuilder.build(thread));

        ClassSymbol mutex = new ClassSymbol("Mutex", SourceLocation.UNKNOWN, true);
        classes.put("Mutex", mutex);
        mutex.setSuperClass(objectClass);
        mutex.addField(new FieldSymbol(mutex, "handle", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        mutex.addConstructor(new ConstructorSymbol(mutex, List.of(), true, SourceLocation.UNKNOWN));
        addInstanceFfiIntrinsic(mutex, "lock", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(mutex, "tryLock", PrimitiveType.BOOLEAN, List.of());
        addInstanceFfiIntrinsic(mutex, "unlock", VoidType.INSTANCE, List.of());
        mutex.setLayout(ObjectLayoutBuilder.build(mutex));
        mutex.setVtable(new io.jnative.vtable.VTable(mutex, List.of()));

        ClassSymbol condition = new ClassSymbol("Condition", SourceLocation.UNKNOWN, true);
        classes.put("Condition", condition);
        condition.setSuperClass(objectClass);
        condition.addField(new FieldSymbol(
                condition, "handle", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        condition.addConstructor(new ConstructorSymbol(condition, List.of(), true, SourceLocation.UNKNOWN));
        addInstanceFfiIntrinsic(condition, "await", VoidType.INSTANCE, List.of(
                new ParameterSymbol("lock", mutex.asType(), SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(condition, "signal", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(condition, "signalAll", VoidType.INSTANCE, List.of());
        condition.setLayout(ObjectLayoutBuilder.build(condition));
        condition.setVtable(new io.jnative.vtable.VTable(condition, List.of()));

        installAtomicBuiltin("AtomicInt", PrimitiveType.INT);
        installAtomicBuiltin("AtomicLong", PrimitiveType.LONG);
        installAtomicBuiltin("AtomicBool", PrimitiveType.BOOLEAN);
    }

    private void installAtomicBuiltin(String name, JType valueType) {
        ClassSymbol cls = new ClassSymbol(name, SourceLocation.UNKNOWN, true);
        classes.put(name, cls);
        cls.setSuperClass(objectClass);
        cls.addField(new FieldSymbol(cls, "value", valueType, 0, false, true, SourceLocation.UNKNOWN));
        ParameterSymbol v = new ParameterSymbol("value", valueType, SourceLocation.UNKNOWN);
        ConstructorSymbol ctor = new ConstructorSymbol(cls, List.of(v), false, SourceLocation.UNKNOWN);
        cls.addConstructor(ctor);
        cls.addConstructor(new ConstructorSymbol(cls, List.of(), true, SourceLocation.UNKNOWN));
        addInstanceFfiIntrinsic(cls, "get", valueType, List.of());
        addInstanceFfiIntrinsic(cls, "set", VoidType.INSTANCE, List.of(
                new ParameterSymbol("value", valueType, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(cls, "compareAndSet", PrimitiveType.BOOLEAN, List.of(
                new ParameterSymbol("expected", valueType, SourceLocation.UNKNOWN),
                new ParameterSymbol("update", valueType, SourceLocation.UNKNOWN)));
        if (!(valueType instanceof PrimitiveType p && p == PrimitiveType.BOOLEAN)) {
            addInstanceFfiIntrinsic(cls, "getAndIncrement", valueType, List.of());
            addInstanceFfiIntrinsic(cls, "incrementAndGet", valueType, List.of());
        }
        cls.setLayout(ObjectLayoutBuilder.build(cls));
        cls.setVtable(new io.jnative.vtable.VTable(cls, List.of()));
    }

    private void installBytesBuiltin() {
        ClassSymbol bytesClass = new ClassSymbol("Bytes", SourceLocation.UNKNOWN, true);
        classes.put("Bytes", bytesClass);
        JType byteArray = new ArrayType(PrimitiveType.BYTE);
        JType stringType = stringClass.asType();

        addFfiIntrinsic(bytesClass, "fromString", byteArray, List.of(
                new ParameterSymbol("s", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(bytesClass, "toString", stringType, List.of(
                new ParameterSymbol("data", byteArray, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(bytesClass, "concat", byteArray, List.of(
                new ParameterSymbol("a", byteArray, SourceLocation.UNKNOWN),
                new ParameterSymbol("b", byteArray, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(bytesClass, "copy", VoidType.INSTANCE, List.of(
                new ParameterSymbol("src", byteArray, SourceLocation.UNKNOWN),
                new ParameterSymbol("srcPos", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("dst", byteArray, SourceLocation.UNKNOWN),
                new ParameterSymbol("dstPos", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("len", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(bytesClass, "indexOf2", PrimitiveType.INT, List.of(
                new ParameterSymbol("data", byteArray, SourceLocation.UNKNOWN),
                new ParameterSymbol("from", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("b0", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("b1", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(bytesClass, "slice", byteArray, List.of(
                new ParameterSymbol("data", byteArray, SourceLocation.UNKNOWN),
                new ParameterSymbol("from", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("to", PrimitiveType.INT, SourceLocation.UNKNOWN)));

        bytesClass.setLayout(ObjectLayoutBuilder.build(bytesClass));
        bytesClass.setVtable(new io.jnative.vtable.VTable(bytesClass, List.of()));
    }

    private void installFileBuiltins() {
        ClassSymbol fileClass = new ClassSymbol("File", SourceLocation.UNKNOWN, true);
        classes.put("File", fileClass);

        JType stringType = stringClass.asType();
        JType byteArray = new ArrayType(PrimitiveType.BYTE);

        addFfiIntrinsic(fileClass, "readString", stringType,
                List.of(new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "readBytes", byteArray,
                List.of(new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "writeString", VoidType.INSTANCE,
                List.of(
                        new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN),
                        new ParameterSymbol("text", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "writeBytes", VoidType.INSTANCE,
                List.of(
                        new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN),
                        new ParameterSymbol("data", byteArray, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "exists", PrimitiveType.BOOLEAN,
                List.of(new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "size", PrimitiveType.LONG,
                List.of(new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(fileClass, "delete", PrimitiveType.BOOLEAN,
                List.of(new ParameterSymbol("path", stringType, SourceLocation.UNKNOWN)));

        fileClass.setLayout(ObjectLayoutBuilder.build(fileClass));
        fileClass.setVtable(new io.jnative.vtable.VTable(fileClass, List.of()));
    }

    private void installNetworkBuiltins() {
        JType stringType = stringClass.asType();
        JType byteArray = new ArrayType(PrimitiveType.BYTE);

        ClassSymbol tcpSocket = new ClassSymbol("TcpSocket", SourceLocation.UNKNOWN, true);
        classes.put("TcpSocket", tcpSocket);
        tcpSocket.setSuperClass(objectClass);
        // Opaque NativeSocketHandle storage (i64) + closed flag; not part of the public API surface.
        tcpSocket.addField(new FieldSymbol(tcpSocket, "handle", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        tcpSocket.addField(new FieldSymbol(tcpSocket, "closed", PrimitiveType.BOOLEAN, 1, SourceLocation.UNKNOWN));

        ClassSymbol tcpServer = new ClassSymbol("TcpServer", SourceLocation.UNKNOWN, true);
        classes.put("TcpServer", tcpServer);
        tcpServer.setSuperClass(objectClass);
        tcpServer.addField(new FieldSymbol(tcpServer, "handle", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        tcpServer.addField(new FieldSymbol(tcpServer, "closed", PrimitiveType.BOOLEAN, 1, SourceLocation.UNKNOWN));

        JType socketType = tcpSocket.asType();
        JType serverType = tcpServer.asType();

        addFfiIntrinsic(tcpSocket, "connect", socketType, List.of(
                new ParameterSymbol("host", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("port", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(tcpSocket, "connectTimeout", socketType, List.of(
                new ParameterSymbol("host", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("port", PrimitiveType.INT, SourceLocation.UNKNOWN),
                new ParameterSymbol("timeoutMs", PrimitiveType.INT, SourceLocation.UNKNOWN)));

        addInstanceFfiIntrinsic(tcpSocket, "read", PrimitiveType.INT, List.of(
                new ParameterSymbol("buffer", byteArray, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "readBytes", byteArray, List.of(
                new ParameterSymbol("maxBytes", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "readAllBytes", byteArray, List.of());
        addInstanceFfiIntrinsic(tcpSocket, "readString", stringType, List.of(
                new ParameterSymbol("maxBytes", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "readAllString", stringType, List.of());
        addInstanceFfiIntrinsic(tcpSocket, "write", VoidType.INSTANCE, List.of(
                new ParameterSymbol("data", byteArray, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "writeString", VoidType.INSTANCE, List.of(
                new ParameterSymbol("data", stringType, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "close", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(tcpSocket, "isClosed", PrimitiveType.BOOLEAN, List.of());
        addInstanceFfiIntrinsic(tcpSocket, "setReadTimeout", VoidType.INSTANCE, List.of(
                new ParameterSymbol("ms", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpSocket, "setWriteTimeout", VoidType.INSTANCE, List.of(
                new ParameterSymbol("ms", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        // Low-level fd for TLS wrap only (0.31 SecureTransport). Not for application use.
        addInstanceFfiIntrinsic(tcpSocket, "nativeFd", PrimitiveType.LONG, List.of());

        addFfiIntrinsic(tcpServer, "listen", serverType, List.of(
                new ParameterSymbol("host", stringType, SourceLocation.UNKNOWN),
                new ParameterSymbol("port", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addFfiIntrinsic(tcpServer, "listenAny", serverType, List.of(
                new ParameterSymbol("port", PrimitiveType.INT, SourceLocation.UNKNOWN)));
        addInstanceFfiIntrinsic(tcpServer, "accept", socketType, List.of());
        addInstanceFfiIntrinsic(tcpServer, "close", VoidType.INSTANCE, List.of());
        addInstanceFfiIntrinsic(tcpServer, "isClosed", PrimitiveType.BOOLEAN, List.of());

        tcpSocket.setLayout(ObjectLayoutBuilder.build(tcpSocket));
        tcpSocket.setVtable(new io.jnative.vtable.VTable(tcpSocket, List.of()));
        tcpServer.setLayout(ObjectLayoutBuilder.build(tcpServer));
        tcpServer.setVtable(new io.jnative.vtable.VTable(tcpServer, List.of()));
    }

    private void addFfiIntrinsic(ClassSymbol owner, String name, JType ret, List<ParameterSymbol> params) {
        String mangled = NameMangler.mangleMethod(
                owner.name(), name, params.stream().map(ParameterSymbol::type).toList(), false);
        MethodSymbol ms = new MethodSymbol(
                owner, name, ret, params, true, false, false, mangled, SourceLocation.UNKNOWN);
        ms.setFfiIntrinsic(true);
        owner.addMethod(ms);
    }

    private void addInstanceFfiIntrinsic(ClassSymbol owner, String name, JType ret, List<ParameterSymbol> params) {
        String mangled = NameMangler.mangleMethod(
                owner.name(), name, params.stream().map(ParameterSymbol::type).toList(), false);
        // isNative=true → non-virtual; still marked ffiIntrinsic for runtime lowering.
        MethodSymbol ms = new MethodSymbol(
                owner, name, ret, params, false, false, true, mangled, SourceLocation.UNKNOWN);
        ms.setFfiIntrinsic(true);
        owner.addMethod(ms);
    }

    private boolean isThrowableType(JType type) {
        if (!(type instanceof ClassType ct)) {
            return false;
        }
        return ct.classSymbol().isSubtypeOf(throwableClass);
    }

    private void declareClass(ClassDeclaration cls) {
        String fqn = cls.qualifiedName();
        if (classes.containsKey(fqn) || interfaces.containsKey(fqn)
                || nativeStructs.containsKey(fqn)) {
            reporter.error(cls.location(), "duplicate class '" + fqn + "'");
            return;
        }
        // Same simple name in the same package is duplicate; across packages OK.
        ClassSymbol symbol = new ClassSymbol(fqn, cls.location(), false);
        symbol.setPackageName(cls.packageName());
        symbol.setVisibility(defaultTypeVisibility(cls));
        symbol.setImportTypes(cls.imports().stream().map(ImportDeclaration::qualifiedType).toList());
        symbol.setAbstract(cls.isAbstract());
        symbol.setFinal(cls.isFinal() || cls.isEnum());
        symbol.setEnum(cls.isEnum());
        if (cls.isEnum()) {
            symbol.setEnumConstants(cls.enumConstants());
        }
        symbol.setTypeParameters(createTypeParameters(cls.typeParameters(), symbol, null));
        classes.put(fqn, symbol);
        indexSimpleName(cls.name(), fqn);
        // Default-package alias: allow legacy simple-name lookups for unpackaged types.
        if (cls.packageName() == null || cls.packageName().isEmpty()) {
            classes.putIfAbsent(cls.name(), symbol);
        }
    }

    private void declareInterface(ClassDeclaration cls) {
        String fqn = cls.qualifiedName();
        if (classes.containsKey(fqn) || interfaces.containsKey(fqn)
                || nativeStructs.containsKey(fqn)) {
            reporter.error(cls.location(), "duplicate interface '" + fqn + "'");
            return;
        }
        InterfaceSymbol symbol = new InterfaceSymbol(fqn, cls.location(), false);
        symbol.setPackageName(cls.packageName());
        symbol.setVisibility(defaultTypeVisibility(cls));
        symbol.setImportTypes(cls.imports().stream().map(ImportDeclaration::qualifiedType).toList());
        symbol.setTypeParameters(createTypeParameters(cls.typeParameters(), symbol, null));
        interfaces.put(fqn, symbol);
        indexSimpleName(cls.name(), fqn);
        if (cls.packageName() == null || cls.packageName().isEmpty()) {
            interfaces.putIfAbsent(cls.name(), symbol);
        }
    }

    private void declareNativeStruct(ClassDeclaration cls) {
        String fqn = cls.qualifiedName();
        if (classes.containsKey(fqn) || interfaces.containsKey(fqn)
                || nativeStructs.containsKey(fqn)) {
            reporter.error(cls.location(), "duplicate type '" + fqn + "'");
            return;
        }
        nativeStructs.put(fqn, new NativeStructSymbol(fqn, cls.location()));
        if (cls.packageName() == null || cls.packageName().isEmpty()) {
            nativeStructs.putIfAbsent(cls.name(), nativeStructs.get(fqn));
        }
    }

    private void indexSimpleName(String simple, String fqn) {
        typesBySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(fqn);
    }

    private ClassSymbol classOf(ClassDeclaration cls) {
        return classes.get(cls.qualifiedName());
    }

    private InterfaceSymbol interfaceOf(ClassDeclaration cls) {
        return interfaces.get(cls.qualifiedName());
    }

    private void linkSuperInterfaces(ClassDeclaration cls) {
        InterfaceSymbol symbol = interfaceOf(cls);
        if (symbol == null) {
            return;
        }
        pushTypeParamScope(symbol.typeParameters());
        try {
            List<ParameterizedType> parameterizedSupers = new ArrayList<>();
            for (TypeName ifaceType : cls.implementedInterfaces()) {
                JType resolved = resolveTypeName(ifaceType, false, cls.location());
                InterfaceSymbol parent = resolveInterfaceRaw(resolved, cls.location());
                if (parent == null) {
                    continue;
                }
                if (parent == symbol) {
                    reporter.error(cls.location(), "interface '" + cls.name() + "' cannot extend itself");
                    continue;
                }
                symbol.addSuperInterface(parent);
                if (resolved instanceof ParameterizedType pt) {
                    parameterizedSupers.add(pt);
                }
            }
            symbol.setParameterizedSuperInterfaces(parameterizedSupers);
        } finally {
            popTypeParamScope();
        }
    }

    private void linkImplements(ClassDeclaration cls) {
        ClassSymbol symbol = classOf(cls);
        if (symbol == null) {
            return;
        }
        pushTypeParamScope(symbol.typeParameters());
        try {
            List<ParameterizedType> parameterizedIfaces = new ArrayList<>();
            for (TypeName ifaceType : cls.implementedInterfaces()) {
                JType resolved = resolveTypeName(ifaceType, false, cls.location());
                InterfaceSymbol iface = resolveInterfaceRaw(resolved, cls.location());
                if (iface == null) {
                    String name = ifaceType.name();
                    if (classes.containsKey(name)) {
                        reporter.error(cls.location(),
                                "class '" + cls.name() + "' cannot implement class '" + name + "'");
                    } else {
                        reporter.error(ifaceType.location(), "undefined interface '" + name + "'");
                    }
                    continue;
                }
                symbol.addImplementedInterface(iface);
                if (resolved instanceof ParameterizedType pt) {
                    parameterizedIfaces.add(pt);
                }
            }
            symbol.setImplementedParameterizedInterfaces(parameterizedIfaces);
        } finally {
            popTypeParamScope();
        }
    }

    private void registerNativeStructFields(NativeStructSymbol symbol, ClassDeclaration cls) {
        int index = 0;
        for (FieldDeclaration field : cls.fields()) {
            JType type = resolveTypeName(field.type(), false, field.location());
            if (!isAllowedNativeStructFieldType(type)) {
                if (!(type instanceof ErrorType)) {
                    reporter.error(field.location(),
                            "unsupported @NativeStruct field type: " + type);
                }
                type = ErrorType.INSTANCE;
            }
            if (symbol.findField(field.name()).isPresent()) {
                reporter.error(field.location(), "duplicate field '" + field.name() + "'");
                continue;
            }
            symbol.addField(new NativeStructFieldSymbol(
                    symbol, field.name(), type, index, field.location()));
            index++;
        }
    }

    private boolean isAllowedNativeStructFieldType(JType type) {
        if (type instanceof PrimitiveType p) {
            return p != PrimitiveType.BOOLEAN;
        }
        return type instanceof NativePtrType || type instanceof NativeStructType;
    }

    private void detectRecursiveNativeStructs() {
        Map<NativeStructSymbol, Integer> color = new HashMap<>();
        for (NativeStructSymbol s : nativeStructs.values()) {
            color.put(s, 0);
        }
        for (NativeStructSymbol s : nativeStructs.values()) {
            if (color.get(s) == 0) {
                dfsNativeStructCycle(s, color);
            }
        }
    }

    private void dfsNativeStructCycle(NativeStructSymbol s, Map<NativeStructSymbol, Integer> color) {
        color.put(s, 1);
        for (NativeStructFieldSymbol field : s.declaredFields()) {
            if (!(field.type() instanceof NativeStructType nestedType)) {
                continue;
            }
            NativeStructSymbol nested = nestedType.symbol();
            int c = color.getOrDefault(nested, 2);
            if (c == 1) {
                reporter.error(field.location(),
                        "recursive native struct; use NativePtr");
            } else if (c == 0) {
                dfsNativeStructCycle(nested, color);
            }
        }
        color.put(s, 2);
    }

    private void buildNativeStructLayouts() {
        for (NativeStructSymbol symbol : topologicalNativeStructs()) {
            try {
                symbol.setLayout(NativeStructLayoutBuilder.buildWithNested(symbol, targetAbi));
            } catch (RuntimeException ex) {
                reporter.error(symbol.location(),
                        "cannot layout native struct '" + symbol.name() + "': " + ex.getMessage());
            }
        }
    }

    private List<NativeStructSymbol> topologicalNativeStructs() {
        List<NativeStructSymbol> result = new ArrayList<>();
        Set<NativeStructSymbol> visiting = new HashSet<>();
        Set<NativeStructSymbol> visited = new HashSet<>();
        for (NativeStructSymbol s : nativeStructs.values()) {
            topoNativeStructVisit(s, visiting, visited, result);
        }
        return result;
    }

    private void topoNativeStructVisit(
            NativeStructSymbol s,
            Set<NativeStructSymbol> visiting,
            Set<NativeStructSymbol> visited,
            List<NativeStructSymbol> result
    ) {
        if (visited.contains(s)) {
            return;
        }
        if (!visiting.add(s)) {
            return;
        }
        for (NativeStructFieldSymbol field : s.declaredFields()) {
            if (field.type() instanceof NativeStructType nested) {
                topoNativeStructVisit(nested.symbol(), visiting, visited, result);
            }
        }
        visiting.remove(s);
        visited.add(s);
        result.add(s);
    }

    private void linkSuperClass(ClassDeclaration cls) {
        ClassSymbol symbol = classOf(cls);
        if (symbol == null) {
            return;
        }
        if (cls.superClassName() == null) {
            if (objectClass != null && symbol != objectClass) {
                symbol.setSuperClass(objectClass);
            }
            return;
        }
        if (cls.superClassName().equals(cls.name())) {
            reporter.error(cls.location(), "class '" + cls.name() + "' cannot extend itself");
            return;
        }
        TypeName superRef = cls.superClassTypeArgs().isEmpty()
                ? TypeName.ofClass(cls.superClassName(), cls.location())
                : TypeName.ofClass(cls.superClassName(), cls.superClassTypeArgs(), cls.location());
        JType superType = resolveTypeName(superRef, false, cls.location());
        ClassSymbol superSymbol = resolveClassRaw(superType, cls.location());
        if (superSymbol == null) {
            return;
        }
        if (superSymbol == stringClass) {
            reporter.error(cls.location(), "class '" + cls.name() + "' cannot extend String");
            return;
        }
        if (superSymbol.isFinal()) {
            reporter.error(cls.location(),
                    "cannot inherit from final class '" + superSymbol.simpleName() + "'");
            return;
        }
        symbol.setSuperClass(superSymbol);
        if (superType instanceof ParameterizedType pt) {
            symbol.setParameterizedSuper(pt);
        }
    }

    private void detectInheritanceCycles(List<CompilationUnit> units) {
        Map<ClassSymbol, Integer> classColor = new HashMap<>();
        for (ClassSymbol cls : classes.values()) {
            if (!cls.isBuiltin()) {
                classColor.put(cls, 0);
            }
        }
        for (CompilationUnit unit : units) {
            for (ClassDeclaration decl : unit.classes()) {
                ClassSymbol cls = classes.get(decl.qualifiedName());
                if (cls == null || cls.isBuiltin()) {
                    continue;
                }
                if (classColor.getOrDefault(cls, 2) == 0) {
                    dfsClassCycle(cls, classColor);
                }
            }
        }

        Map<InterfaceSymbol, Integer> ifaceColor = new HashMap<>();
        for (InterfaceSymbol iface : interfaces.values()) {
            if (!iface.isBuiltin()) {
                ifaceColor.put(iface, 0);
            }
        }
        for (CompilationUnit unit : units) {
            for (ClassDeclaration decl : unit.classes()) {
                if (!decl.isInterface()) {
                    continue;
                }
                InterfaceSymbol iface = interfaces.get(decl.qualifiedName());
                if (iface == null || iface.isBuiltin()) {
                    continue;
                }
                if (ifaceColor.getOrDefault(iface, 2) == 0) {
                    dfsInterfaceCycle(iface, ifaceColor);
                }
            }
        }
    }

    private void dfsClassCycle(ClassSymbol cls, Map<ClassSymbol, Integer> color) {
        color.put(cls, 1);
        cls.superClass().ifPresent(superClass -> {
            int c = color.getOrDefault(superClass, 2);
            if (c == 1) {
                reporter.error(cls.location(), "cyclic inheritance involving '" + cls.name() + "'");
                cls.setSuperClass(null);
            } else if (c == 0) {
                dfsClassCycle(superClass, color);
            }
        });
        color.put(cls, 2);
    }

    private void dfsInterfaceCycle(InterfaceSymbol iface, Map<InterfaceSymbol, Integer> color) {
        color.put(iface, 1);
        for (InterfaceSymbol parent : iface.superInterfaces()) {
            int c = color.getOrDefault(parent, 2);
            if (c == 1) {
                reporter.error(iface.location(),
                        "cyclic interface inheritance involving '" + iface.name() + "'");
            } else if (c == 0) {
                dfsInterfaceCycle(parent, color);
            }
        }
        color.put(iface, 2);
    }

    private List<InterfaceSymbol> topologicalInterfaces() {
        List<InterfaceSymbol> result = new ArrayList<>();
        Set<InterfaceSymbol> visiting = new HashSet<>();
        Set<InterfaceSymbol> visited = new HashSet<>();
        for (InterfaceSymbol iface : interfaces.values()) {
            if (!iface.isBuiltin()) {
                topoInterfaceVisit(iface, visiting, visited, result);
            }
        }
        return result;
    }

    private void topoInterfaceVisit(
            InterfaceSymbol iface,
            Set<InterfaceSymbol> visiting,
            Set<InterfaceSymbol> visited,
            List<InterfaceSymbol> result
    ) {
        if (visited.contains(iface) || iface.isBuiltin()) {
            return;
        }
        if (!visiting.add(iface)) {
            return;
        }
        for (InterfaceSymbol parent : iface.superInterfaces()) {
            topoInterfaceVisit(parent, visiting, visited, result);
        }
        visiting.remove(iface);
        visited.add(iface);
        result.add(iface);
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
        pushTypeParamScope(symbol.typeParameters());
        int index = 0;
        try {
        for (FieldDeclaration field : cls.fields()) {
            JType type = resolveTypeName(field.type(), field.isArray(), field.location());
            if (type instanceof VoidType) {
                reporter.error(field.location(), "field cannot have type void");
                type = ErrorType.INSTANCE;
            }
            if (type instanceof NativeStructType) {
                reporter.error(field.location(),
                        "native struct requires explicit storage: @Stack or NativeMemory");
                type = ErrorType.INSTANCE;
            }
            if (field.isVolatile() && type.isReference() && !field.isStatic()) {
                // allowed; volatile refs use acquire/release loads/stores
            }
            FieldSymbol fs = new FieldSymbol(
                    symbol, field.name(), type, index, field.isStatic(), field.isVolatile(),
                    exportStdlibMember(
                            field.visibility() == null ? Visibility.PACKAGE : field.visibility(),
                            field.location()),
                    field.location());
            if (field.initializer() != null) {
                fs.setInitializer(field.initializer());
            }
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
        } finally {
            popTypeParamScope();
        }
    }

    private void analyzeStaticFieldInitializers(ClassSymbol symbol) {
        MethodSymbol prevMethod = currentMethod;
        currentMethod = null; // treat as static context
        for (FieldSymbol field : symbol.declaredFields()) {
            if (!field.isStatic() || field.initializer() == null) {
                continue;
            }
            JType initType = analyzeExpression(field.initializer());
            if (!assignableWithBoxing(field.type(), initType) && !(initType instanceof ErrorType)) {
                reporter.error(field.location(),
                        "incompatible types: " + initType + " cannot be converted to " + field.type());
            }
        }
        currentMethod = prevMethod;
    }

    private void registerMethods(ClassSymbol symbol, ClassDeclaration cls) {
        pushTypeParamScope(symbol.typeParameters());
        try {
        for (MethodDeclaration method : cls.methods()) {
            if (method.isConstructor()) {
                continue;
            }
            List<TypeParameterSymbol> methodTypeParams =
                    createTypeParameters(method.typeParameters(), symbol, null);
            pushTypeParamScope(methodTypeParams);
            List<ParameterSymbol> params = new ArrayList<>();
            for (Parameter p : method.parameters()) {
                JType pt = resolveTypeName(p.type(), p.isArray(), p.location());
                params.add(new ParameterSymbol(p.name(), pt, p.location(), p.isFinal()));
            }
            JType logicalReturn = resolveTypeName(method.returnType(), method.returnsArray(), method.location());
            JType returnType = logicalReturn;
            if (method.isAsync()) {
                if (method.returnType().kind() == TypeName.Kind.CLASS
                        && "Future".equals(method.returnType().name())) {
                    reporter.error(method.location(),
                            "async method return type must not be Future<T>; use 'async T m()' instead");
                } else if (logicalReturn instanceof VoidType) {
                    reporter.error(method.location(), "async method must return a value (not void)");
                } else if (isFutureType(logicalReturn, method.location())) {
                    reporter.error(method.location(),
                            "async method return type must not be Future<T>; use 'async T m()' instead");
                } else if (!(logicalReturn instanceof ErrorType)) {
                    ClassSymbol fc = resolveFutureClass(method.location());
                    if (fc == null) {
                        reporter.error(method.location(),
                                "async/await requires Future (missing async runtime)");
                    } else if (fc.isGeneric()) {
                        returnType = new ParameterizedType(fc, List.of(logicalReturn));
                    } else {
                        returnType = fc.asType();
                    }
                }
            }

            boolean looksLikeMain = method.name().equals("main")
                    && method.isStatic()
                    && returnType instanceof VoidType
                    && params.size() == 1
                    && params.getFirst().type() instanceof ArrayType at
                    && at.elementType() instanceof ClassType ct
                    && ct.name().equals("String");
            // Only one process entry per CU may mangle to jn_main. Additional
            // main methods keep normal mangling (avoids duplicate @jn_main / %args.addr).
            boolean isMain = looksLikeMain && !entryMainAssigned;
            if (isMain) {
                entryMainAssigned = true;
            }

            String mangled = NameMangler.mangleMethod(
                    symbol.name(), method.name(),
                    params.stream().map(ParameterSymbol::type).toList(),
                    isMain);

            MethodSymbol ms = new MethodSymbol(
                    symbol, method.name(), returnType, params,
                    method.isStatic(), isMain, method.isNative(), method.isAbstract(), mangled, method.location());
            ms.setTypeParameters(methodTypeParams);
            ms.setFinal(method.isFinal());
            Visibility methodVis = method.visibility() == null
                    ? (method.isPublic() ? Visibility.PUBLIC : Visibility.PACKAGE)
                    : method.visibility();
            ms.setVisibility(exportStdlibMember(methodVis, method.location()));
            if (method.isAsync()) {
                ms.setAsync(true);
                ms.setLogicalReturnType(logicalReturn);
            }
            applyEffectContract(ms, method.effectContract());

            if (method.isNative()) {
                bindNativeMethod(ms, method, returnType, params);
            }

            Optional<MethodSymbol> inherited = symbol.superClass()
                    .flatMap(s -> s.findMethod(method.name(), ms.parameterTypes()));
            if (inherited.isPresent()) {
                MethodSymbol parent = inherited.get();
                if (ms.isNative() || parent.isNative()) {
                    reporter.error(method.location(),
                            "native methods cannot override or be overridden");
                } else if (parent.isStatic() || ms.isStatic()) {
                    if (!(parent.isStatic() && ms.isStatic())) {
                        reporter.error(method.location(),
                                "instance/static mismatch overriding '" + method.name() + "'");
                    }
                    /* static same-signature hide is allowed; different signatures are overloads */
                } else if (parent.isFinal()) {
                    reporter.error(method.location(),
                            "method '" + method.name() + "' cannot override final method in "
                                    + parent.owner().name());
                } else if (!ms.sameSignatureAs(parent)) {
                    reporter.error(method.location(),
                            "method '" + method.name()
                                    + "' does not correctly override superclass method");
                } else {
                    ms.setOverrides(parent);
                    if (visibilityRank(ms.visibility()) < visibilityRank(parent.visibility())) {
                        reporter.error(method.location(),
                                "method '" + method.name()
                                        + "' is attempting to assign weaker access privileges; was "
                                        + parent.visibility().name().toLowerCase());
                    }
                }
            }

            if (!symbol.addMethod(ms)) {
                reporter.error(method.location(),
                        "duplicate method '" + method.name()
                                + "' with the same parameter types");
            }
            popTypeParamScope();
        }
        } finally {
            popTypeParamScope();
        }
    }

    private void registerInterfaceMethods(InterfaceSymbol symbol, ClassDeclaration cls) {
        pushTypeParamScope(symbol.typeParameters());
        try {
        for (MethodDeclaration method : cls.methods()) {
            if (method.isConstructor()) {
                continue;
            }
            List<TypeParameterSymbol> methodTypeParams =
                    createTypeParameters(method.typeParameters(), symbol, null);
            pushTypeParamScope(methodTypeParams);
            List<ParameterSymbol> params = new ArrayList<>();
            for (Parameter p : method.parameters()) {
                JType pt = resolveTypeName(p.type(), p.isArray(), p.location());
                params.add(new ParameterSymbol(p.name(), pt, p.location(), p.isFinal()));
            }
            JType returnType = resolveTypeName(method.returnType(), method.returnsArray(), method.location());
            String mangled = NameMangler.mangleMethod(
                    symbol.name(), method.name(),
                    params.stream().map(ParameterSymbol::type).toList(),
                    false);
            MethodSymbol ms = new MethodSymbol(
                    symbol, method.name(), returnType, params,
                    method.isStatic(), false, method.isNative(), true, mangled, method.location());
            ms.setTypeParameters(methodTypeParams);
            Visibility ifaceVis = method.visibility() == null
                    ? (method.isPublic() ? Visibility.PUBLIC : Visibility.PACKAGE)
                    : method.visibility();
            ms.setVisibility(exportStdlibMember(ifaceVis, method.location()));
            applyEffectContract(ms, method.effectContract());
            if (!symbol.addMethod(ms)) {
                reporter.error(method.location(),
                        "method overloading is not supported yet (duplicate '"
                                + method.name() + "')");
            }
            popTypeParamScope();
        }
        } finally {
            popTypeParamScope();
        }
    }

    private void verifyAbstractAndInterfaceCompleteness(CompilationUnit unit) {
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct() || cls.isInterface()) {
                continue;
            }
            ClassSymbol symbol = classOf(cls);
            if (symbol == null || symbol.isAbstract()) {
                continue;
            }
            for (MethodSymbol declared : symbol.declaredMethods()) {
                if (declared.isAbstract()) {
                    reporter.error(cls.location(),
                            "concrete class '" + cls.name()
                                    + "' must not declare abstract method '" + declared.name() + "'");
                }
            }
            ClassSymbol cursor = symbol.superClass().orElse(null);
            while (cursor != null) {
                for (MethodSymbol m : cursor.declaredMethods()) {
                    if (!m.isAbstract()) {
                        continue;
                    }
                    Optional<MethodSymbol> impl = symbol.findMethod(m.name());
                    if (impl.isEmpty() || impl.get().isAbstract()) {
                        reporter.error(cls.location(),
                                "class '" + cls.name() + "' must implement abstract method '"
                                        + m.name() + "' from " + cursor.name());
                    }
                }
                cursor = cursor.superClass().orElse(null);
            }
            for (InterfaceSymbol iface : symbol.transitiveInterfaces()) {
                for (InterfaceMethodSlot slot : iface.slots()) {
                    Optional<MethodSymbol> impl = ITableBuilder.resolveImplementation(symbol, slot.method());
                    if (impl.isEmpty()) {
                        reporter.error(cls.location(),
                                "class '" + cls.name() + "' must implement "
                                        + iface.name() + "." + slot.name());
                    }
                }
            }
        }
    }

    private ClassSymbol getOrCreateInterfaceAdapter(InterfaceSymbol iface) {
        return interfaceAdapters.computeIfAbsent(iface, this::createInterfaceAdapter);
    }

    private ClassSymbol createInterfaceAdapter(InterfaceSymbol iface) {
        FunctionType ft = iface.functionType();
        if (ft == null) {
            throw new IllegalStateException("non-functional interface: " + iface.name());
        }
        List<MethodSymbol> abstractMethods = FunctionalInterfaceAnalyzer.abstractMethods(iface);
        if (abstractMethods.size() != 1) {
            throw new IllegalStateException("functional interface must have one abstract method: " + iface.name());
        }
        MethodSymbol samDecl = abstractMethods.getFirst();
        String adapterName = "__jn.IFaceAdapter." + iface.name();
        ClassSymbol adapter = new ClassSymbol(adapterName, iface.location(), true);
        adapter.setSuperClass(objectClass);
        adapter.addImplementedInterface(iface);
        adapter.setFunctionType(ft);
        adapter.addField(new FieldSymbol(adapter, "code", PrimitiveType.LONG, 0, SourceLocation.UNKNOWN));
        adapter.addField(new FieldSymbol(adapter, "env", objectClass.asType(), 1, SourceLocation.UNKNOWN));
        List<ParameterSymbol> params = new ArrayList<>();
        for (int i = 0; i < ft.parameterTypes().size(); i++) {
            params.add(new ParameterSymbol("p" + i, ft.parameterTypes().get(i), SourceLocation.UNKNOWN));
        }
        String mangled = NameMangler.mangleMethod(
                adapterName, samDecl.name(), ft.parameterTypes(), false);
        MethodSymbol ms = new MethodSymbol(
                adapter, samDecl.name(), ft.returnType(), params,
                false, false, true, false, mangled, iface.location());
        ms.setFfiIntrinsic(true);
        String invokeRt = invokeRuntimeForFunctionType(ft);
        if (invokeRt == null) {
            throw new IllegalStateException(
                    "unsupported functional interface signature: " + iface.name() + " " + ft);
        }
        ms.setSamInvokeRuntime(invokeRt);
        adapter.addMethod(ms);
        adapter.setLayout(ObjectLayoutBuilder.build(adapter));
        adapter.setVtable(VTableBuilder.build(adapter));
        adapter.setItables(ITableBuilder.buildAll(adapter));
        classes.put(adapterName, adapter);
        iface.addImplementor(adapter);
        return adapter;
    }

    /**
     * @return runtime invoke helper name, or {@code null} if the SAM signature is unsupported
     *         (must diagnose — never silently map to a wrong arity/return helper).
     */
    private static String invokeRuntimeForFunctionType(FunctionType ft) {
        List<JType> params = ft.parameterTypes();
        JType ret = ft.returnType();
        if (params.isEmpty() && ret instanceof VoidType) {
            return "jnative_invoke_void";
        }
        if (params.isEmpty() && ret.isReference()) {
            return "jnative_invoke_ptr";
        }
        if (params.size() == 1 && isInvokeI32Like(params.getFirst()) && isInvokeI32Like(ret)) {
            return "jnative_invoke_i32_i32";
        }
        if (params.size() == 2
                && isInvokeI32Like(params.get(0))
                && isInvokeI32Like(params.get(1))
                && isInvokeI32Like(ret)) {
            return "jnative_invoke_i32_i32_i32";
        }
        if (params.size() == 1 && isInvokePtrLike(params.getFirst()) && ret instanceof VoidType) {
            return "jnative_invoke_void_ptr";
        }
        if (params.size() == 1 && isInvokePtrLike(params.getFirst()) && isInvokeI32Like(ret)) {
            return "jnative_invoke_i32_ptr";
        }
        if (params.size() == 2
                && isInvokePtrLike(params.get(0))
                && isInvokePtrLike(params.get(1))
                && ret instanceof VoidType) {
            return "jnative_invoke_void_ptr_ptr";
        }
        if (params.size() == 1 && isInvokePtrLike(params.getFirst()) && ret.isReference()) {
            return "jnative_invoke_ptr_ptr";
        }
        if (params.size() == 2
                && isInvokePtrLike(params.get(0))
                && isInvokePtrLike(params.get(1))
                && isInvokeI32Like(ret)) {
            return "jnative_invoke_i32_ptr_ptr";
        }
        return null;
    }

    private static boolean isInvokePtrLike(JType t) {
        return t != null && (t.isReference() || t instanceof NativePtrType);
    }

    /** int/boolean share i32 ABI in callable invoke helpers. */
    private static boolean isInvokeI32Like(JType t) {
        return t instanceof PrimitiveType p
                && (p == PrimitiveType.INT || p == PrimitiveType.BOOLEAN);
    }

    private FunctionType expectedSamFunctionType() {
        if (expectedSamType instanceof ClassType ct && ct.classSymbol().functionType() != null) {
            return ct.classSymbol().functionType();
        }
        if (expectedSamType instanceof InterfaceType it && it.interfaceSymbol().functionType() != null) {
            return it.interfaceSymbol().functionType();
        }
        if (expectedSamType instanceof ParameterizedType pt
                && pt.raw() instanceof InterfaceSymbol iface
                && iface.functionType() != null) {
            return substituteFunctionType(
                    iface.functionType(),
                    TypeSubstitution.zip(iface.typeParameters(), pt.typeArguments()));
        }
        return null;
    }

    private FunctionType substituteFunctionType(FunctionType ft, TypeSubstitution sub) {
        List<JType> params = new ArrayList<>();
        for (JType p : ft.parameterTypes()) {
            params.add(sub.apply(p));
        }
        JType ret = sub.apply(ft.returnType());
        if (ret instanceof VoidType) {
            ret = VoidType.INSTANCE;
        }
        return new FunctionType(params, ret, ft.domain());
    }

    private ClassSymbol resolveSamAdapterClass(JType samType) {
        if (samType instanceof ClassType ct && ct.classSymbol().functionType() != null) {
            return ct.classSymbol();
        }
        if (samType instanceof InterfaceType it && it.interfaceSymbol().functionType() != null) {
            return getOrCreateInterfaceAdapter(it.interfaceSymbol());
        }
        if (samType instanceof ParameterizedType pt
                && pt.raw() instanceof InterfaceSymbol iface
                && iface.functionType() != null) {
            return getOrCreateInterfaceAdapter(iface);
        }
        return null;
    }

    private String samMethodNameFor(JType samType) {
        if (samType instanceof ClassType ct) {
            return samMethodName(ct.classSymbol());
        }
        if (samType instanceof InterfaceType it) {
            List<MethodSymbol> methods = FunctionalInterfaceAnalyzer.abstractMethods(it.interfaceSymbol());
            return methods.isEmpty() ? "apply" : methods.getFirst().name();
        }
        if (samType instanceof ParameterizedType pt && pt.raw() instanceof InterfaceSymbol iface) {
            List<MethodSymbol> methods = FunctionalInterfaceAnalyzer.abstractMethods(iface);
            return methods.isEmpty() ? "apply" : methods.getFirst().name();
        }
        return "apply";
    }

    private void registerConstructors(ClassSymbol symbol, ClassDeclaration cls) {
        pushTypeParamScope(symbol.typeParameters());
        try {
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
                params.add(new ParameterSymbol(p.name(), pt, p.location(), p.isFinal()));
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
        } finally {
            popTypeParamScope();
        }
    }

    private void resolveConstructorDelegations(CompilationUnit unit) {
        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol symbol = classOf(cls);
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
        pushTypeParamScope(owner.typeParameters());
        try {
            ConstructorSymbol ctor = findCtor(owner, method);
            if (ctor == null) {
                return;
            }
            currentConstructor = ctor;
            currentMethod = null;
            current = new Scope(null);
            beginDaFrame();
            current.declare(new Symbol.VariableBinding(
                    new VariableSymbol("this", owner.asType(),
                            VariableSymbol.Kind.PARAMETER, method.location())));
            for (ParameterSymbol p : ctor.parameters()) {
                VariableSymbol vs = new VariableSymbol(
                        p.name(), p.type(), VariableSymbol.Kind.PARAMETER, p.location(),
                        false, p.isFinal());
                if (!current.declare(new Symbol.VariableBinding(vs))) {
                    reporter.error(p.location(), "duplicate parameter '" + p.name() + "'");
                }
                daEnv.declare(vs, true);
            }
            analyzeStatement(method.body());
            current = null;
            currentConstructor = null;
            endDaFrame();
        } finally {
            popTypeParamScope();
        }
    }

    private ConstructorSymbol findCtor(ClassSymbol owner, MethodDeclaration method) {
        // Caller must have pushed owner's type-parameter scope when parameters may
        // mention class type variables (e.g. Box(T value)).
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

    private void analyzeMethod(ClassSymbol owner, MethodDeclaration method, int overloadIndex) {
        /*
         * Match overload by declaration order within the same name.
         * Do NOT re-resolve parameter types and findMethod(...) — for generics that
         * creates fresh TypeVariables that fail equality with the registered ones,
         * skipping body analysis (empty expressionTypes → IR NPE on new T[]).
         */
        List<MethodSymbol> overs = owner.findDeclaredMethods(method.name());
        if (overs.isEmpty()) {
            return;
        }
        MethodSymbol matched = overloadIndex < overs.size()
                ? overs.get(overloadIndex)
                : overs.get(0);
        currentMethod = matched;
        currentConstructor = null;
        current = new Scope(null);
        beginDaFrame();
        pushTypeParamScope(owner.typeParameters());
        pushTypeParamScope(currentMethod.typeParameters());
        try {
            if (!currentMethod.isStatic()) {
                current.declare(new Symbol.VariableBinding(
                        new VariableSymbol("this", owner.asType(),
                                VariableSymbol.Kind.PARAMETER, method.location())));
            }
            for (ParameterSymbol p : currentMethod.parameters()) {
                VariableSymbol vs = new VariableSymbol(
                        p.name(), p.type(), VariableSymbol.Kind.PARAMETER, p.location(),
                        false, p.isFinal());
                if (!current.declare(new Symbol.VariableBinding(vs))) {
                    reporter.error(p.location(), "duplicate parameter '" + p.name() + "'");
                }
                daEnv.declare(vs, true);
            }
            if (method.isNative() || method.isAbstract() || method.body() == null) {
                return;
            }
            analyzeStatement(method.body());
            if (!(currentMethod.returnType() instanceof VoidType)
                    && !(currentMethod.returnType() instanceof ErrorType)) {
                if (!hasReturn(method.body())) {
                    reporter.error(method.location(),
                            "missing return statement in method '" + method.name() + "'");
                }
            }
        } finally {
            popTypeParamScope(); // method type params
            popTypeParamScope(); // class type params
            current = null;
            currentMethod = null;
            endDaFrame();
        }
    }

    private void beginDaFrame() {
        daEnv = new DaEnv();
        daReachable = true;
        breakDaExits.clear();
        blockDaLocals.clear();
        loopDeclaredLocals.clear();
    }

    private void endDaFrame() {
        daEnv = new DaEnv();
        daReachable = true;
        breakDaExits.clear();
        blockDaLocals.clear();
        loopDeclaredLocals.clear();
    }

    private boolean isLiteralTrue(Expression expr) {
        return expr instanceof LiteralExpression lit
                && lit.kind() == LiteralExpression.Kind.BOOLEAN
                && Boolean.TRUE.equals(lit.value());
    }

    private void noteLocalForDa(VariableSymbol vs) {
        if (!blockDaLocals.isEmpty()) {
            blockDaLocals.peek().add(vs);
        }
        if (!loopDeclaredLocals.isEmpty()) {
            loopDeclaredLocals.peek().add(vs);
        }
    }

    private boolean declaredInsideSomeLoop(VariableSymbol vs) {
        for (Set<VariableSymbol> set : loopDeclaredLocals) {
            if (set.contains(vs)) {
                return true;
            }
        }
        return false;
    }

    private void checkLocalRead(VariableSymbol vs, SourceLocation loc) {
        if (skippingDaRead || !daReachable || !daEnv.tracks(vs)) {
            return;
        }
        if (vs.kind() == VariableSymbol.Kind.LOCAL && !daEnv.isAssigned(vs)) {
            reporter.error(loc,
                    "variable '" + vs.name() + "' might not have been initialized");
        }
    }

    private void assignLocal(VariableSymbol vs, SourceLocation loc) {
        if (!daReachable || !daEnv.tracks(vs)) {
            return;
        }
        if (vs.isFinal()) {
            if (daEnv.isAssigned(vs)) {
                reporter.error(loc,
                        "cannot assign a value to final variable '" + vs.name() + "'");
                return;
            }
            if (!daEnv.isUnassigned(vs)) {
                reporter.error(loc,
                        "variable '" + vs.name() + "' might already have been assigned");
                return;
            }
            // One-pass loop analysis: outer blank finals are not DU on later iterations.
            if (loopDepth > 0 && !declaredInsideSomeLoop(vs)) {
                reporter.error(loc,
                        "variable '" + vs.name() + "' might already have been assigned");
                return;
            }
        }
        daEnv.markAssigned(vs);
        vs.setAssignedAfterInit(true);
    }

    private void bindNativeMethod(
            MethodSymbol ms,
            MethodDeclaration method,
            JType returnType,
            List<ParameterSymbol> params
    ) {
        if (!method.isStatic()) {
            reporter.error(method.location(), "instance native methods are not supported in 0.7");
        }
        if (method.body() != null) {
            reporter.error(method.location(), "native method cannot have a body");
        }
        for (ParameterSymbol p : params) {
            if (!isFfiAllowedType(p.type())) {
                reporter.error(p.location(),
                        "unsupported native parameter type: " + p.type());
            }
        }
        if (!(returnType instanceof VoidType) && !isFfiAllowedType(returnType)) {
            reporter.error(method.location(),
                    "unsupported native return type: " + returnType);
        }
        String symbol = method.nativeAnnotation() != null
                ? method.nativeAnnotation().symbol().orElse(method.name())
                : method.name();
        String library = method.nativeAnnotation() != null
                ? method.nativeAnnotation().library().orElse(null)
                : null;
        if (library != null) {
            nativeLibraries.putIfAbsent(library, library);
        }
        try {
            List<AbiType> abiParams = new ArrayList<>();
            for (ParameterSymbol p : params) {
                abiParams.add(mapToAbi(p.type()));
            }
            AbiType abiRet = returnType instanceof VoidType
                    ? AbiType.VOID
                    : mapToAbi(returnType);
            NativeFunction nf = new NativeFunction(
                    method.name(),
                    symbol,
                    library,
                    new NativeSignature(abiRet, abiParams),
                    CallingConvention.C);
            ms.setNativeFunction(nf);
            if (method.nativeAnnotation() != null && method.nativeAnnotation().effects().isPresent()) {
                String contract = method.nativeAnnotation().effects().get();
                ms.setNativeEffectsContract(contract);
                ms.setInferredEffects(effectsFromNativeContract(contract));
                ms.setEffectiveEffects(ms.inferredEffects().copy());
            } else {
                ms.setNativeEffectsContract("UNKNOWN");
                ms.setInferredEffects(FunctionEffects.unknown());
                ms.setEffectiveEffects(FunctionEffects.unknown());
            }
        } catch (IllegalArgumentException ex) {
            reporter.error(method.location(), ex.getMessage());
        }
    }

    private void applyEffectContract(MethodSymbol ms, io.jnative.ast.EffectContract ec) {
        if (ec == null) {
            return;
        }
        ms.setContractNoGc(ec.noGc());
        ms.setContractNoThrow(ec.noThrow());
        ms.setContractNoBlock(ec.noBlock());
        FunctionEffects declared = FunctionEffects.none();
        // Declared contracts forbid effects; stored as flags on the symbol.
        ms.setDeclaredEffects(declared);
    }

    private static FunctionEffects effectsFromNativeContract(String contract) {
        String c = contract == null ? "UNKNOWN" : contract.trim().toUpperCase();
        return switch (c) {
            case "PURE" -> FunctionEffects.pure();
            case "NO_BLOCK" -> new FunctionEffects(FunctionEffects.NATIVE_CALL);
            case "MAY_BLOCK" -> FunctionEffects.nativeCall(true, false);
            case "NO_CALLBACK" -> new FunctionEffects(FunctionEffects.NATIVE_CALL);
            case "MAY_CALLBACK" -> FunctionEffects.nativeCall(false, true);
            default -> FunctionEffects.unknown();
        };
    }

    private boolean isFfiAllowedType(JType type) {
        if (type instanceof PrimitiveType) {
            return true;
        }
        if (type instanceof NativePtrType) {
            return true;
        }
        // NativeStruct by-value is not an FFI parameter/return type in 0.8.
        if (type instanceof NativeStructType) {
            return false;
        }
        if (type instanceof ClassType ct && ct.name().equals("String")) {
            return true;
        }
        return false;
    }

    private AbiType mapToAbi(JType type) {
        if (type instanceof NativePtrType) {
            return AbiType.PTR;
        }
        if (type instanceof ClassType ct && ct.name().equals("String")) {
            return AbiType.CSTRING;
        }
        if (type instanceof PrimitiveType p) {
            return targetAbi.mapPrimitiveJavaType(p.displayName());
        }
        throw new IllegalArgumentException("unsupported ABI mapping for " + type);
    }

    private boolean hasReturn(Statement stmt) {
        return switch (stmt) {
            case ReturnStatement ignored -> true;
            case ThrowStatement ignored -> true;
            case BlockStatement b -> b.statements().stream().anyMatch(this::hasReturn);
            case IfStatement i -> hasReturn(i.thenBranch())
                    && i.elseBranch() != null && hasReturn(i.elseBranch());
            case WhileStatement w -> hasReturn(w.body());
            case DoWhileStatement d -> hasReturn(d.body());
            case ForStatement f -> hasReturn(f.body());
            case EnhancedForStatement ef -> hasReturn(ef.body());
            case SwitchStatement s -> s.sections().stream()
                    .flatMap(sec -> sec.statements().stream())
                    .anyMatch(this::hasReturn);
            case BreakStatement ignored -> false;
            case ContinueStatement ignored -> false;
            case TryStatement t -> hasReturn(t.tryBlock())
                    || t.catches().stream().anyMatch(c -> hasReturn(c.body()))
                    || (t.finallyBlock() != null && hasReturn(t.finallyBlock()));
            default -> false;
        };
    }

    /**
     * Synthetic members for basic enums: {@code $name}/{@code $ordinal} instance fields,
     * public static constants, private (String,int) constructor, and name/ordinal/values/valueOf.
     */
    private void registerEnumSynthetics(ClassSymbol symbol, ClassDeclaration cls) {
        SourceLocation loc = cls.location();
        JType self = symbol.asType();
        JType stringType = stringClass.asType();
        FieldSymbol nameField = new FieldSymbol(
                symbol, "$name", stringType, 0, false, false, Visibility.PRIVATE, loc);
        FieldSymbol ordinalField = new FieldSymbol(
                symbol, "$ordinal", PrimitiveType.INT, 1, false, false, Visibility.PRIVATE, loc);
        symbol.addField(nameField);
        symbol.addField(ordinalField);
        int index = 2;
        for (String constant : cls.enumConstants()) {
            FieldSymbol fs = new FieldSymbol(
                    symbol, constant, self, index++, true, false, Visibility.PUBLIC, loc);
            if (!symbol.addField(fs)) {
                reporter.error(loc, "duplicate enum constant '" + constant + "'");
            }
        }
        ConstructorSymbol ctor = new ConstructorSymbol(
                symbol,
                List.of(
                        new ParameterSymbol("$name", stringType, loc),
                        new ParameterSymbol("$ordinal", PrimitiveType.INT, loc)),
                false,
                loc);
        symbol.addConstructor(ctor);

        addEnumMethod(symbol, "name", stringType, List.of(), false, loc);
        addEnumMethod(symbol, "ordinal", PrimitiveType.INT, List.of(), false, loc);
        addEnumMethod(symbol, "values", new ArrayType(self), List.of(), true, loc);
        addEnumMethod(symbol, "valueOf", self,
                List.of(new ParameterSymbol("name", stringType, loc)), true, loc);
    }

    private void addEnumMethod(
            ClassSymbol owner,
            String name,
            JType returnType,
            List<ParameterSymbol> params,
            boolean isStatic,
            SourceLocation loc
    ) {
        String mangled = NameMangler.mangleMethod(
                owner.name(), name, params.stream().map(ParameterSymbol::type).toList(), false);
        MethodSymbol ms = new MethodSymbol(
                owner, name, returnType, params, isStatic, false, false, false, mangled, loc);
        ms.setVisibility(Visibility.PUBLIC);
        // Bodies are synthesized in IrGenerator.lowerEnumMethods (no source MethodDeclaration).
        owner.addMethod(ms);
    }

    private void analyzeSwitch(SwitchStatement s) {
        JType selectorType = analyzeExpression(s.selector());
        boolean enumSwitch = false;
        ClassSymbol enumSym = null;
        if (selectorType instanceof ClassType ct && ct.classSymbol().isEnum()) {
            enumSwitch = true;
            enumSym = ct.classSymbol();
        } else if (selectorType == PrimitiveType.BYTE
                || selectorType == PrimitiveType.SHORT
                || selectorType == PrimitiveType.CHAR
                || selectorType == PrimitiveType.INT) {
            enumSwitch = false;
        } else if (selectorType instanceof ErrorType) {
            // already reported
        } else {
            reporter.error(s.selector().location(),
                    "switch selector must be byte, short, char, int, or an enum type; found "
                            + selectorType);
        }

        Set<Integer> intLabels = new HashSet<>();
        Set<String> enumLabels = new HashSet<>();
        boolean sawDefault = false;
        DaEnv beforeSwitch = daEnv.copy();
        breakDaExits.push(new ArrayList<>());
        switchDepth++;
        Scope previous = current;
        current = new Scope(previous);
        blockDaLocals.push(new ArrayList<>());
        try {
            for (SwitchSection section : s.sections()) {
                for (SwitchLabel label : section.labels()) {
                    switch (label) {
                        case DefaultLabel d -> {
                            if (sawDefault) {
                                reporter.error(d.location(), "duplicate default label in switch");
                            }
                            sawDefault = true;
                        }
                        case CaseLabel c -> {
                            if (enumSwitch && enumSym != null) {
                                String constName = enumCaseName(c.constant());
                                if (constName == null) {
                                    reporter.error(c.location(),
                                            "enum switch case must be an unqualified enum constant name");
                                } else if (enumSym.enumOrdinal(constName) < 0) {
                                    reporter.error(c.location(),
                                            "unknown enum constant '" + constName + "' for type "
                                                    + enumSym.simpleName());
                                } else if (!enumLabels.add(constName)) {
                                    reporter.error(c.location(),
                                            "duplicate case label '" + constName + "'");
                                }
                            } else {
                                Integer value = evalSwitchIntConstant(c.constant());
                                if (value == null) {
                                    reporter.error(c.location(),
                                            "case label must be a compile-time integer constant");
                                } else if (!intLabels.add(value)) {
                                    reporter.error(c.location(),
                                            "duplicate case label " + value);
                                } else {
                                    expressionTypes.put(c.constant(), PrimitiveType.INT);
                                }
                            }
                        }
                    }
                }
                // Case is a jump target: intersect fall-through state with pre-switch state.
                if (!daReachable) {
                    daEnv = beforeSwitch.copy();
                    daReachable = true;
                } else {
                    DaEnv fall = daEnv.copy();
                    daEnv = beforeSwitch.copy();
                    daEnv.mergeFrom(fall);
                }
                for (Statement st : section.statements()) {
                    analyzeStatement(st);
                }
            }
            List<DaEnv> exits = breakDaExits.pop();
            if (daReachable) {
                exits.add(daEnv.copy());
            }
            if (!sawDefault) {
                exits.add(beforeSwitch.copy());
            }
            if (exits.isEmpty()) {
                daReachable = false;
            } else {
                daReachable = true;
                daEnv = DaEnv.mergeAll(exits);
            }
        } finally {
            for (VariableSymbol vs : blockDaLocals.pop()) {
                daEnv.remove(vs);
            }
            current = previous;
            switchDepth--;
        }
    }

    private String enumCaseName(Expression constant) {
        if (constant instanceof VariableExpression v) {
            return v.name();
        }
        return null;
    }

    /** Compile-time int constant for switch case labels (literals, unary ±, grouping, int cast). */
    private Integer evalSwitchIntConstant(Expression expr) {
        return switch (expr) {
            case LiteralExpression lit -> switch (lit.kind()) {
                case INT -> (Integer) lit.value();
                case LONG -> {
                    long v = (Long) lit.value();
                    if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                        yield null;
                    }
                    yield (int) v;
                }
                default -> null;
            };
            case UnaryExpression u -> {
                Integer inner = evalSwitchIntConstant(u.operand());
                if (inner == null) {
                    yield null;
                }
                yield switch (u.operator()) {
                    case NEGATE -> -inner;
                    case PLUS -> inner;
                    default -> null;
                };
            }
            case GroupingExpression g -> evalSwitchIntConstant(g.expression());
            case CastExpression c -> {
                JType target = resolveTypeName(c.type(), false, c.location());
                if (target != PrimitiveType.INT && target != PrimitiveType.BYTE
                        && target != PrimitiveType.SHORT && target != PrimitiveType.CHAR) {
                    yield null;
                }
                Integer inner = evalSwitchIntConstant(c.expression());
                if (inner == null) {
                    yield null;
                }
                if (target == PrimitiveType.BYTE) {
                    yield (int) (byte) (int) inner;
                }
                if (target == PrimitiveType.SHORT) {
                    yield (int) (short) (int) inner;
                }
                if (target == PrimitiveType.CHAR) {
                    yield inner & 0xFFFF;
                }
                yield inner;
            }
            default -> null;
        };
    }

    private void analyzeStatement(Statement stmt) {
        switch (stmt) {
            case BlockStatement b -> {
                Scope previous = current;
                current = new Scope(previous);
                blockDaLocals.push(new ArrayList<>());
                for (Statement s : b.statements()) {
                    analyzeStatement(s);
                }
                for (VariableSymbol vs : blockDaLocals.pop()) {
                    daEnv.remove(vs);
                }
                current = previous;
            }
            case VariableDeclaration v -> analyzeVariableDeclaration(v);
            case ExpressionStatement e -> analyzeExpression(e.expression());
            case IfStatement i -> {
                expectBoolean(analyzeExpression(i.condition()), i.condition().location(), "if condition");
                DaEnv before = daEnv.copy();
                boolean reachBefore = daReachable;
                analyzeStatement(i.thenBranch());
                DaEnv thenEnv = daEnv.copy();
                boolean thenReach = daReachable;
                daEnv = before.copy();
                daReachable = reachBefore;
                if (i.elseBranch() != null) {
                    analyzeStatement(i.elseBranch());
                }
                DaEnv elseEnv = daEnv.copy();
                boolean elseReach = daReachable;
                List<DaEnv> merge = new ArrayList<>();
                if (thenReach) {
                    merge.add(thenEnv);
                }
                if (elseReach) {
                    merge.add(elseEnv);
                }
                if (merge.isEmpty()) {
                    daReachable = false;
                } else {
                    daReachable = true;
                    daEnv = DaEnv.mergeAll(merge);
                }
            }
            case WhileStatement w -> {
                expectBoolean(analyzeExpression(w.condition()), w.condition().location(), "while condition");
                DaEnv before = daEnv.copy();
                boolean infinite = isLiteralTrue(w.condition());
                breakDaExits.push(new ArrayList<>());
                loopDeclaredLocals.push(new HashSet<>());
                loopDepth++;
                analyzeStatement(w.body());
                loopDepth--;
                loopDeclaredLocals.pop();
                List<DaEnv> exits = breakDaExits.pop();
                if (!infinite) {
                    exits.add(before);
                }
                if (exits.isEmpty()) {
                    daReachable = false;
                } else {
                    daReachable = true;
                    daEnv = DaEnv.mergeAll(exits);
                }
            }
            case DoWhileStatement d -> {
                breakDaExits.push(new ArrayList<>());
                loopDeclaredLocals.push(new HashSet<>());
                loopDepth++;
                analyzeStatement(d.body());
                loopDepth--;
                loopDeclaredLocals.pop();
                List<DaEnv> breaks = breakDaExits.pop();
                expectBoolean(analyzeExpression(d.condition()), d.condition().location(), "do-while condition");
                boolean infinite = isLiteralTrue(d.condition());
                List<DaEnv> exits = new ArrayList<>(breaks);
                if (!infinite && daReachable) {
                    exits.add(daEnv.copy());
                }
                if (exits.isEmpty()) {
                    daReachable = false;
                } else {
                    daReachable = true;
                    daEnv = DaEnv.mergeAll(exits);
                }
            }
            case ForStatement f -> {
                Scope previous = current;
                current = new Scope(previous);
                blockDaLocals.push(new ArrayList<>());
                if (f.initializer() != null) {
                    analyzeStatement(f.initializer());
                }
                DaEnv beforeLoop = daEnv.copy();
                if (f.condition() != null) {
                    expectBoolean(analyzeExpression(f.condition()),
                            f.condition().location(), "for condition");
                }
                boolean infinite = f.condition() == null || isLiteralTrue(f.condition());
                breakDaExits.push(new ArrayList<>());
                loopDeclaredLocals.push(new HashSet<>());
                loopDepth++;
                analyzeStatement(f.body());
                loopDepth--;
                loopDeclaredLocals.pop();
                if (f.update() != null && daReachable) {
                    analyzeExpression(f.update());
                } else if (f.update() != null) {
                    // Still type-check update on a side path
                    boolean r = daReachable;
                    daReachable = true;
                    analyzeExpression(f.update());
                    daReachable = r;
                }
                List<DaEnv> exits = breakDaExits.pop();
                if (!infinite) {
                    exits.add(beforeLoop);
                }
                if (exits.isEmpty()) {
                    daReachable = false;
                } else {
                    daReachable = true;
                    daEnv = DaEnv.mergeAll(exits);
                }
                for (VariableSymbol vs : blockDaLocals.pop()) {
                    daEnv.remove(vs);
                }
                current = previous;
            }
            case EnhancedForStatement ef -> analyzeEnhancedFor(ef);
            case SwitchStatement s -> analyzeSwitch(s);
            case BreakStatement b -> {
                if (loopDepth + switchDepth <= 0) {
                    reporter.error(b.location(),
                            "[JN1001] break outside loop or switch");
                } else if (daReachable) {
                    if (!breakDaExits.isEmpty()) {
                        breakDaExits.peek().add(daEnv.copy());
                    }
                    daReachable = false;
                }
            }
            case ContinueStatement c -> {
                if (loopDepth <= 0) {
                    reporter.error(c.location(),
                            "[JN1002] continue outside loop");
                } else if (daReachable) {
                    daReachable = false;
                }
            }
            case ReturnStatement r -> {
                analyzeReturn(r);
                if (daReachable) {
                    daReachable = false;
                }
            }
            case ConstructorDelegationStatement d -> {
                for (Expression arg : d.arguments()) {
                    analyzeExpression(arg);
                }
                if (currentConstructor != null && currentConstructor.delegatesTo() != null) {
                    // Keep for IR via ConstructorSymbol.delegatesTo
                }
            }
            case EmptyStatement ignored -> {
            }
            case ThrowStatement t -> {
                analyzeThrow(t);
                if (daReachable) {
                    daReachable = false;
                }
            }
            case TryStatement t -> analyzeTry(t);
        }
    }

    private void analyzeEnhancedFor(EnhancedForStatement ef) {
        JType elemType = resolveTypeName(ef.elementType(), ef.elementIsArray(), ef.location());
        if (elemType instanceof VoidType) {
            reporter.error(ef.location(), "cannot declare variable of type void");
            elemType = ErrorType.INSTANCE;
        }
        JType iterableType = analyzeExpression(ef.array());
        boolean isArray = iterableType instanceof ArrayType;
        JType sourceElem = null;
        if (isArray) {
            sourceElem = ((ArrayType) iterableType).elementType();
        } else if (!(iterableType instanceof ErrorType)) {
            sourceElem = extractIterableElementType(iterableType);
            if (sourceElem == null) {
                reporter.error(ef.array().location(),
                        "enhanced for requires an array or Iterable; found " + iterableType);
            }
        }
        if (sourceElem != null && !(elemType instanceof ErrorType)
                && !assignableWithBoxing(elemType, sourceElem)) {
            reporter.error(ef.location(),
                    "incompatible types: " + sourceElem
                            + " cannot be converted to " + elemType);
        }

        Scope previous = current;
        current = new Scope(previous);
        blockDaLocals.push(new ArrayList<>());

        if (!isArray && sourceElem != null) {
            int id = System.identityHashCode(ef);
            String iterName = "$foreach$iter$" + id;
            MethodCallExpression iteratorCall = new MethodCallExpression(
                    ef.array(), "iterator", List.of(), ef.location());
            JType iteratorType = analyzeExpression(iteratorCall);
            if (!(iteratorType instanceof ErrorType)) {
                VariableSymbol iterVs = new VariableSymbol(
                        iterName, iteratorType, VariableSymbol.Kind.LOCAL, ef.location());
                current.declare(new Symbol.VariableBinding(iterVs));
                daEnv.declare(iterVs, true);
                noteLocalForDa(iterVs);

                VariableExpression iterVar = new VariableExpression(iterName, ef.location());
                expressionTypes.put(iterVar, iteratorType);
                resolvedSymbols.put(iterVar, iterVs);

                MethodCallExpression hasNextCall = new MethodCallExpression(
                        iterVar, "hasNext", List.of(), ef.location());
                MethodCallExpression nextCall = new MethodCallExpression(
                        iterVar, "next", List.of(), ef.location());
                JType hn = analyzeExpression(hasNextCall);
                expectBoolean(hn, hasNextCall.location(), "Iterator.hasNext()");
                analyzeExpression(nextCall);
                enhancedForDesugars.put(ef, new EnhancedForDesugar(
                        iteratorCall, hasNextCall, nextCall, iterName));
            }
        }

        VariableSymbol vs = new VariableSymbol(
                ef.variableName(), elemType, VariableSymbol.Kind.LOCAL, ef.location(),
                false, ef.elementIsFinal());
        if (!current.declare(new Symbol.VariableBinding(vs))) {
            reporter.error(ef.location(), "duplicate variable '" + ef.variableName() + "'");
        }
        daEnv.declare(vs, true);
        noteLocalForDa(vs);

        DaEnv before = daEnv.copy();
        breakDaExits.push(new ArrayList<>());
        loopDeclaredLocals.push(new HashSet<>());
        loopDepth++;
        analyzeStatement(ef.body());
        loopDepth--;
        loopDeclaredLocals.pop();
        List<DaEnv> exits = breakDaExits.pop();
        exits.add(before);
        daReachable = true;
        daEnv = DaEnv.mergeAll(exits);
        for (VariableSymbol local : blockDaLocals.pop()) {
            daEnv.remove(local);
        }
        current = previous;
    }

    /** Element type of Iterable&lt;E&gt; (or subtype), or null if not iterable. */
    private JType extractIterableElementType(JType type) {
        InterfaceSymbol iterable = findInterfaceBySimpleName("Iterable");
        if (iterable == null) {
            return null;
        }
        if (type instanceof ParameterizedType pt) {
            if (pt.raw() instanceof InterfaceSymbol is && sameInterface(is, iterable)) {
                return pt.typeArguments().isEmpty() ? null : pt.typeArguments().get(0);
            }
            if (pt.raw() instanceof ClassSymbol cs) {
                TypeSubstitution sub = TypeSubstitution.zip(cs.typeParameters(), pt.typeArguments());
                return findIterableElemFromClass(cs, sub, iterable);
            }
            if (pt.raw() instanceof InterfaceSymbol is) {
                TypeSubstitution sub = TypeSubstitution.zip(is.typeParameters(), pt.typeArguments());
                return findIterableElemFromIface(is, sub, iterable);
            }
        }
        if (type instanceof ClassType ct) {
            return findIterableElemFromClass(
                    ct.classSymbol(), new TypeSubstitution(Map.of()), iterable);
        }
        if (type instanceof InterfaceType it) {
            return findIterableElemFromIface(
                    it.interfaceSymbol(), new TypeSubstitution(Map.of()), iterable);
        }
        return null;
    }

    private JType findIterableElemFromClass(
            ClassSymbol cs, TypeSubstitution classSub, InterfaceSymbol iterable
    ) {
        for (ParameterizedType impl : cs.implementedParameterizedInterfaces()) {
            JType applied = classSub.apply(impl);
            if (applied instanceof ParameterizedType pt && pt.raw() instanceof InterfaceSymbol is) {
                TypeSubstitution ifaceSub = TypeSubstitution.zip(
                        is.typeParameters(), pt.typeArguments());
                JType elem = findIterableElemFromIface(is, ifaceSub, iterable);
                if (elem != null) {
                    return elem;
                }
            }
        }
        for (InterfaceSymbol iface : cs.implementedInterfaces()) {
            JType elem = findIterableElemFromIface(iface, classSub, iterable);
            if (elem != null) {
                return elem;
            }
        }
        return cs.superClass()
                .map(sup -> findIterableElemFromClass(sup, classSub, iterable))
                .orElse(null);
    }

    private JType findIterableElemFromIface(
            InterfaceSymbol iface, TypeSubstitution outerSub, InterfaceSymbol iterable
    ) {
        if (sameInterface(iface, iterable)) {
            if (iface.typeParameters().isEmpty()) {
                return objectClass != null ? objectClass.asType() : null;
            }
            return outerSub.apply(iface.typeParameters().get(0).asType());
        }
        for (ParameterizedType superPt : iface.parameterizedSuperInterfaces()) {
            JType applied = outerSub.apply(superPt);
            if (applied instanceof ParameterizedType pt && pt.raw() instanceof InterfaceSymbol is) {
                if (sameInterface(is, iterable)) {
                    return pt.typeArguments().isEmpty() ? null : pt.typeArguments().get(0);
                }
                TypeSubstitution nested = TypeSubstitution.zip(
                        is.typeParameters(), pt.typeArguments());
                JType elem = findIterableElemFromIface(is, nested, iterable);
                if (elem != null) {
                    return elem;
                }
            }
        }
        for (InterfaceSymbol superI : iface.superInterfaces()) {
            JType elem = findIterableElemFromIface(superI, outerSub, iterable);
            if (elem != null) {
                return elem;
            }
        }
        return null;
    }

    private InterfaceSymbol findInterfaceBySimpleName(String simple) {
        InterfaceSymbol direct = interfaces.get(simple);
        if (direct != null) {
            return direct;
        }
        for (InterfaceSymbol is : interfaces.values()) {
            if (simple.equals(is.simpleName())) {
                return is;
            }
        }
        return null;
    }

    private static boolean sameInterface(InterfaceSymbol a, InterfaceSymbol b) {
        return a == b || a.name().equals(b.name()) || a.simpleName().equals(b.simpleName());
    }

    private void analyzeThrow(ThrowStatement t) {
        JType type = analyzeExpression(t.expression());
        // Java: `throw null` is well-typed and becomes NullPointerException at runtime.
        if (type instanceof NullType) {
            return;
        }
        if (!isThrowableType(type) && !(type instanceof ErrorType)) {
            reporter.error(t.location(), "throw expression must be a Throwable subtype");
        }
    }

    private void analyzeTry(TryStatement t) {
        Scope outer = current;
        current = new Scope(outer);
        List<MethodCallExpression> closes = new ArrayList<>();
        for (VariableDeclaration resource : t.resources()) {
            if (resource.initializer() == null) {
                reporter.error(resource.location(),
                        "try-with-resources variable must be initialized");
            }
            analyzeVariableDeclaration(resource);
            checkResourceType(resource);
            VariableExpression ve = new VariableExpression(resource.name(), resource.location());
            analyzeExpression(ve);
            MethodCallExpression closeCall = new MethodCallExpression(
                    ve, "close", List.of(), resource.location());
            analyzeExpression(closeCall);
            // Reverse order so IR closes last-acquired first.
            closes.add(0, closeCall);
        }
        if (!t.resources().isEmpty()) {
            resourceCloseCalls.put(t, List.copyOf(closes));
        }
        analyzeStatement(t.tryBlock());
        current = outer;

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
                    c.variableName(), catchType, VariableSymbol.Kind.LOCAL, c.location(), false, true);
            if (!current.declare(new Symbol.VariableBinding(vs))) {
                reporter.error(c.location(), "duplicate variable '" + c.variableName() + "'");
            }
            daEnv.declare(vs, true);
            analyzeStatement(c.body());
            daEnv.remove(vs);
            current = previous;
        }
        if (t.finallyBlock() != null) {
            analyzeStatement(t.finallyBlock());
        }
    }

    /**
     * Resource types must implement {@code AutoCloseable} or declare a no-arg {@code void close()}.
     */
    private void checkResourceType(VariableDeclaration resource) {
        JType type = resolveTypeName(resource.type(), resource.isArray(), resource.location());
        if (type instanceof ErrorType) {
            return;
        }
        InterfaceSymbol autoCloseable = interfaces.get("AutoCloseable");
        if (type instanceof InterfaceType it) {
            if (autoCloseable != null && it.interfaceSymbol().isSubtypeOf(autoCloseable)) {
                return;
            }
            Optional<MethodSymbol> close = it.interfaceSymbol().findMethod("close");
            if (close.isPresent() && close.get().parameterTypes().isEmpty()
                    && close.get().returnType() instanceof VoidType) {
                return;
            }
            reporter.error(resource.location(),
                    "resource type must be AutoCloseable or declare void close()");
            return;
        }
        if (type instanceof ClassType ct) {
            ClassSymbol cls = ct.classSymbol();
            if (autoCloseable != null && cls.implementsInterface(autoCloseable)) {
                return;
            }
            Optional<MethodSymbol> close = cls.findMethod("close");
            if (close.isPresent() && !close.get().isStatic()
                    && close.get().parameterTypes().isEmpty()
                    && close.get().returnType() instanceof VoidType) {
                return;
            }
            reporter.error(resource.location(),
                    "resource type must be AutoCloseable or declare void close()");
            return;
        }
        if (type instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cls) {
            if (autoCloseable != null && cls.implementsInterface(autoCloseable)) {
                return;
            }
            Optional<MethodSymbol> close = cls.findMethod("close");
            if (close.isPresent() && !close.get().isStatic()
                    && close.get().parameterTypes().isEmpty()
                    && close.get().returnType() instanceof VoidType) {
                return;
            }
            reporter.error(resource.location(),
                    "resource type must be AutoCloseable or declare void close()");
            return;
        }
        reporter.error(resource.location(),
                "resource type must be AutoCloseable or declare void close()");
    }

    private void analyzeVariableDeclaration(VariableDeclaration v) {
        JType type = resolveTypeName(v.type(), v.isArray(), v.location());
        if (type instanceof VoidType) {
            reporter.error(v.location(), "cannot declare variable of type void");
            type = ErrorType.INSTANCE;
        }
        if (v.stackAllocated()) {
            if (!(type instanceof NativeStructType) && !(type instanceof ErrorType)) {
                reporter.error(v.location(), "@Stack requires a @NativeStruct type");
            }
        } else if (type instanceof NativeStructType) {
            reporter.error(v.location(),
                    "native struct requires explicit storage: @Stack or NativeMemory");
        }

        if (v.initializer() != null) {
            boolean prev = allowingStackNativeNew;
            allowingStackNativeNew = v.stackAllocated() && type instanceof NativeStructType;
            try {
                Expression init = v.initializer();
                if (allowingStackNativeNew) {
                    if (init instanceof NewObjectExpression n
                            && type instanceof NativeStructType ns
                            && n.className().equals(ns.name())
                            && n.arguments().isEmpty()) {
                        JType initType = analyzeExpression(init);
                        if (!type.isAssignableFrom(initType) && !(initType instanceof ErrorType)) {
                            reporter.error(v.location(),
                                    "incompatible types: " + initType
                                            + " cannot be converted to " + type);
                        }
                    } else {
                        reporter.error(v.location(),
                                "stack-allocated native struct initializer must be new "
                                        + (type instanceof NativeStructType ns
                                        ? ns.name()
                                        : "Struct") + "()");
                        analyzeExpression(init);
                    }
                } else {
                    JType initExpected = samExpectedType(type);
                    if (initExpected == null) {
                        // diamond / generic constructor / method return inference target
                        initExpected = type;
                    }
                    JType initType = analyzeExpression(init, initExpected);
                    if (!assignableWithBoxing(type, initType)) {
                        reporter.error(v.location(),
                                "incompatible types: " + initType
                                        + " cannot be converted to " + type);
                    }
                    if (type instanceof NativePtrType && init instanceof NativeNullExpression) {
                        // NativePtr.NULL is assignable to any NativePtr.
                    }
                }
            } finally {
                allowingStackNativeNew = prev;
            }
        }

        VariableSymbol vs = new VariableSymbol(
                v.name(), type, VariableSymbol.Kind.LOCAL, v.location(), v.stackAllocated(), v.isFinal());
        if (v.stackAllocated()) {
            vs.setProvenance(PointerProvenance.STACK);
        } else if (type instanceof NativePtrType && v.initializer() != null) {
            vs.setProvenance(provenances.getOrDefault(v.initializer(), PointerProvenance.UNKNOWN));
        }
        if (!current.declare(new Symbol.VariableBinding(vs))) {
            reporter.error(v.location(), "duplicate variable '" + v.name() + "'");
        }
        boolean initialized = v.initializer() != null;
        daEnv.declare(vs, initialized);
        noteLocalForDa(vs);
        if (initialized) {
            vs.setAssignedAfterInit(false);
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
        JType expected;
        FunctionType samFt = expectedSamFunctionType();
        if (!lambdaStack.isEmpty() && samFt != null) {
            expected = samFt.returnType();
        } else if (currentMethod == null) {
            reporter.error(r.location(), "return outside method");
            return;
        } else {
            expected = currentMethod.isAsync()
                    ? currentMethod.logicalReturnType()
                    : currentMethod.returnType();
        }
        if (r.value() == null) {
            if (!(expected instanceof VoidType)) {
                reporter.error(r.location(), "missing return value; expected " + expected);
            }
            return;
        }
        JType actual = analyzeExpression(r.value(), samExpectedType(expected));
        if (expected instanceof VoidType) {
            reporter.error(r.location(), "cannot return a value from void method");
        } else if (!assignableWithBoxing(expected, actual)) {
            reporter.error(r.location(),
                    "incompatible return type: " + actual + " cannot be converted to " + expected);
        }
        if (actual instanceof NativePtrType
                && provenances.get(r.value()) == PointerProvenance.STACK) {
            reporter.error(r.location(), "stack pointer escapes via return");
        }
    }

    private JType samExpectedType(JType type) {
        if (type instanceof ClassType ct && ct.classSymbol().functionType() != null) {
            return ct;
        }
        if (type instanceof InterfaceType it && it.interfaceSymbol().functionType() != null) {
            return it;
        }
        if (type instanceof ParameterizedType pt
                && pt.raw() instanceof InterfaceSymbol iface
                && iface.functionType() != null) {
            return type;
        }
        return null;
    }

    private JType analyzeExpression(Expression expr) {
        return analyzeExpression(expr, null);
    }

    private JType analyzeExpression(Expression expr, JType expected) {
        JType prevExpected = expectedSamType;
        if (expected != null) {
            expectedSamType = expected;
        }
        try {
            JType type = switch (expr) {
                case LiteralExpression l -> typeOfLiteral(l);
                case NullLiteralExpression ignored -> NullType.INSTANCE;
                case ThisExpression t -> typeOfThis(t);
                case VariableExpression v -> typeOfVariable(v);
                case AssignmentExpression a -> typeOfAssignment(a);
                case BinaryExpression b -> typeOfBinary(b);
                case UnaryExpression u -> typeOfUnary(u);
                case MethodCallExpression m -> typeOfCall(m, expected);
                case PrintlnExpression p -> typeOfPrintln(p);
                case GroupingExpression g -> analyzeExpression(g.expression());
                case NewObjectExpression n -> typeOfNew(n, expected);
                case FieldAccessExpression f -> typeOfFieldAccess(f);
                case NewArrayExpression n -> typeOfNewArray(n);
                case ArrayAccessExpression a -> typeOfArrayAccess(a);
                case InstanceOfExpression i -> typeOfInstanceOf(i);
                case CastExpression c -> typeOfCast(c);
                case ConditionalExpression c -> typeOfConditional(c);
                case GcCollectExpression ignored -> VoidType.INSTANCE;
                case AwaitExpression a -> typeOfAwait(a);
                case NativeNullExpression n -> {
                    provenances.put(n, PointerProvenance.NULL);
                    yield NativePtrType.VOID;
                }
                case LambdaExpression l -> typeOfLambda(l);
                case MethodReferenceExpression r -> typeOfMethodReference(r);
            };
            expressionTypes.put(expr, type);
            return type;
        } finally {
            expectedSamType = prevExpected;
        }
    }

    private JType typeOfLiteral(LiteralExpression l) {
        return switch (l.kind()) {
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case FLOAT -> PrimitiveType.FLOAT;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case STRING -> stringClass.asType();
        };
    }

    private JType typeOfThis(ThisExpression t) {
        if (currentConstructor != null) {
            captureEnclosingThis(t.location());
            return currentClass.asType();
        }
        if (currentMethod == null || currentMethod.isStatic()) {
            reporter.error(t.location(), "'this' is not available in a static context");
            return ErrorType.INSTANCE;
        }
        captureEnclosingThis(t.location());
        return currentClass.asType();
    }

    private JType typeOfVariable(VariableExpression v) {
        Optional<Symbol> symbol = current.resolve(v.name());
        if (symbol.isPresent() && symbol.get() instanceof Symbol.VariableBinding vb) {
            resolvedSymbols.put(v, vb.variable());
            VariableSymbol vs = vb.variable();
            checkLocalRead(vs, v.location());
            if (!lambdaStack.isEmpty() && !isLambdaLocalSymbol(vs)) {
                captureVariable(vs, v.location());
            }
            if (vs.type() instanceof NativePtrType) {
                provenances.put(v, vs.provenance());
            }
            return vs.type();
        }
        if (currentClass != null) {
            Optional<FieldSymbol> field = currentClass.findField(v.name());
            if (field.isPresent()) {
                FieldSymbol fs = field.get();
                boolean inInstance = currentConstructor != null
                        || (currentMethod != null && !currentMethod.isStatic());
                if (fs.isStatic() || inInstance) {
                    resolvedSymbols.put(v, fs);
                    if (!fs.isStatic()) {
                        captureEnclosingThis(v.location());
                    }
                    if (fs.type() instanceof NativePtrType) {
                        provenances.put(v, PointerProvenance.UNKNOWN);
                    }
                    return fs.type();
                }
            }
        }
        ClassSymbol asClass = resolveClassBySourceName(v.name());
        if (asClass != null) {
            // Class name used as static-field / static-method receiver.
            resolvedSymbols.put(v, asClass);
            return asClass.asType();
        }
        InterfaceSymbol asIface = resolveInterfaceBySourceName(v.name());
        if (asIface != null) {
            resolvedSymbols.put(v, asIface);
            return asIface.asType();
        }
        reporter.error(v.location(), "undefined variable '" + v.name() + "'");
        return ErrorType.INSTANCE;
    }

    private JType typeOfAssignment(AssignmentExpression a) {
        JType targetType;
        if (a.target() instanceof VariableExpression) {
            boolean prev = skippingDaRead;
            skippingDaRead = true;
            try {
                targetType = analyzeExpression(a.target());
            } finally {
                skippingDaRead = prev;
            }
        } else {
            targetType = analyzeExpression(a.target());
        }
        JType valueType = analyzeExpression(a.value(), samExpectedType(targetType));

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
        if (!assignableWithBoxing(targetType, valueType)) {
            reporter.error(a.location(),
                    "incompatible types: " + valueType + " cannot be converted to " + targetType);
            return ErrorType.INSTANCE;
        }
        if (targetType instanceof NativePtrType) {
            PointerProvenance prov = provenances.getOrDefault(a.value(), PointerProvenance.UNKNOWN);
            if (prov == PointerProvenance.STACK
                    && a.target() instanceof FieldAccessExpression) {
                reporter.error(a.location(), "stack pointer escapes via field store");
            }
            if (a.target() instanceof VariableExpression) {
                Object sym = resolvedSymbols.get(a.target());
                if (sym instanceof VariableSymbol vs) {
                    vs.setProvenance(prov);
                }
            }
            provenances.put(a, prov);
        }
        if (a.target() instanceof VariableExpression ve) {
            Object sym = resolvedSymbols.get(ve);
            if (sym instanceof VariableSymbol vs) {
                assignLocal(vs, a.location());
                if (vs.isCapturedByLambda()) {
                    reporter.error(a.location(),
                            "local variable '" + vs.name() + "' must be effectively final");
                }
            }
        }
        return targetType;
    }

    private JType typeOfBinary(BinaryExpression b) {
        if (b.operator() == BinaryOp.AND || b.operator() == BinaryOp.OR) {
            JType left = analyzeExpression(b.left());
            left = unboxForOperator(left);
            expectBoolean(left, b.left().location(), "operand of " + b.operator());
            DaEnv afterLeft = daEnv.copy();
            JType right = analyzeExpression(b.right());
            right = unboxForOperator(right);
            expectBoolean(right, b.right().location(), "operand of " + b.operator());
            DaEnv afterRight = daEnv.copy();
            // Short-circuit: merge path that skips right with path that evaluates right.
            daEnv = afterLeft.copy();
            daEnv.mergeFrom(afterRight);
            return PrimitiveType.BOOLEAN;
        }
        JType left = analyzeExpression(b.left());
        JType right = analyzeExpression(b.right());
        // Binary numeric / relational ops unbox wrappers (Integer + int, etc.).
        if (b.operator() != BinaryOp.EQ && b.operator() != BinaryOp.NE
                && b.operator() != BinaryOp.BIT_AND && b.operator() != BinaryOp.BIT_OR
                && b.operator() != BinaryOp.BIT_XOR) {
            left = unboxForOperator(left);
            right = unboxForOperator(right);
        } else if (b.operator() == BinaryOp.EQ || b.operator() == BinaryOp.NE) {
            // Keep reference == for two wrappers; unbox only when mixed with primitive.
            if (Boxing.isWrapper(left) && right instanceof PrimitiveType) {
                left = unboxForOperator(left);
            } else if (Boxing.isWrapper(right) && left instanceof PrimitiveType) {
                right = unboxForOperator(right);
            }
        }
        return switch (b.operator()) {
            case ADD -> {
                if (isString(left) && isString(right)) {
                    yield stringClass.asType();
                }
                if (isString(left) && (right.isNumeric() || Boxing.isWrapper(right))) {
                    yield stringClass.asType();
                }
                if ((left.isNumeric() || Boxing.isWrapper(left)) && isString(right)) {
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
                // float/double % is Java remainder (toward-zero quotient), not IEEE 754 remainder.
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
                if (left instanceof NativePtrType && right instanceof NativePtrType) {
                    yield PrimitiveType.BOOLEAN;
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
            case AND, OR -> PrimitiveType.BOOLEAN; // handled above
            case BIT_AND, BIT_OR, BIT_XOR -> {
                if (left == PrimitiveType.BOOLEAN && right == PrimitiveType.BOOLEAN) {
                    yield PrimitiveType.BOOLEAN;
                }
                if (left.isInteger() && right.isInteger()) {
                    yield JType.binaryArithmeticResult(left, right);
                }
                if (!(left instanceof ErrorType) && !(right instanceof ErrorType)) {
                    reporter.error(b.location(),
                            "operator " + bitwiseSymbol(b.operator()) + " cannot be applied to "
                                    + left + ", " + right);
                }
                yield ErrorType.INSTANCE;
            }
            case SHL, SHR, USHR -> {
                if (!left.isInteger() || !right.isInteger()) {
                    if (!(left instanceof ErrorType) && !(right instanceof ErrorType)) {
                        reporter.error(b.location(),
                                "operator " + shiftSymbol(b.operator()) + " cannot be applied to "
                                        + left + ", " + right);
                    }
                    yield ErrorType.INSTANCE;
                }
                // Result type follows unary numeric promotion of the left operand.
                yield unaryNumericPromotion(left);
            }
        };
    }

    private static String bitwiseSymbol(BinaryOp op) {
        return switch (op) {
            case BIT_AND -> "&";
            case BIT_OR -> "|";
            case BIT_XOR -> "^";
            default -> op.name();
        };
    }

    private static String shiftSymbol(BinaryOp op) {
        return switch (op) {
            case SHL -> "<<";
            case SHR -> ">>";
            case USHR -> ">>>";
            default -> op.name();
        };
    }

    /** Java unary numeric promotion for integral types (byte/short/char → int). */
    private static JType unaryNumericPromotion(JType t) {
        if (t == PrimitiveType.BYTE || t == PrimitiveType.SHORT || t == PrimitiveType.CHAR) {
            return PrimitiveType.INT;
        }
        return t;
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
            case BIT_NOT -> {
                if (!operand.isInteger() && !(operand instanceof ErrorType)) {
                    reporter.error(u.location(), "operator ~ cannot be applied to " + operand);
                    yield ErrorType.INSTANCE;
                }
                yield unaryNumericPromotion(operand);
            }
            case NEGATE, PLUS -> {
                if (!operand.isNumeric() && !(operand instanceof ErrorType)) {
                    reporter.error(u.location(), "unary operator cannot be applied to " + operand);
                    yield ErrorType.INSTANCE;
                }
                yield unaryNumericPromotion(operand);
            }
        };
    }

    private JType typeOfConditional(ConditionalExpression c) {
        JType cond = analyzeExpression(c.condition());
        expectBoolean(cond, c.condition().location(), "condition of ?:");
        DaEnv beforeBranches = daEnv.copy();
        JType thenT = analyzeExpression(c.thenExpr());
        DaEnv thenEnv = daEnv.copy();
        daEnv = beforeBranches.copy();
        JType elseT = analyzeExpression(c.elseExpr());
        DaEnv elseEnv = daEnv.copy();
        daEnv = thenEnv.copy();
        daEnv.mergeFrom(elseEnv);
        if (thenT instanceof ErrorType || elseT instanceof ErrorType) {
            return ErrorType.INSTANCE;
        }
        if (thenT == PrimitiveType.BOOLEAN && elseT == PrimitiveType.BOOLEAN) {
            return PrimitiveType.BOOLEAN;
        }
        if (thenT.isNumeric() && elseT.isNumeric()) {
            return JType.binaryArithmeticResult(thenT, elseT);
        }
        if (thenT instanceof NullType && elseT.isReference()) {
            return elseT;
        }
        if (elseT instanceof NullType && thenT.isReference()) {
            return thenT;
        }
        if (thenT.isReference() && elseT.isReference()) {
            if (thenT.isAssignableFrom(elseT)) {
                return thenT;
            }
            if (elseT.isAssignableFrom(thenT)) {
                return elseT;
            }
        }
        if (thenT.equals(elseT)) {
            return thenT;
        }
        reporter.error(c.location(),
                "incompatible types for ?: branches: " + thenT + " and " + elseT);
        return ErrorType.INSTANCE;
    }

    private JType typeOfCall(MethodCallExpression m) {
        return typeOfCall(m, null);
    }

    private JType typeOfCall(MethodCallExpression m, JType expectedReturn) {
        if (m.receiver() instanceof VariableExpression nv && "Native".equals(nv.name())) {
            return typeOfNativeBuiltin(m);
        }
        if (m.receiver() != null) {
            ClassSymbol typeRecv = resolveTypeNameReceiver(m.receiver());
            if (typeRecv != null) {
                checkTypeAccessible(typeRecv.visibility(), typeRecv.packageName(), typeRecv.name(),
                        m.location());
                List<MethodSymbol> statics = new ArrayList<>();
                for (MethodSymbol cand : typeRecv.findMethods(m.name())) {
                    if (cand.isStatic()) {
                        statics.add(cand);
                    }
                }
                if (statics.isEmpty()) {
                    if (!typeRecv.findMethods(m.name()).isEmpty()) {
                        reporter.error(m.location(),
                                "instance method '" + m.name() + "' cannot be called on a type name");
                    } else {
                        reporter.error(m.location(),
                                "undefined method '" + m.name() + "' in class '" + typeRecv.name() + "'");
                    }
                    return ErrorType.INSTANCE;
                }
                MethodSymbol ms = resolveOverload(statics, m, true);
                if (ms == null) {
                    if (!lastOverloadAmbiguous) {
                        reporter.error(m.location(),
                                "no matching overload for '" + typeRecv.name() + "." + m.name() + "'");
                    }
                    return ErrorType.INSTANCE;
                }
                checkMemberAccessible(ms.visibility(), typeRecv, m.location(), "method", ms.name());
                if (ms.isFfiIntrinsic() && "allocate".equals(ms.name())) {
                    checkArguments(m, ms);
                    checkNetworkPortLiteral(m, ms);
                    resolvedSymbols.put(m, ms);
                    provenances.put(m, PointerProvenance.NATIVE_HEAP);
                    return ms.returnType();
                }
                checkNetworkPortLiteral(m, ms);
                return resolveGenericCall(m, ms, expectedReturn);
            }
            // Else: receiver may be an expression (e.g. EnumType.CONST.instanceMethod()).
        }
        if (m.receiver() != null) {
            JType recvType = analyzeExpression(m.receiver());
            InterfaceSymbol recvIface = null;
            TypeSubstitution ifaceSub = null;
            if (recvType instanceof InterfaceType it) {
                recvIface = it.interfaceSymbol();
            } else if (recvType instanceof ParameterizedType pt
                    && pt.raw() instanceof InterfaceSymbol is) {
                recvIface = is;
                ifaceSub = TypeSubstitution.zip(is.typeParameters(), pt.typeArguments());
            }
            if (recvIface != null) {
                Optional<MethodSymbol> method = recvIface.findMethod(m.name());
                if (method.isEmpty()) {
                    reporter.error(m.location(),
                            "undefined method '" + m.name() + "' in interface '"
                                    + recvIface.name() + "'");
                    return ErrorType.INSTANCE;
                }
                MethodSymbol ms = method.get();
                MethodSymbol callTarget = ifaceSub != null ? substituteMethod(ms, ifaceSub) : ms;
                checkArguments(m, callTarget);
                Integer slot = ms.interfaceSlot();
                if (slot == null) {
                    reporter.error(m.location(),
                            "interface method '" + m.name() + "' has no itable slot");
                    return ErrorType.INSTANCE;
                }
                resolvedSymbols.put(m, new InterfaceCallTarget(
                        recvIface, slot, ms, callTarget.returnType(), callTarget.parameterTypes()));
                return callTarget.returnType();
            }
            ClassSymbol recvClass = null;
            TypeSubstitution recvSub = null;
            if (recvType instanceof ClassType ct) {
                recvClass = ct.classSymbol();
            } else if (recvType instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
                recvClass = cs;
                recvSub = TypeSubstitution.zip(cs.typeParameters(), pt.typeArguments());
            } else if (recvType instanceof TypeVariable tv) {
                // Use upper bound for member lookup (class or interface); else Object.
                JType bound = tv.symbol().erasure(objectClass);
                if (bound instanceof ClassType ect) {
                    recvClass = ect.classSymbol();
                } else if (bound instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
                    recvClass = cs;
                    recvSub = TypeSubstitution.zip(cs.typeParameters(), pt.typeArguments());
                } else if (bound instanceof InterfaceType it) {
                    Optional<MethodSymbol> method = it.interfaceSymbol().findMethod(m.name());
                    if (method.isEmpty()) {
                        reporter.error(m.location(),
                                "undefined method '" + m.name() + "' in bound '"
                                        + it.interfaceSymbol().name() + "'");
                        return ErrorType.INSTANCE;
                    }
                    MethodSymbol ms = method.get();
                    checkArguments(m, ms);
                    Integer slot = ms.interfaceSlot();
                    if (slot == null) {
                        reporter.error(m.location(),
                                "interface method '" + m.name() + "' has no itable slot");
                        return ErrorType.INSTANCE;
                    }
                    resolvedSymbols.put(m, new InterfaceCallTarget(
                            it.interfaceSymbol(), slot, ms, ms.returnType(), ms.parameterTypes()));
                    return ms.returnType();
                } else if (bound instanceof ParameterizedType pt
                        && pt.raw() instanceof InterfaceSymbol is) {
                    TypeSubstitution boundSub = TypeSubstitution.zip(
                            is.typeParameters(), pt.typeArguments());
                    Optional<MethodSymbol> method = is.findMethod(m.name());
                    if (method.isEmpty()) {
                        reporter.error(m.location(),
                                "undefined method '" + m.name() + "' in bound '" + is.name() + "'");
                        return ErrorType.INSTANCE;
                    }
                    MethodSymbol ms = method.get();
                    MethodSymbol callTarget = substituteMethod(ms, boundSub);
                    checkArguments(m, callTarget);
                    Integer slot = ms.interfaceSlot();
                    if (slot == null) {
                        reporter.error(m.location(),
                                "interface method '" + m.name() + "' has no itable slot");
                        return ErrorType.INSTANCE;
                    }
                    resolvedSymbols.put(m, new InterfaceCallTarget(
                            is, slot, ms, callTarget.returnType(), callTarget.parameterTypes()));
                    return callTarget.returnType();
                } else {
                    recvClass = objectClass;
                }
            }
            if (recvClass == null) {
                if (!(recvType instanceof ErrorType)) {
                    reporter.error(m.location(),
                            "cannot invoke method on non-reference type " + recvType);
                }
                return ErrorType.INSTANCE;
            }
            MethodSymbol ms = resolveOverload(recvClass.findMethods(m.name()), m, false, recvSub);
            if (ms == null) {
                if (!lastOverloadAmbiguous) {
                    reporter.error(m.location(),
                            "undefined method '" + m.name() + "' in class '" + recvClass.name() + "'");
                }
                return ErrorType.INSTANCE;
            }
            checkMemberAccessible(ms.visibility(), recvClass, m.location(), "method", ms.name());
            if (ms.isStatic()) {
                reporter.error(m.location(),
                        "static method '" + m.name() + "' should be called without an instance");
            }
            MethodSymbol callTarget = ms;
            if (recvSub != null) {
                callTarget = substituteMethod(ms, recvSub);
            }
            checkArguments(m, callTarget);
            // Store substituted params/return so IR can box (ArrayList<Integer>.add(int)).
            resolvedSymbols.put(m, callTarget);
            return callTarget.returnType();
        }

        if (currentClass != null) {
            MethodSymbol ms = resolveOverload(currentClass.findMethods(m.name()), m, true);
            if (ms != null) {
                boolean inInstance = currentConstructor != null
                        || (currentMethod != null && !currentMethod.isStatic());
                if (!ms.isStatic() && !inInstance) {
                    reporter.error(m.location(),
                            "cannot call instance method '" + m.name() + "' from a static context");
                    return ErrorType.INSTANCE;
                }
                if (!ms.isStatic()) {
                    captureEnclosingThis(m.location());
                }
                return resolveGenericCall(m, ms, expectedReturn);
            }
            if (lastOverloadAmbiguous) {
                return ErrorType.INSTANCE;
            }
        }

        List<MethodSymbol> staticCandidates = new ArrayList<>();
        for (ClassSymbol cls : classes.values()) {
            for (MethodSymbol method : cls.findDeclaredMethods(m.name())) {
                if (method.isStatic()) {
                    staticCandidates.add(method);
                }
            }
        }
        MethodSymbol found = resolveOverload(staticCandidates, m, true);
        if (found != null) {
            return resolveGenericCall(m, found, expectedReturn);
        }
        if (!lastOverloadAmbiguous) {
            reporter.error(m.location(), "undefined method '" + m.name() + "'");
        }
        return ErrorType.INSTANCE;
    }

    /**
     * Pick the best overload for a call. When {@code allowStatic} is false, static methods are skipped.
     * Argument expressions are analyzed once for typing.
     * When a single arity-compatible candidate exists, its parameter types are used as SAM
     * expectations so lambdas like {@code onClick(() -> {})} type-check (UI stdlib).
     */
    /** Set when {@link #resolveOverload} rejected a call for ambiguity (diagnostic already emitted). */
    private boolean lastOverloadAmbiguous;

    private MethodSymbol resolveOverload(
            List<MethodSymbol> candidates,
            MethodCallExpression m,
            boolean allowStatic
    ) {
        return resolveOverload(candidates, m, allowStatic, null);
    }

    /**
     * @param receiverSub when non-null (e.g. {@code ArrayList<String>}), applied so lambda
     *                    arguments see {@code Predicate<String>} rather than erased {@code T}.
     */
    private MethodSymbol resolveOverload(
            List<MethodSymbol> candidates,
            MethodCallExpression m,
            boolean allowStatic,
            TypeSubstitution receiverSub
    ) {
        lastOverloadAmbiguous = false;
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<MethodSymbol> usable = new ArrayList<>();
        for (MethodSymbol ms : candidates) {
            if (!allowStatic && ms.isStatic()) {
                continue;
            }
            usable.add(ms);
        }
        if (usable.isEmpty()) {
            return null;
        }
        int argc = m.arguments().size();
        List<MethodSymbol> arityOk = new ArrayList<>();
        for (MethodSymbol ms : usable) {
            if (ms.parameterTypes().size() == argc) {
                arityOk.add(ms);
            }
        }
        if (arityOk.isEmpty()) {
            arityOk.addAll(usable);
        }
        MethodSymbol hint = arityOk.size() == 1 ? arityOk.get(0) : null;
        MethodSymbol hintSam = hint;
        if (hint != null && receiverSub != null) {
            hintSam = substituteMethod(hint, receiverSub);
        }
        List<JType> argTypes = new ArrayList<>();
        for (int i = 0; i < m.arguments().size(); i++) {
            Expression arg = m.arguments().get(i);
            JType expected = null;
            if (hintSam != null && i < hintSam.parameterTypes().size()) {
                expected = samExpectedType(hintSam.parameterTypes().get(i));
            }
            argTypes.add(analyzeExpression(arg, expected));
        }
        List<CallableResolver.Candidate> list = new ArrayList<>();
        for (MethodSymbol ms : arityOk) {
            List<JType> pts = ms.parameterTypes();
            if (receiverSub != null) {
                pts = substituteMethod(ms, receiverSub).parameterTypes();
            }
            list.add(new CallableResolver.Candidate(pts, ms));
        }
        Object resolved = CallableResolver.resolve(list, argTypes);
        if (resolved instanceof MethodSymbol ms) {
            return ms;
        }
        if (list.size() == 1) {
            /* Sole candidate: checkArguments reports arity / conversion errors. */
            return (MethodSymbol) list.get(0).symbol();
        }
        if (list.size() > 1) {
            lastOverloadAmbiguous = true;
            reporter.error(m.location(),
                    "ambiguous method call '" + m.name() + "' with " + argTypes.size()
                            + " argument(s); " + list.size() + " overloads apply");
        }
        return null;
    }

    /** Resolve call, applying method type-parameter inference when needed. */
    private JType resolveGenericCall(MethodCallExpression m, MethodSymbol ms, JType targetReturn) {
        MethodSymbol callTarget = ms;
        if (ms.isGeneric()) {
            List<JType> explicit = resolveExplicitTypeArguments(m.typeArguments(), m.location());
            TypeSubstitution sub;
            if (!explicit.isEmpty()) {
                if (explicit.size() != ms.typeParameters().size()) {
                    reporter.error(m.location(),
                            "wrong number of type arguments for method '" + m.name() + "'");
                    return ErrorType.INSTANCE;
                }
                checkTypeArgumentBounds(ms.typeParameters(), explicit, m.location());
                sub = TypeSubstitution.zip(ms.typeParameters(), explicit);
            } else {
                List<JType> actualArgs = new ArrayList<>();
                for (Expression arg : m.arguments()) {
                    actualArgs.add(analyzeExpression(arg));
                }
                TypeInferenceEngine.InferenceResult inferred = TypeInferenceEngine.inferMethod(
                        ms.typeParameters(),
                        ms.parameterTypes(),
                        actualArgs,
                        ms.returnType(),
                        targetReturn);
                if (!inferred.success()) {
                    reporter.error(m.location(),
                            inferred.message() != null
                                    ? inferred.message()
                                    : "cannot infer type parameters for '" + m.name() + "'");
                    return ErrorType.INSTANCE;
                }
                sub = inferred.substitution();
            }
            callTarget = substituteMethod(ms, sub);
            checkArguments(m, callTarget);
        } else {
            checkArguments(m, ms);
        }
        resolvedSymbols.put(m, ms);
        return callTarget.returnType();
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
            JType exp = expected.get(i);
            JType actual = analyzeExpression(args.get(i), samExpectedType(exp));
            if (exp instanceof NativePtrType && actual instanceof NativeFunctionPtrType) {
                // C ABI: function pointers pass as void*.
                continue;
            }
            if (exp instanceof NativeFunctionPtrType expFn && actual instanceof NativeFunctionPtrType actFn) {
                if (expFn.isAssignableFrom(actFn)) {
                    continue;
                }
            }
            if (!assignableWithBoxing(exp, actual)) {
                reporter.error(args.get(i).location(),
                        "argument " + (i + 1) + ": " + actual
                                + " cannot be converted to " + exp);
            }
            // STACK provenance args are OK for native / FFI calls (non-capturing contract).
        }
    }

    /** Compile-time port range check when the port argument is an int literal. */
    private void checkNetworkPortLiteral(MethodCallExpression m, MethodSymbol ms) {
        String owner = ms.owner().name();
        int portArgIndex = -1;
        if ("TcpSocket".equals(owner)
                && ("connect".equals(ms.name()) || "connectTimeout".equals(ms.name()))) {
            portArgIndex = 1;
        } else if ("TcpServer".equals(owner) && "listen".equals(ms.name())) {
            portArgIndex = 1;
        } else if ("TcpServer".equals(owner) && "listenAny".equals(ms.name())) {
            portArgIndex = 0;
        }
        if (portArgIndex < 0 || portArgIndex >= m.arguments().size()) {
            return;
        }
        Expression portExpr = m.arguments().get(portArgIndex);
        if (portExpr instanceof LiteralExpression lit && lit.kind() == LiteralExpression.Kind.INT) {
            int port = ((Number) lit.value()).intValue();
            if (port < 0 || port > 65535) {
                reporter.error(portExpr.location(),
                        "port out of range (0..65535): " + port);
            }
        }
    }

    private JType typeOfNativeBuiltin(MethodCallExpression m) {
        return switch (m.name()) {
            case "addressOf" -> typeOfNativeAddressOf(m);
            case "loadInt" -> typeOfNativeLoadInt(m);
            case "storeInt" -> typeOfNativeStoreInt(m);
            case "allocate" -> typeOfNativeAllocate(m);
            case "callback" -> typeOfNativeCallback(m);
            default -> {
                reporter.error(m.location(),
                        "undefined method '" + m.name() + "' in class 'Native'");
                yield ErrorType.INSTANCE;
            }
        };
    }

    private JType typeOfNativeCallback(MethodCallExpression m) {
        if (m.arguments().size() != 1) {
            reporter.error(m.location(),
                    "method 'callback' expects 1 argument(s) but got " + m.arguments().size());
            return ErrorType.INSTANCE;
        }
        Expression arg = m.arguments().getFirst();
        JType expectedSam = null;
        if (arg instanceof LambdaExpression lambda) {
            expectedSam = inferSamTypeForNativeCallback(lambda);
        } else if (arg instanceof VariableExpression ve) {
            Optional<Symbol> sym = current.resolve(ve.name());
            if (sym.isPresent() && sym.get() instanceof Symbol.VariableBinding vb) {
                JType vt = vb.variable().type();
                if (vt instanceof ClassType ct && ct.classSymbol().functionType() != null) {
                    expectedSam = vt;
                }
            }
        }
        JType argType = analyzeExpression(arg, expectedSam);
        if (!(argType instanceof ClassType ct) || ct.classSymbol().functionType() == null) {
            if (!(argType instanceof ErrorType)) {
                reporter.error(arg.location(), "Native.callback requires a SAM callable");
            }
            return ErrorType.INSTANCE;
        }
        CallableBinding binding = callableBindings.get(arg);
        if (binding != null && binding.hasEnv()) {
            reporter.error(arg.location(), "Native.callback does not support capturing lambdas");
            return ErrorType.INSTANCE;
        }
        FunctionType ft = ct.classSymbol().functionType();
        int id = callbackCounter++;
        String trampoline = "_JN_cb_" + id;
        String invokeRuntime = ct.classSymbol().findMethod(samMethodName(ct.classSymbol()))
                .orElseThrow()
                .samInvokeRuntime();
        resolvedSymbols.put(m, new NativeCallbackSite(trampoline, invokeRuntime, id, ft));
        return nativeCallbackHandleClass.asType();
    }

    private JType inferSamTypeForNativeCallback(LambdaExpression lambda) {
        int n = lambda.parameters().size();
        if (n == 0) {
            return classes.get("Runnable").asType();
        }
        if (n == 1) {
            return classes.get("IntUnaryFunction").asType();
        }
        if (n == 2) {
            boolean pointers = lambda.parameters().stream()
                    .map(LambdaParameter::type)
                    .filter(Objects::nonNull)
                    .anyMatch(t -> {
                        JType resolved = resolveTypeName(t, false, lambda.location());
                        return resolved instanceof NativePtrType;
                    });
            return classes.get(pointers ? "IntCompare" : "IntBinaryFunction").asType();
        }
        return null;
    }

    private boolean isLambdaLocalSymbol(VariableSymbol vs) {
        if (lambdaStack.isEmpty()) {
            return false;
        }
        return isLocalToFrame(lambdaStack.peek(), vs);
    }

    private static boolean isLocalToFrame(LambdaAnalysisFrame frame, VariableSymbol vs) {
        Optional<Symbol> sym = frame.bodyScope.resolveLocal(vs.name());
        return sym.isPresent() && sym.get() instanceof Symbol.VariableBinding vb
                && vb.variable() == vs;
    }

    /**
     * Record a capture on every lambda frame that does not declare {@code vs}, so nested
     * lambdas force outer envs to retain the value after the enclosing method returns.
     */
    private void captureVariable(VariableSymbol vs, SourceLocation loc) {
        if (lambdaStack.isEmpty() || vs == null) {
            return;
        }
        for (LambdaAnalysisFrame frame : lambdaStack) {
            if (isLocalToFrame(frame, vs)) {
                return;
            }
            if (frame.captured.add(vs)) {
                vs.setCapturedByLambda(true);
                if (vs.isAssignedAfterInit()) {
                    reporter.error(loc,
                            "local variable '" + vs.name() + "' must be effectively final");
                }
            }
        }
    }

    private void captureEnclosingThis(SourceLocation loc) {
        if (lambdaStack.isEmpty()) {
            return;
        }
        Optional<Symbol> sym = current.resolve("this");
        if (sym.isPresent() && sym.get() instanceof Symbol.VariableBinding vb) {
            captureVariable(vb.variable(), loc);
        }
    }

    private JType typeOfLambda(LambdaExpression lambda) {
        FunctionType expected = expectedSamFunctionType();
        ClassSymbol samClass = resolveSamAdapterClass(expectedSamType);
        if (expected == null || samClass == null) {
            reporter.error(lambda.location(), "cannot infer lambda type; expected SAM type");
            return ErrorType.INSTANCE;
        }
        if (lambda.parameters().size() != expected.parameterTypes().size()) {
            reporter.error(lambda.location(),
                    "lambda parameter count mismatch: expected "
                            + expected.parameterTypes().size() + " but got "
                            + lambda.parameters().size());
            return ErrorType.INSTANCE;
        }
        String invokeRuntime = invokeRuntimeForFunctionType(expected);
        if (invokeRuntime == null) {
            reporter.error(lambda.location(),
                    "unsupported functional interface signature for lambda: " + expected
                            + " (no callable invoke runtime)");
            return ErrorType.INSTANCE;
        }

        Scope outer = current;
        current = new Scope(outer);
        LambdaAnalysisFrame frame = new LambdaAnalysisFrame(current);
        lambdaStack.push(frame);
        try {
            for (int i = 0; i < lambda.parameters().size(); i++) {
                LambdaParameter p = lambda.parameters().get(i);
                JType pType = expected.parameterTypes().get(i);
                if (p.type() != null) {
                    JType explicit = resolveTypeName(p.type(), p.isArray(), p.location());
                    if (!pType.isAssignableFrom(explicit) && !explicit.isAssignableFrom(pType)) {
                        reporter.error(p.location(),
                                "lambda parameter type mismatch: expected " + pType + " but got " + explicit);
                    }
                    pType = explicit;
                }
                VariableSymbol vs = new VariableSymbol(
                        p.name(), pType, VariableSymbol.Kind.PARAMETER, p.location());
                if (!current.declare(new Symbol.VariableBinding(vs))) {
                    reporter.error(p.location(), "duplicate lambda parameter '" + p.name() + "'");
                }
            }
            if (lambda.isBlock()) {
                for (Statement stmt : lambda.blockBody().statements()) {
                    analyzeStatement(stmt);
                }
            } else {
                JType bodyType = analyzeExpression(lambda.expressionBody());
                if (!assignableWithBoxing(expected.returnType(), bodyType)
                        && !(expected.returnType() instanceof VoidType)) {
                    if (!(bodyType instanceof ErrorType)) {
                        reporter.error(lambda.expressionBody().location(),
                                "lambda body type " + bodyType + " incompatible with "
                                        + expected.returnType());
                    }
                }
            }
        } finally {
            lambdaStack.pop();
            current = outer;
        }

        ClassSymbol envClass = null;
        List<ClosureCapture> captures = List.of();
        if (!frame.captured.isEmpty()) {
            String envName = "__jn.Env." + (envClassCounter++);
            envClass = new ClassSymbol(envName, lambda.location(), true);
            envClass.setSuperClass(objectClass);
            int idx = 0;
            List<ClosureCapture> capList = new ArrayList<>();
            for (VariableSymbol vs : frame.captured) {
                String fieldName = "c" + idx;
                envClass.addField(new FieldSymbol(
                        envClass, fieldName, vs.type(), idx, lambda.location()));
                capList.add(new ClosureCapture(vs, fieldName, vs.type(), vs.type().isReference()));
                idx++;
            }
            envClass.setLayout(ObjectLayoutBuilder.build(envClass));
            envClass.setVtable(new io.jnative.vtable.VTable(envClass, List.of()));
            classes.put(envName, envClass);
            captures = List.copyOf(capList);
        }

        String codeSymbol = "_JN_lambda_" + (lambdaCounter++);
        MethodSymbol samMethod = samClass.findMethod(samMethodNameFor(expectedSamType)).orElse(null);
        if (samMethod != null && samMethod.samInvokeRuntime() != null) {
            invokeRuntime = samMethod.samInvokeRuntime();
        }
        CallableBinding binding = new CallableBinding(
                samClass,
                codeSymbol,
                envClass,
                captures,
                FunctionEffects.unknown(),
                false,
                false,
                null,
                invokeRuntime,
                lambda.expressionBody(),
                lambda.blockBody(),
                lambda.parameters(),
                null,
                null);
        callableBindings.put(lambda, binding);
        if (expectedSamType instanceof InterfaceType it) {
            return it;
        }
        if (expectedSamType instanceof ParameterizedType pt) {
            return pt;
        }
        return samClass.asType();
    }

    private static String samMethodName(ClassSymbol sam) {
        return switch (sam.name()) {
            case "Runnable" -> "run";
            case "IntCompare" -> "compare";
            case "HttpHandler" -> "handle";
            default -> "apply";
        };
    }

    private JType typeOfMethodReference(MethodReferenceExpression ref) {
        FunctionType expected = expectedSamFunctionType();
        ClassSymbol samClass = resolveSamAdapterClass(expectedSamType);
        if (expected == null || samClass == null) {
            reporter.error(ref.location(), "cannot infer method reference type; expected SAM type");
            return ErrorType.INSTANCE;
        }
        String samMethod = samMethodNameFor(expectedSamType);

        if (ref.typeName() != null) {
            ClassSymbol cls = classes.get(ref.typeName().name());
            if (cls == null) {
                // Also allow simple interface/class names from packages via resolve helpers.
                cls = resolveClassBySourceName(ref.typeName().name());
            }
            if (cls == null) {
                reporter.error(ref.location(), "unknown type '" + ref.typeName().name() + "'");
                return ErrorType.INSTANCE;
            }
            if ("new".equals(ref.methodName())) {
                reporter.error(ref.location(),
                        "constructor method references (Type::new) are not supported yet");
                return ErrorType.INSTANCE;
            }
            Optional<MethodSymbol> method = cls.findMethod(ref.methodName());
            if (method.isEmpty()) {
                reporter.error(ref.location(),
                        "method '" + ref.methodName() + "' not found on " + cls.name());
                return ErrorType.INSTANCE;
            }
            MethodSymbol ms = method.get();
            if (ms.isStatic()) {
                if (!methodMatchesSam(ms, expected)) {
                    reporter.error(ref.location(),
                            "method '" + ref.methodName() + "' incompatible with SAM");
                    return ErrorType.INSTANCE;
                }
                String codeSymbol = "_JN_thunk_static_" + cls.name() + "_" + ref.methodName();
                CallableBinding binding = new CallableBinding(
                        samClass, codeSymbol, null, List.of(), FunctionEffects.unknown(),
                        false, false, null,
                        samClass.findMethod(samMethod).orElseThrow().samInvokeRuntime(),
                        null, null, List.of(),
                        ms, null);
                callableBindings.put(ref, binding);
                return resultSamType();
            }
            // Unbound instance method reference: SAM params = (receiver, methodParams...)
            if (!methodMatchesSamUnbound(ms, cls.asType(), expected)) {
                reporter.error(ref.location(),
                        "unbound instance method '" + ref.methodName()
                                + "' incompatible with SAM (expected receiver as first parameter)");
                return ErrorType.INSTANCE;
            }
            String codeSymbol = "_JN_thunk_unbound_" + cls.name() + "_" + ref.methodName();
            boolean virtualDispatch = ms.isVirtual();
            CallableBinding binding = new CallableBinding(
                    samClass, codeSymbol, null, List.of(), FunctionEffects.unknown(),
                    false, virtualDispatch, null,
                    samClass.findMethod(samMethod).orElseThrow().samInvokeRuntime(),
                    null, null, List.of(),
                    ms, null);
            callableBindings.put(ref, binding);
            return resultSamType();
        }

        JType recvType = analyzeExpression(ref.receiver());
        if (!(recvType instanceof ClassType ct)) {
            if (!(recvType instanceof ErrorType)) {
                reporter.error(ref.location(), "method reference requires instance of reference type");
            }
            return ErrorType.INSTANCE;
        }
        Optional<MethodSymbol> method = ct.classSymbol().findMethod(ref.methodName());
        if (method.isEmpty() || method.get().isStatic()) {
            reporter.error(ref.location(),
                    "instance method '" + ref.methodName() + "' not found on " + ct.name());
            return ErrorType.INSTANCE;
        }
        if (!methodMatchesSam(method.get(), expected)) {
            reporter.error(ref.location(), "method '" + ref.methodName() + "' incompatible with SAM");
            return ErrorType.INSTANCE;
        }
        String codeSymbol = "_JN_thunk_bound_" + ct.name() + "_" + ref.methodName();
        // Bound instance method refs always use virtual dispatch when the method is virtual,
        // so Animal a = new Dog(); a::speak invokes Dog.speak.
        boolean virtualDispatch = method.get().isVirtual();
        CallableBinding binding = new CallableBinding(
                samClass, codeSymbol, null, List.of(), FunctionEffects.unknown(),
                false, virtualDispatch, ct,
                samClass.findMethod(samMethod).orElseThrow().samInvokeRuntime(),
                null, null, List.of(),
                method.get(), ref.receiver());
        callableBindings.put(ref, binding);
        return resultSamType();
    }

    private JType resultSamType() {
        if (expectedSamType instanceof InterfaceType it) {
            return it;
        }
        return resolveSamAdapterClass(expectedSamType).asType();
    }

    private boolean hasSubclassOverride(ClassSymbol staticReceiver, MethodSymbol method) {
        for (ClassSymbol cls : classes.values()) {
            if (cls == staticReceiver || !cls.isSubtypeOf(staticReceiver)) {
                continue;
            }
            Optional<MethodSymbol> m = cls.findMethod(method.name());
            if (m.isPresent() && m.get().overrides() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean methodMatchesSam(MethodSymbol method, FunctionType expected) {
        if (method.parameterTypes().size() != expected.parameterTypes().size()) {
            return false;
        }
        for (int i = 0; i < expected.parameterTypes().size(); i++) {
            JType exp = expected.parameterTypes().get(i);
            JType act = method.parameterTypes().get(i);
            if (!exp.isAssignableFrom(act) && !act.isAssignableFrom(exp)) {
                return false;
            }
        }
        return expected.returnType().isAssignableFrom(method.returnType())
                || method.returnType().isAssignableFrom(expected.returnType())
                || assignableWithBoxing(expected.returnType(), method.returnType());
    }

    /** Unbound instance ref: SAM(receiver, args...) ↔ instanceMethod(args...). */
    private boolean methodMatchesSamUnbound(MethodSymbol method, JType receiverType, FunctionType expected) {
        if (expected.parameterTypes().isEmpty()) {
            return false;
        }
        JType expRecv = expected.parameterTypes().getFirst();
        if (!expRecv.isAssignableFrom(receiverType) && !receiverType.isAssignableFrom(expRecv)) {
            return false;
        }
        if (method.parameterTypes().size() != expected.parameterTypes().size() - 1) {
            return false;
        }
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            JType exp = expected.parameterTypes().get(i + 1);
            JType act = method.parameterTypes().get(i);
            if (!exp.isAssignableFrom(act) && !act.isAssignableFrom(exp)) {
                return false;
            }
        }
        return expected.returnType().isAssignableFrom(method.returnType())
                || method.returnType().isAssignableFrom(expected.returnType())
                || assignableWithBoxing(expected.returnType(), method.returnType());
    }

    private JType typeOfNativeAddressOf(MethodCallExpression m) {
        if (m.arguments().size() != 1) {
            reporter.error(m.location(),
                    "method 'addressOf' expects 1 argument(s) but got " + m.arguments().size());
            return ErrorType.INSTANCE;
        }
        Expression arg = m.arguments().getFirst();
        JType argType = analyzeExpression(arg);
        if (!(arg instanceof VariableExpression)
                || !(resolvedSymbols.get(arg) instanceof VariableSymbol vs)
                || !vs.isStackAllocated()
                || !(vs.type() instanceof NativeStructType nst)) {
            if (!(argType instanceof ErrorType)) {
                reporter.error(arg.location(),
                        "addressOf requires a @Stack @NativeStruct variable");
            }
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(m, NativeIntrinsic.ADDRESS_OF);
        NativePtrType result = NativePtrType.of(nst);
        provenances.put(m, PointerProvenance.STACK);
        return result;
    }

    private JType typeOfNativeLoadInt(MethodCallExpression m) {
        if (m.arguments().size() != 1) {
            reporter.error(m.location(),
                    "method 'loadInt' expects 1 argument(s) but got " + m.arguments().size());
            return ErrorType.INSTANCE;
        }
        Expression arg = m.arguments().getFirst();
        JType argType = analyzeExpression(arg);
        if (!(argType instanceof NativePtrType) && !(argType instanceof ErrorType)) {
            reporter.error(arg.location(),
                    "argument 1: " + argType + " cannot be converted to NativePtr");
        }
        resolvedSymbols.put(m, NativeIntrinsic.LOAD_INT);
        return PrimitiveType.INT;
    }

    private JType typeOfNativeStoreInt(MethodCallExpression m) {
        if (m.arguments().size() != 2) {
            reporter.error(m.location(),
                    "method 'storeInt' expects 2 argument(s) but got " + m.arguments().size());
            return ErrorType.INSTANCE;
        }
        Expression ptrArg = m.arguments().get(0);
        Expression valueArg = m.arguments().get(1);
        JType ptrType = analyzeExpression(ptrArg);
        JType valueType = analyzeExpression(valueArg);
        if (!(ptrType instanceof NativePtrType) && !(ptrType instanceof ErrorType)) {
            reporter.error(ptrArg.location(),
                    "argument 1: " + ptrType + " cannot be converted to NativePtr");
        }
        if (!PrimitiveType.INT.isAssignableFrom(valueType) && !(valueType instanceof ErrorType)) {
            reporter.error(valueArg.location(),
                    "argument 2: " + valueType + " cannot be converted to int");
        }
        resolvedSymbols.put(m, NativeIntrinsic.STORE_INT);
        return VoidType.INSTANCE;
    }

    private JType typeOfNativeAllocate(MethodCallExpression m) {
        for (Expression arg : m.arguments()) {
            analyzeExpression(arg);
        }
        if (!m.arguments().isEmpty()) {
            reporter.error(m.location(),
                    "method 'allocate' expects 0 argument(s) but got " + m.arguments().size());
        }
        if (m.typeArguments().size() != 1) {
            reporter.error(m.location(), "Native.allocate requires a type argument");
            return ErrorType.INSTANCE;
        }
        JType typeArg = resolveTypeName(m.typeArguments().getFirst(), false, m.location());
        if (!(typeArg instanceof NativeStructType nst)) {
            if (!(typeArg instanceof ErrorType)) {
                reporter.error(m.location(),
                        "Native.allocate requires a @NativeStruct type");
            }
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(m, NativeIntrinsic.ALLOCATE_STRUCT);
        NativePtrType result = NativePtrType.of(nst);
        provenances.put(m, PointerProvenance.NATIVE_HEAP);
        return result;
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

    private JType typeOfNew(NewObjectExpression n, JType expected) {
        String resolvedNew = resolveTypeReferenceName(n.className(), n.location());
        if (resolvedNew == null) {
            for (Expression arg : n.arguments()) {
                analyzeExpression(arg);
            }
            return ErrorType.INSTANCE;
        }
        NativeStructSymbol nativeStruct = nativeStructs.get(resolvedNew);
        if (nativeStruct != null) {
            for (Expression arg : n.arguments()) {
                analyzeExpression(arg);
            }
            if (!allowingStackNativeNew) {
                reporter.error(n.location(),
                        "native struct requires explicit storage: @Stack or NativeMemory");
            } else if (!n.arguments().isEmpty()) {
                reporter.error(n.location(),
                        "stack-allocated native struct initializer must be new "
                                + n.className() + "()");
            }
            resolvedSymbols.put(n, nativeStruct);
            return nativeStruct.asType();
        }
        if (interfaces.containsKey(resolvedNew)) {
            reporter.error(n.location(), "cannot instantiate interface '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        ClassSymbol symbol = classes.get(resolvedNew);
        if (symbol == null) {
            reporter.error(n.location(), "undefined class '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        checkTypeAccessible(symbol.visibility(), symbol.packageName(), symbol.name(), n.location());
        List<JType> typeArgs = resolveExplicitTypeArguments(n.typeArguments(), n.location());
        if (n.diamond()) {
            if (expected instanceof ParameterizedType pt
                    && pt.raw().name().equals(n.className())) {
                typeArgs = pt.typeArguments();
            } else if (expected instanceof ClassType ct && ct.name().equals(n.className())
                    && !symbol.isGeneric()) {
                typeArgs = List.of();
            } else {
                reporter.error(n.location(),
                        "cannot infer type arguments for '" + n.className() + "'; specify explicit type args");
                typeArgs = List.of();
            }
        }
        if (symbol.isGeneric() && typeArgs.isEmpty()) {
            reporter.error(n.location(),
                    "raw types are not supported; use " + n.className() + "<?> or " + n.className() + "<T>");
            return ErrorType.INSTANCE;
        }
        ParameterizedType instantiated = null;
        if (!typeArgs.isEmpty()) {
            if (typeArgs.size() != symbol.typeParameters().size()) {
                reporter.error(n.location(),
                        "wrong number of type arguments for " + symbol.name()
                                + ": expected " + symbol.typeParameters().size()
                                + ", got " + typeArgs.size());
            } else {
                checkTypeArgumentBounds(symbol.typeParameters(), typeArgs, n.location());
                instantiated = new ParameterizedType(symbol, typeArgs);
            }
        }
        if (symbol.isEnum()) {
            reporter.error(n.location(), "cannot instantiate enum type '" + n.className() + "' with new");
            return ErrorType.INSTANCE;
        }
        if (symbol.isAbstract()) {
            reporter.error(n.location(), "cannot instantiate abstract class '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        if (symbol.isBuiltin() && !isConstructibleBuiltin(symbol)) {
            reporter.error(n.location(), "cannot instantiate builtin type '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        TypeSubstitution ctorSub = (instantiated != null)
                ? TypeSubstitution.zip(symbol.typeParameters(), typeArgs)
                : null;
        List<ConstructorSymbol> byArity = symbol.constructors().stream()
                .filter(c -> c.parameters().size() == n.arguments().size())
                .toList();
        List<JType> argTypes = new ArrayList<>();
        if (byArity.size() == 1) {
            ConstructorSymbol only = byArity.getFirst();
            for (int i = 0; i < n.arguments().size(); i++) {
                JType expectedParam = only.parameters().get(i).type();
                if (ctorSub != null) {
                    expectedParam = ctorSub.apply(expectedParam);
                }
                argTypes.add(analyzeExpression(
                        n.arguments().get(i), samExpectedType(expectedParam)));
            }
        } else {
            for (Expression arg : n.arguments()) {
                argTypes.add(analyzeExpression(arg));
            }
        }
        List<CallableResolver.Candidate> candidates = new ArrayList<>();
        for (ConstructorSymbol c : symbol.constructors()) {
            List<JType> params = c.parameterTypes();
            if (ctorSub != null) {
                List<JType> substituted = new ArrayList<>(params.size());
                for (JType p : params) {
                    substituted.add(ctorSub.apply(p));
                }
                params = substituted;
            }
            candidates.add(new CallableResolver.Candidate(params, c));
        }
        Object resolved = CallableResolver.resolve(candidates, argTypes);
        if (!(resolved instanceof ConstructorSymbol ctor)) {
            reporter.error(n.location(),
                    "no matching constructor for " + n.className()
                            + "(" + NameMangler.describeSignature(argTypes) + ")");
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(n, ctor);
        if (instantiated != null) {
            return instantiated;
        }
        return symbol.asType();
    }

    private boolean isConstructibleBuiltin(ClassSymbol symbol) {
        String name = symbol.name();
        if ("Thread".equals(name) || "Mutex".equals(name) || "Condition".equals(name)
                || "AtomicInt".equals(name) || "AtomicLong".equals(name) || "AtomicBool".equals(name)
                || "Future".equals(name)) {
            return true;
        }
        return throwableClass != null && symbol.isSubtypeOf(throwableClass);
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
        // Dotted FQN type or static field: walk AST names without analyzing package prefixes.
        String recvFqn = flattenQualifiedName(f.receiver());
        if (recvFqn != null) {
            ClassSymbol typeRecv = recvFqn.contains(".")
                    ? classes.get(recvFqn)
                    : resolveClassBySourceName(recvFqn);
            String asTypeFqn = recvFqn + "." + f.fieldName();
            if (classes.containsKey(asTypeFqn)) {
                ClassSymbol t = classes.get(asTypeFqn);
                resolvedSymbols.put(f, t);
                return t.asType();
            }
            if (interfaces.containsKey(asTypeFqn)) {
                InterfaceSymbol t = interfaces.get(asTypeFqn);
                resolvedSymbols.put(f, t);
                return t.asType();
            }
            if (typeRecv != null) {
                Optional<FieldSymbol> field = typeRecv.findField(f.fieldName());
                if (field.isPresent()) {
                    FieldSymbol fs = field.get();
                    checkMemberAccessible(fs.visibility(), typeRecv, f.location(), "field", fs.name());
                    if (!fs.isStatic()) {
                        reporter.error(f.location(),
                                "instance field '" + fs.name() + "' cannot be accessed via type name");
                        return ErrorType.INSTANCE;
                    }
                    resolvedSymbols.put(f, fs);
                    return fs.type();
                }
            }
        }
        JType recvType = analyzeExpression(f.receiver());
        if (recvType instanceof ArrayType && f.fieldName().equals("length")) {
            resolvedSymbols.put(f, ArrayLengthSymbol.INSTANCE);
            return PrimitiveType.INT;
        }
        NativeStructSymbol structSymbol = null;
        if (recvType instanceof NativeStructType nst) {
            structSymbol = nst.symbol();
        } else if (recvType instanceof NativePtrType np
                && np.pointee() instanceof NativeStructType nst) {
            structSymbol = nst.symbol();
        }
        if (structSymbol != null) {
            Optional<NativeStructFieldSymbol> nsField = structSymbol.findField(f.fieldName());
            if (nsField.isEmpty()) {
                reporter.error(f.location(),
                        "unknown field '" + f.fieldName() + "' in native struct '"
                                + structSymbol.name() + "'");
                return ErrorType.INSTANCE;
            }
            resolvedSymbols.put(f, nsField.get());
            JType fieldType = nsField.get().type();
            if (fieldType instanceof NativePtrType) {
                provenances.put(f, PointerProvenance.UNKNOWN);
            }
            return fieldType;
        }
        ClassSymbol recvClass = null;
        TypeSubstitution recvSub = null;
        if (recvType instanceof ClassType ct) {
            recvClass = ct.classSymbol();
        } else if (recvType instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
            recvClass = cs;
            recvSub = TypeSubstitution.zip(cs.typeParameters(), pt.typeArguments());
        }
        if (recvClass == null) {
            if (!(recvType instanceof ErrorType) && !(recvType instanceof NullType)) {
                reporter.error(f.location(),
                        "cannot access field on non-reference type " + recvType);
            } else if (recvType instanceof NullType) {
                reporter.error(f.location(), "cannot access field on null");
            }
            return ErrorType.INSTANCE;
        }
        Optional<FieldSymbol> field = recvClass.findField(f.fieldName());
        if (field.isEmpty()) {
            reporter.error(f.location(),
                    "unknown field '" + f.fieldName() + "' in class '" + recvClass.name() + "'");
            return ErrorType.INSTANCE;
        }
        FieldSymbol fs = field.get();
        checkMemberAccessible(fs.visibility(), recvClass, f.location(), "field", fs.name());
        boolean receiverIsTypeName = f.receiver() instanceof VariableExpression ve
                && resolvedSymbols.get(ve) instanceof ClassSymbol;
        if (fs.isStatic()) {
            if (!receiverIsTypeName && !(f.receiver() instanceof VariableExpression)) {
                // allow instance.staticField as Java does
            }
        } else if (receiverIsTypeName) {
            reporter.error(f.location(),
                    "instance field '" + f.fieldName() + "' cannot be accessed via type name");
            return ErrorType.INSTANCE;
        }
        resolvedSymbols.put(f, fs);
        if (fs.type() instanceof NativePtrType) {
            provenances.put(f, PointerProvenance.UNKNOWN);
        }
        JType fieldType = fs.type();
        if (recvSub != null) {
            fieldType = recvSub.apply(fieldType);
        }
        return fieldType;
    }

    private JType typeOfInstanceOf(InstanceOfExpression i) {
        analyzeExpression(i.expression());
        boolean prev = instanceofContext;
        instanceofContext = true;
        JType target;
        try {
            target = resolveTypeName(i.type(), false, i.location());
        } finally {
            instanceofContext = prev;
        }
        if (target instanceof InterfaceType it) {
            resolvedSymbols.put(i, it.interfaceSymbol().metadata());
            return PrimitiveType.BOOLEAN;
        }
        if (target instanceof ClassType ct) {
            resolvedSymbols.put(i, ct.classSymbol().metadata());
            return PrimitiveType.BOOLEAN;
        }
        if (target instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
            resolvedSymbols.put(i, cs.metadata());
            return PrimitiveType.BOOLEAN;
        }
        reporter.error(i.location(), "instanceof requires a class or interface type");
        return ErrorType.INSTANCE;
    }

    private JType typeOfCast(CastExpression c) {
        JType source = analyzeExpression(c.expression());
        JType target = resolveTypeName(c.type(), false, c.location());
        if (target instanceof ErrorType || source instanceof ErrorType) {
            return ErrorType.INSTANCE;
        }
        if (target.isPrimitive() || source.isPrimitive()) {
            return typeOfPrimitiveCast(source, target, c.location());
        }
        if (target instanceof NativePtrType && source instanceof NativePtrType) {
            provenances.put(c, provenances.getOrDefault(c.expression(), PointerProvenance.UNKNOWN));
            return target;
        }
        if (target instanceof NativePtrType && source instanceof ErrorType) {
            return target;
        }
        if (target instanceof InterfaceType targetIt) {
            if (source instanceof NullType || source instanceof ErrorType) {
                resolvedSymbols.put(c, targetIt.interfaceSymbol().metadata());
                return target;
            }
            if (source instanceof ClassType || source instanceof InterfaceType) {
                // Runtime check — closed-world may prove some casts impossible later.
                resolvedSymbols.put(c, targetIt.interfaceSymbol().metadata());
                return target;
            }
            reporter.error(c.location(), "cannot cast " + source + " to " + target);
            return ErrorType.INSTANCE;
        }
        if (!(target instanceof ClassType targetCt)) {
            reporter.error(c.location(), "cast target must be a class, interface, or supported primitive type");
            return ErrorType.INSTANCE;
        }
        if (source instanceof NullType || source instanceof ErrorType) {
            resolvedSymbols.put(c, targetCt.classSymbol().metadata());
            return target;
        }
        if (source instanceof InterfaceType) {
            // Interface → class requires runtime check.
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

    /**
     * Numeric cast matrix.
     * Integers: two's complement narrow / sign or zero extend ({@code char} zero-extends).
     * Float/double: binary32/64; to-integer is toward-zero with NaN→0 and saturation
     * (see {@code jnative_f64_to_i32} / docs/jnative-float-double.md).
     * Boolean conversions are rejected.
     */
    private JType typeOfPrimitiveCast(JType source, JType target, SourceLocation loc) {
        if (source == PrimitiveType.BOOLEAN || target == PrimitiveType.BOOLEAN) {
            reporter.error(loc, "cannot cast between boolean and numeric types");
            return ErrorType.INSTANCE;
        }
        if (!source.isPrimitive() || !target.isPrimitive()) {
            reporter.error(loc, "cannot cast " + source + " to " + target);
            return ErrorType.INSTANCE;
        }
        if (!source.isNumeric() || !target.isNumeric()) {
            reporter.error(loc, "cannot cast " + source + " to " + target);
            return ErrorType.INSTANCE;
        }
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

    /** Subtype / primitive widen, or boxing/unboxing conversion. */
    private static boolean assignableWithBoxing(JType expected, JType actual) {
        if (expected == null || actual instanceof ErrorType) {
            return true;
        }
        return Boxing.assignableOrBoxing(expected, actual);
    }

    /** Unbox wrapper to its primitive for operators; leave other types unchanged. */
    private static JType unboxForOperator(JType type) {
        PrimitiveType p = Boxing.primitiveOfWrapper(type);
        return p != null ? p : type;
    }

    private JType resolveTypeName(TypeName typeName, boolean isArray, SourceLocation fallback) {
        SourceLocation loc = typeName.location() != null ? typeName.location() : fallback;
        JType base = switch (typeName.kind()) {
            case VOID -> VoidType.INSTANCE;
            case BYTE -> PrimitiveType.BYTE;
            case SHORT -> PrimitiveType.SHORT;
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case FLOAT -> PrimitiveType.FLOAT;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case CHAR -> PrimitiveType.CHAR;
            case NATIVE_PTR -> resolveNativePtrType(typeName, loc);
            case WILDCARD -> resolveWildcardType(typeName, loc);
            case TYPE_VAR -> resolveTypeVariable(typeName.name(), loc);
            case CLASS -> resolveClassTypeName(typeName, loc);
        };
        if (isArray) {
            if (base instanceof VoidType || base instanceof ErrorType
                    || base instanceof NativeStructType) {
                reporter.error(loc, "invalid array type");
                return ErrorType.INSTANCE;
            }
            arrayMetadataFor(base);
            return new ArrayType(base);
        }
        return base;
    }

    private JType resolveClassTypeName(TypeName typeName, SourceLocation loc) {
        String raw = typeName.name();
        if ("NativePtr".equals(raw)) {
            return NativePtrType.VOID;
        }
        TypeParameterSymbol typeParam = lookupTypeParameter(raw);
        if (typeParam != null && !typeName.hasTypeArguments()) {
            return typeParam.asType();
        }
        String resolvedName = resolveTypeReferenceName(raw, loc);
        if (resolvedName == null) {
            return ErrorType.INSTANCE;
        }
        NativeStructSymbol nativeStruct = nativeStructs.get(resolvedName);
        if (nativeStruct != null) {
            if (typeName.hasTypeArguments()) {
                reporter.error(loc, "native struct cannot be parameterized");
            }
            return nativeStruct.asType();
        }
        List<JType> args = resolveExplicitTypeArguments(typeName.typeArguments(), loc);
        InterfaceSymbol iface = interfaces.get(resolvedName);
        if (iface != null) {
            if (!checkTypeAccessible(iface.visibility(), iface.packageName(), iface.name(), loc)) {
                return ErrorType.INSTANCE;
            }
            if (iface.isGeneric() && args.isEmpty() && !instanceofContext) {
                reporter.error(loc,
                        "raw types are not supported; use " + iface.simpleName() + "<?> or "
                                + iface.simpleName() + "<T>");
                return ErrorType.INSTANCE;
            }
            if (!args.isEmpty()) {
                if (args.size() != iface.typeParameters().size()) {
                    reporter.error(loc,
                            "wrong number of type arguments for " + iface.name());
                    return ErrorType.INSTANCE;
                }
                checkTypeArgumentBounds(iface.typeParameters(), args, loc);
                return new ParameterizedType(iface, args);
            }
            return iface.asType();
        }
        ClassSymbol symbol = classes.get(resolvedName);
        if (symbol == null) {
            reporter.error(loc, "undefined class '" + raw + "'");
            return ErrorType.INSTANCE;
        }
        if (!checkTypeAccessible(symbol.visibility(), symbol.packageName(), symbol.name(), loc)) {
            return ErrorType.INSTANCE;
        }
        if (symbol.isGeneric() && args.isEmpty() && !instanceofContext) {
            reporter.error(loc,
                    "raw types are not supported; use " + symbol.simpleName() + "<?> or "
                            + symbol.simpleName() + "<T>");
            return ErrorType.INSTANCE;
        }
        if (!args.isEmpty()) {
            if (args.size() != symbol.typeParameters().size()) {
                reporter.error(loc,
                        "wrong number of type arguments for " + symbol.name());
                return ErrorType.INSTANCE;
            }
            checkTypeArgumentBounds(symbol.typeParameters(), args, loc);
            return new ParameterizedType(symbol, args);
        }
        return symbol.asType();
    }

    private ClassSymbol resolveClassBySourceName(String raw) {
        String fqn = resolveTypeReferenceNameQuiet(raw);
        if (fqn == null) {
            return null;
        }
        return classes.get(fqn);
    }

    /**
     * Resolve a call/field receiver that is a type name: {@code Foo}, imported simple name,
     * or dotted FQN chain {@code com.acme.Foo}.
     */
    private ClassSymbol resolveTypeNameReceiver(Expression recv) {
        if (recv instanceof VariableExpression v) {
            return resolveClassBySourceName(v.name());
        }
        String fqn = flattenQualifiedName(recv);
        if (fqn == null || !fqn.contains(".")) {
            return null;
        }
        return classes.get(fqn);
    }

    /** {@code a.b.c} as Variable/FieldAccess chain → {@code "a.b.c"}; otherwise null. */
    private String flattenQualifiedName(Expression expr) {
        if (expr instanceof VariableExpression v) {
            return v.name();
        }
        if (expr instanceof FieldAccessExpression f) {
            String left = flattenQualifiedName(f.receiver());
            if (left == null) {
                return null;
            }
            return left + "." + f.fieldName();
        }
        return null;
    }

    private InterfaceSymbol resolveInterfaceBySourceName(String raw) {
        String fqn = resolveTypeReferenceNameQuiet(raw);
        if (fqn == null) {
            return null;
        }
        return interfaces.get(fqn);
    }

    private static int visibilityRank(Visibility v) {
        if (v == null) {
            return 1; // package
        }
        return switch (v) {
            case PUBLIC -> 3;
            case PROTECTED -> 2;
            case PACKAGE -> 1;
            case PRIVATE -> 0;
        };
    }

    /**
     * Resolve a source type name to an FQN using imports and the current type's package.
     */
    private String resolveTypeReferenceName(String raw, SourceLocation loc) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (!raw.contains(".")) {
            String ambigImport = ambiguousImportFor(raw);
            if (ambigImport != null) {
                reporter.error(loc, ambigImport);
                return null;
            }
        }
        String fqn = resolveTypeReferenceNameQuiet(raw);
        if (fqn != null) {
            if (!raw.contains(".")
                    && !classes.containsKey(fqn) && !interfaces.containsKey(fqn)
                    && !nativeStructs.containsKey(fqn)) {
                reporter.error(loc, "undefined class '" + fqn + "'");
                return null;
            }
            return fqn;
        }
        if (raw.contains(".")) {
            reporter.error(loc, "undefined class '" + raw + "'");
            return null;
        }
        String pkg = currentPackageName();
        List<String> candidates = typesBySimpleName.getOrDefault(raw, List.of());
        if (candidates.size() > 1) {
            reporter.error(loc, "ambiguous type '" + raw + "'; candidates: "
                    + String.join(", ", candidates));
            return null;
        }
        if (candidates.size() == 1) {
            reporter.error(loc, "undefined class '" + raw + "' in package '"
                    + (pkg.isEmpty() ? "<default>" : pkg)
                    + "'; use a fully qualified name or import");
            return null;
        }
        reporter.error(loc, "undefined class '" + raw + "'");
        return null;
    }

    private String ambiguousImportFor(String simple) {
        String first = null;
        for (String imp : currentImportTypes()) {
            int dot = imp.lastIndexOf('.');
            String s = dot < 0 ? imp : imp.substring(dot + 1);
            if (s.equals(simple)) {
                if (first != null && !first.equals(imp)) {
                    return "ambiguous type '" + simple + "'; imported as both "
                            + first + " and " + imp;
                }
                first = imp;
            }
        }
        return null;
    }

    /**
     * Quiet resolution for expression/type-name probes (no diagnostics).
     * Returns FQN when uniquely determined via FQN, import, same package, or default-package alias.
     */
    private String resolveTypeReferenceNameQuiet(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.contains(".")) {
            if (classes.containsKey(raw) || interfaces.containsKey(raw) || nativeStructs.containsKey(raw)) {
                return raw;
            }
            return null;
        }
        List<String> imports = currentImportTypes();
        String fromImport = null;
        for (String imp : imports) {
            int dot = imp.lastIndexOf('.');
            String simple = dot < 0 ? imp : imp.substring(dot + 1);
            if (simple.equals(raw)) {
                if (fromImport != null && !fromImport.equals(imp)) {
                    return null; // ambiguous imports — noisy path reports
                }
                fromImport = imp;
            }
        }
        if (fromImport != null) {
            return fromImport;
        }
        String pkg = currentPackageName();
        if (!pkg.isEmpty()) {
            String samePkg = pkg + "." + raw;
            if (classes.containsKey(samePkg) || interfaces.containsKey(samePkg)
                    || nativeStructs.containsKey(samePkg)) {
                return samePkg;
            }
        }
        if (classes.containsKey(raw) || interfaces.containsKey(raw) || nativeStructs.containsKey(raw)) {
            return raw;
        }
        List<String> candidates = typesBySimpleName.getOrDefault(raw, List.of());
        if (candidates.size() == 1) {
            String only = candidates.getFirst();
            if (pkg.isEmpty() && !only.contains(".")) {
                return only;
            }
        }
        return null;
    }

    private void beginResolve(ClassDeclaration cls) {
        resolvePackage = cls.packageName() == null ? "" : cls.packageName();
        resolveImports = cls.imports().stream().map(ImportDeclaration::qualifiedType).toList();
        currentClass = cls.isInterface() || cls.nativeStruct() ? null : classOf(cls);
    }

    private void endResolve() {
        resolvePackage = "";
        resolveImports = List.of();
        currentClass = null;
    }

    private String currentPackageName() {
        if (resolvePackage != null && !resolvePackage.isEmpty()) {
            return resolvePackage;
        }
        if (currentClass != null) {
            return currentClass.packageName();
        }
        return "";
    }

    private List<String> currentImportTypes() {
        if (resolveImports != null && !resolveImports.isEmpty()) {
            return resolveImports;
        }
        if (currentClass != null) {
            return currentClass.importTypes();
        }
        return List.of();
    }

    private boolean checkTypeAccessible(
            Visibility visibility, String typePkg, String typeFqn, SourceLocation loc) {
        String fromPkg = currentPackageName();
        String fromFqn = currentClass == null ? "" : currentClass.name();
        if (visibility.accessibleFrom(typePkg, typeFqn, fromPkg, fromFqn, false)) {
            return true;
        }
        reporter.error(loc, "type '" + typeFqn + "' is not accessible from "
                + (fromPkg.isEmpty() ? "<default package>" : fromPkg));
        return false;
    }

    /**
     * Stdlib sources omit {@code public} historically; treat them as exported so
     * named packages can use ArrayList/Integer/FileInputStream (java.lang-like).
     * Parser defaults missing modifiers to {@link Visibility#PACKAGE}, so we elevate
     * PACKAGE (not only null) when the declaration comes from stdlib.
     * User types without {@code public} remain package-private.
     */
    private static Visibility defaultTypeVisibility(ClassDeclaration cls) {
        Visibility v = cls.visibility();
        if (v == Visibility.PUBLIC || v == Visibility.PRIVATE || v == Visibility.PROTECTED) {
            return v;
        }
        if (isStdlibSource(cls.location())) {
            return Visibility.PUBLIC;
        }
        return Visibility.PACKAGE;
    }

    private static Visibility exportStdlibMember(Visibility declared, SourceLocation loc) {
        Visibility v = declared == null ? Visibility.PACKAGE : declared;
        if (v == Visibility.PACKAGE && isStdlibSource(loc)) {
            return Visibility.PUBLIC;
        }
        return v;
    }

    private static boolean isStdlibSource(SourceLocation loc) {
        if (loc == null || loc.file() == null) {
            return false;
        }
        String f = loc.file();
        return f.contains("/stdlib/") || f.startsWith("<stdlib>") || f.contains("stdlib/");
    }

    private boolean isSubtypeOf(ClassSymbol sub, ClassSymbol sup) {
        ClassSymbol cur = sub;
        while (cur != null) {
            if (cur == sup) {
                return true;
            }
            cur = cur.superClass().orElse(null);
        }
        return false;
    }

    private void checkMemberAccessible(
            Visibility visibility,
            ClassSymbol owner,
            SourceLocation loc,
            String memberKind,
            String memberName
    ) {
        if (owner == null || visibility == null) {
            return;
        }
        String fromPkg = currentPackageName();
        String fromFqn = currentClass == null ? "" : currentClass.name();
        boolean subtype = currentClass != null && isSubtypeOf(currentClass, owner);
        if (visibility.accessibleFrom(owner.packageName(), owner.name(), fromPkg, fromFqn, subtype)) {
            return;
        }
        reporter.error(loc, memberKind + " '" + owner.simpleName() + "." + memberName
                + "' has " + visibility.name().toLowerCase()
                + " access and is not accessible from "
                + (fromPkg.isEmpty() ? "<default package>" : fromPkg));
    }

    private JType resolveWildcardType(TypeName typeName, SourceLocation loc) {
        return switch (typeName.wildcardKind()) {
            case UNBOUNDED, NONE -> WildcardType.unbounded();
            case EXTENDS -> {
                JType bound = resolveTypeName(typeName.wildcardBound(), false, loc);
                yield WildcardType.extendsBound(bound);
            }
            case SUPER -> {
                JType bound = resolveTypeName(typeName.wildcardBound(), false, loc);
                yield WildcardType.superBound(bound);
            }
        };
    }

    private JType resolveTypeVariable(String name, SourceLocation loc) {
        TypeParameterSymbol sym = lookupTypeParameter(name);
        if (sym == null) {
            reporter.error(loc, "undefined type variable '" + name + "'");
            return ErrorType.INSTANCE;
        }
        return sym.asType();
    }

    private List<JType> resolveExplicitTypeArguments(List<TypeName> typeArgs, SourceLocation loc) {
        if (typeArgs == null || typeArgs.isEmpty()) {
            return List.of();
        }
        List<JType> out = new ArrayList<>();
        for (TypeName arg : typeArgs) {
            out.add(resolveTypeName(arg, false, loc));
        }
        return out;
    }

    private void checkTypeArgumentBounds(
            List<TypeParameterSymbol> params,
            List<JType> args,
            SourceLocation loc
    ) {
        int n = Math.min(params.size(), args.size());
        for (int i = 0; i < n; i++) {
            JType arg = args.get(i);
            if (arg instanceof WildcardType) {
                continue;
            }
            for (JType bound : params.get(i).bounds()) {
                if (!bound.isAssignableFrom(arg) && !(arg instanceof ErrorType)) {
                    reporter.error(loc,
                            "type argument " + arg + " does not satisfy bound " + bound);
                }
            }
        }
    }

    private List<TypeParameterSymbol> createTypeParameters(
            List<TypeParameterDeclaration> decls,
            ReferenceTypeSymbol owner,
            MethodSymbol ownerMethod
    ) {
        if (decls == null || decls.isEmpty()) {
            return List.of();
        }
        List<TypeParameterSymbol> params = new ArrayList<>();
        for (int i = 0; i < decls.size(); i++) {
            TypeParameterDeclaration d = decls.get(i);
            params.add(new TypeParameterSymbol(d.name(), owner, ownerMethod, i, d.location()));
        }
        pushTypeParamScope(params);
        for (int i = 0; i < decls.size(); i++) {
            for (TypeName bound : decls.get(i).bounds()) {
                params.get(i).addBound(resolveTypeName(bound, false, decls.get(i).location()));
            }
        }
        popTypeParamScope();
        return params;
    }

    private void pushTypeParamScope(List<TypeParameterSymbol> params) {
        // Always push so popTypeParamScope stays balanced (empty method type-params
        // must not pop the enclosing class/interface scope).
        Map<String, TypeParameterSymbol> map = new LinkedHashMap<>();
        if (params != null) {
            for (TypeParameterSymbol p : params) {
                map.put(p.name(), p);
            }
        }
        typeParamScopes.push(map);
    }

    private void popTypeParamScope() {
        if (!typeParamScopes.isEmpty()) {
            typeParamScopes.pop();
        }
    }

    private TypeParameterSymbol lookupTypeParameter(String name) {
        for (Map<String, TypeParameterSymbol> scope : typeParamScopes) {
            TypeParameterSymbol sym = scope.get(name);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    private ClassSymbol resolveClassRaw(JType type, SourceLocation loc) {
        if (type instanceof ClassType ct) {
            return ct.classSymbol();
        }
        if (type instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
            return cs;
        }
        if (!(type instanceof ErrorType)) {
            reporter.error(loc, "expected a class type");
        }
        return null;
    }

    private InterfaceSymbol resolveInterfaceRaw(JType type, SourceLocation loc) {
        if (type instanceof InterfaceType it) {
            return it.interfaceSymbol();
        }
        if (type instanceof ParameterizedType pt && pt.raw() instanceof InterfaceSymbol is) {
            return is;
        }
        if (!(type instanceof ErrorType)) {
            reporter.error(loc, "expected an interface type");
        }
        return null;
    }

    private MethodSymbol substituteMethod(MethodSymbol ms, TypeSubstitution sub) {
        List<ParameterSymbol> params = ms.parameters().stream()
                .map(p -> new ParameterSymbol(p.name(), sub.apply(p.type()), p.location()))
                .toList();
        JType ret = sub.apply(ms.returnType());
        MethodSymbol copy = new MethodSymbol(
                ms.owner(), ms.name(), ret, params,
                ms.isStatic(), ms.isMain(), ms.isNative(), ms.isAbstract(),
                ms.mangledName(), ms.location());
        copy.setAsync(ms.isAsync());
        copy.setLogicalReturnType(sub.apply(ms.logicalReturnType()));
        return copy;
    }

    private JType typeOfAwait(AwaitExpression a) {
        if (currentMethod == null || !currentMethod.isAsync()) {
            reporter.error(a.location(), "await is only allowed inside async methods");
            return ErrorType.INSTANCE;
        }
        JType operandType = analyzeExpression(a.operand());
        JType element = futureElementType(operandType, a.location());
        if (element == null) {
            reporter.error(a.location(), "await operand must be a Future<T>, got " + operandType);
            return ErrorType.INSTANCE;
        }
        return element;
    }

    private static boolean isFutureType(JType type, SourceLocation loc) {
        if (type instanceof ParameterizedType pt && "Future".equals(pt.name())) {
            return true;
        }
        if (type instanceof ClassType ct && "Future".equals(ct.name())) {
            return true;
        }
        return false;
    }

    private ClassSymbol resolveFutureClass(SourceLocation loc) {
        if (futureClass != null) {
            return futureClass;
        }
        ClassSymbol declared = classes.get("Future");
        if (declared != null) {
            return declared;
        }
        return null;
    }

    private JType futureElementType(JType type, SourceLocation loc) {
        if (type instanceof ParameterizedType pt && "Future".equals(pt.name())) {
            if (pt.typeArguments().isEmpty()) {
                reporter.error(loc, "Future type argument missing");
                return null;
            }
            return pt.typeArguments().getFirst();
        }
        if (type instanceof ClassType ct && "Future".equals(ct.name())) {
            return objectClass.asType();
        }
        return null;
    }

    private JType resolveNativePtrType(TypeName typeName, SourceLocation loc) {
        TypeName arg = typeName.typeArgument();
        if (arg == null) {
            return NativePtrType.VOID;
        }
        JType pointee = resolveNativePtrPointee(arg, loc);
        if (pointee instanceof ErrorType) {
            return NativePtrType.VOID;
        }
        return NativePtrType.of(pointee);
    }

    private JType resolveNativePtrPointee(TypeName arg, SourceLocation loc) {
        JType pointee = resolveTypeName(arg, false, loc);
        if (pointee instanceof ErrorType) {
            return pointee;
        }
        if (pointee instanceof VoidType) {
            return pointee;
        }
        if (pointee instanceof PrimitiveType p) {
            if (p == PrimitiveType.BOOLEAN) {
                reporter.error(loc, "boolean cannot be a NativePtr pointee without NativeBool");
                return ErrorType.INSTANCE;
            }
            return pointee;
        }
        if (pointee instanceof NativeStructType) {
            return pointee;
        }
        if (pointee instanceof ClassType) {
            reporter.error(loc, "managed class cannot be a NativePtr pointee");
            return ErrorType.INSTANCE;
        }
        if (pointee instanceof ArrayType) {
            reporter.error(loc, "array cannot be a NativePtr pointee");
            return ErrorType.INSTANCE;
        }
        reporter.error(loc, "invalid NativePtr pointee type: " + pointee);
        return ErrorType.INSTANCE;
    }
}
