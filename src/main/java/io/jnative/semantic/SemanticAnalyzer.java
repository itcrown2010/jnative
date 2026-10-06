package io.jnative.semantic;

import io.jnative.ast.*;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.mangle.NameMangler;
import io.jnative.ffi.AbiType;
import io.jnative.ffi.CallingConvention;
import io.jnative.ffi.NativeFunction;
import io.jnative.ffi.NativeSignature;
import io.jnative.ffi.NativeStructLayoutBuilder;
import io.jnative.ffi.PointerProvenance;
import io.jnative.ffi.TargetAbi;
import io.jnative.vtable.VTableBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic analysis for JNative 0.8 (native data model, constructors, arrays, String, RTTI).
 */
public final class SemanticAnalyzer {
    private final DiagnosticReporter reporter;
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();
    private final Map<String, NativeStructSymbol> nativeStructs = new LinkedHashMap<>();
    private final Map<Expression, JType> expressionTypes = new HashMap<>();
    private final Map<Expression, Object> resolvedSymbols = new HashMap<>();
    private final Map<Expression, CallableBinding> callableBindings = new HashMap<>();
    private final Map<Expression, PointerProvenance> provenances = new HashMap<>();
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
    private ClassSymbol nativeMemoryClass;
    private ClassSymbol nativeClass;
    private boolean allowingStackNativeNew;
    private final TargetAbi targetAbi = TargetAbi.forHost();
    private final LinkedHashMap<String, String> nativeLibraries = new LinkedHashMap<>();
    private JType expectedSamType;
    private boolean inLambdaBody;
    private Scope lambdaBodyScope;
    private final Set<VariableSymbol> lambdaCapturedVars = new HashSet<>();
    private int envClassCounter;
    private int lambdaCounter;
    private int callbackCounter;
    private ClassSymbol runnableClass;
    private ClassSymbol nativeCallbackHandleClass;

    public SemanticAnalyzer(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }

    public TypedCompilationUnit analyze(CompilationUnit unit) {
        classes.clear();
        expressionTypes.clear();
        resolvedSymbols.clear();
        callableBindings.clear();
        arrayMetadatas.clear();
        constructorAst.clear();
        catchTypes.clear();
        nativeStructs.clear();
        provenances.clear();
        current = new Scope(null);
        currentClass = null;
        currentMethod = null;
        currentConstructor = null;
        allowingStackNativeNew = false;

        envClassCounter = 0;
        lambdaCounter = 0;
        callbackCounter = 0;
        installBuiltins();
        // File.readBytes / argv need these metadatas present for the runtime.
        arrayMetadataFor(PrimitiveType.BYTE);
        arrayMetadataFor(stringClass.asType());
        nativeLibraries.clear();
        nativeStructs.clear();
        provenances.clear();

        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                declareNativeStruct(cls);
            } else {
                declareClass(cls);
            }
        }
        for (ClassDeclaration cls : unit.classes()) {
            if (!cls.nativeStruct()) {
                linkSuperClass(cls);
            }
        }
        detectInheritanceCycles(unit);

        for (ClassDeclaration cls : unit.classes()) {
            if (!cls.nativeStruct()) {
                continue;
            }
            NativeStructSymbol symbol = nativeStructs.get(cls.name());
            if (symbol != null) {
                registerNativeStructFields(symbol, cls);
            }
        }
        detectRecursiveNativeStructs();
        buildNativeStructLayouts();

        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol != null) {
                registerFields(symbol, cls);
            }
        }
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
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
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol symbol = classes.get(cls.name());
            if (symbol == null) {
                continue;
            }
            currentClass = symbol;
            analyzeStaticFieldInitializers(symbol);
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
                Map.copyOf(nativeStructs),
                Map.copyOf(expressionTypes),
                Map.copyOf(resolvedSymbols),
                Map.copyOf(callableBindings),
                Map.copyOf(arrayMetadatas),
                Map.copyOf(catchTypes),
                List.copyOf(nativeLibraries.keySet()),
                targetAbi
        );
    }

    private void installBuiltins() {
        installStringBuiltin();
        installObjectHierarchy();
        installCallableBuiltins();
        installFfiBuiltins();
    }

    private void installObjectHierarchy() {
        objectClass = new ClassSymbol("Object", SourceLocation.UNKNOWN, true);
        classes.put("Object", objectClass);
        objectClass.addConstructor(new ConstructorSymbol(
                objectClass, List.of(), true, SourceLocation.UNKNOWN));
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
        ClassSymbol ioExceptionClass = newExceptionClass("IOException", exceptionClass);
        ClassSymbol fileNotFoundClass = newExceptionClass("FileNotFoundException", ioExceptionClass);
        ClassSymbol socketExceptionClass = newExceptionClass("SocketException", ioExceptionClass);
        ClassSymbol connectExceptionClass = newExceptionClass("ConnectException", socketExceptionClass);
        ClassSymbol unknownHostClass = newExceptionClass("UnknownHostException", socketExceptionClass);
        ClassSymbol socketTimeoutClass = newExceptionClass("SocketTimeoutException", socketExceptionClass);

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
                "IllegalStateException", "IllegalMonitorStateException")) {
            ClassSymbol leafClass = newExceptionClass(leaf, runtimeExceptionClass);
            leafClass.constructors().getFirst().setDelegatesTo(runtimeExceptionClass.constructors().getFirst());
            leafClass.constructors().getFirst().setDelegatesToSuper(true);
            ConstructorSymbol leafString = addStringSuperCtor(leafClass);
            leafString.setDelegatesTo(runtimeString);
            leafString.setDelegatesToSuper(true);
            leaves.add(leafClass);
        }

        // Layouts must be built after message field exists, parents before children.
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
        stringClass.setVtable(new io.jnative.vtable.VTable(stringClass, List.of()));
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
        addFfiIntrinsic(nativeMemoryClass, "fromCString", stringClass.asType(),
                List.of(new ParameterSymbol("ptr", NativePtrType.VOID, SourceLocation.UNKNOWN)));

        nativeMemoryClass.setLayout(ObjectLayoutBuilder.build(nativeMemoryClass));
        nativeMemoryClass.setVtable(new io.jnative.vtable.VTable(nativeMemoryClass, List.of()));

        installFileBuiltins();
        installNetworkBuiltins();
        installBytesBuiltin();
        installThreadBuiltins();
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

        FunctionType consumer = new FunctionType(
                List.of(objectClass.asType()), VoidType.INSTANCE, FunctionType.Domain.MANAGED);
        installSam("Consumer", "accept", consumer, "jnative_invoke_void_ptr");

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
        if (classes.containsKey(cls.name()) || nativeStructs.containsKey(cls.name())) {
            reporter.error(cls.location(), "duplicate class '" + cls.name() + "'");
            return;
        }
        classes.put(cls.name(), new ClassSymbol(cls.name(), cls.location(), false));
    }

    private void declareNativeStruct(ClassDeclaration cls) {
        if (classes.containsKey(cls.name()) || nativeStructs.containsKey(cls.name())) {
            reporter.error(cls.location(), "duplicate type '" + cls.name() + "'");
            return;
        }
        nativeStructs.put(cls.name(), new NativeStructSymbol(cls.name(), cls.location()));
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
                    symbol, field.name(), type, index, field.isStatic(), field.isVolatile(), field.location());
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
    }

    private void analyzeStaticFieldInitializers(ClassSymbol symbol) {
        MethodSymbol prevMethod = currentMethod;
        currentMethod = null; // treat as static context
        for (FieldSymbol field : symbol.declaredFields()) {
            if (!field.isStatic() || field.initializer() == null) {
                continue;
            }
            JType initType = analyzeExpression(field.initializer());
            if (!field.type().isAssignableFrom(initType) && !(initType instanceof ErrorType)) {
                reporter.error(field.location(),
                        "incompatible types: " + initType + " cannot be converted to " + field.type());
            }
        }
        currentMethod = prevMethod;
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
            JType returnType = resolveTypeName(method.returnType(), method.returnsArray(), method.location());

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
                    method.isStatic(), isMain, method.isNative(), mangled, method.location());
            applyEffectContract(ms, method.effectContract());

            if (method.isNative()) {
                bindNativeMethod(ms, method, returnType, params);
            }

            Optional<MethodSymbol> inherited = symbol.superClass()
                    .flatMap(s -> s.findMethod(method.name()));
            if (inherited.isPresent()) {
                MethodSymbol parent = inherited.get();
                if (ms.isNative() || parent.isNative()) {
                    reporter.error(method.location(),
                            "native methods cannot override or be overridden");
                } else if (parent.isStatic() || ms.isStatic()) {
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
        if (method.isNative()) {
            current = null;
            currentMethod = null;
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
        current = null;
        currentMethod = null;
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
                    JType initType = analyzeExpression(init, samExpectedType(type));
                    if (!type.isAssignableFrom(initType)) {
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
                v.name(), type, VariableSymbol.Kind.LOCAL, v.location(), v.stackAllocated());
        if (v.stackAllocated()) {
            vs.setProvenance(PointerProvenance.STACK);
        } else if (type instanceof NativePtrType && v.initializer() != null) {
            vs.setProvenance(provenances.getOrDefault(v.initializer(), PointerProvenance.UNKNOWN));
        }
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
        JType expected;
        if (inLambdaBody && expectedSamType instanceof ClassType samCt
                && samCt.classSymbol().functionType() != null) {
            expected = samCt.classSymbol().functionType().returnType();
        } else if (currentMethod == null) {
            reporter.error(r.location(), "return outside method");
            return;
        } else {
            expected = currentMethod.returnType();
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
        } else if (!expected.isAssignableFrom(actual)) {
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
            VariableSymbol vs = vb.variable();
            if (inLambdaBody && lambdaBodyScope != null && !isLambdaLocalSymbol(vs)) {
                lambdaCapturedVars.add(vs);
                vs.setCapturedByLambda(true);
                if (vs.isAssignedAfterInit()) {
                    reporter.error(v.location(),
                            "local variable '" + v.name() + "' must be effectively final");
                }
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
                    if (fs.type() instanceof NativePtrType) {
                        provenances.put(v, PointerProvenance.UNKNOWN);
                    }
                    return fs.type();
                }
            }
        }
        if (classes.containsKey(v.name())) {
            // Class name used as static-field / static-method receiver.
            resolvedSymbols.put(v, classes.get(v.name()));
            return classes.get(v.name()).asType();
        }
        reporter.error(v.location(), "undefined variable '" + v.name() + "'");
        return ErrorType.INSTANCE;
    }

    private JType typeOfAssignment(AssignmentExpression a) {
        JType targetType = analyzeExpression(a.target());
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
        if (!targetType.isAssignableFrom(valueType)) {
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
                vs.setAssignedAfterInit(true);
                if (vs.isCapturedByLambda()) {
                    reporter.error(a.location(),
                            "local variable '" + vs.name() + "' must be effectively final");
                }
            }
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
        if (m.receiver() instanceof VariableExpression nv && "Native".equals(nv.name())) {
            return typeOfNativeBuiltin(m);
        }
        if (m.receiver() instanceof VariableExpression v && classes.containsKey(v.name())) {
            ClassSymbol cls = classes.get(v.name());
            Optional<MethodSymbol> method = cls.findMethod(m.name());
            if (method.isEmpty()) {
                reporter.error(m.location(),
                        "undefined method '" + m.name() + "' in class '" + cls.name() + "'");
                return ErrorType.INSTANCE;
            }
            MethodSymbol ms = method.get();
            if (!ms.isStatic()) {
                reporter.error(m.location(),
                        "instance method '" + m.name() + "' cannot be called on a type name");
                return ErrorType.INSTANCE;
            }
            checkArguments(m, ms);
            checkNetworkPortLiteral(m, ms);
            resolvedSymbols.put(m, ms);
            if (ms.isFfiIntrinsic() && "allocate".equals(ms.name())) {
                provenances.put(m, PointerProvenance.NATIVE_HEAP);
            }
            return ms.returnType();
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
            if (!exp.isAssignableFrom(actual)) {
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
        if (lambdaBodyScope == null) {
            return false;
        }
        Optional<Symbol> sym = lambdaBodyScope.resolveLocal(vs.name());
        return sym.isPresent() && sym.get() instanceof Symbol.VariableBinding vb
                && vb.variable() == vs;
    }

    private JType typeOfLambda(LambdaExpression lambda) {
        if (!(expectedSamType instanceof ClassType samCt) || samCt.classSymbol().functionType() == null) {
            reporter.error(lambda.location(), "cannot infer lambda type; expected SAM type");
            return ErrorType.INSTANCE;
        }
        ClassSymbol samClass = samCt.classSymbol();
        FunctionType expected = samClass.functionType();
        if (lambda.parameters().size() != expected.parameterTypes().size()) {
            reporter.error(lambda.location(),
                    "lambda parameter count mismatch: expected "
                            + expected.parameterTypes().size() + " but got "
                            + lambda.parameters().size());
            return ErrorType.INSTANCE;
        }

        Scope outer = current;
        current = new Scope(outer);
        lambdaBodyScope = current;
        lambdaCapturedVars.clear();
        inLambdaBody = true;
        List<VariableSymbol> paramSymbols = new ArrayList<>();
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
                paramSymbols.add(vs);
            }
            if (lambda.isBlock()) {
                for (Statement stmt : lambda.blockBody().statements()) {
                    analyzeStatement(stmt);
                }
            } else {
                JType bodyType = analyzeExpression(lambda.expressionBody());
                if (!expected.returnType().isAssignableFrom(bodyType)
                        && !(expected.returnType() instanceof VoidType)) {
                    if (!(bodyType instanceof ErrorType)) {
                        reporter.error(lambda.expressionBody().location(),
                                "lambda body type " + bodyType + " incompatible with "
                                        + expected.returnType());
                    }
                }
            }
        } finally {
            inLambdaBody = false;
            lambdaBodyScope = null;
            current = outer;
        }

        ClassSymbol envClass = null;
        List<ClosureCapture> captures = List.of();
        if (!lambdaCapturedVars.isEmpty()) {
            String envName = "__jn.Env." + (envClassCounter++);
            envClass = new ClassSymbol(envName, lambda.location(), true);
            envClass.setSuperClass(objectClass);
            int idx = 0;
            List<ClosureCapture> capList = new ArrayList<>();
            for (VariableSymbol vs : lambdaCapturedVars) {
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
        String invokeRuntime = samClass.findMethod(samMethodName(samClass)).orElseThrow().samInvokeRuntime();
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
        return samClass.asType();
    }

    private static String samMethodName(ClassSymbol sam) {
        return switch (sam.name()) {
            case "Runnable" -> "run";
            case "Consumer" -> "accept";
            case "IntCompare" -> "compare";
            case "HttpHandler" -> "handle";
            default -> "apply";
        };
    }

    private JType typeOfMethodReference(MethodReferenceExpression ref) {
        if (!(expectedSamType instanceof ClassType samCt) || samCt.classSymbol().functionType() == null) {
            reporter.error(ref.location(), "cannot infer method reference type; expected SAM type");
            return ErrorType.INSTANCE;
        }
        ClassSymbol samClass = samCt.classSymbol();
        FunctionType expected = samClass.functionType();
        String samMethod = samMethodName(samClass);

        if (ref.typeName() != null) {
            ClassSymbol cls = classes.get(ref.typeName().name());
            if (cls == null) {
                reporter.error(ref.location(), "unknown type '" + ref.typeName().name() + "'");
                return ErrorType.INSTANCE;
            }
            Optional<MethodSymbol> method = cls.findMethod(ref.methodName());
            if (method.isEmpty() || !method.get().isStatic()) {
                reporter.error(ref.location(),
                        "static method '" + ref.methodName() + "' not found on " + cls.name());
                return ErrorType.INSTANCE;
            }
            if (!methodMatchesSam(method.get(), expected)) {
                reporter.error(ref.location(), "method '" + ref.methodName() + "' incompatible with SAM");
                return ErrorType.INSTANCE;
            }
            String codeSymbol = "_JN_thunk_static_" + cls.name() + "_" + ref.methodName();
            CallableBinding binding = new CallableBinding(
                    samClass, codeSymbol, null, List.of(), FunctionEffects.unknown(),
                    false, false, null,
                    samClass.findMethod(samMethod).orElseThrow().samInvokeRuntime(),
                    null, null, List.of(),
                    method.get(), null);
            callableBindings.put(ref, binding);
            return samClass.asType();
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
        return samClass.asType();
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
                || method.returnType().isAssignableFrom(expected.returnType());
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

    private JType typeOfNew(NewObjectExpression n) {
        NativeStructSymbol nativeStruct = nativeStructs.get(n.className());
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
        ClassSymbol symbol = classes.get(n.className());
        if (symbol == null) {
            reporter.error(n.location(), "undefined class '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        if (symbol.isBuiltin() && !isConstructibleBuiltin(symbol)) {
            reporter.error(n.location(), "cannot instantiate builtin type '" + n.className() + "'");
            return ErrorType.INSTANCE;
        }
        List<ConstructorSymbol> byArity = symbol.constructors().stream()
                .filter(c -> c.parameters().size() == n.arguments().size())
                .toList();
        List<JType> argTypes = new ArrayList<>();
        if (byArity.size() == 1) {
            ConstructorSymbol only = byArity.getFirst();
            for (int i = 0; i < n.arguments().size(); i++) {
                argTypes.add(analyzeExpression(
                        n.arguments().get(i), samExpectedType(only.parameters().get(i).type())));
            }
        } else {
            for (Expression arg : n.arguments()) {
                argTypes.add(analyzeExpression(arg));
            }
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

    private boolean isConstructibleBuiltin(ClassSymbol symbol) {
        String name = symbol.name();
        if ("Thread".equals(name) || "Mutex".equals(name) || "Condition".equals(name)
                || "AtomicInt".equals(name) || "AtomicLong".equals(name) || "AtomicBool".equals(name)) {
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
        FieldSymbol fs = field.get();
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
        return fs.type();
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
        if (target instanceof NativePtrType && source instanceof NativePtrType) {
            provenances.put(c, provenances.getOrDefault(c.expression(), PointerProvenance.UNKNOWN));
            return target;
        }
        if (target instanceof NativePtrType && source instanceof ErrorType) {
            return target;
        }
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
            case BYTE -> PrimitiveType.BYTE;
            case SHORT -> PrimitiveType.SHORT;
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case FLOAT -> PrimitiveType.FLOAT;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case CHAR -> PrimitiveType.CHAR;
            case NATIVE_PTR -> resolveNativePtrType(typeName, loc);
            case CLASS -> {
                if ("NativePtr".equals(typeName.name())) {
                    yield NativePtrType.VOID;
                }
                NativeStructSymbol nativeStruct = nativeStructs.get(typeName.name());
                if (nativeStruct != null) {
                    yield nativeStruct.asType();
                }
                ClassSymbol symbol = classes.get(typeName.name());
                if (symbol == null) {
                    reporter.error(loc, "undefined class '" + typeName.name() + "'");
                    yield ErrorType.INSTANCE;
                }
                yield symbol.asType();
            }
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
