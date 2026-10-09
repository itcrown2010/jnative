package io.jnative.ir;

import io.jnative.ast.*;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.generic.GenericLowering;
import io.jnative.ffi.AbiType;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.semantic.ArrayLengthSymbol;
import io.jnative.semantic.ArrayType;
import io.jnative.semantic.Boxing;
import io.jnative.semantic.CallableBinding;
import io.jnative.generic.TypeSubstitution;
import io.jnative.mangle.NameMangler;
import io.jnative.semantic.ClassMetadata;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ClosureCapture;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.FunctionType;
import io.jnative.semantic.InterfaceCallTarget;
import io.jnative.semantic.InterfaceMetadata;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.NativeCallbackSite;
import io.jnative.semantic.NativeIntrinsic;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructFieldSymbol;
import io.jnative.semantic.NativeStructSymbol;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.EnhancedForDesugar;
import io.jnative.semantic.ErrorType;
import io.jnative.semantic.InterfaceType;
import io.jnative.semantic.ParameterizedType;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.TypeVariable;
import io.jnative.semantic.WildcardType;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.semantic.VariableSymbol;
import io.jnative.semantic.VoidType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers typed AST to JNative IR (0.4 runtime foundation).
 * @author 张晓达
 */
public final class IrGenerator {

    private TypedCompilationUnit typed;
    private IrBuilder builder;
    private ClassSymbol currentClass;
    private MethodSymbol currentMethod;
    private ConstructorSymbol currentConstructor;
    private IrFunction currentFunction;
    private final Map<String, IrFunction> functionsByMangled = new LinkedHashMap<>();
    private final Deque<TryFrame> tryFrames = new ArrayDeque<>();
    /** Innermost loop (or future switch) break/continue targets. */
    private final Deque<LoopFrame> loopFrames = new ArrayDeque<>();
    /** Abrupt branch targets registered for break/continue × finally dispatch. */
    private final List<IrBasicBlock> abruptTargets = new ArrayList<>();
    /** Number of finally blocks currently being lowered (return-in-finally must not re-enter). */
    private int finallyDepth;
    /** Resume-state id generator for await sites in the current async method. */
    private int awaitStateCounter;
    /** Async frame temp produced by {@link IrInstruction.AsyncBegin}. */
    private IrValue asyncFrame;
    private final SourceLocationMapper locationMapper;
    private boolean emitSourceDebug;

    public IrGenerator() {
        this(SourceLocationMapper.identity(), false);
    }

    public IrGenerator(SourceLocationMapper locationMapper, boolean emitSourceDebug) {
        this.locationMapper = locationMapper == null ? SourceLocationMapper.identity() : locationMapper;
        this.emitSourceDebug = emitSourceDebug;
    }

    private record TryFrame(
            IrBasicBlock dispatchBlock,
            IrBasicBlock afterBlock,
            IrBasicBlock finallyBlock,
            List<CatchClause> catches,
            /** Exception deferred while running finally; restored if finally completes normally. */
            IrValue.Local savedException
    ) {
        private TryFrame(
                IrBasicBlock dispatchBlock,
                IrBasicBlock afterBlock,
                IrBasicBlock finallyBlock,
                List<CatchClause> catches
        ) {
            this(dispatchBlock, afterBlock, finallyBlock, catches, null);
        }
    }

    /**
     * Control-flow target for break/continue.
     * Loops provide both break and continue; switches provide break only ({@code continueTarget == null}).
     */
    private record LoopFrame(
            IrBasicBlock breakTarget,
            IrBasicBlock continueTarget,
            int breakTargetId,
            int continueTargetId,
            int tryDepthAtEntry,
            boolean isSwitch
    ) {
        private LoopFrame(
                IrBasicBlock breakTarget,
                IrBasicBlock continueTarget,
                int breakTargetId,
                int continueTargetId,
                int tryDepthAtEntry
        ) {
            this(breakTarget, continueTarget, breakTargetId, continueTargetId, tryDepthAtEntry, false);
        }
    }

    public IrModule generate(TypedCompilationUnit typed) {
        this.typed = typed;
        CompilationUnit unit = typed.unit();
        IrModule module = new IrModule(unit.fileName());
        if (emitSourceDebug) {
            module.debugInfo().setEnabled(true);
            String abs = locationMapper.userFile();
            if (abs != null && !abs.isEmpty()) {
                java.nio.file.Path p = java.nio.file.Path.of(abs);
                java.nio.file.Path parent = p.getParent();
                module.debugInfo().setCompileUnit(
                        parent == null ? "" : parent.toString(),
                        p.getFileName().toString());
            } else {
                module.debugInfo().setCompileUnit("", unit.fileName());
            }
        }
        builder = new IrBuilder(module);

        for (ClassSymbol cls : typed.classes().values()) {
            if (cls.layout() != null) {
                module.addClassLayout(cls.layout());
            }
            if (cls.vtable() != null) {
                module.addVTable(cls.vtable());
            }
            if (cls.metadata() != null) {
                module.addClassMetadata(cls.metadata());
            }
            for (var itable : cls.itables()) {
                module.addITable(itable);
            }
        }
        for (var iface : typed.interfaces().values()) {
            module.addInterfaceMetadata(iface.metadata());
        }
        for (ClassMetadata meta : typed.arrayMetadatas().values()) {
            module.addArrayMetadata(meta);
        }
        for (NativeStructSymbol ns : typed.nativeStructs().values()) {
            if (ns.layout() != null) {
                module.addNativeStructLayout(ns.layout());
            }
        }
        for (ClassSymbol cls : typed.classes().values()) {
            for (FieldSymbol field : cls.declaredFields()) {
                if (!field.isStatic()) {
                    continue;
                }
                IrType ft = IrType.from(field.type());
                module.addStaticField(new IrModule.StaticField(
                        field.staticGlobal(), ft, field.isVolatile(), field.isReference()));
            }
        }

        Map<MethodSymbol, MethodDeclaration> methodAst = new HashMap<>();
        Map<ConstructorSymbol, MethodDeclaration> ctorAst = new HashMap<>();

        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol classSymbol = typed.classes().get(cls.qualifiedName());
            if (classSymbol == null) {
                classSymbol = typed.classes().get(cls.name());
            }
            if (classSymbol == null) {
                continue;
            }
            registerClassFunctions(module, cls, classSymbol, methodAst, ctorAst);
        }

        for (ClassSymbol spec : typed.classes().values()) {
            if (!spec.isSpecialized() || spec.genericOrigin() == null) {
                continue;
            }
            ClassDeclaration originDecl = findClassDeclaration(unit, spec.genericOrigin().name());
            if (originDecl == null) {
                continue;
            }
            registerClassFunctions(module, originDecl, spec, methodAst, ctorAst);
        }

        // Emit ITable/vtable entry points for SAM adapter methods (InvokeCallable thunks).
        for (ClassSymbol cls : typed.classes().values()) {
            for (MethodSymbol ms : cls.declaredMethods()) {
                if (ms.samInvokeRuntime() == null || functionsByMangled.containsKey(ms.mangledName())) {
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                params.add(new IrValue.Param("this", IrType.PTR));
                for (var p : ms.parameters()) {
                    params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                }
                IrFunction function = new IrFunction(
                        ms.mangledName(), IrType.from(ms.returnType()), params, false);
                module.addFunction(function);
                functionsByMangled.put(ms.mangledName(), function);
                lowerSamInvokeThunk(function, ms);
            }
        }

        // Implicit constructors without AST (includes enum synthetic constructors)
        for (ClassSymbol cls : typed.classes().values()) {
            if (cls.isBuiltin()) {
                continue;
            }
            for (ConstructorSymbol ctor : cls.constructors()) {
                if (functionsByMangled.containsKey(ctor.mangledName())) {
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                params.add(new IrValue.Param("this", IrType.PTR));
                for (var p : ctor.parameters()) {
                    params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                }
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                if (cls.isEnum()) {
                    lowerEnumConstructor(function, ctor);
                } else {
                    lowerImplicitConstructor(function, ctor);
                }
            }
            if (cls.isEnum()) {
                lowerEnumMethods(module, cls);
            }
        }

        for (var entry : methodAst.entrySet()) {
            MethodSymbol ms = entry.getKey();
            currentClass = ms.classOwner();
            if (currentClass == null) {
                continue;
            }
            currentMethod = ms;
            currentConstructor = null;
            lowerMethod(functionsByMangled.get(ms.mangledName()), entry.getValue(), ms);
        }

        for (var entry : ctorAst.entrySet()) {
            ConstructorSymbol ctor = entry.getKey();
            currentClass = ctor.owner();
            currentMethod = null;
            currentConstructor = ctor;
            lowerConstructor(functionsByMangled.get(ctor.mangledName()), entry.getValue(), ctor);
        }

        lowerBuiltinObjectMethods(module, typed);
        lowerBuiltinStringObjectMethods(module, typed);
        lowerBuiltinThrowableMethods(module, typed);
        lowerDelegatingBuiltinConstructors(module, typed);
        lowerCallableFunctions(module, typed);
        lowerThreadBuiltins(module, typed);
        lowerStaticClinit(module, typed);

        currentClass = null;
        currentMethod = null;
        currentConstructor = null;
        return module;
    }

    private void lowerStaticClinit(IrModule module, TypedCompilationUnit typed) {
        IrFunction clinit = new IrFunction("jnative_clinit", IrType.VOID, List.of(), false);
        module.addFunction(clinit);
        builder.beginFunction(clinit);
        for (ClassSymbol cls : typed.classes().values()) {
            currentClass = cls;
            currentMethod = null;
            if (cls.isEnum()) {
                lowerEnumConstantInit(cls);
            }
            for (FieldSymbol field : cls.declaredFields()) {
                if (!field.isStatic() || field.initializer() == null) {
                    continue;
                }
                IrValue value = lowerExpression(field.initializer());
                IrValue casted = castIfNeeded(value, IrType.from(field.type()));
                builder.storeStatic(field.staticGlobal(), casted, field.isVolatile());
            }
        }
        builder.retVoid();
    }

    /** Allocate each enum constant as a managed object and store into its static field (GC root). */
    private void lowerEnumConstantInit(ClassSymbol cls) {
        ConstructorSymbol ctor = cls.constructors().getFirst();
        String llvmType = cls.layout().llvmTypeName();
        long size = cls.layout().instanceSize();
        String meta = cls.metadata().globalName();
        int ordinal = 0;
        for (String name : cls.enumConstants()) {
            FieldSymbol field = cls.findField(name).orElseThrow();
            IrValue obj = builder.newObject(cls.name(), llvmType, size, meta);
            IrValue.GlobalString gs = builder.stringConstant(name);
            int len = name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            IrValue nameStr = builder.stringLiteral(gs, len);
            builder.constructorCall(ctor.mangledName(),
                    List.of(obj, nameStr, new IrValue.Const(IrType.I32, ordinal)));
            builder.storeStatic(field.staticGlobal(), obj, false);
            ordinal++;
        }
    }

    private void lowerThreadBuiltins(IrModule module, TypedCompilationUnit typed) {
        ClassSymbol thread = typed.classes().get("Thread");
        if (thread != null) {
            thread.findMethod("run").ifPresent(ms -> {
                if (functionsByMangled.containsKey(ms.mangledName())) {
                    return;
                }
                IrFunction function = new IrFunction(
                        ms.mangledName(), IrType.VOID, List.of(new IrValue.Param("this", IrType.PTR)), false);
                module.addFunction(function);
                functionsByMangled.put(ms.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                IrValue thisPtr = builder.load(thisLocal);
                FieldSymbol targetField = thread.findField("target").orElseThrow();
                IrValue target = builder.loadField(
                        thisPtr,
                        thread.name(),
                        thread.layout().llvmTypeName(),
                        targetField.gepIndex(),
                        IrType.PTR);
                IrValue hasTarget = builder.compare(
                        IrInstruction.CmpOp.NE, target, new IrValue.Const(IrType.PTR, "null"));
                IrBasicBlock invokeBlock = builder.newBlock("run.target");
                IrBasicBlock endBlock = builder.newBlock("run.end");
                builder.condBranch(hasTarget, invokeBlock, endBlock);
                builder.setBlock(invokeBlock);
                builder.invokeCallable(target, IrType.VOID, List.of());
                builder.branch(endBlock);
                builder.setBlock(endBlock);
                builder.retVoid();
            });
            for (ConstructorSymbol ctor : thread.constructors()) {
                if (functionsByMangled.containsKey(ctor.mangledName())) {
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                params.add(new IrValue.Param("this", IrType.PTR));
                if (!ctor.parameters().isEmpty()) {
                    params.add(new IrValue.Param("target", IrType.PTR));
                }
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                if (!ctor.parameters().isEmpty()) {
                    IrValue.Local targetLocal = builder.alloca("target", IrType.PTR);
                    builder.store(new IrValue.Param("target", IrType.PTR), targetLocal);
                    FieldSymbol targetField = thread.findField("target").orElseThrow();
                    builder.storeField(
                            builder.load(thisLocal),
                            thread.name(),
                            thread.layout().llvmTypeName(),
                            targetField.gepIndex(),
                            builder.load(targetLocal));
                }
                builder.retVoid();
            }
        }
        for (String name : List.of("Mutex", "Condition")) {
            ClassSymbol cls = typed.classes().get(name);
            if (cls == null) {
                continue;
            }
            for (ConstructorSymbol ctor : cls.constructors()) {
                if (functionsByMangled.containsKey(ctor.mangledName())) {
                    continue;
                }
                IrFunction function = new IrFunction(
                        ctor.mangledName(), IrType.VOID, List.of(new IrValue.Param("this", IrType.PTR)), false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                String init = name.equals("Mutex") ? "jnative_mutex_init" : "jnative_cond_init";
                builder.call(init, IrType.VOID, List.of(builder.load(thisLocal)), false);
                builder.retVoid();
            }
        }
        lowerAtomicCtor(module, typed, "AtomicInt", "jnative_atomic_int_init", IrType.I32);
        lowerAtomicCtor(module, typed, "AtomicLong", "jnative_atomic_long_init", IrType.I64);
        lowerAtomicCtor(module, typed, "AtomicBool", "jnative_atomic_bool_init", IrType.I1);
    }

    private void lowerAtomicCtor(
            IrModule module, TypedCompilationUnit typed, String className, String initFn, IrType valueTy
    ) {
        ClassSymbol cls = typed.classes().get(className);
        if (cls == null) {
            return;
        }
        for (ConstructorSymbol ctor : cls.constructors()) {
            if (functionsByMangled.containsKey(ctor.mangledName())) {
                continue;
            }
            List<IrValue.Param> params = new ArrayList<>();
            params.add(new IrValue.Param("this", IrType.PTR));
            if (!ctor.parameters().isEmpty()) {
                params.add(new IrValue.Param("value", valueTy));
            }
            IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
            module.addFunction(function);
            functionsByMangled.put(ctor.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            IrValue val;
            if (ctor.parameters().isEmpty()) {
                val = new IrValue.Const(valueTy, "0");
            } else {
                IrValue.Local vLocal = builder.alloca("value", valueTy);
                builder.store(new IrValue.Param("value", valueTy), vLocal);
                val = builder.load(vLocal);
            }
            builder.call(initFn, IrType.VOID, List.of(builder.load(thisLocal), val), false);
            builder.retVoid();
        }
    }

    private void lowerBuiltinObjectMethods(IrModule module, TypedCompilationUnit typed) {
        ClassSymbol object = typed.classes().get("Object");
        if (object == null) {
            return;
        }
        object.findMethod("equals").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            List<IrValue.Param> params = List.of(
                    new IrValue.Param("this", IrType.PTR),
                    new IrValue.Param("other", IrType.PTR));
            IrFunction function = new IrFunction(ms.mangledName(), IrType.I1, params, false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            IrValue.Local otherLocal = builder.alloca("other", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            builder.store(new IrValue.Param("other", IrType.PTR), otherLocal);
            IrValue eq = builder.compare(
                    IrInstruction.CmpOp.EQ,
                    builder.load(thisLocal),
                    builder.load(otherLocal));
            builder.ret(eq);
        });
        object.findMethod("hashCode").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.I32, List.of(new IrValue.Param("this", IrType.PTR)), false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            IrValue h = builder.call(
                    "jnative_identity_hashcode", IrType.I32, List.of(builder.load(thisLocal)));
            builder.ret(h);
        });
    }

    private void lowerBuiltinStringObjectMethods(IrModule module, TypedCompilationUnit typed) {
        ClassSymbol string = typed.classes().get("String");
        if (string == null) {
            return;
        }
        string.findMethod("equals").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            List<IrValue.Param> params = List.of(
                    new IrValue.Param("this", IrType.PTR),
                    new IrValue.Param("other", IrType.PTR));
            IrFunction function = new IrFunction(ms.mangledName(), IrType.I1, params, false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            IrValue.Local otherLocal = builder.alloca("other", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            builder.store(new IrValue.Param("other", IrType.PTR), otherLocal);
            IrValue result = builder.call(
                    "jnative_string_equals_object",
                    IrType.I1,
                    List.of(builder.load(thisLocal), builder.load(otherLocal)));
            builder.ret(result);
        });
        string.findMethod("hashCode").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.I32, List.of(new IrValue.Param("this", IrType.PTR)), false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            IrValue result = builder.call(
                    "jnative_string_hashcode",
                    IrType.I32,
                    List.of(builder.load(thisLocal)));
            builder.ret(result);
        });
    }

    private void lowerBuiltinThrowableMethods(IrModule module, TypedCompilationUnit typed) {
        ClassSymbol throwable = typed.classes().get("Throwable");
        if (throwable == null) {
            return;
        }
        ClassLayout layout = throwable.layout();
        int messageIdx = throwable.findField("message").map(f -> f.gepIndex()).orElse(1);
        int causeIdx = throwable.findField("cause").map(f -> f.gepIndex()).orElse(2);
        for (ConstructorSymbol ctor : throwable.constructors()) {
            if (functionsByMangled.containsKey(ctor.mangledName())) {
                continue;
            }
            if (ctor.isImplicit()) {
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID,
                        List.of(new IrValue.Param("this", IrType.PTR)), false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                builder.retVoid();
                continue;
            }
            int arity = ctor.parameters().size();
            if (arity == 1) {
                List<IrValue.Param> params = List.of(
                        new IrValue.Param("this", IrType.PTR),
                        new IrValue.Param("message", IrType.PTR));
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                IrValue.Local msgLocal = builder.alloca("message", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                builder.store(new IrValue.Param("message", IrType.PTR), msgLocal);
                builder.storeField(
                        builder.load(thisLocal),
                        throwable.name(),
                        layout.llvmTypeName(),
                        messageIdx,
                        builder.load(msgLocal));
                builder.retVoid();
            } else if (arity == 2) {
                List<IrValue.Param> params = List.of(
                        new IrValue.Param("this", IrType.PTR),
                        new IrValue.Param("message", IrType.PTR),
                        new IrValue.Param("cause", IrType.PTR));
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                IrValue.Local msgLocal = builder.alloca("message", IrType.PTR);
                IrValue.Local causeLocal = builder.alloca("cause", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                builder.store(new IrValue.Param("message", IrType.PTR), msgLocal);
                builder.store(new IrValue.Param("cause", IrType.PTR), causeLocal);
                IrValue thisPtr = builder.load(thisLocal);
                builder.storeField(
                        thisPtr, throwable.name(), layout.llvmTypeName(), messageIdx, builder.load(msgLocal));
                builder.storeField(
                        thisPtr, throwable.name(), layout.llvmTypeName(), causeIdx, builder.load(causeLocal));
                builder.retVoid();
            }
        }

        throwable.findMethod("getMessage").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.PTR, List.of(new IrValue.Param("this", IrType.PTR)), false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            IrValue msg = builder.loadField(
                    builder.load(thisLocal),
                    throwable.name(),
                    layout.llvmTypeName(),
                    messageIdx,
                    IrType.PTR);
            builder.ret(msg);
        });
        throwable.findMethod("getCause").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.PTR, List.of(new IrValue.Param("this", IrType.PTR)), false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            IrValue cause = builder.loadField(
                    builder.load(thisLocal),
                    throwable.name(),
                    layout.llvmTypeName(),
                    causeIdx,
                    IrType.PTR);
            builder.ret(cause);
        });
        throwable.findMethod("addSuppressed").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.VOID,
                    List.of(new IrValue.Param("this", IrType.PTR),
                            new IrValue.Param("exception", IrType.PTR)),
                    false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            builder.call("jnative_throwable_add_suppressed", IrType.VOID,
                    List.of(new IrValue.Param("this", IrType.PTR),
                            new IrValue.Param("exception", IrType.PTR)),
                    false);
            builder.retVoid();
        });
        throwable.findMethod("getSuppressed").ifPresent(ms -> {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                return;
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.PTR, List.of(new IrValue.Param("this", IrType.PTR)), false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            builder.beginFunction(function);
            IrValue arr = builder.call("jnative_throwable_get_suppressed", IrType.PTR,
                    List.of(new IrValue.Param("this", IrType.PTR)), false);
            builder.ret(arr);
        });
    }

    private void lowerDelegatingBuiltinConstructors(IrModule module, TypedCompilationUnit typed) {
        // Object root constructor (empty).
        ClassSymbol object = typed.classes().get("Object");
        if (object != null) {
            for (ConstructorSymbol ctor : object.constructors()) {
                if (functionsByMangled.containsKey(ctor.mangledName())) {
                    continue;
                }
                IrFunction function = new IrFunction(
                        ctor.mangledName(), IrType.VOID,
                        List.of(new IrValue.Param("this", IrType.PTR)), false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                builder.retVoid();
            }
        }
        for (ClassSymbol cls : typed.classes().values()) {
            if (!cls.isBuiltin() || cls.name().equals("Throwable") || cls.name().equals("String")
                    || cls.name().equals("Object")) {
                continue;
            }
            for (ConstructorSymbol ctor : cls.constructors()) {
                if (ctor.delegatesTo() == null) {
                    continue;
                }
                if (functionsByMangled.containsKey(ctor.mangledName())) {
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                params.add(new IrValue.Param("this", IrType.PTR));
                for (ParameterSymbol p : ctor.parameters()) {
                    params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                }
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                builder.beginFunction(function);
                IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
                builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
                List<IrValue> args = new ArrayList<>();
                args.add(builder.load(thisLocal));
                for (ParameterSymbol p : ctor.parameters()) {
                    IrValue.Local local = builder.alloca(p.name(), IrType.from(p.type()));
                    builder.store(new IrValue.Param(p.name(), IrType.from(p.type())), local);
                    args.add(builder.load(local));
                }
                builder.constructorCall(ctor.delegatesTo().mangledName(), args);
                builder.retVoid();
            }
        }
    }

    private void registerClassFunctions(
            IrModule module,
            ClassDeclaration cls,
            ClassSymbol classSymbol,
            Map<MethodSymbol, MethodDeclaration> methodAst,
            Map<ConstructorSymbol, MethodDeclaration> ctorAst
    ) {
        for (MethodDeclaration method : cls.methods()) {
            if (method.isConstructor()) {
                ConstructorSymbol ctor = classSymbol.isSpecialized()
                        ? findCtorByArity(classSymbol, method)
                        : findCtor(classSymbol, method);
                if (ctor == null) {
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                params.add(new IrValue.Param("this", IrType.PTR));
                for (var p : ctor.parameters()) {
                    params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                }
                IrFunction function = new IrFunction(
                        ctor.mangledName(), IrType.VOID, params, false);
                module.addFunction(function);
                functionsByMangled.put(ctor.mangledName(), function);
                ctorAst.put(ctor, method);
            } else {
                /* Match overload by parameter types — findMethod(name) returns only the first. */
                MethodSymbol ms = findMethodForAst(classSymbol, method);
                if (ms == null) {
                    continue;
                }
                if (ms.isAbstract()) {
                    continue;
                }
                if (ms.isNative()) {
                    if (ms.nativeFunction() != null) {
                        module.addNativeDeclaration(ms.nativeFunction());
                    }
                    continue;
                }
                if (functionsByMangled.containsKey(ms.mangledName())) {
                    methodAst.put(ms, method);
                    continue;
                }
                List<IrValue.Param> params = new ArrayList<>();
                if (!ms.isStatic()) {
                    params.add(new IrValue.Param("this", IrType.PTR));
                }
                for (var p : ms.parameters()) {
                    params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                }
                IrFunction function = new IrFunction(
                        ms.mangledName(), IrType.from(ms.returnType()), params, ms.isMain());
                function.setAsync(ms.isAsync());
                configureEffectFlags(function, ms);
                module.addFunction(function);
                functionsByMangled.put(ms.mangledName(), function);
                methodAst.put(ms, method);
            }
        }
    }

    private ClassDeclaration findClassDeclaration(CompilationUnit unit, String name) {
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.qualifiedName().equals(name) || cls.name().equals(name)) {
                return cls;
            }
        }
        return null;
    }

    private MethodSymbol findMethodForAst(ClassSymbol owner, MethodDeclaration method) {
        List<MethodSymbol> overs = owner.findDeclaredMethods(method.name());
        if (overs.isEmpty()) {
            return owner.findMethod(method.name()).orElse(null);
        }
        /* Common case: no overloads — same as legacy findMethod(name). */
        if (overs.size() == 1) {
            return overs.get(0);
        }
        List<JType> pts = new ArrayList<>();
        for (Parameter p : method.parameters()) {
            pts.add(resolveTypeNameInOwner(p.type(), p.isArray(), owner));
        }
        Optional<MethodSymbol> exact = owner.findMethod(method.name(), pts);
        if (exact.isPresent()) {
            return exact.get();
        }
        /* Specialized generics: AST type vars vs substituted params — match arity. */
        List<MethodSymbol> arity = new ArrayList<>();
        for (MethodSymbol m : overs) {
            if (m.parameters().size() == method.parameters().size()) {
                arity.add(m);
            }
        }
        if (arity.size() == 1) {
            return arity.get(0);
        }
        if (!arity.isEmpty()) {
            return arity.get(0);
        }
        return overs.get(0);
    }

    private ConstructorSymbol findCtor(ClassSymbol owner, MethodDeclaration method) {
        List<JType> pts = new ArrayList<>();
        for (Parameter p : method.parameters()) {
            pts.add(resolveTypeNameInOwner(p.type(), p.isArray(), owner));
        }
        for (ConstructorSymbol c : owner.constructors()) {
            if (c.parameterTypes().equals(pts)) {
                return c;
            }
        }
        // Generic classes: AST may spell type variables as CLASS names; match by arity.
        if (owner.isGeneric() || owner.isSpecialized()) {
            return findCtorByArity(owner, method);
        }
        return null;
    }

    private JType resolveTypeNameInOwner(TypeName typeName, boolean isArray, ClassSymbol owner) {
        if (typeName.kind() == TypeName.Kind.CLASS
                && typeName.typeArguments().isEmpty()
                && owner != null) {
            for (var tp : owner.typeParameters()) {
                if (tp.name().equals(typeName.name())) {
                    JType tv = tp.asType();
                    return isArray ? new ArrayType(tv) : tv;
                }
            }
            if (owner.genericOrigin() != null) {
                for (var tp : owner.genericOrigin().typeParameters()) {
                    if (tp.name().equals(typeName.name())) {
                        JType tv = tp.asType();
                        return isArray ? new ArrayType(tv) : tv;
                    }
                }
            }
        }
        return resolveTypeName(typeName, isArray);
    }

    private ConstructorSymbol findCtorByArity(ClassSymbol owner, MethodDeclaration method) {
        int arity = method.parameters().size();
        for (ConstructorSymbol c : owner.constructors()) {
            if (c.parameters().size() == arity) {
                return c;
            }
        }
        return null;
    }

    private JType resolveTypeName(TypeName typeName, boolean isArray) {
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
            case NATIVE_PTR -> {
                TypeName arg = typeName.typeArgument();
                if (arg == null) {
                    yield NativePtrType.VOID;
                }
                JType pointee = resolveTypeName(arg, false);
                yield NativePtrType.of(pointee);
            }
            case WILDCARD -> typed.classes().get("Object").asType();
            case TYPE_VAR -> typed.classes().get("Object").asType();
            case CLASS -> resolveClassTypeName(typeName);
        };
        return isArray ? new ArrayType(base) : base;
    }

    private JType resolveClassTypeName(TypeName typeName) {
        if ("NativePtr".equals(typeName.name())) {
            return NativePtrType.VOID;
        }
        String resolved = resolveTypeKey(typeName.name());
        NativeStructSymbol ns = typed.nativeStructs().get(resolved);
        if (ns == null) {
            ns = typed.nativeStructs().get(typeName.name());
        }
        if (ns != null) {
            return ns.asType();
        }
        List<JType> args = resolveTypeArguments(typeName.typeArguments());
        ClassSymbol cs = typed.classes().get(resolved);
        if (cs == null) {
            cs = typed.classes().get(typeName.name());
        }
        if (cs != null) {
            if (!args.isEmpty() && cs.isGeneric()) {
                ParameterizedType pt = new ParameterizedType(cs, args);
                if (pt.needsPrimitiveSpecialization()) {
                    ClassSymbol spec = typed.classes().get(GenericLowering.specializedName(cs, args));
                    if (spec != null) {
                        return spec.asType();
                    }
                }
            }
            return cs.asType();
        }
        var iface = typed.interfaces().get(resolved);
        if (iface == null) {
            iface = typed.interfaces().get(typeName.name());
        }
        if (iface != null) {
            if (!args.isEmpty() && iface.isGeneric()) {
                return iface.asType();
            }
            return iface.asType();
        }
        return ErrorType.INSTANCE;
    }

    /**
     * Map a source type name to the FQN key used in {@link TypedCompilationUnit} tables,
     * using the current class's language package and explicit imports.
     */
    private String resolveTypeKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (raw.contains(".")) {
            return raw;
        }
        if (currentClass != null) {
            for (String imp : currentClass.importTypes()) {
                int dot = imp.lastIndexOf('.');
                String simple = dot < 0 ? imp : imp.substring(dot + 1);
                if (simple.equals(raw)) {
                    return imp;
                }
            }
            String pkg = currentClass.packageName();
            if (pkg != null && !pkg.isEmpty()) {
                String samePkg = pkg + "." + raw;
                if (typed.classes().containsKey(samePkg)
                        || typed.interfaces().containsKey(samePkg)
                        || typed.nativeStructs().containsKey(samePkg)) {
                    return samePkg;
                }
            }
        }
        return raw;
    }

    private List<JType> resolveTypeArguments(List<TypeName> typeArgs) {
        if (typeArgs == null || typeArgs.isEmpty()) {
            return List.of();
        }
        List<JType> out = new ArrayList<>();
        for (TypeName arg : typeArgs) {
            out.add(resolveTypeName(arg, false));
        }
        return out;
    }

    private void lowerImplicitConstructor(IrFunction function, ConstructorSymbol ctor) {
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        emitDelegation(ctor, thisLocal);
        if (ctor.delegatesTo() == null || ctor.delegatesToSuper()) {
            emitInstanceFieldInitializers(ctor.owner(), thisLocal);
        }
        builder.retVoid();
    }

    private void lowerEnumConstructor(IrFunction function, ConstructorSymbol ctor) {
        ClassSymbol cls = ctor.owner();
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        IrValue thisPtr = builder.load(thisLocal);
        FieldSymbol nameField = cls.findField("$name").orElseThrow();
        FieldSymbol ordinalField = cls.findField("$ordinal").orElseThrow();
        builder.storeField(thisPtr, cls.name(), cls.layout().llvmTypeName(),
                nameField.gepIndex(), new IrValue.Param("$name", IrType.PTR));
        builder.storeField(thisPtr, cls.name(), cls.layout().llvmTypeName(),
                ordinalField.gepIndex(), new IrValue.Param("$ordinal", IrType.I32));
        builder.retVoid();
    }

    private void lowerEnumMethods(IrModule module, ClassSymbol cls) {
        for (MethodSymbol ms : cls.declaredMethods()) {
            if (functionsByMangled.containsKey(ms.mangledName())) {
                continue;
            }
            List<IrValue.Param> params = new ArrayList<>();
            if (!ms.isStatic()) {
                params.add(new IrValue.Param("this", IrType.PTR));
            }
            for (var p : ms.parameters()) {
                params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
            }
            IrFunction function = new IrFunction(
                    ms.mangledName(), IrType.from(ms.returnType()), params, false);
            module.addFunction(function);
            functionsByMangled.put(ms.mangledName(), function);
            switch (ms.name()) {
                case "name" -> lowerEnumName(function, cls);
                case "ordinal" -> lowerEnumOrdinal(function, cls);
                case "values" -> lowerEnumValues(function, cls);
                case "valueOf" -> lowerEnumValueOf(function, cls);
                default -> throw new IllegalStateException("unexpected enum method " + ms.name());
            }
        }
    }

    private void lowerEnumName(IrFunction function, ClassSymbol cls) {
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        IrValue thisPtr = builder.load(thisLocal);
        FieldSymbol nameField = cls.findField("$name").orElseThrow();
        builder.ret(builder.loadField(
                thisPtr, cls.name(), cls.layout().llvmTypeName(),
                nameField.gepIndex(), IrType.PTR));
    }

    private void lowerEnumOrdinal(IrFunction function, ClassSymbol cls) {
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        IrValue thisPtr = builder.load(thisLocal);
        FieldSymbol ordinalField = cls.findField("$ordinal").orElseThrow();
        builder.ret(builder.loadField(
                thisPtr, cls.name(), cls.layout().llvmTypeName(),
                ordinalField.gepIndex(), IrType.I32));
    }

    private void lowerEnumValues(IrFunction function, ClassSymbol cls) {
        builder.beginFunction(function);
        int n = cls.enumConstants().size();
        String metaKey = io.jnative.mangle.NameMangler.arrayMetaName(cls.asType());
        ClassMetadata arrayMeta = typed.arrayMetadatas().get(metaKey);
        if (arrayMeta == null) {
            arrayMeta = new ClassMetadata(metaKey, true, cls.asType());
        }
        builder.module().addArrayMetadata(arrayMeta);
        IrValue length = new IrValue.Const(IrType.I32, n);
        IrValue arr = builder.newArray(IrType.PTR, length, 8, arrayMeta.globalName(), true);
        for (int i = 0; i < n; i++) {
            String cname = cls.enumConstants().get(i);
            FieldSymbol field = cls.findField(cname).orElseThrow();
            IrValue elem = builder.loadStatic(field.staticGlobal(), IrType.PTR, false);
            builder.storeElement(arr, new IrValue.Const(IrType.I32, i), elem);
        }
        builder.ret(arr);
    }

    private void lowerEnumValueOf(IrFunction function, ClassSymbol cls) {
        builder.beginFunction(function);
        IrValue.Local nameLocal = builder.alloca("name", IrType.PTR);
        builder.store(new IrValue.Param("name", IrType.PTR), nameLocal);
        IrValue namePtr = builder.load(nameLocal);
        IrBasicBlock nullBlock = builder.newBlock("valueOf.null");
        IrBasicBlock chain = builder.newBlock("valueOf.chain0");
        IrValue isNull = builder.compare(
                IrInstruction.CmpOp.EQ, namePtr, new IrValue.Const(IrType.PTR, "null"));
        builder.condBranch(isNull, nullBlock, chain);
        builder.setBlock(nullBlock);
        builder.call("jnative_throw_npe", IrType.VOID, List.of(), false);
        builder.ret(new IrValue.Const(IrType.PTR, "null"));

        List<String> constants = cls.enumConstants();
        IrBasicBlock current = chain;
        for (int i = 0; i < constants.size(); i++) {
            builder.setBlock(current);
            String cname = constants.get(i);
            FieldSymbol field = cls.findField(cname).orElseThrow();
            IrValue constant = builder.loadStatic(field.staticGlobal(), IrType.PTR, false);
            FieldSymbol nameField = cls.findField("$name").orElseThrow();
            IrValue constName = builder.loadField(
                    constant, cls.name(), cls.layout().llvmTypeName(),
                    nameField.gepIndex(), IrType.PTR);
            IrValue eq = i32ToI1(builder.call("jnative_string_equals", IrType.I32,
                    List.of(builder.load(nameLocal), constName), false));
            IrBasicBlock matched = builder.newBlock("valueOf.m" + i);
            IrBasicBlock next = (i + 1 < constants.size())
                    ? builder.newBlock("valueOf.chain" + (i + 1))
                    : builder.newBlock("valueOf.nf");
            builder.condBranch(eq, matched, next);
            builder.setBlock(matched);
            builder.ret(constant);
            current = next;
        }
        builder.setBlock(current);
        builder.call("jnative_throw_illegal_argument", IrType.VOID, List.of(), false);
        builder.ret(new IrValue.Const(IrType.PTR, "null"));
    }

    private void lowerConstructor(IrFunction function, MethodDeclaration method, ConstructorSymbol ctor) {
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        for (var p : ctor.parameters()) {
            IrType irType = IrType.from(p.type());
            IrValue.Local local = builder.alloca(p.name(), irType);
            builder.store(new IrValue.Param(p.name(), irType), local);
        }

        List<Statement> stmts = method.body().statements();
        boolean hasExplicitDelegation = !stmts.isEmpty()
                && stmts.getFirst() instanceof ConstructorDelegationStatement;

        if (hasExplicitDelegation) {
            ConstructorDelegationStatement del = (ConstructorDelegationStatement) stmts.getFirst();
            ConstructorSymbol target = ctor.delegatesTo();
            if (target != null) {
                List<IrValue> args = new ArrayList<>();
                args.add(builder.load(thisLocal));
                for (int i = 0; i < del.arguments().size(); i++) {
                    IrValue arg = lowerExpression(del.arguments().get(i));
                    if (i < target.parameters().size()) {
                        arg = castIfNeeded(arg, IrType.from(target.parameters().get(i).type()));
                    }
                    args.add(arg);
                }
                builder.constructorCall(target.mangledName(), args);
            }
            if (ctor.delegatesToSuper()) {
                emitInstanceFieldInitializers(ctor.owner(), thisLocal);
            }
            for (int i = 1; i < stmts.size(); i++) {
                lowerStatement(stmts.get(i));
            }
        } else {
            emitDelegation(ctor, thisLocal);
            if (ctor.delegatesTo() == null || ctor.delegatesToSuper()) {
                emitInstanceFieldInitializers(ctor.owner(), thisLocal);
            }
            for (Statement s : stmts) {
                lowerStatement(s);
            }
        }

        if (!builder.block().isTerminated()) {
            builder.retVoid();
        }
    }

    private void emitDelegation(ConstructorSymbol ctor, IrValue.Local thisLocal) {
        if (ctor.delegatesTo() == null) {
            return;
        }
        List<IrValue> args = new ArrayList<>();
        args.add(builder.load(thisLocal));
        builder.constructorCall(ctor.delegatesTo().mangledName(), args);
    }

    /** Run instance field initializers after super() (not after this()). */
    private void emitInstanceFieldInitializers(ClassSymbol cls, IrValue.Local thisLocal) {
        IrValue thisPtr = builder.load(thisLocal);
        ClassLayout layout = cls.layout();
        for (FieldSymbol field : cls.declaredFields()) {
            if (field.isStatic() || field.initializer() == null) {
                continue;
            }
            IrValue value = lowerExpression(field.initializer());
            builder.storeField(
                    thisPtr,
                    cls.name(),
                    layout.llvmTypeName(),
                    field.gepIndex(),
                    castIfNeeded(value, IrType.from(field.type())),
                    field.isVolatile());
        }
    }

    private static void configureEffectFlags(IrFunction function, MethodSymbol ms) {
        boolean noGc = ms.contractNoGc();
        function.setOmitSafepoints(noGc);
        function.setNoGcRegion(noGc);
    }

    private void lowerMethod(IrFunction function, MethodDeclaration method, MethodSymbol ms) {
        currentFunction = function;
        builder.beginFunction(function);
        awaitStateCounter = 0;
        asyncFrame = null;
        abruptTargets.clear();
        loopFrames.clear();

        SourceLocation methodLoc = mapLoc(method.location());
        if (methodLoc == SourceLocation.UNKNOWN && ms.location() != null) {
            methodLoc = mapLoc(ms.location());
        }
        builder.setLocation(methodLoc);
        if (emitSourceDebug && methodLoc != SourceLocation.UNKNOWN) {
            String display = ms.name();
            if (ms.owner() != null) {
                display = ms.owner().name() + "." + ms.name();
            }
            builder.module().debugInfo().setFunction(function, display, methodLoc);
        }

        if (ms.isAsync()) {
            IrValue.Temp frame = builder.temp(IrType.PTR);
            builder.emit(new IrInstruction.AsyncBegin(
                    frame, ms.mangledName(), IrType.from(ms.logicalReturnType())));
            asyncFrame = frame;
        }

        if (!ms.isStatic()) {
            builder.setLocation(methodLoc);
            String thisTy = ms.owner() != null ? ms.owner().name() : null;
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR, true, 0, thisTy);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        }
        int argIndex = ms.isStatic() ? 0 : 1;
        for (var p : ms.parameters()) {
            SourceLocation pLoc = p.location() != null ? mapLoc(p.location()) : methodLoc;
            builder.setLocation(pLoc);
            IrType irType = IrType.from(p.type());
            IrValue.Local local = builder.alloca(
                    p.name(), irType, true, argIndex++, debugTypeName(p.type()));
            builder.store(new IrValue.Param(p.name(), irType), local);
        }

        lowerStatement(method.body());

        if (!builder.block().isTerminated()) {
            if (ms.isAsync()) {
                IrValue def = defaultValue(IrType.from(ms.logicalReturnType()));
                builder.emit(new IrInstruction.AsyncComplete(def, IrType.from(ms.logicalReturnType())));
                builder.block().setTerminated(true);
            } else if (function.returnType() == IrType.VOID) {
                builder.retVoid();
            } else {
                builder.ret(defaultValue(function.returnType()));
            }
        }
        currentFunction = null;
        asyncFrame = null;
    }

    private SourceLocation mapLoc(SourceLocation loc) {
        return locationMapper.map(loc);
    }

    /** Stable Java type name for DWARF / LLDB (null for primitives). */
    private static String debugTypeName(JType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof ClassType ct) {
            return ct.name();
        }
        if (type instanceof InterfaceType it) {
            return it.name();
        }
        if (type instanceof ArrayType at) {
            String elem = debugTypeName(at.elementType());
            if (elem == null) {
                IrType ir = IrType.from(at.elementType());
                elem = switch (ir) {
                    case I1 -> "boolean";
                    case I8 -> "byte";
                    case I16 -> "short";
                    case I32 -> "int";
                    case I64 -> "long";
                    case FLOAT -> "float";
                    case DOUBLE -> "double";
                    default -> "ptr";
                };
            }
            return elem + "[]";
        }
        return null;
    }

    private void lowerStatement(Statement stmt) {
        if (builder.block().isTerminated()) {
            return;
        }
        if (stmt != null) {
            builder.setLocation(mapLoc(stmt.location()));
        }
        switch (stmt) {
            case BlockStatement b -> {
                for (Statement s : b.statements()) {
                    lowerStatement(s);
                }
            }
            case VariableDeclaration v -> {
                JType jt = resolveDeclType(v);
                if (v.stackAllocated() && jt instanceof NativeStructType nst) {
                    var layout = nst.symbol().layout();
                    IrValue.Local local = builder.stackAlloc(
                            v.name(),
                            nst.name(),
                            layout.llvmTypeName(),
                            layout.alignment(),
                            layout.size());
                    // Initializer new Point() is zero-init via StackAlloc lowering; ignore GC new.
                    if (v.initializer() != null
                            && !(v.initializer() instanceof NewObjectExpression)) {
                        throw new IllegalStateException("stack native struct init must be new Struct()");
                    }
                    break;
                }
                IrType type = IrType.from(jt);
                IrValue.Local local = builder.alloca(v.name(), type, false, -1, debugTypeName(jt));
                if (v.initializer() != null) {
                    builder.setLocation(mapLoc(v.initializer().location()));
                    IrValue value = lowerExpression(v.initializer());
                    builder.setLocation(mapLoc(v.location()));
                    JType fromJt = remapType(typed.expressionTypes().get(v.initializer()));
                    builder.store(convertTo(value, fromJt, jt), local);
                } else {
                    builder.store(defaultValue(type), local);
                }
            }
            case ExpressionStatement e -> {
                builder.setLocation(mapLoc(e.expression().location()));
                lowerExpression(e.expression());
            }
            case IfStatement i -> lowerIf(i);
            case WhileStatement w -> lowerWhile(w);
            case DoWhileStatement d -> lowerDoWhile(d);
            case ForStatement f -> lowerFor(f);
            case EnhancedForStatement ef -> lowerEnhancedFor(ef);
            case SwitchStatement s -> lowerSwitch(s);
            case BreakStatement ignored -> lowerBreak();
            case ContinueStatement ignored -> lowerContinue();
            case ReturnStatement r -> lowerReturn(r);
            case ConstructorDelegationStatement ignored -> {
                // handled in lowerConstructor
            }
            case EmptyStatement ignored -> {
            }
            case ThrowStatement t -> {
                IrValue ex = lowerExpression(t.expression());
                builder.throwException(ex);
                // throwException already checked pending; if we land here without
                // a local handler the block was terminated by unwind return.
            }
            case TryStatement t -> lowerTry(t);
        }
    }

    private int registerAbruptTarget(IrBasicBlock block) {
        int id = abruptTargets.size();
        abruptTargets.add(block);
        return id;
    }

    private void pushLoop(IrBasicBlock breakTarget, IrBasicBlock continueTarget) {
        int breakId = registerAbruptTarget(breakTarget);
        int continueId = registerAbruptTarget(continueTarget);
        loopFrames.push(new LoopFrame(
                breakTarget, continueTarget, breakId, continueId, tryFrames.size(), false));
    }

    private void pushSwitch(IrBasicBlock breakTarget) {
        int breakId = registerAbruptTarget(breakTarget);
        loopFrames.push(new LoopFrame(
                breakTarget, null, breakId, -1, tryFrames.size(), true));
    }

    private void lowerBreak() {
        if (loopFrames.isEmpty()) {
            throw new IllegalStateException("break without loop/switch frame");
        }
        LoopFrame loop = loopFrames.peek();
        TryFrame finallyTarget = innermostFinallyInsideLoop(loop);
        if (finallyTarget != null) {
            builder.setBreakCompletion(loop.breakTargetId());
            builder.branch(finallyTarget.finallyBlock());
            return;
        }
        builder.branch(loop.breakTarget());
    }

    private void lowerContinue() {
        LoopFrame loop = null;
        for (LoopFrame frame : loopFrames) {
            if (!frame.isSwitch()) {
                loop = frame;
                break;
            }
        }
        if (loop == null || loop.continueTarget() == null) {
            throw new IllegalStateException("continue without loop frame");
        }
        TryFrame finallyTarget = innermostFinallyInsideLoop(loop);
        if (finallyTarget != null) {
            builder.setContinueCompletion(loop.continueTargetId());
            builder.branch(finallyTarget.finallyBlock());
            return;
        }
        builder.branch(loop.continueTarget());
    }

    private TryFrame innermostFinallyInsideLoop(LoopFrame loop) {
        int skip = finallyDepth;
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        // frames[0] = innermost (deque head)
        for (int i = 0; i < frames.length; i++) {
            int depthFromOuter = frames.length - i;
            if (depthFromOuter <= loop.tryDepthAtEntry()) {
                break;
            }
            if (frames[i].finallyBlock() == null) {
                continue;
            }
            if (skip > 0) {
                skip--;
                continue;
            }
            return frames[i];
        }
        return null;
    }

    private void lowerReturn(ReturnStatement r) {
        TryFrame finallyTarget = innermostFinallyFrame();
        if (finallyTarget != null) {
            if (r.value() == null) {
                builder.setReturnCompletion(defaultValue(IrType.I64));
            } else {
                IrValue value = lowerExpression(r.value());
                builder.setReturnCompletion(castIfNeeded(value, builder.function().returnType()));
            }
            builder.branch(finallyTarget.finallyBlock());
            return;
        }
        if (currentMethod != null && currentMethod.isAsync()) {
            IrType logical = IrType.from(currentMethod.logicalReturnType());
            IrValue value = r.value() == null
                    ? defaultValue(logical)
                    : convertTo(lowerExpression(r.value()),
                    remapType(typed.expressionTypes().get(r.value())),
                    currentMethod.logicalReturnType());
            builder.emit(new IrInstruction.AsyncComplete(value, logical));
            // AsyncLoweringPass turns AsyncComplete into complete + return of the method Future.
            builder.block().setTerminated(true);
            return;
        }
        if (r.value() == null) {
            builder.retVoid();
        } else {
            IrValue value = lowerExpression(r.value());
            JType retJt = currentMethod != null ? currentMethod.returnType() : null;
            if (retJt != null) {
                builder.ret(convertTo(value, remapType(typed.expressionTypes().get(r.value())), retJt));
            } else {
                builder.ret(castIfNeeded(value, builder.function().returnType()));
            }
        }
    }

    private TryFrame innermostFinallyFrame() {
        // Skip finally frames we are already inside so `return` in finally does not loop.
        // frames[0] = innermost (same order as innermostFinallyInsideLoop).
        int skip = finallyDepth;
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        for (int i = 0; i < frames.length; i++) {
            if (frames[i].finallyBlock() == null) {
                continue;
            }
            if (skip > 0) {
                skip--;
                continue;
            }
            return frames[i];
        }
        return null;
    }

    private void lowerTry(TryStatement t) {
        // Allocate resource slots (null) before the protected region; run initializers inside
        // so a later open failure still closes earlier resources via finally.
        for (VariableDeclaration resource : t.resources()) {
            lowerResourceSlot(resource);
        }
        List<MethodCallExpression> resourceCloses =
                typed.resourceCloseCalls().getOrDefault(t, List.of());
        if (!resourceCloses.isEmpty()) {
            lowerTryWithResources(t, resourceCloses);
            return;
        }
        lowerTryCatchFinally(t);
    }

    /**
     * Plain try/catch/finally (no resources). Abrupt completions enter {@code finally} once;
     * catch fallthrough also runs finally before resuming the original completion.
     */
    private void lowerTryCatchFinally(TryStatement t) {
        boolean needsCleanup = t.finallyBlock() != null;

        IrBasicBlock afterBlock = builder.newBlock("try.after");
        IrBasicBlock dispatchBlock = builder.newBlock("try.dispatch");
        IrBasicBlock finallyBlock = needsCleanup ? builder.newBlock("try.finally") : null;
        IrValue.Local savedEx = needsCleanup ? builder.alloca("finally.savedEx", IrType.PTR) : null;
        if (savedEx != null) {
            builder.store(new IrValue.Const(IrType.PTR, "null"), savedEx);
        }
        TryFrame frame = new TryFrame(dispatchBlock, afterBlock, finallyBlock, t.catches(), savedEx);
        tryFrames.push(frame);
        builder.pushExceptionHandler(dispatchBlock);

        if (!builder.block().isTerminated()) {
            lowerStatement(t.tryBlock());
        }
        if (!builder.block().isTerminated()) {
            branchToFinallyOrAfter(finallyBlock, afterBlock);
        }

        builder.popExceptionHandler();

        // During catch bodies: route new throws to finally (if any), else outer handler.
        if (finallyBlock != null) {
            builder.pushExceptionHandler(finallyBlock);
        } else {
            pushOuterExceptionHandler();
        }

        builder.setBlock(dispatchBlock);
        emitCatchHandlers(frame, finallyBlock, afterBlock);

        if (finallyBlock != null) {
            builder.popExceptionHandler();
            builder.setBlock(finallyBlock);
            emitStashPendingForFinally(frame);
            pushOuterExceptionHandler();
            finallyDepth++;
            if (t.finallyBlock() != null) {
                lowerStatement(t.finallyBlock());
            }
            finallyDepth--;
            popOuterExceptionHandler();
            if (!builder.block().isTerminated()) {
                emitFinallyEpilogue(frame);
            }
        } else {
            popOuterExceptionHandler();
        }

        tryFrames.pop();
        builder.setBlock(afterBlock);
        builder.maybeCheckPendingPublic();
    }

    /**
     * try-with-resources as nested frames: inner closes resources before outer catch/finally
     * (JLS §14.20.3). Body/init exceptions and abrupt completions enter the close frame first.
     */
    private void lowerTryWithResources(TryStatement t, List<MethodCallExpression> resourceCloses) {
        boolean hasCatches = !t.catches().isEmpty();
        boolean hasUserFinally = t.finallyBlock() != null;
        boolean hasOuter = hasCatches || hasUserFinally;

        IrBasicBlock afterBlock = builder.newBlock("try.after");
        IrBasicBlock dispatchBlock = hasCatches ? builder.newBlock("try.dispatch") : null;
        IrBasicBlock userFinallyBlock = hasUserFinally ? builder.newBlock("try.finally") : null;
        IrValue.Local outerSavedEx = hasUserFinally ? builder.alloca("finally.savedEx", IrType.PTR) : null;
        if (outerSavedEx != null) {
            builder.store(new IrValue.Const(IrType.PTR, "null"), outerSavedEx);
        }

        TryFrame outer = null;
        if (hasOuter) {
            IrBasicBlock outerDispatch = dispatchBlock != null ? dispatchBlock : userFinallyBlock;
            outer = new TryFrame(outerDispatch, afterBlock, userFinallyBlock, t.catches(), outerSavedEx);
            tryFrames.push(outer);
            builder.pushExceptionHandler(outerDispatch);
        }

        IrBasicBlock closeBlock = builder.newBlock("twr.close");
        IrBasicBlock afterClose = builder.newBlock("twr.after");
        // Resource close must observe the body exception for suppress logic — do not stash here.
        TryFrame inner = new TryFrame(closeBlock, afterClose, closeBlock, List.of(), null);
        tryFrames.push(inner);
        builder.pushExceptionHandler(closeBlock);

        for (VariableDeclaration resource : t.resources()) {
            if (builder.block().isTerminated()) {
                break;
            }
            lowerResourceInit(resource);
        }
        if (!builder.block().isTerminated()) {
            lowerStatement(t.tryBlock());
        }
        if (!builder.block().isTerminated()) {
            builder.branch(closeBlock);
        }

        builder.popExceptionHandler();

        builder.setBlock(closeBlock);
        finallyDepth++;
        emitResourceCleanup(resourceCloses);
        finallyDepth--;
        if (!builder.block().isTerminated()) {
            emitFinallyEpilogue(inner);
        }

        tryFrames.pop(); // inner — catch/return must not re-close

        builder.setBlock(afterClose);
        // Outer handler still pushed: pending after close → catch dispatch / user finally.
        builder.maybeCheckPendingPublic();
        if (!builder.block().isTerminated()) {
            if (userFinallyBlock != null) {
                builder.branch(userFinallyBlock);
            } else {
                builder.branch(afterBlock);
            }
        }

        if (hasOuter) {
            builder.popExceptionHandler(); // end outer body protection

            if (hasCatches) {
                if (userFinallyBlock != null) {
                    builder.pushExceptionHandler(userFinallyBlock);
                } else {
                    pushOuterExceptionHandler();
                }
                builder.setBlock(dispatchBlock);
                emitCatchHandlers(outer,
                        userFinallyBlock != null ? userFinallyBlock : afterBlock,
                        afterBlock);
                if (userFinallyBlock != null) {
                    builder.popExceptionHandler();
                } else {
                    popOuterExceptionHandler();
                }
            }

            if (userFinallyBlock != null) {
                builder.setBlock(userFinallyBlock);
                emitStashPendingForFinally(outer);
                pushOuterExceptionHandler();
                finallyDepth++;
                lowerStatement(t.finallyBlock());
                finallyDepth--;
                popOuterExceptionHandler();
                if (!builder.block().isTerminated()) {
                    emitFinallyEpilogue(outer);
                }
            }

            tryFrames.pop();
        }

        builder.setBlock(afterBlock);
        builder.maybeCheckPendingPublic();
    }

    /**
     * Clear pending while running finally so ops inside finally are not diverted by the
     * try-body exception. Epilogue restores it if finally completes normally.
     */
    private void emitStashPendingForFinally(TryFrame frame) {
        if (frame.savedException() == null) {
            return;
        }
        IrValue pending = builder.call("jnative_exception_pending", IrType.I1, List.of(), false);
        IrBasicBlock stash = builder.newBlock("finally.stash");
        IrBasicBlock cont = builder.newBlock("finally.body");
        builder.condBranch(pending, stash, cont);
        builder.setBlock(stash);
        IrValue taken = builder.call("jnative_exception_take", IrType.PTR, List.of(), false);
        builder.store(taken, frame.savedException());
        builder.branch(cont);
        builder.setBlock(cont);
    }

    /** Handler of the try frame beneath the current top (outer), if any. */
    private void pushOuterExceptionHandler() {
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        if (frames.length >= 2) {
            builder.pushExceptionHandler(frames[1].dispatchBlock());
        }
    }

    private void popOuterExceptionHandler() {
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        if (frames.length >= 2) {
            builder.popExceptionHandler();
        }
    }

    /** Alloca resource local and store null (initializer runs inside the try). */
    private void lowerResourceSlot(VariableDeclaration v) {
        JType jt = resolveDeclType(v);
        IrType type = IrType.from(jt);
        IrValue.Local local = builder.alloca(v.name(), type, false, -1, debugTypeName(jt));
        builder.store(new IrValue.Const(IrType.PTR, "null"), local);
    }

    private void lowerResourceInit(VariableDeclaration v) {
        if (v.initializer() == null) {
            return;
        }
        IrValue.Local local = builder.local(v.name());
        if (local == null) {
            throw new IllegalStateException("missing resource local " + v.name());
        }
        builder.setLocation(mapLoc(v.initializer().location()));
        IrValue value = lowerExpression(v.initializer());
        builder.setLocation(mapLoc(v.location()));
        builder.store(castIfNeeded(value, local.type()), local);
        builder.maybeCheckPendingPublic();
    }

    /**
     * Closes try-with-resources in reverse acquisition order.
     * Body exception is primary; close failures are {@code addSuppressed} onto it.
     * With no body exception, the first close failure becomes primary and later ones are suppressed.
     */
    private void emitResourceCleanup(List<MethodCallExpression> closes) {
        if (closes.isEmpty() || builder.block().isTerminated()) {
            return;
        }
        for (MethodCallExpression close : closes) {
            if (builder.block().isTerminated()) {
                return;
            }
            emitOneResourceClose(close);
        }
    }

    private void emitOneResourceClose(MethodCallExpression close) {
        // Snapshot primary (body or earlier close), then clear so this close can throw.
        IrValue hadPrimary = builder.call(
                "jnative_exception_pending", IrType.I1, List.of(), false);
        IrValue primary = builder.call(
                "jnative_exception_peek", IrType.PTR, List.of(), false);

        IrBasicBlock clearPending = builder.newBlock("twr.clear");
        IrBasicBlock afterClear = builder.newBlock("twr.afterclear");
        builder.condBranch(hadPrimary, clearPending, afterClear);
        builder.setBlock(clearPending);
        builder.clearException();
        builder.branch(afterClear);
        builder.setBlock(afterClear);

        Expression receiverExpr = close.receiver();
        IrValue receiver = lowerExpression(receiverExpr);
        IrBasicBlock doClose = builder.newBlock("twr.close");
        IrBasicBlock afterClose = builder.newBlock("twr.afterclose");
        IrValue isNull = builder.compare(
                IrInstruction.CmpOp.EQ, receiver, new IrValue.Const(IrType.PTR, "null"));
        builder.condBranch(isNull, afterClose, doClose);

        builder.setBlock(doClose);
        lowerCallNoCheck(close, receiver);
        builder.branch(afterClose);
        builder.setBlock(afterClose);

        IrValue closeThrew = builder.call(
                "jnative_exception_pending", IrType.I1, List.of(), false);

        IrBasicBlock closeYes = builder.newBlock("twr.closeYes");
        IrBasicBlock closeNo = builder.newBlock("twr.closeNo");
        IrBasicBlock done = builder.newBlock("twr.done");
        builder.condBranch(closeThrew, closeYes, closeNo);

        builder.setBlock(closeYes);
        IrValue closeEx = builder.call("jnative_exception_take", IrType.PTR, List.of(), false);
        IrBasicBlock suppress = builder.newBlock("twr.suppress");
        IrBasicBlock primaryIsClose = builder.newBlock("twr.primaryClose");
        builder.condBranch(hadPrimary, suppress, primaryIsClose);

        builder.setBlock(suppress);
        builder.call("jnative_throwable_add_suppressed", IrType.VOID,
                List.of(primary, closeEx), false);
        builder.call("jnative_exception_throw", IrType.VOID, List.of(primary), false);
        builder.branch(done);

        builder.setBlock(primaryIsClose);
        builder.call("jnative_exception_throw", IrType.VOID, List.of(closeEx), false);
        builder.branch(done);

        builder.setBlock(closeNo);
        IrBasicBlock restorePrimary = builder.newBlock("twr.restorePrimary");
        IrBasicBlock noEx = builder.newBlock("twr.noEx");
        builder.condBranch(hadPrimary, restorePrimary, noEx);
        builder.setBlock(restorePrimary);
        builder.call("jnative_exception_throw", IrType.VOID, List.of(primary), false);
        builder.branch(done);
        builder.setBlock(noEx);
        builder.branch(done);

        builder.setBlock(done);
    }

    /** Emit a resolved {@code close()} call without pending-exception diversion. */
    private void lowerCallNoCheck(MethodCallExpression m, IrValue receiver) {
        Object resolved = typed.resolvedSymbols().get(m);
        if (resolved instanceof InterfaceCallTarget ict) {
            builder.interfaceCall(
                    receiver,
                    ict.interfaceSymbol().interfaceId(),
                    ict.slot(),
                    IrType.from(ict.returnType()),
                    List.of(receiver),
                    ict.interfaceSymbol().name() + "." + ict.interfaceMethod().name(),
                    false);
            return;
        }
        if (!(resolved instanceof MethodSymbol ms)) {
            return;
        }
        List<IrValue> args = new ArrayList<>();
        args.add(receiver);
        if (ms.isVirtual() && ms.vtableSlot() != null) {
            MethodSymbol slotMethod = ms;
            while (slotMethod.overrides() != null) {
                slotMethod = slotMethod.overrides();
            }
            int slot = slotMethod.vtableSlot() != null ? slotMethod.vtableSlot() : ms.vtableSlot();
            builder.virtualCall(
                    receiver,
                    slot,
                    IrType.from(ms.returnType()),
                    args,
                    ms.owner().name() + "." + ms.name(),
                    false);
            return;
        }
        if (ms.isNative() && ms.nativeFunction() != null) {
            // Native close (e.g. File/socket helpers): reuse normal path without check via mangled call.
            builder.call(ms.mangledName(), IrType.from(ms.returnType()), args, false);
            return;
        }
        if (ms.isFfiIntrinsic()) {
            lowerFfiIntrinsic(ms, args);
            return;
        }
        builder.call(ms.mangledName(), IrType.from(ms.returnType()), args, false);
    }

    private void branchToFinallyOrAfter(IrBasicBlock finallyBlock, IrBasicBlock afterBlock) {
        if (finallyBlock != null) {
            builder.branch(finallyBlock);
        } else {
            builder.branch(afterBlock);
        }
    }

    private void emitCatchHandlers(TryFrame frame, IrBasicBlock finallyBlock, IrBasicBlock afterBlock) {
        for (CatchClause c : frame.catches()) {
            ClassSymbol catchType = typed.catchTypes().get(c);
            IrBasicBlock catchBlock = builder.newBlock("catch." + c.variableName());
            IrBasicBlock next = builder.newBlock("catch.next");
            IrValue ex = builder.call("jnative_exception_peek", IrType.PTR, List.of(), false);
            IrValue match = builder.instanceOf(ex, catchType.metadata().globalName());
            builder.condBranch(match, catchBlock, next);

            builder.setBlock(catchBlock);
            IrValue taken = builder.call("jnative_exception_take", IrType.PTR, List.of(), false);
            IrValue.Local var = builder.alloca(c.variableName(), IrType.PTR);
            builder.store(taken, var);
            lowerStatement(c.body());
            if (!builder.block().isTerminated()) {
                branchToFinallyOrAfter(finallyBlock, afterBlock);
            }
            builder.setBlock(next);
        }
        if (!builder.block().isTerminated()) {
            branchToFinallyOrAfter(finallyBlock, afterBlock);
        }
    }

    private void emitFinallyEpilogue(TryFrame frame) {
        IrBasicBlock checkReturn = builder.newBlock("finally.retcheck");
        IrBasicBlock propagate = builder.newBlock("finally.propagate");
        IrBasicBlock afterPending = builder.newBlock("finally.afterPending");
        IrValue pending = builder.call("jnative_exception_pending", IrType.I1, List.of(), false);
        builder.condBranch(pending, propagate, afterPending);

        builder.setBlock(propagate);
        builder.branch(frame.afterBlock());

        // Restore stashed body/catch exception if finally completed normally.
        builder.setBlock(afterPending);
        if (frame.savedException() != null) {
            IrValue saved = builder.load(frame.savedException());
            IrValue hasSaved = builder.compare(
                    IrInstruction.CmpOp.NE, saved, new IrValue.Const(IrType.PTR, "null"));
            IrBasicBlock restore = builder.newBlock("finally.restore");
            builder.condBranch(hasSaved, restore, checkReturn);
            builder.setBlock(restore);
            builder.call("jnative_exception_throw", IrType.VOID, List.of(saved), false);
            builder.store(new IrValue.Const(IrType.PTR, "null"), frame.savedException());
            builder.branch(propagate);
        } else {
            builder.branch(checkReturn);
        }

        builder.setBlock(checkReturn);
        IrValue hasReturn = builder.hasReturnCompletion();
        IrBasicBlock resume = builder.newBlock("finally.resume");
        // Only emit break/continue arms when this function registered loop targets.
        IrBasicBlock noReturn = abruptTargets.isEmpty()
                ? frame.afterBlock()
                : builder.newBlock("finally.breakcheck");
        builder.condBranch(hasReturn, resume, noReturn);

        builder.setBlock(resume);
        IrBasicBlock outerFinally = findOuterFinallyBlock(frame);
        if (outerFinally != null) {
            builder.branch(outerFinally);
        } else {
            IrType retType = builder.function().returnType();
            if (retType == IrType.VOID) {
                builder.retVoid();
            } else {
                builder.ret(castIfNeeded(builder.takeReturnCompletion(retType), retType));
            }
        }

        if (abruptTargets.isEmpty()) {
            return;
        }

        IrBasicBlock checkBreak = noReturn;
        builder.setBlock(checkBreak);
        IrValue hasBreak = builder.hasBreakCompletion();
        IrBasicBlock doBreak = builder.newBlock("finally.dobreak");
        IrBasicBlock checkContinue = builder.newBlock("finally.contcheck");
        builder.condBranch(hasBreak, doBreak, checkContinue);

        builder.setBlock(doBreak);
        IrBasicBlock outerBreakFinally = findOuterFinallyForLoopAbrupt(frame);
        if (outerBreakFinally != null) {
            builder.branch(outerBreakFinally);
        } else {
            emitAbruptTargetDispatch();
        }

        builder.setBlock(checkContinue);
        IrValue hasContinue = builder.hasContinueCompletion();
        IrBasicBlock doContinue = builder.newBlock("finally.docontinue");
        IrBasicBlock fallthrough = builder.newBlock("finally.fallthrough");
        builder.condBranch(hasContinue, doContinue, fallthrough);

        builder.setBlock(doContinue);
        IrBasicBlock outerContFinally = findOuterFinallyForLoopAbrupt(frame);
        if (outerContFinally != null) {
            builder.branch(outerContFinally);
        } else {
            emitAbruptTargetDispatch();
        }

        builder.setBlock(fallthrough);
        builder.branch(frame.afterBlock());
    }

    private IrBasicBlock findOuterFinallyForLoopAbrupt(TryFrame current) {
        if (loopFrames.isEmpty()) {
            return null;
        }
        LoopFrame loop = loopFrames.peek();
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        int i = 0;
        for (; i < frames.length; i++) {
            if (frames[i] == current) {
                break;
            }
        }
        for (int j = i + 1; j < frames.length; j++) {
            if ((frames.length - j) <= loop.tryDepthAtEntry()) {
                return null;
            }
            if (frames[j].finallyBlock() != null) {
                return frames[j].finallyBlock();
            }
        }
        return null;
    }

    /** Lower break/continue target id (IR CFG branch — LLVM never sees `break`). */
    private void emitAbruptTargetDispatch() {
        IrValue targetId = builder.takeAbruptTarget();
        emitAbruptDispatchChain(targetId, 0);
    }

    private void emitAbruptDispatchChain(IrValue targetId, int index) {
        if (index >= abruptTargets.size()) {
            builder.branch(abruptTargets.get(0));
            return;
        }
        if (index == abruptTargets.size() - 1) {
            builder.branch(abruptTargets.get(index));
            return;
        }
        IrBasicBlock match = builder.newBlock("abrupt.match." + index);
        IrBasicBlock next = builder.newBlock("abrupt.next." + index);
        IrValue eq = builder.compare(IrInstruction.CmpOp.EQ, targetId, new IrValue.Const(IrType.I32, index));
        builder.condBranch(eq, match, next);
        builder.setBlock(match);
        builder.branch(abruptTargets.get(index));
        builder.setBlock(next);
        emitAbruptDispatchChain(targetId, index + 1);
    }

    private IrBasicBlock findOuterFinallyBlock(TryFrame current) {
        boolean seen = false;
        for (TryFrame f : tryFrames) {
            if (f == current) {
                seen = true;
                continue;
            }
            if (seen && f.finallyBlock() != null) {
                return f.finallyBlock();
            }
        }
        return null;
    }

    private JType resolveDeclType(VariableDeclaration v) {
        if (currentMethod != null) {
            for (var tp : currentMethod.typeParameters()) {
                if (tp.name().equals(v.type().name())) {
                    JType tv = tp.asType();
                    return remapType(v.isArray() ? new ArrayType(tv) : tv);
                }
            }
        }
        JType t = resolveTypeNameInOwner(v.type(), v.isArray(), currentClass);
        return remapType(t);
    }

    private void lowerIf(IfStatement i) {
        IrValue cond = lowerExpression(i.condition());
        IrBasicBlock thenBlock = builder.newBlock("if.then");
        IrBasicBlock mergeBlock = builder.newBlock("if.end");
        boolean hasElse = i.elseBranch() != null;
        IrBasicBlock elseBlock = hasElse ? builder.newBlock("if.else") : null;
        builder.condBranch(cond, thenBlock, hasElse ? elseBlock : mergeBlock);

        builder.setBlock(thenBlock);
        lowerStatement(i.thenBranch());
        if (!builder.block().isTerminated()) {
            builder.branch(mergeBlock);
        }
        if (hasElse) {
            builder.setBlock(elseBlock);
            lowerStatement(i.elseBranch());
            if (!builder.block().isTerminated()) {
                builder.branch(mergeBlock);
            }
        }
        builder.setBlock(mergeBlock);
    }

    private void lowerWhile(WhileStatement w) {
        IrBasicBlock condBlock = builder.newBlock("while.cond");
        IrBasicBlock bodyBlock = builder.newBlock("while.body");
        IrBasicBlock endBlock = builder.newBlock("while.end");
        pushLoop(endBlock, condBlock);
        builder.branch(condBlock);
        builder.setBlock(condBlock);
        IrValue cond = lowerExpression(w.condition());
        builder.condBranch(cond, bodyBlock, endBlock);
        builder.setBlock(bodyBlock);
        lowerStatement(w.body());
        if (!builder.block().isTerminated()) {
            if (currentFunction == null || !currentFunction.omitSafepoints()) {
                builder.safepointPoll();
            }
            builder.branch(condBlock);
        }
        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    private void lowerDoWhile(DoWhileStatement d) {
        IrBasicBlock bodyBlock = builder.newBlock("dowhile.body");
        IrBasicBlock condBlock = builder.newBlock("dowhile.cond");
        IrBasicBlock endBlock = builder.newBlock("dowhile.end");
        pushLoop(endBlock, condBlock);
        builder.branch(bodyBlock);
        builder.setBlock(bodyBlock);
        lowerStatement(d.body());
        if (!builder.block().isTerminated()) {
            builder.branch(condBlock);
        }
        builder.setBlock(condBlock);
        IrValue cond = lowerExpression(d.condition());
        if (currentFunction == null || !currentFunction.omitSafepoints()) {
            builder.safepointPoll();
        }
        builder.condBranch(cond, bodyBlock, endBlock);
        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    private void lowerFor(ForStatement f) {
        if (f.initializer() != null) {
            lowerStatement(f.initializer());
        }
        IrBasicBlock condBlock = builder.newBlock("for.cond");
        IrBasicBlock bodyBlock = builder.newBlock("for.body");
        IrBasicBlock updateBlock = builder.newBlock("for.update");
        IrBasicBlock endBlock = builder.newBlock("for.end");
        pushLoop(endBlock, updateBlock);
        builder.branch(condBlock);
        builder.setBlock(condBlock);
        if (f.condition() != null) {
            builder.condBranch(lowerExpression(f.condition()), bodyBlock, endBlock);
        } else {
            builder.branch(bodyBlock);
        }
        builder.setBlock(bodyBlock);
        lowerStatement(f.body());
        if (!builder.block().isTerminated()) {
            builder.branch(updateBlock);
        }
        builder.setBlock(updateBlock);
        if (f.update() != null) {
            lowerExpression(f.update());
        }
        if (!builder.block().isTerminated()) {
            if (currentFunction == null || !currentFunction.omitSafepoints()) {
                builder.safepointPoll();
            }
            builder.branch(condBlock);
        }
        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    /**
     * Lower enhanced-for: arrays use an index loop; Iterables use iterator()/hasNext()/next().
     * Collection/array expression and {@code iterator()} are each evaluated once.
     */
    private void lowerEnhancedFor(EnhancedForStatement ef) {
        EnhancedForDesugar desugar = typed.enhancedForDesugars().get(ef);
        if (desugar != null) {
            lowerIterableEnhancedFor(ef, desugar);
            return;
        }
        lowerArrayEnhancedFor(ef);
    }

    private void lowerArrayEnhancedFor(EnhancedForStatement ef) {
        int id = System.identityHashCode(ef);
        JType arrJt = remapType(typed.expressionTypes().get(ef.array()));
        JType arrayElemJt = arrJt instanceof ArrayType at ? at.elementType() : PrimitiveType.INT;
        JType declared = remapType(resolveTypeNameInOwner(
                ef.elementType(), ef.elementIsArray(), currentClass));
        IrType arrayElemIr = IrType.from(arrayElemJt);
        IrType declaredIr = IrType.from(declared);

        IrValue arrOnce = lowerExpression(ef.array());
        IrValue.Local arrSlot = builder.alloca("foreach.arr" + id, IrType.PTR);
        builder.store(arrOnce, arrSlot);

        IrValue.Local idxSlot = builder.alloca("foreach.idx" + id, IrType.I32);
        builder.store(new IrValue.Const(IrType.I32, 0), idxSlot);

        IrValue.Local elemSlot = builder.alloca(
                ef.variableName(), declaredIr, false, -1, debugTypeName(declared));

        IrBasicBlock condBlock = builder.newBlock("foreach.cond");
        IrBasicBlock bodyBlock = builder.newBlock("foreach.body");
        IrBasicBlock updateBlock = builder.newBlock("foreach.update");
        IrBasicBlock endBlock = builder.newBlock("foreach.end");
        pushLoop(endBlock, updateBlock);
        builder.branch(condBlock);

        builder.setBlock(condBlock);
        IrValue arr = builder.load(arrSlot);
        builder.nullCheck(arr);
        IrValue len = builder.arrayLength(arr);
        IrValue idx = builder.load(idxSlot);
        IrValue cmp = builder.compare(IrInstruction.CmpOp.LT, idx, castIfNeeded(len, IrType.I32));
        builder.condBranch(cmp, bodyBlock, endBlock);

        builder.setBlock(bodyBlock);
        IrValue arrBody = builder.load(arrSlot);
        IrValue idxBody = builder.load(idxSlot);
        IrValue elem = builder.loadElement(arrBody, idxBody, arrayElemIr);
        builder.store(castIfNeeded(elem, declaredIr), elemSlot);
        lowerStatement(ef.body());
        if (!builder.block().isTerminated()) {
            builder.branch(updateBlock);
        }

        builder.setBlock(updateBlock);
        IrValue i = builder.load(idxSlot);
        IrValue next = builder.binary(
                IrInstruction.BinOp.ADD, i, new IrValue.Const(IrType.I32, 1));
        builder.store(next, idxSlot);
        if (!builder.block().isTerminated()) {
            if (currentFunction == null || !currentFunction.omitSafepoints()) {
                builder.safepointPoll();
            }
            builder.branch(condBlock);
        }
        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    private void lowerIterableEnhancedFor(EnhancedForStatement ef, EnhancedForDesugar desugar) {
        JType declared = remapType(resolveTypeNameInOwner(
                ef.elementType(), ef.elementIsArray(), currentClass));
        IrType declaredIr = IrType.from(declared);
        JType iterJt = remapType(typed.expressionTypes().get(desugar.hasNextCall().receiver()));
        IrType iterIr = IrType.from(iterJt != null ? iterJt : declared);

        IrValue.Local iterSlot = builder.alloca(
                desugar.iteratorLocalName(), IrType.PTR, false, -1,
                iterJt != null ? debugTypeName(iterJt) : "Iterator");
        IrValue iterator = lowerExpression(desugar.iteratorCall());
        builder.store(castIfNeeded(iterator, IrType.PTR), iterSlot);

        IrValue.Local elemSlot = builder.alloca(
                ef.variableName(), declaredIr, false, -1, debugTypeName(declared));

        IrBasicBlock condBlock = builder.newBlock("foreach.cond");
        IrBasicBlock bodyBlock = builder.newBlock("foreach.body");
        IrBasicBlock updateBlock = builder.newBlock("foreach.update");
        IrBasicBlock endBlock = builder.newBlock("foreach.end");
        pushLoop(endBlock, updateBlock);
        builder.branch(condBlock);

        builder.setBlock(condBlock);
        IrValue hasNext = lowerExpression(desugar.hasNextCall());
        builder.condBranch(hasNext, bodyBlock, endBlock);

        builder.setBlock(bodyBlock);
        IrValue nextVal = lowerExpression(desugar.nextCall());
        JType nextJt = remapType(typed.expressionTypes().get(desugar.nextCall()));
        builder.store(convertTo(nextVal, nextJt, declared), elemSlot);
        lowerStatement(ef.body());
        if (!builder.block().isTerminated()) {
            builder.branch(updateBlock);
        }

        builder.setBlock(updateBlock);
        if (!builder.block().isTerminated()) {
            if (currentFunction == null || !currentFunction.omitSafepoints()) {
                builder.safepointPoll();
            }
            builder.branch(condBlock);
        }
        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    /**
     * Cascade switch (no jump table): evaluate selector once, then test each case.
     * Fall-through links section bodies; {@code break} targets {@code end}.
     */
    private void lowerSwitch(SwitchStatement s) {
        JType selectorJt = typed.expressionTypes().get(s.selector());
        boolean enumSwitch = selectorJt instanceof ClassType ct && ct.classSymbol().isEnum();
        ClassSymbol enumSym = enumSwitch ? ((ClassType) selectorJt).classSymbol() : null;

        IrValue selectorVal = lowerExpression(s.selector());
        IrValue.Local selSlot;
        if (enumSwitch) {
            // Null check then load ordinal into an int temp.
            builder.nullCheck(selectorVal);
            FieldSymbol ordField = enumSym.findField("$ordinal").orElseThrow();
            IrValue ordinal = builder.loadField(
                    selectorVal,
                    enumSym.name(),
                    enumSym.layout().llvmTypeName(),
                    ordField.gepIndex(),
                    IrType.I32);
            selSlot = builder.alloca("switch.sel" + System.identityHashCode(s), IrType.I32);
            builder.store(ordinal, selSlot);
        } else {
            IrValue asInt = castIfNeeded(selectorVal, IrType.I32);
            selSlot = builder.alloca("switch.sel" + System.identityHashCode(s), IrType.I32);
            builder.store(asInt, selSlot);
        }

        IrBasicBlock endBlock = builder.newBlock("switch.end");
        pushSwitch(endBlock);

        List<SwitchSection> sections = s.sections();
        int n = sections.size();
        IrBasicBlock[] bodyBlocks = new IrBasicBlock[n];
        IrBasicBlock[] testBlocks = new IrBasicBlock[n];
        IrBasicBlock defaultBody = null;
        for (int i = 0; i < n; i++) {
            bodyBlocks[i] = builder.newBlock("switch.body" + i);
            testBlocks[i] = builder.newBlock("switch.test" + i);
            for (SwitchLabel label : sections.get(i).labels()) {
                if (label instanceof DefaultLabel) {
                    defaultBody = bodyBlocks[i];
                }
            }
        }
        IrBasicBlock defaultOrEnd = defaultBody != null ? defaultBody : endBlock;

        if (n == 0) {
            builder.branch(endBlock);
            loopFrames.pop();
            builder.setBlock(endBlock);
            return;
        }
        builder.branch(testBlocks[0]);

        for (int i = 0; i < n; i++) {
            builder.setBlock(testBlocks[i]);
            SwitchSection section = sections.get(i);
            List<Integer> caseValues = new ArrayList<>();
            boolean isDefaultOnly = true;
            for (SwitchLabel label : section.labels()) {
                if (label instanceof CaseLabel c) {
                    isDefaultOnly = false;
                    if (enumSwitch) {
                        String name = ((VariableExpression) c.constant()).name();
                        caseValues.add(enumSym.enumOrdinal(name));
                    } else {
                        caseValues.add(evalCaseInt(c.constant()));
                    }
                }
            }
            IrBasicBlock nextTest = (i + 1 < n) ? testBlocks[i + 1] : defaultOrEnd;
            if (isDefaultOnly) {
                // Default-only section is not entered via tests; fall to next test or defaultOrEnd.
                builder.branch(nextTest);
            } else {
                emitCaseTests(selSlot, caseValues, bodyBlocks[i], nextTest);
            }
        }

        for (int i = 0; i < n; i++) {
            builder.setBlock(bodyBlocks[i]);
            for (Statement st : sections.get(i).statements()) {
                lowerStatement(st);
            }
            if (!builder.block().isTerminated()) {
                if (i + 1 < n) {
                    builder.branch(bodyBlocks[i + 1]); // fall-through
                } else {
                    builder.branch(endBlock);
                }
            }
        }

        loopFrames.pop();
        builder.setBlock(endBlock);
    }

    private void emitCaseTests(
            IrValue.Local selSlot, List<Integer> values, IrBasicBlock body, IrBasicBlock next) {
        if (values.isEmpty()) {
            builder.branch(next);
            return;
        }
        IrValue sel = builder.load(selSlot);
        for (int i = 0; i < values.size(); i++) {
            IrValue cmp = builder.compare(
                    IrInstruction.CmpOp.EQ, sel, new IrValue.Const(IrType.I32, values.get(i)));
            if (i == values.size() - 1) {
                builder.condBranch(cmp, body, next);
            } else {
                IrBasicBlock more = builder.newBlock("switch.case.or");
                builder.condBranch(cmp, body, more);
                builder.setBlock(more);
                sel = builder.load(selSlot);
            }
        }
    }

    private static int evalCaseInt(Expression expr) {
        return switch (expr) {
            case LiteralExpression lit -> switch (lit.kind()) {
                case INT -> (Integer) lit.value();
                case LONG -> (int) (long) (Long) lit.value();
                default -> throw new IllegalStateException("non-int case");
            };
            case UnaryExpression u -> {
                int inner = evalCaseInt(u.operand());
                yield u.operator() == UnaryOp.NEGATE ? -inner : inner;
            }
            case GroupingExpression g -> evalCaseInt(g.expression());
            case CastExpression c -> evalCaseInt(c.expression());
            default -> throw new IllegalStateException("non-constant case");
        };
    }

    private IrValue lowerExpression(Expression expr) {
        return switch (expr) {
            case LiteralExpression l -> lowerLiteral(l);
            case NullLiteralExpression ignored -> new IrValue.Const(IrType.PTR, "null");
            case NativeNullExpression ignored -> new IrValue.Const(IrType.NATIVE_PTR, "null");
            case ThisExpression ignored -> builder.load(builder.local("this"));
            case VariableExpression v -> lowerVariable(v);
            case AssignmentExpression a -> lowerAssignment(a);
            case BinaryExpression b -> lowerBinary(b);
            case UnaryExpression u -> lowerUnary(u);
            case MethodCallExpression m -> lowerCall(m);
            case PrintlnExpression p -> {
                builder.println(lowerExpression(p.argument()));
                yield new IrValue.Const(IrType.I32, 0);
            }
            case GroupingExpression g -> lowerExpression(g.expression());
            case NewObjectExpression n -> lowerNew(n);
            case FieldAccessExpression f -> lowerFieldLoad(f);
            case NewArrayExpression n -> lowerNewArray(n);
            case ArrayAccessExpression a -> lowerArrayLoad(a);
            case InstanceOfExpression i -> lowerInstanceOf(i);
            case CastExpression c -> lowerCast(c);
            case ConditionalExpression c -> lowerConditional(c);
            case GcCollectExpression ignored -> {
                builder.gcCollect();
                yield new IrValue.Const(IrType.I32, 0);
            }
            case AwaitExpression a -> lowerAwait(a);
            case LambdaExpression l -> lowerCallable(l);
            case MethodReferenceExpression r -> lowerCallable(r);
        };
    }

    private IrValue lowerCallable(Expression expr) {
        CallableBinding binding = typed.callableBindings().get(expr);
        if (binding == null) {
            throw new IllegalStateException("missing callable binding for " + expr.getClass().getSimpleName());
        }
        IrValue env = new IrValue.Const(IrType.PTR, "null");
        boolean hasEnv = false;
        if (binding.envClass() != null) {
            ClassSymbol envClass = binding.envClass();
            ClassLayout layout = envClass.layout();
            IrValue envObj = builder.newObject(
                    envClass.name(),
                    layout.llvmTypeName(),
                    layout.instanceSize(),
                    envClass.metadata().globalName());
            for (ClosureCapture cap : binding.captures()) {
                FieldSymbol field = envClass.findField(cap.fieldName()).orElseThrow();
                IrValue val = loadCaptureValue(cap);
                builder.storeField(
                        envObj, envClass.name(), layout.llvmTypeName(), field.gepIndex(),
                        castIfNeeded(val, IrType.from(field.type())));
            }
            env = envObj;
            hasEnv = true;
        } else if (binding.receiverExpression() != null) {
            // Bound method reference: evaluate receiver once at creation (Java-compatible).
            env = lowerExpression(binding.receiverExpression());
            builder.nullCheck(env);
            hasEnv = true;
        }
        ClassSymbol sam = binding.samClass();
        return builder.makeCallable(
                sam.name(),
                sam.metadata().globalName(),
                sam.layout().instanceSize(),
                binding.codeSymbolName(),
                env,
                hasEnv);
    }

    private IrValue loadCaptureValue(ClosureCapture cap) {
        // Captured locals are resolved via VariableSymbol name in enclosing function scope.
        IrValue.Local local = builder.local(cap.variable().name());
        if (local == null) {
            throw new IllegalStateException("missing capture local " + cap.variable().name());
        }
        if (cap.variable().isStackAllocated()) {
            return local;
        }
        return builder.load(local);
    }

    private void lowerCallableFunctions(IrModule module, TypedCompilationUnit typed) {
        Set<String> emitted = new HashSet<>();
        for (CallableBinding binding : typed.callableBindings().values()) {
            if (!emitted.add(binding.codeSymbolName())) {
                continue;
            }
            if (binding.targetMethod() != null) {
                lowerMethodRefThunk(module, binding);
            } else {
                lowerLambdaFunction(module, binding);
            }
        }
    }

    private void lowerLambdaFunction(IrModule module, CallableBinding binding) {
        FunctionType ft = binding.samClass().functionType();
        List<IrValue.Param> params = new ArrayList<>();
        params.add(new IrValue.Param("env", IrType.PTR));
        for (int i = 0; i < ft.parameterTypes().size(); i++) {
            params.add(new IrValue.Param("p" + i, IrType.from(ft.parameterTypes().get(i))));
        }
        IrType ret = IrType.from(ft.returnType());
        IrFunction function = new IrFunction(binding.codeSymbolName(), ret, params, false);
        module.addFunction(function);
        functionsByMangled.put(binding.codeSymbolName(), function);
        // Block-body `return` must use the lambda/SAM return type, not the enclosing method.
        MethodSymbol savedMethod = currentMethod;
        ConstructorSymbol savedCtor = currentConstructor;
        currentMethod = null;
        currentConstructor = null;
        builder.beginFunction(function);
        try {
            if (binding.envClass() != null) {
                for (ClosureCapture cap : binding.captures()) {
                    FieldSymbol field = binding.envClass().findField(cap.fieldName()).orElseThrow();
                    IrValue envPtr = new IrValue.Param("env", IrType.PTR);
                    IrValue val = builder.loadField(
                            envPtr,
                            binding.envClass().name(),
                            binding.envClass().layout().llvmTypeName(),
                            field.gepIndex(),
                            IrType.from(field.type()));
                    IrValue.Local capLocal = builder.alloca(cap.variable().name(), IrType.from(cap.type()));
                    builder.store(val, capLocal);
                }
            }
            for (int i = 0; i < binding.lambdaParameters().size(); i++) {
                var lp = binding.lambdaParameters().get(i);
                IrType pTy = IrType.from(ft.parameterTypes().get(i));
                IrValue.Local pLocal = builder.alloca(lp.name(), pTy);
                builder.store(new IrValue.Param("p" + i, pTy), pLocal);
            }
            if (binding.bodyBlock() != null) {
                for (Statement stmt : binding.bodyBlock().statements()) {
                    lowerStatement(stmt);
                }
                if (!builder.currentBlockTerminated()) {
                    if (ret == IrType.VOID) {
                        builder.retVoid();
                    } else {
                        builder.ret(defaultValue(ret));
                    }
                }
            } else {
                IrValue result = lowerExpression(binding.bodyExpression());
                if (ret == IrType.VOID) {
                    builder.retVoid();
                } else {
                    builder.ret(castIfNeeded(result, ret));
                }
            }
        } finally {
            currentMethod = savedMethod;
            currentConstructor = savedCtor;
        }
    }

    private void lowerMethodRefThunk(IrModule module, CallableBinding binding) {
        MethodSymbol target = binding.targetMethod();
        FunctionType ft = binding.samClass().functionType();
        List<IrValue.Param> params = new ArrayList<>();
        params.add(new IrValue.Param("env", IrType.PTR));
        for (int i = 0; i < ft.parameterTypes().size(); i++) {
            params.add(new IrValue.Param("p" + i, IrType.from(ft.parameterTypes().get(i))));
        }
        IrType ret = IrType.from(ft.returnType());
        IrFunction function = new IrFunction(binding.codeSymbolName(), ret, params, false);
        module.addFunction(function);
        functionsByMangled.put(binding.codeSymbolName(), function);
        builder.beginFunction(function);

        List<IrValue> samArgs = new ArrayList<>();
        for (int i = 0; i < ft.parameterTypes().size(); i++) {
            samArgs.add(new IrValue.Param("p" + i, IrType.from(ft.parameterTypes().get(i))));
        }

        IrValue result;
        if (target.isStatic()) {
            result = builder.call(target.mangledName(), ret, samArgs);
        } else {
            // Bound: receiver stored in env at creation. Unbound: first SAM arg is receiver.
            boolean unbound = binding.receiverExpression() == null && binding.boundReceiverType() == null;
            IrValue receiver;
            List<IrValue> callArgs = new ArrayList<>();
            if (unbound) {
                if (samArgs.isEmpty()) {
                    throw new IllegalStateException("unbound method ref missing receiver argument");
                }
                receiver = samArgs.getFirst();
                builder.nullCheck(receiver);
                callArgs.add(receiver);
                for (int i = 1; i < samArgs.size(); i++) {
                    callArgs.add(samArgs.get(i));
                }
            } else {
                receiver = new IrValue.Param("env", IrType.PTR);
                callArgs.add(receiver);
                callArgs.addAll(samArgs);
            }
            if (binding.isVirtualMethodRef() && target.vtableSlot() != null) {
                MethodSymbol slotMethod = target;
                while (slotMethod.overrides() != null) {
                    slotMethod = slotMethod.overrides();
                }
                int slot = slotMethod.vtableSlot() != null ? slotMethod.vtableSlot() : target.vtableSlot();
                // VirtualCall args must include this as arg0 (same as lowerCall).
                result = builder.virtualCall(
                        receiver,
                        slot,
                        ret,
                        callArgs,
                        target.owner().name() + "." + target.name());
            } else {
                result = builder.call(target.mangledName(), ret, callArgs);
            }
        }

        if (ret == IrType.VOID) {
            builder.retVoid();
        } else {
            builder.ret(castIfNeeded(result, ret));
        }
    }

    /** Prefer specialized field layout when lowering a specialized class body. */
    private FieldSymbol remapField(FieldSymbol field) {
        if (field == null || currentClass == null || !currentClass.isSpecialized()) {
            return field;
        }
        ClassSymbol origin = currentClass.genericOrigin();
        if (origin != null && field.owner() == origin) {
            return currentClass.findField(field.name()).orElse(field);
        }
        return field;
    }

    /**
     * Remap type variables in specialized class bodies (e.g. {@code T[]} → {@code int[]}).
     * For non-specialized generic bodies, type variables erase to Object.
     */
    private JType remapType(JType type) {
        if (type == null) {
            return null;
        }
        if (currentClass != null && currentClass.isSpecialized()
                && currentClass.genericOrigin() != null
                && !currentClass.specializationArgs().isEmpty()) {
            TypeSubstitution sub = TypeSubstitution.zip(
                    currentClass.genericOrigin().typeParameters(),
                    currentClass.specializationArgs());
            return sub.apply(type);
        }
        if (type instanceof TypeVariable tv) {
            ClassSymbol object = typed.classes().get("Object");
            return tv.symbol().erasure(object);
        }
        if (type instanceof ArrayType at) {
            JType elem = remapType(at.elementType());
            if (elem != at.elementType()) {
                return new ArrayType(elem);
            }
        }
        return type;
    }

    private ClassMetadata arrayMetadataForElement(JType elementType) {
        String key = NameMangler.arrayMetaName(elementType);
        for (ClassMetadata meta : typed.arrayMetadatas().values()) {
            if (meta.globalName().equals("meta." + key) || meta.name().equals(key)) {
                return meta;
            }
        }
        // Synthesize metadata for specialized array types not seen during semantic analysis.
        ClassMetadata meta = new ClassMetadata(key, elementType.isReference(), elementType);
        builder.module().addArrayMetadata(meta);
        return meta;
    }

    private IrValue primitiveHashCode(IrValue value, PrimitiveType type) {
        return switch (type) {
            case INT, BYTE, SHORT, CHAR, BOOLEAN -> castIfNeeded(value, IrType.I32);
            case LONG -> {
                IrValue asI64 = castIfNeeded(value, IrType.I64);
                // fold high/low without bitwise ops: (int)(v % 2147483647) style mix via trunc
                yield builder.cast(asI64, IrType.I32);
            }
            case FLOAT -> builder.cast(castIfNeeded(value, IrType.FLOAT), IrType.I32);
            case DOUBLE -> builder.cast(castIfNeeded(value, IrType.DOUBLE), IrType.I32);
        };
    }

    private IrValue lowerVariable(VariableExpression v) {
        Object resolved = typed.resolvedSymbols().get(v);
        if (resolved instanceof FieldSymbol field) {
            field = remapField(field);
            if (field.isStatic()) {
                return builder.loadStatic(
                        field.staticGlobal(), IrType.from(field.type()), field.isVolatile());
            }
            IrValue thisPtr = builder.load(builder.local("this"));
            ClassLayout layout = field.owner().layout();
            return builder.loadField(
                    thisPtr,
                    field.owner().name(),
                    layout.llvmTypeName(),
                    field.gepIndex(),
                    IrType.from(field.type()),
                    field.isVolatile());
        }
        if (resolved instanceof ClassSymbol) {
            throw new IllegalStateException("class name used as value: " + v.name());
        }
        IrValue.Local local = builder.local(v.name());
        if (local == null) {
            throw new IllegalStateException("missing local for " + v.name());
        }
        // Stack NativeStruct locals hold the alloca address (NATIVE_PTR) — do not load.
        if (resolved instanceof VariableSymbol vs && vs.isStackAllocated()) {
            return local;
        }
        if (local.type() == IrType.NATIVE_PTR
                && typed.expressionTypes().get(v) instanceof NativeStructType) {
            return local;
        }
        return builder.load(local);
    }

    private IrValue lowerAssignment(AssignmentExpression a) {
        IrValue value = lowerExpression(a.value());
        JType fromJt = remapType(typed.expressionTypes().get(a.value()));
        Expression target = a.target();
        if (target instanceof VariableExpression v) {
            Object resolved = typed.resolvedSymbols().get(v);
            if (resolved instanceof FieldSymbol field) {
                field = remapField(field);
                IrValue casted = convertTo(value, fromJt, remapType(field.type()));
                if (field.isStatic()) {
                    builder.storeStatic(field.staticGlobal(), casted, field.isVolatile());
                } else {
                    IrValue thisPtr = builder.load(builder.local("this"));
                    ClassLayout layout = field.owner().layout();
                    builder.storeField(thisPtr, field.owner().name(), layout.llvmTypeName(),
                            field.gepIndex(), casted, field.isVolatile());
                }
                return casted;
            }
            IrValue.Local local = builder.local(v.name());
            JType localJt = resolved instanceof VariableSymbol vs
                    ? remapType(vs.type())
                    : fromJt;
            IrValue casted = convertTo(value, fromJt, localJt != null ? localJt : fromJt);
            builder.store(casted, local);
            return casted;
        }
        if (target instanceof FieldAccessExpression f) {
            Object resolved = typed.resolvedSymbols().get(f);
            if (resolved instanceof NativeStructFieldSymbol nfs) {
                IrValue base = lowerNativeStructBase(f.receiver(), nfs.owner());
                IrValue addr = builder.nativeFieldAddress(
                        base,
                        nfs.owner().name(),
                        nfs.owner().layout().llvmTypeName(),
                        nfs.name(),
                        nfs.offset());
                IrValue casted = castIfNeeded(value, IrType.from(nfs.type()));
                builder.nativeStore(addr, casted);
                return casted;
            }
            FieldSymbol field = remapField((FieldSymbol) resolved);
            IrValue casted = convertTo(value, fromJt, remapType(field.type()));
            if (field.isStatic()) {
                builder.storeStatic(field.staticGlobal(), casted, field.isVolatile());
            } else {
                IrValue receiver = lowerExpression(f.receiver());
                ClassLayout layout = field.owner().layout();
                builder.storeField(receiver, field.owner().name(), layout.llvmTypeName(),
                        field.gepIndex(), casted, field.isVolatile());
            }
            return casted;
        }
        if (target instanceof ArrayAccessExpression aexpr) {
            IrValue array = lowerExpression(aexpr.array());
            IrValue index = lowerExpression(aexpr.index());
            JType elemType = remapType(typed.expressionTypes().get(aexpr));
            IrValue casted = convertTo(value, fromJt, elemType);
            builder.storeElement(array, index, casted);
            return casted;
        }
        throw new IllegalStateException("invalid assignment target");
    }

    private IrValue lowerFieldLoad(FieldAccessExpression f) {
        Object resolved = typed.resolvedSymbols().get(f);
        if (resolved instanceof ArrayLengthSymbol) {
            return builder.arrayLength(lowerExpression(f.receiver()));
        }
        if (resolved instanceof NativeStructFieldSymbol nfs) {
            IrValue base = lowerNativeStructBase(f.receiver(), nfs.owner());
            IrValue addr = builder.nativeFieldAddress(
                    base,
                    nfs.owner().name(),
                    nfs.owner().layout().llvmTypeName(),
                    nfs.name(),
                    nfs.offset());
            return builder.nativeLoad(addr, IrType.from(nfs.type()));
        }
        FieldSymbol field = remapField((FieldSymbol) resolved);
        if (field.isStatic()) {
            return builder.loadStatic(
                    field.staticGlobal(), IrType.from(field.type()), field.isVolatile());
        }
        IrValue receiver = lowerExpression(f.receiver());
        ClassLayout layout = field.owner().layout();
        return builder.loadField(
                receiver,
                field.owner().name(),
                layout.llvmTypeName(),
                field.gepIndex(),
                IrType.from(field.type()),
                field.isVolatile());
    }

    private IrValue lowerNativeStructBase(Expression receiver, NativeStructSymbol owner) {
        if (receiver instanceof VariableExpression v) {
            Object resolved = typed.resolvedSymbols().get(v);
            if (resolved instanceof VariableSymbol vs && vs.isStackAllocated()) {
                return builder.local(v.name());
            }
        }
        JType recvType = typed.expressionTypes().get(receiver);
        if (recvType instanceof NativePtrType || recvType instanceof NativeStructType) {
            return lowerExpression(receiver);
        }
        return lowerExpression(receiver);
    }

    private IrValue lowerNew(NewObjectExpression n) {
        Object resolved = typed.resolvedSymbols().get(n);
        if (resolved instanceof NativeStructSymbol) {
            throw new IllegalStateException("native struct new outside @Stack lowering");
        }
        ConstructorSymbol ctor = (ConstructorSymbol) resolved;
        ClassSymbol cs = ctor.owner();
        // Inside a specialized body, new Foo<T>(...) must allocate Foo$P_...
        if (currentClass != null && currentClass.isSpecialized()
                && currentClass.genericOrigin() != null
                && cs.isGeneric() && !cs.isSpecialized()) {
            List<JType> specArgs = new ArrayList<>();
            if (!n.typeArguments().isEmpty()) {
                TypeSubstitution sub = TypeSubstitution.zip(
                        currentClass.genericOrigin().typeParameters(),
                        currentClass.specializationArgs());
                for (TypeName tn : n.typeArguments()) {
                    JType arg = resolveTypeNameInOwner(tn, false, currentClass);
                    specArgs.add(sub.apply(arg));
                }
            } else {
                specArgs = currentClass.specializationArgs();
            }
            if (!specArgs.isEmpty()) {
                String specName = GenericLowering.specializedName(cs, specArgs);
                ClassSymbol spec = typed.classes().get(specName);
                if (spec != null) {
                    ConstructorSymbol specCtor = null;
                    for (ConstructorSymbol c : spec.constructors()) {
                        if (c.parameters().size() == ctor.parameters().size()) {
                            specCtor = c;
                            break;
                        }
                    }
                    if (specCtor != null) {
                        ctor = specCtor;
                        cs = spec;
                    }
                }
            }
        }
        ClassLayout layout = cs.layout();
        IrValue obj = builder.newObject(
                cs.name(),
                layout.llvmTypeName(),
                layout.instanceSize(),
                cs.metadata().globalName());
        List<IrValue> args = new ArrayList<>();
        args.add(obj);
        for (int i = 0; i < n.arguments().size(); i++) {
            IrValue arg = lowerExpression(n.arguments().get(i));
            if (i < ctor.parameters().size()) {
                JType pt = remapType(ctor.parameters().get(i).type());
                JType from = remapType(typed.expressionTypes().get(n.arguments().get(i)));
                arg = convertTo(arg, from, pt);
            }
            args.add(arg);
        }
        builder.constructorCall(ctor.mangledName(), args);
        return obj;
    }

    private IrValue lowerNewArray(NewArrayExpression n) {
        JType arrayType = remapType(typed.expressionTypes().get(n));
        ArrayType at = (ArrayType) arrayType;
        JType elementType = at.elementType();
        ClassMetadata meta = arrayMetadataForElement(elementType);
        IrValue length = lowerExpression(n.length());
        length = castIfNeeded(length, IrType.I32);
        long elemSize = ObjectLayoutBuilder.sizeOf(elementType);
        return builder.newArray(
                IrType.from(elementType),
                length,
                elemSize,
                meta.globalName(),
                elementType.isReference());
    }

    private IrValue lowerArrayLoad(ArrayAccessExpression a) {
        IrValue array = lowerExpression(a.array());
        IrValue index = lowerExpression(a.index());
        JType elemType = remapType(typed.expressionTypes().get(a));
        return builder.loadElement(array, index, IrType.from(elemType));
    }

    private IrValue lowerInstanceOf(InstanceOfExpression i) {
        Object resolved = typed.resolvedSymbols().get(i);
        IrValue obj = lowerExpression(i.expression());
        if (resolved instanceof InterfaceMetadata im) {
            return builder.instanceOfInterface(obj, im.interfaceId());
        }
        ClassMetadata meta = (ClassMetadata) resolved;
        return builder.instanceOf(obj, meta.globalName());
    }

    private IrValue lowerCast(CastExpression c) {
        JType targetType = typed.expressionTypes().get(c);
        JType sourceType = typed.expressionTypes().get(c.expression());
        IrValue obj = lowerExpression(c.expression());
        if (targetType != null && targetType.isPrimitive()
                && sourceType != null && sourceType.isPrimitive()) {
            return lowerPrimitiveCast(obj, sourceType, targetType);
        }
        Object resolved = typed.resolvedSymbols().get(c);
        if (resolved instanceof NativePtrType || targetType instanceof NativePtrType) {
            // NativePtr casts are bitcasts at IR level (same machine pointer width).
            return castIfNeeded(obj, IrType.NATIVE_PTR);
        }
        if (resolved instanceof InterfaceMetadata im) {
            return builder.checkedCastInterface(obj, im.interfaceId());
        }
        ClassMetadata meta = (ClassMetadata) resolved;
        return builder.checkedCast(obj, meta.globalName());
    }

    /**
     * Numeric casts. Float/double→integer use runtime helpers (NaN→0, saturate).
     * Integers: narrow by truncation; widen with sext except {@code char} (zext).
     */
    private IrValue lowerPrimitiveCast(IrValue value, JType source, JType target) {
        IrType from = value.type();
        IrType to = IrType.from(target);
        if (from == to) {
            return value;
        }
        boolean srcFp = source == PrimitiveType.FLOAT || source == PrimitiveType.DOUBLE;
        boolean dstFp = target == PrimitiveType.FLOAT || target == PrimitiveType.DOUBLE;
        if (srcFp && target.isNumeric() && !dstFp) {
            // Emit Cast; LLVM uses jnative_f*_to_i* (NaN→0/saturate), WASM uses trunc_sat.
            if (target == PrimitiveType.LONG) {
                return builder.cast(value, IrType.I64);
            }
            IrValue i32 = builder.cast(value, IrType.I32);
            if (target == PrimitiveType.INT) {
                return i32;
            }
            if (target == PrimitiveType.CHAR) {
                return builder.cast(i32, IrType.I16, IrInstruction.CastMode.ZERO);
            }
            return castIfNeeded(i32, to);
        }
        if (!srcFp && dstFp) {
            return castIfNeeded(value, to); // sitofp in backend
        }
        if (srcFp && dstFp) {
            return castIfNeeded(value, to); // fpext / fptrunc
        }
        int fromW = bitWidthOf(from);
        int toW = bitWidthOf(to);
        if (fromW < toW && source == PrimitiveType.CHAR) {
            return builder.cast(value, to, IrInstruction.CastMode.ZERO);
        }
        return castIfNeeded(value, to);
    }

    private static int bitWidthOf(IrType t) {
        return switch (t) {
            case I1 -> 1;
            case I8 -> 8;
            case I16 -> 16;
            case I32, FLOAT -> 32;
            case I64, DOUBLE, PTR, NATIVE_PTR -> 64;
            case VOID -> 0;
        };
    }

    private IrValue lowerConditional(ConditionalExpression c) {
        JType resultJt = typed.expressionTypes().get(c);
        IrType resultType = resultJt == null ? IrType.I32 : IrType.from(resultJt);
        IrValue.Local slot = builder.alloca("tern.tmp" + System.identityHashCode(c), resultType);
        IrBasicBlock thenBlock = builder.newBlock("tern.then");
        IrBasicBlock elseBlock = builder.newBlock("tern.else");
        IrBasicBlock endBlock = builder.newBlock("tern.end");
        IrValue cond = lowerExpression(c.condition());
        builder.condBranch(cond, thenBlock, elseBlock);
        builder.setBlock(thenBlock);
        IrValue thenV = castIfNeeded(lowerExpression(c.thenExpr()), resultType);
        builder.store(thenV, slot);
        builder.branch(endBlock);
        builder.setBlock(elseBlock);
        IrValue elseV = castIfNeeded(lowerExpression(c.elseExpr()), resultType);
        builder.store(elseV, slot);
        builder.branch(endBlock);
        builder.setBlock(endBlock);
        return builder.load(slot);
    }

    private IrValue lowerNativeIntrinsic(NativeIntrinsic intrinsic, MethodCallExpression m) {
        return switch (intrinsic) {
            case ADDRESS_OF -> {
                Expression arg = m.arguments().get(0);
                if (arg instanceof VariableExpression v) {
                    IrValue.Local local = builder.local(v.name());
                    yield builder.addressOf(local);
                }
                yield castIfNeeded(lowerExpression(arg), IrType.NATIVE_PTR);
            }
            case LOAD_INT -> builder.nativeLoad(
                    lowerExpression(m.arguments().get(0)), IrType.I32);
            case STORE_INT -> {
                builder.nativeStore(
                        lowerExpression(m.arguments().get(0)),
                        castIfNeeded(lowerExpression(m.arguments().get(1)), IrType.I32));
                yield new IrValue.Const(IrType.I32, 0);
            }
            case ALLOCATE_STRUCT -> {
                TypeName tn = m.typeArguments().get(0);
                NativeStructSymbol ns = typed.requireNativeStruct(
                        tn.kind() == TypeName.Kind.CLASS ? tn.name() : tn.toString());
                // Also resolve via expression type
                JType ret = typed.expressionTypes().get(m);
                if (ret instanceof NativePtrType np && np.pointee() instanceof NativeStructType nst) {
                    ns = nst.symbol();
                }
                IrValue size = new IrValue.Const(IrType.I64, ns.layout().size());
                yield builder.call("jnative_native_alloc", IrType.NATIVE_PTR, List.of(size), false);
            }
        };
    }

    private void lowerSamInvokeThunk(IrFunction function, MethodSymbol ms) {
        builder.beginFunction(function);
        IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
        builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        IrValue receiver = builder.load(thisLocal);
        List<IrValue> invokeArgs = new ArrayList<>();
        for (var p : ms.parameters()) {
            IrType ty = IrType.from(p.type());
            IrValue.Local local = builder.alloca(p.name(), ty);
            builder.store(new IrValue.Param(p.name(), ty), local);
            invokeArgs.add(builder.load(local));
        }
        IrType ret = IrType.from(ms.returnType());
        if (ret == IrType.VOID) {
            builder.invokeCallable(receiver, ret, invokeArgs);
            builder.retVoid();
        } else {
            IrValue result = builder.invokeCallable(receiver, ret, invokeArgs);
            builder.ret(result);
        }
    }

    private IrValue lowerCall(MethodCallExpression m) {
        Object resolved = typed.resolvedSymbols().get(m);
        if (resolved instanceof NativeCallbackSite site) {
            IrValue callable = lowerExpression(m.arguments().getFirst());
            ClassSymbol handleClass = typed.requireClass("NativeCallbackHandle");
            return builder.createNativeCallback(
                    site.trampolineSymbol(),
                    callable,
                    site.invokeRuntime(),
                    site.callbackId(),
                    handleClass.metadata().globalName(),
                    handleClass.layout().instanceSize());
        }
        if (resolved instanceof NativeIntrinsic intrinsic) {
            return lowerNativeIntrinsic(intrinsic, m);
        }
        if (resolved instanceof InterfaceCallTarget ict) {
            IrValue receiver = m.receiver() != null
                    ? lowerExpression(m.receiver())
                    : builder.load(builder.local("this"));
            builder.nullCheck(receiver);
            List<IrValue> args = new ArrayList<>();
            args.add(receiver);
            List<JType> paramTypes = ict.parameterTypes();
            for (int i = 0; i < m.arguments().size(); i++) {
                IrValue arg = lowerExpression(m.arguments().get(i));
                if (i < paramTypes.size()) {
                    JType from = remapType(typed.expressionTypes().get(m.arguments().get(i)));
                    arg = convertTo(arg, from, remapType(paramTypes.get(i)));
                }
                args.add(arg);
            }
            return builder.interfaceCall(
                    receiver,
                    ict.interfaceSymbol().interfaceId(),
                    ict.slot(),
                    IrType.from(ict.returnType()),
                    args,
                    ict.interfaceSymbol().name() + "." + ict.interfaceMethod().name());
        }
        MethodSymbol ms = (MethodSymbol) resolved;
        List<IrValue> args = new ArrayList<>();
        IrValue receiver = null;
        if (!ms.isStatic()) {
            if (m.receiver() != null) {
                receiver = lowerExpression(m.receiver());
            } else {
                receiver = builder.load(builder.local("this"));
            }
            JType recvJ = m.receiver() != null
                    ? remapType(typed.expressionTypes().get(m.receiver()))
                    : null;
            // Primitive-specialized bodies: T.hashCode()/equals() lower to value ops (no boxing).
            if (recvJ instanceof PrimitiveType) {
                if ("hashCode".equals(ms.name()) && m.arguments().isEmpty()) {
                    return primitiveHashCode(receiver, (PrimitiveType) recvJ);
                }
                if ("equals".equals(ms.name()) && m.arguments().size() == 1) {
                    IrValue other = lowerExpression(m.arguments().getFirst());
                    other = castIfNeeded(other, IrType.from(recvJ));
                    return builder.compare(IrInstruction.CmpOp.EQ, receiver, other);
                }
            }
            if (!(recvJ instanceof PrimitiveType)) {
                builder.nullCheck(receiver);
            }
            args.add(receiver);
        }
        // Static call may still have a type-name receiver in AST (LibC.puts); ignore it.
        // Substitute class type args so ArrayList<Integer>.add expects Integer (for boxing).
        TypeSubstitution recvSub = null;
        if (m.receiver() != null) {
            JType recvTy = remapType(typed.expressionTypes().get(m.receiver()));
            if (recvTy instanceof ParameterizedType pt && pt.raw() instanceof ClassSymbol cs) {
                recvSub = TypeSubstitution.zip(cs.typeParameters(), pt.typeArguments());
            }
        }
        List<JType> paramTypes = ms.parameterTypes();
        for (int i = 0; i < m.arguments().size(); i++) {
            IrValue arg = lowerExpression(m.arguments().get(i));
            if (i < paramTypes.size()) {
                JType pt = paramTypes.get(i);
                if (recvSub != null) {
                    pt = recvSub.apply(pt);
                }
                pt = remapType(pt);
                JType from = remapType(typed.expressionTypes().get(m.arguments().get(i)));
                arg = convertTo(arg, from, pt);
            }
            args.add(arg);
        }

        if (ms.samInvokeRuntime() != null && receiver != null) {
            List<IrValue> invokeArgs = args.size() > 1 ? args.subList(1, args.size()) : List.of();
            return builder.invokeCallable(receiver, IrType.from(ms.returnType()), invokeArgs);
        }

        ClassSymbol ownerClass = ms.classOwner();
        if (ownerClass != null && ownerClass.name().equals("Throwable") && receiver != null) {
            if (ms.name().equals("getMessage") || ms.name().equals("getCause")) {
                ClassLayout layout = ownerClass.layout();
                int idx = ms.name().equals("getCause")
                        ? ownerClass.findField("cause").map(f -> f.gepIndex()).orElse(2)
                        : ownerClass.findField("message").map(f -> f.gepIndex()).orElse(1);
                return builder.loadField(
                        receiver,
                        ownerClass.name(),
                        layout.llvmTypeName(),
                        idx,
                        IrType.PTR);
            }
            if (ms.name().equals("getSuppressed")) {
                return builder.call("jnative_throwable_get_suppressed", IrType.PTR,
                        List.of(receiver), true);
            }
            if (ms.name().equals("addSuppressed")) {
                return builder.call("jnative_throwable_add_suppressed", IrType.VOID, args, true);
            }
        }

        // String intrinsics
        if (ownerClass != null && ownerClass.isBuiltin()
                && ownerClass.name().equals("String") && receiver != null) {
            return switch (ms.name()) {
                case "length" -> builder.call("jnative_string_length", IrType.I32, List.of(receiver));
                case "utf8Length" -> builder.call("jnative_string_utf8_length", IrType.I32, List.of(receiver));
                case "charAt" -> builder.call("jnative_string_char_at", IrType.I16, args);
                case "equals" -> builder.call("jnative_string_equals_object", IrType.I1, args);
                case "hashCode" -> builder.call("jnative_string_hashcode", IrType.I32, List.of(receiver));
                case "indexOf" -> builder.call("jnative_string_index_of", IrType.I32, args);
                case "indexOfFrom" -> builder.call("jnative_string_index_of_from", IrType.I32, args);
                case "substring" -> builder.call("jnative_string_substring", IrType.PTR, args);
                case "substringFrom" -> builder.call("jnative_string_substring_from", IrType.PTR, args);
                case "startsWith" -> i32ToI1(builder.call("jnative_string_starts_with", IrType.I32, args));
                case "endsWith" -> i32ToI1(builder.call("jnative_string_ends_with", IrType.I32, args));
                case "equalsIgnoreCase" -> i32ToI1(builder.call(
                        "jnative_string_equals_ignore_case", IrType.I32, args));
                case "trim" -> builder.call("jnative_string_trim", IrType.PTR, List.of(receiver));
                case "getBytes" -> builder.call("jnative_string_get_bytes", IrType.PTR, List.of(receiver));
                case "toLowerCase" -> builder.call("jnative_string_to_lower", IrType.PTR, List.of(receiver));
                case "isEmpty" -> i32ToI1(builder.call(
                        "jnative_string_is_empty", IrType.I32, List.of(receiver)));
                case "contains" -> i32ToI1(builder.call("jnative_string_contains", IrType.I32, args));
                case "lastIndexOf" -> builder.call("jnative_string_last_index_of", IrType.I32, args);
                case "lastIndexOfFrom" -> builder.call("jnative_string_last_index_of_from", IrType.I32, args);
                case "compareTo" -> builder.call("jnative_string_compare_to", IrType.I32, args);
                case "toUpperCase" -> builder.call("jnative_string_to_upper", IrType.PTR, List.of(receiver));
                case "replace" -> builder.call("jnative_string_replace", IrType.PTR, args);
                case "split" -> builder.call("jnative_string_split", IrType.PTR, args);
                default -> builder.call(ms.mangledName(), IrType.from(ms.returnType()), args);
            };
        }

        if (ms.isFfiIntrinsic()) {
            return lowerFfiIntrinsic(ms, args);
        }

        if (ms.isNative() && ms.nativeFunction() != null) {
            return lowerNativeCall(ms, args);
        }

        if (ms.isVirtual() && ms.vtableSlot() != null && receiver != null) {
            MethodSymbol slotMethod = ms;
            while (slotMethod.overrides() != null) {
                slotMethod = slotMethod.overrides();
            }
            int slot = slotMethod.vtableSlot() != null ? slotMethod.vtableSlot() : ms.vtableSlot();
            return builder.virtualCall(
                    receiver,
                    slot,
                    IrType.from(ms.returnType()),
                    args,
                    ms.owner().name() + "." + ms.name());
        }

        return builder.call(ms.mangledName(), IrType.from(ms.returnType()), args);
    }

    private IrValue lowerNativeCall(MethodSymbol ms, List<IrValue> javaArgs) {
        var nf = ms.nativeFunction();
        builder.module().addNativeDeclaration(nf);
        List<IrValue> abiArgs = new ArrayList<>();
        List<IrType> abiArgTypes = new ArrayList<>();
        List<IrValue> cstringTemps = new ArrayList<>();
        List<AbiType> paramAbis = nf.parameterAbis();
        for (int i = 0; i < javaArgs.size(); i++) {
            IrValue arg = javaArgs.get(i);
            AbiType abi = i < paramAbis.size() ? paramAbis.get(i) : AbiType.PTR;
            if (abi == AbiType.CSTRING) {
                IrValue cstr = builder.call(
                        "jnative_ffi_string_to_cstr", IrType.NATIVE_PTR, List.of(arg), false);
                abiArgs.add(cstr);
                abiArgTypes.add(IrType.NATIVE_PTR);
                cstringTemps.add(cstr);
            } else {
                IrType irt = switch (abi) {
                    case I32, U32 -> IrType.I32;
                    case I64, U64, SIZE -> IrType.I64;
                    case F64 -> IrType.DOUBLE;
                    case F32 -> IrType.DOUBLE;
                    case I16, U16 -> IrType.I16;
                    case I8, U8 -> IrType.I32;
                    case PTR, CSTRING -> IrType.NATIVE_PTR;
                    case VOID -> IrType.VOID;
                    default -> IrType.from(ms.parameters().get(i).type());
                };
                abiArgs.add(castIfNeeded(arg, irt));
                abiArgTypes.add(irt);
            }
        }
        IrType retIr = IrType.from(ms.returnType());
        if (nf.returnAbi() == AbiType.SIZE || nf.returnAbi() == AbiType.I64) {
            retIr = IrType.I64;
        } else if (nf.returnAbi() == AbiType.PTR) {
            retIr = IrType.NATIVE_PTR;
        } else if (nf.returnAbi() == AbiType.VOID) {
            retIr = IrType.VOID;
        }
        IrValue result = builder.nativeCall(
                nf.symbol(),
                retIr,
                abiArgs,
                abiArgTypes,
                nf.library().orElse(null));
        for (IrValue temp : cstringTemps) {
            builder.call("jnative_ffi_cstr_free", IrType.VOID, List.of(temp), false);
        }
        return result;
    }

    private IrValue lowerFfiIntrinsic(MethodSymbol ms, List<IrValue> args) {
        if ("File".equals(ms.owner().name())) {
            return lowerFileIntrinsic(ms, args);
        }
        if ("TcpSocket".equals(ms.owner().name()) || "TcpServer".equals(ms.owner().name())) {
            return lowerTcpIntrinsic(ms, args);
        }
        if ("Bytes".equals(ms.owner().name())) {
            return lowerBytesIntrinsic(ms, args);
        }
        if ("NativeCallbackHandle".equals(ms.owner().name())) {
            return lowerNativeCallbackHandleIntrinsic(ms, args);
        }
        if ("Thread".equals(ms.owner().name())) {
            return lowerThreadIntrinsic(ms, args);
        }
        if ("Mutex".equals(ms.owner().name())) {
            return lowerMutexIntrinsic(ms, args);
        }
        if ("Condition".equals(ms.owner().name())) {
            return lowerConditionIntrinsic(ms, args);
        }
        if ("Future".equals(ms.owner().name())) {
            return lowerFutureIntrinsic(ms, args);
        }
        if (ms.owner().name().startsWith("Atomic")) {
            return lowerAtomicIntrinsic(ms, args);
        }
        if ("String".equals(ms.owner().name())) {
            if ("fromBytes".equals(ms.name())) {
                return builder.call("jnative_string_from_bytes", IrType.PTR, List.of(args.get(0)), false);
            }
            if ("valueOf".equals(ms.name())) {
                if (!args.isEmpty() && args.get(0).type() == IrType.I64) {
                    return builder.call("jnative_string_value_of_i64", IrType.PTR, List.of(args.get(0)), false);
                }
                return builder.call("jnative_string_value_of_i32", IrType.PTR, List.of(args.get(0)), false);
            }
            if ("join".equals(ms.name())) {
                return builder.call("jnative_string_join", IrType.PTR, args, false);
            }
        }
        return switch (ms.name()) {
            case "allocate" -> builder.call(
                    "jnative_native_alloc", IrType.NATIVE_PTR, List.of(castIfNeeded(args.get(0), IrType.I64)), false);
            case "free" -> {
                builder.call("jnative_native_free", IrType.VOID, List.of(args.get(0)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "getInt" -> builder.call(
                    "jnative_native_get_i32",
                    IrType.I32,
                    List.of(args.get(0), castIfNeeded(args.get(1), IrType.I64)),
                    false);
            case "putInt" -> {
                builder.call(
                        "jnative_native_put_i32",
                        IrType.VOID,
                        List.of(args.get(0), castIfNeeded(args.get(1), IrType.I64), args.get(2)),
                        false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "getPtr" -> builder.call(
                    "jnative_native_get_ptr",
                    IrType.NATIVE_PTR,
                    List.of(args.get(0), castIfNeeded(args.get(1), IrType.I64)),
                    false);
            case "putPtr" -> {
                builder.call(
                        "jnative_native_put_ptr",
                        IrType.VOID,
                        List.of(args.get(0), castIfNeeded(args.get(1), IrType.I64), args.get(2)),
                        false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "fromCString" -> builder.call(
                    "jnative_ffi_cstr_to_string",
                    IrType.PTR,
                    List.of(args.get(0)),
                    false);
            default -> throw new IllegalStateException("unknown FFI intrinsic " + ms.name());
        };
    }

    private IrValue lowerNativeCallbackHandleIntrinsic(MethodSymbol ms, List<IrValue> args) {
        ClassSymbol handleClass = typed.requireClass("NativeCallbackHandle");
        ClassLayout layout = handleClass.layout();
        IrValue handle = args.getFirst();
        return switch (ms.name()) {
            case "pointer" -> {
                FieldSymbol handleField = handleClass.findField("handle").orElseThrow();
                IrValue bits = builder.loadField(
                        handle,
                        handleClass.name(),
                        layout.llvmTypeName(),
                        handleField.gepIndex(),
                        IrType.I64);
                yield castIfNeeded(bits, IrType.NATIVE_PTR);
            }
            case "close" -> {
                FieldSymbol slotField = handleClass.findField("slot").orElseThrow();
                FieldSymbol handleField = handleClass.findField("handle").orElseThrow();
                FieldSymbol retainedField = handleClass.findField("retained").orElseThrow();
                builder.releaseNativeCallback(
                        handle,
                        layout.llvmTypeName(),
                        slotField.gepIndex(),
                        handleField.gepIndex(),
                        retainedField.gepIndex());
                yield new IrValue.Const(IrType.I32, 0);
            }
            default -> throw new IllegalStateException("unknown NativeCallbackHandle intrinsic " + ms.name());
        };
    }

    private IrValue lowerThreadIntrinsic(MethodSymbol ms, List<IrValue> args) {
        return switch (ms.name()) {
            case "start" -> {
                builder.call("jnative_thread_start", IrType.VOID, List.of(args.get(0)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "join" -> {
                builder.call("jnative_thread_join", IrType.VOID, List.of(args.get(0)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "sleep" -> {
                builder.call("jnative_thread_sleep", IrType.VOID,
                        List.of(castIfNeeded(args.get(0), IrType.I64)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "currentThread" -> builder.call("jnative_thread_current", IrType.PTR, List.of(), false);
            case "id" -> builder.call("jnative_thread_id_of", IrType.I64, List.of(args.get(0)), false);
            default -> throw new IllegalStateException("unknown Thread intrinsic " + ms.name());
        };
    }

    private IrValue lowerMutexIntrinsic(MethodSymbol ms, List<IrValue> args) {
        return switch (ms.name()) {
            case "lock" -> {
                builder.call("jnative_mutex_lock", IrType.VOID, List.of(args.get(0)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "unlock" -> {
                builder.call("jnative_mutex_unlock", IrType.VOID, List.of(args.get(0)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "tryLock" -> builder.call("jnative_mutex_trylock", IrType.I1, List.of(args.get(0)), false);
            default -> throw new IllegalStateException("unknown Mutex intrinsic " + ms.name());
        };
    }

    private IrValue lowerAwait(AwaitExpression a) {
        if (asyncFrame == null) {
            throw new IllegalStateException("await outside async method");
        }
        JType resultJType = typed.expressionTypes().get(a);
        if (resultJType == null) {
            resultJType = ErrorType.INSTANCE;
        }
        IrType resultType = IrType.from(resultJType);
        IrValue future = lowerExpression(a.operand());
        int state = ++awaitStateCounter;
        IrBasicBlock resumeBlock = builder.newBlock("await.resume." + state);
        IrValue.Temp dest = builder.temp(resultType);
        builder.emit(new IrInstruction.Await(dest, future, state, resumeBlock.name(), resultType));
        builder.setBlock(resumeBlock);
        builder.emit(new IrInstruction.AsyncResume(state));
        return dest;
    }

    private IrValue lowerFutureIntrinsic(MethodSymbol ms, List<IrValue> args) {
        return switch (ms.name()) {
            case "completed" -> builder.call("jnative_future_completed", IrType.PTR, List.of(args.get(0)), false);
            case "pending" -> builder.call("jnative_future_pending", IrType.PTR, List.of(), false);
            case "failed" -> builder.call("jnative_future_failed", IrType.PTR, List.of(args.get(0)), false);
            case "isComplete" -> builder.call("jnative_future_isComplete", IrType.I1, List.of(args.get(0)), false);
            case "getState" -> builder.call("jnative_future_get_state", IrType.I32, List.of(args.get(0)), false);
            case "getCompleted" -> builder.call("jnative_future_getResult", IrType.PTR, List.of(args.get(0)), false);
            case "getError" -> builder.call("jnative_future_getError", IrType.PTR, List.of(args.get(0)), false);
            case "tryComplete" -> builder.call("jnative_future_try_complete", IrType.I1,
                    List.of(args.get(0), args.get(1)), false);
            case "tryFail" -> builder.call("jnative_future_try_fail", IrType.I1,
                    List.of(args.get(0), args.get(1)), false);
            case "tryCancel" -> builder.call("jnative_future_try_cancel", IrType.I1, List.of(args.get(0)), false);
            case "awaitCooperative" -> {
                builder.call("jnative_async_awaitCooperative", IrType.VOID, args, true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            default -> throw new IllegalStateException("unknown Future intrinsic " + ms.name());
        };
    }

    private IrValue lowerConditionIntrinsic(MethodSymbol ms, List<IrValue> args) {
        return switch (ms.name()) {
            case "await" -> {
                builder.call("jnative_cond_await", IrType.VOID, List.of(args.get(0), args.get(1)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "signal" -> {
                builder.call("jnative_cond_signal", IrType.VOID, List.of(args.get(0)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "signalAll" -> {
                builder.call("jnative_cond_signal_all", IrType.VOID, List.of(args.get(0)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            default -> throw new IllegalStateException("unknown Condition intrinsic " + ms.name());
        };
    }

    private IrValue lowerAtomicIntrinsic(MethodSymbol ms, List<IrValue> args) {
        String owner = ms.owner().name();
        String prefix = switch (owner) {
            case "AtomicInt" -> "jnative_atomic_int_";
            case "AtomicLong" -> "jnative_atomic_long_";
            case "AtomicBool" -> "jnative_atomic_bool_";
            default -> throw new IllegalStateException("unknown atomic class " + owner);
        };
        IrType ret = IrType.from(ms.returnType());
        return switch (ms.name()) {
            case "get" -> builder.call(prefix + "get", ret, List.of(args.get(0)), false);
            case "set" -> {
                builder.call(prefix + "set", IrType.VOID, List.of(args.get(0), args.get(1)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "compareAndSet" -> builder.call(
                    prefix + "cas", IrType.I1, List.of(args.get(0), args.get(1), args.get(2)), false);
            case "getAndIncrement" -> builder.call(prefix + "get_and_inc", ret, List.of(args.get(0)), false);
            case "incrementAndGet" -> builder.call(prefix + "inc_and_get", ret, List.of(args.get(0)), false);
            default -> throw new IllegalStateException("unknown atomic intrinsic " + ms.name());
        };
    }

    private IrValue lowerFileIntrinsic(MethodSymbol ms, List<IrValue> args) {
        // File.* → runtime; check pending exception after calls that may throw.
        return switch (ms.name()) {
            case "readString" -> builder.call(
                    "jnative_file_read_string", IrType.PTR, List.of(args.get(0)), true);
            case "readBytes" -> builder.call(
                    "jnative_file_read_bytes", IrType.PTR, List.of(args.get(0)), true);
            case "writeString" -> {
                builder.call("jnative_file_write_string", IrType.VOID,
                        List.of(args.get(0), args.get(1)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "writeBytes" -> {
                builder.call("jnative_file_write_bytes", IrType.VOID,
                        List.of(args.get(0), args.get(1)), true);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "exists" -> builder.call(
                    "jnative_file_exists", IrType.I1, List.of(args.get(0)), false);
            case "size" -> builder.call(
                    "jnative_file_size", IrType.I64, List.of(args.get(0)), true);
            case "delete" -> builder.call(
                    "jnative_file_delete", IrType.I1, List.of(args.get(0)), false);
            default -> throw new IllegalStateException("unknown File intrinsic " + ms.name());
        };
    }

    private IrValue lowerBytesIntrinsic(MethodSymbol ms, List<IrValue> args) {
        return switch (ms.name()) {
            case "fromString" -> builder.call(
                    "jnative_bytes_from_string", IrType.PTR, List.of(args.get(0)), false);
            case "toString" -> builder.call(
                    "jnative_bytes_to_string", IrType.PTR, List.of(args.get(0)), false);
            case "concat" -> builder.call(
                    "jnative_bytes_concat", IrType.PTR, List.of(args.get(0), args.get(1)), false);
            case "copy" -> {
                builder.call("jnative_bytes_copy", IrType.VOID, args, false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "indexOf2" -> builder.call(
                    "jnative_bytes_index_of2", IrType.I32, args, false);
            case "slice" -> builder.call(
                    "jnative_bytes_slice", IrType.PTR,
                    List.of(args.get(0), args.get(1), args.get(2)), false);
            default -> throw new IllegalStateException("unknown Bytes intrinsic " + ms.name());
        };
    }

    private IrValue lowerTcpIntrinsic(MethodSymbol ms, List<IrValue> args) {
        String owner = ms.owner().name();
        String name = ms.name();
        if ("TcpSocket".equals(owner)) {
            return switch (name) {
                case "connect" -> builder.call(
                        "jnative_tcp_connect", IrType.PTR, List.of(args.get(0), args.get(1)), true);
                case "connectTimeout" -> builder.call(
                        "jnative_tcp_connect_timeout", IrType.PTR,
                        List.of(args.get(0), args.get(1), args.get(2)), true);
                case "read" -> builder.call(
                        "jnative_tcp_read", IrType.I32, List.of(args.get(0), args.get(1)), true);
                case "readBytes" -> builder.call(
                        "jnative_tcp_read_bytes", IrType.PTR, List.of(args.get(0), args.get(1)), true);
                case "readAllBytes" -> builder.call(
                        "jnative_tcp_read_all_bytes", IrType.PTR, List.of(args.get(0)), true);
                case "readString" -> builder.call(
                        "jnative_tcp_read_string", IrType.PTR, List.of(args.get(0), args.get(1)), true);
                case "readAllString" -> builder.call(
                        "jnative_tcp_read_all_string", IrType.PTR, List.of(args.get(0)), true);
                case "write" -> {
                    builder.call("jnative_tcp_write", IrType.VOID, List.of(args.get(0), args.get(1)), true);
                    yield new IrValue.Const(IrType.I32, 0);
                }
                case "writeString" -> {
                    builder.call("jnative_tcp_write_string", IrType.VOID,
                            List.of(args.get(0), args.get(1)), true);
                    yield new IrValue.Const(IrType.I32, 0);
                }
                case "close" -> {
                    builder.call("jnative_tcp_close", IrType.VOID, List.of(args.get(0)), false);
                    yield new IrValue.Const(IrType.I32, 0);
                }
                case "isClosed" -> builder.call(
                        "jnative_tcp_is_closed", IrType.I1, List.of(args.get(0)), false);
                case "setReadTimeout" -> {
                    builder.call("jnative_tcp_set_read_timeout", IrType.VOID,
                            List.of(args.get(0), args.get(1)), true);
                    yield new IrValue.Const(IrType.I32, 0);
                }
                case "setWriteTimeout" -> {
                    builder.call("jnative_tcp_set_write_timeout", IrType.VOID,
                            List.of(args.get(0), args.get(1)), true);
                    yield new IrValue.Const(IrType.I32, 0);
                }
                case "nativeFd" -> builder.call(
                        "jnative_net_require_handle", IrType.I64, List.of(args.get(0)), true);
                default -> throw new IllegalStateException("unknown TcpSocket intrinsic " + name);
            };
        }
        return switch (name) {
            case "listen" -> builder.call(
                    "jnative_tcp_listen", IrType.PTR, List.of(args.get(0), args.get(1)), true);
            case "listenAny" -> builder.call(
                    "jnative_tcp_listen_any", IrType.PTR, List.of(args.get(0)), true);
            case "accept" -> builder.call(
                    "jnative_tcp_accept", IrType.PTR, List.of(args.get(0)), true);
            case "close" -> {
                builder.call("jnative_tcp_server_close", IrType.VOID, List.of(args.get(0)), false);
                yield new IrValue.Const(IrType.I32, 0);
            }
            case "isClosed" -> builder.call(
                    "jnative_tcp_server_is_closed", IrType.I1, List.of(args.get(0)), false);
            default -> throw new IllegalStateException("unknown TcpServer intrinsic " + name);
        };
    }

    private IrValue lowerLiteral(LiteralExpression l) {
        return switch (l.kind()) {
            case INT -> new IrValue.Const(IrType.I32, l.value());
            case LONG -> new IrValue.Const(IrType.I64, l.value());
            case FLOAT -> new IrValue.Const(IrType.FLOAT, l.value());
            case DOUBLE -> new IrValue.Const(IrType.DOUBLE, l.value());
            case BOOLEAN -> new IrValue.Const(IrType.I1, l.value());
            case STRING -> {
                String content = (String) l.value();
                IrValue.GlobalString gs = builder.stringConstant(content);
                int len = content.getBytes(StandardCharsets.UTF_8).length;
                yield builder.stringLiteral(gs, len);
            }
        };
    }

    private IrValue lowerBinary(BinaryExpression b) {
        if (b.operator() == BinaryOp.AND || b.operator() == BinaryOp.OR) {
            return lowerLogical(b);
        }
        if (b.operator() == BinaryOp.SHL || b.operator() == BinaryOp.SHR
                || b.operator() == BinaryOp.USHR) {
            return lowerShift(b);
        }

        // String concat (String+String, String+number, number+String)
        if (b.operator() == BinaryOp.ADD) {
            JType lt = typed.expressionTypes().get(b.left());
            JType rt = typed.expressionTypes().get(b.right());
            boolean leftStr = lt instanceof ClassType lc && lc.name().equals("String");
            boolean rightStr = rt instanceof ClassType rc && rc.name().equals("String");
            if (leftStr || rightStr) {
                IrValue left = lowerExpression(b.left());
                IrValue right = lowerExpression(b.right());
                if (!leftStr && lt != null && lt.isNumeric()) {
                    left = numericToString(left, lt);
                }
                if (!rightStr && rt != null && rt.isNumeric()) {
                    right = numericToString(right, rt);
                }
                return builder.call("jnative_string_concat", IrType.PTR, List.of(left, right));
            }
        }

        // Boolean & | ^ evaluate both sides (no short-circuit).
        JType lt = typed.expressionTypes().get(b.left());
        JType rt = typed.expressionTypes().get(b.right());
        if ((b.operator() == BinaryOp.BIT_AND || b.operator() == BinaryOp.BIT_OR
                || b.operator() == BinaryOp.BIT_XOR)
                && lt == PrimitiveType.BOOLEAN && rt == PrimitiveType.BOOLEAN) {
            IrValue left = lowerExpression(b.left());
            IrValue right = lowerExpression(b.right());
            IrInstruction.BinOp op = switch (b.operator()) {
                case BIT_AND -> IrInstruction.BinOp.AND;
                case BIT_OR -> IrInstruction.BinOp.OR;
                case BIT_XOR -> IrInstruction.BinOp.XOR;
                default -> throw new IllegalStateException();
            };
            return builder.binary(op, castIfNeeded(left, IrType.I1), castIfNeeded(right, IrType.I1));
        }

        IrValue left = lowerExpression(b.left());
        IrValue right = lowerExpression(b.right());
        // Unbox wrappers for arithmetic / relational / mixed == with primitives.
        boolean numericOp = b.operator() != BinaryOp.EQ && b.operator() != BinaryOp.NE
                && b.operator() != BinaryOp.BIT_AND && b.operator() != BinaryOp.BIT_OR
                && b.operator() != BinaryOp.BIT_XOR;
        boolean mixedEq = (b.operator() == BinaryOp.EQ || b.operator() == BinaryOp.NE)
                && ((Boxing.isWrapper(lt) && rt instanceof PrimitiveType)
                || (Boxing.isWrapper(rt) && lt instanceof PrimitiveType));
        if (numericOp || mixedEq) {
            if (Boxing.isWrapper(lt)) {
                PrimitiveType p = Boxing.primitiveOfWrapper(lt);
                left = emitUnbox(left, ((ClassType) lt).name(), p);
                lt = p;
            }
            if (Boxing.isWrapper(rt)) {
                PrimitiveType p = Boxing.primitiveOfWrapper(rt);
                right = emitUnbox(right, ((ClassType) rt).name(), p);
                rt = p;
            }
        }

        // Specialized generics: `T != null` when T is int — always false/true, never bitcast.
        if (b.operator() == BinaryOp.EQ || b.operator() == BinaryOp.NE) {
            boolean leftNull = isNullConst(right) && left.type().isIntegerLike();
            boolean rightNull = isNullConst(left) && right.type().isIntegerLike();
            if (leftNull || rightNull) {
                boolean eq = false; // primitive never equals null
                return new IrValue.Const(IrType.I1, b.operator() == BinaryOp.EQ ? eq : !eq);
            }
        }

        if ((b.operator() == BinaryOp.EQ || b.operator() == BinaryOp.NE)
                && (left.type() == IrType.PTR || left.type() == IrType.NATIVE_PTR)
                && (right.type() == IrType.PTR || right.type() == IrType.NATIVE_PTR)) {
            return builder.compare(
                    b.operator() == BinaryOp.EQ ? IrInstruction.CmpOp.EQ : IrInstruction.CmpOp.NE,
                    left, right);
        }

        // char widen uses zero-extend for bitwise/arithmetic with mixed types.
        IrType resultType = promote(left.type(), right.type());
        left = widenForBinary(left, lt, resultType);
        right = widenForBinary(right, rt, resultType);
        return switch (b.operator()) {
            case ADD -> builder.binary(IrInstruction.BinOp.ADD, left, right);
            case SUB -> builder.binary(IrInstruction.BinOp.SUB, left, right);
            case MUL -> builder.binary(IrInstruction.BinOp.MUL, left, right);
            case DIV -> builder.binary(IrInstruction.BinOp.DIV, left, right);
            case MOD -> builder.binary(IrInstruction.BinOp.MOD, left, right);
            case LT -> builder.compare(IrInstruction.CmpOp.LT, left, right);
            case LE -> builder.compare(IrInstruction.CmpOp.LE, left, right);
            case GT -> builder.compare(IrInstruction.CmpOp.GT, left, right);
            case GE -> builder.compare(IrInstruction.CmpOp.GE, left, right);
            case EQ -> builder.compare(IrInstruction.CmpOp.EQ, left, right);
            case NE -> builder.compare(IrInstruction.CmpOp.NE, left, right);
            case BIT_AND -> builder.binary(IrInstruction.BinOp.AND, left, right);
            case BIT_OR -> builder.binary(IrInstruction.BinOp.OR, left, right);
            case BIT_XOR -> builder.binary(IrInstruction.BinOp.XOR, left, right);
            case AND, OR, SHL, SHR, USHR -> throw new IllegalStateException();
        };
    }

    private IrValue widenForBinary(IrValue value, JType sourceType, IrType target) {
        if (value.type() == target) {
            return value;
        }
        if (sourceType == PrimitiveType.CHAR && bitWidthOf(value.type()) < bitWidthOf(target)) {
            return builder.cast(value, target, IrInstruction.CastMode.ZERO);
        }
        return castIfNeeded(value, target);
    }

    /**
     * Java shift: mask distance with 0x1f (int) or 0x3f (long); result type = promoted left.
     */
    private IrValue lowerShift(BinaryExpression b) {
        JType leftJt = typed.expressionTypes().get(b.left());
        IrValue left = lowerExpression(b.left());
        IrValue right = lowerExpression(b.right());
        IrType leftType = unaryPromoteIr(left.type());
        if (leftJt == PrimitiveType.CHAR) {
            left = builder.cast(left, leftType, IrInstruction.CastMode.ZERO);
        } else {
            left = castIfNeeded(left, leftType);
        }
        IrType distType = leftType == IrType.I64 ? IrType.I64 : IrType.I32;
        long mask = leftType == IrType.I64 ? 63L : 31L;
        right = castIfNeeded(right, distType);
        IrValue masked = builder.binary(IrInstruction.BinOp.AND, right,
                new IrValue.Const(distType, mask));
        IrInstruction.BinOp op = switch (b.operator()) {
            case SHL -> IrInstruction.BinOp.SHL;
            case SHR -> IrInstruction.BinOp.ASHR;
            case USHR -> IrInstruction.BinOp.LSHR;
            default -> throw new IllegalStateException();
        };
        return builder.binary(op, left, masked);
    }

    private static IrType unaryPromoteIr(IrType t) {
        if (t == IrType.I8 || t == IrType.I16 || t == IrType.I1) {
            return IrType.I32;
        }
        return t;
    }

    private IrValue lowerLogical(BinaryExpression b) {
        boolean isAnd = b.operator() == BinaryOp.AND;
        IrValue.Local result = builder.alloca("sc.tmp" + System.identityHashCode(b), IrType.I1);
        IrBasicBlock rhsBlock = builder.newBlock(isAnd ? "and.rhs" : "or.rhs");
        IrBasicBlock endBlock = builder.newBlock(isAnd ? "and.end" : "or.end");
        IrValue left = lowerExpression(b.left());
        builder.store(left, result);
        if (isAnd) {
            builder.condBranch(left, rhsBlock, endBlock);
        } else {
            builder.condBranch(left, endBlock, rhsBlock);
        }
        builder.setBlock(rhsBlock);
        IrValue right = lowerExpression(b.right());
        builder.store(right, result);
        builder.branch(endBlock);
        builder.setBlock(endBlock);
        return builder.load(result);
    }

    private IrValue lowerUnary(UnaryExpression u) {
        IrValue operand = lowerExpression(u.operand());
        return switch (u.operator()) {
            case NOT -> builder.not(operand);
            case NEGATE -> {
                IrType t = unaryPromoteIr(operand.type());
                IrValue v = castIfNeeded(operand, t);
                /* IEEE: (+0)-(+0)=+0; subtract from -0 so sign flip yields -0. */
                if (t == IrType.FLOAT || t == IrType.DOUBLE) {
                    IrValue negZero = t == IrType.FLOAT
                            ? new IrValue.Const(IrType.FLOAT, -0.0f)
                            : new IrValue.Const(IrType.DOUBLE, -0.0);
                    yield builder.binary(IrInstruction.BinOp.SUB, negZero, v);
                }
                yield builder.binary(IrInstruction.BinOp.SUB, defaultValue(t), v);
            }
            case PLUS -> {
                IrType t = unaryPromoteIr(operand.type());
                yield castIfNeeded(operand, t);
            }
            case BIT_NOT -> {
                JType src = typed.expressionTypes().get(u.operand());
                IrType t = unaryPromoteIr(operand.type());
                IrValue v = src == PrimitiveType.CHAR
                        ? builder.cast(operand, t, IrInstruction.CastMode.ZERO)
                        : castIfNeeded(operand, t);
                IrValue ones = new IrValue.Const(t, t == IrType.I64 ? -1L : -1);
                yield builder.binary(IrInstruction.BinOp.XOR, v, ones);
            }
        };
    }

    private IrType promote(IrType a, IrType b) {
        if (a == IrType.DOUBLE || b == IrType.DOUBLE) {
            return IrType.DOUBLE;
        }
        if (a == IrType.FLOAT || b == IrType.FLOAT) {
            return IrType.FLOAT;
        }
        if (a == IrType.I64 || b == IrType.I64) {
            return IrType.I64;
        }
        if (a == IrType.PTR || b == IrType.PTR) {
            return IrType.PTR;
        }
        if (a == IrType.NATIVE_PTR || b == IrType.NATIVE_PTR) {
            return IrType.NATIVE_PTR;
        }
        if (a == IrType.I16 || b == IrType.I16) {
            return IrType.I32;
        }
        if (a == IrType.I1 || b == IrType.I1) {
            return IrType.I1;
        }
        return IrType.I32;
    }

    private IrValue i32ToI1(IrValue v) {
        return builder.compare(IrInstruction.CmpOp.NE, v, new IrValue.Const(IrType.I32, 0));
    }

    private IrValue numericToString(IrValue value, JType type) {
        if (type == PrimitiveType.LONG) {
            return builder.call("jnative_string_value_of_i64", IrType.PTR,
                    List.of(castIfNeeded(value, IrType.I64)), false);
        }
        if (type == PrimitiveType.DOUBLE) {
            return builder.call("jnative_string_value_of_f64", IrType.PTR,
                    List.of(castIfNeeded(value, IrType.DOUBLE)), false);
        }
        if (type == PrimitiveType.FLOAT) {
            return builder.call("jnative_string_value_of_f32", IrType.PTR,
                    List.of(castIfNeeded(value, IrType.FLOAT)), false);
        }
        return builder.call("jnative_string_value_of_i32", IrType.PTR,
                List.of(castIfNeeded(value, IrType.I32)), false);
    }

    private IrValue castIfNeeded(IrValue value, IrType target) {
        // Specialized bodies may clear slots with null; map to zero for primitive storage.
        if (value instanceof IrValue.Const c
                && "null".equals(String.valueOf(c.value()))
                && target != IrType.PTR
                && target != IrType.NATIVE_PTR
                && target != IrType.VOID) {
            return defaultValue(target);
        }
        return builder.cast(value, target);
    }

    /**
     * Apply boxing/unboxing when {@code from}/{@code to} require it; otherwise IR cast.
     * Null unbox → {@code nullCheck} then NPE at runtime.
     */
    private IrValue convertTo(IrValue value, JType from, JType to) {
        if (to == null || to instanceof ErrorType) {
            return value;
        }
        to = remapType(to);
        if (from == null || from instanceof ErrorType) {
            from = jtypeFromIr(value.type());
        } else {
            from = remapType(from);
        }
        if (from == null) {
            return castIfNeeded(value, IrType.from(to));
        }
        Boxing.Kind kind = Boxing.boxingKind(to, from);
        if (kind == null || kind == Boxing.Kind.NONE) {
            if (to.isAssignableFrom(from) || from.isAssignableFrom(to)
                    || IrType.from(to) == value.type()) {
                return castIfNeeded(value, IrType.from(to));
            }
            return castIfNeeded(value, IrType.from(to));
        }
        return switch (kind) {
            case BOX -> emitBox(value, (PrimitiveType) from, ((ClassType) to).name());
            case WIDEN_BOX -> {
                PrimitiveType targetPrim = Boxing.primitiveOfWrapper(to);
                IrValue widened = castIfNeeded(value, IrType.from(targetPrim));
                yield emitBox(widened, targetPrim, ((ClassType) to).name());
            }
            case UNBOX -> emitUnbox(value, ((ClassType) from).name(), (PrimitiveType) to);
            case UNBOX_WIDEN -> {
                PrimitiveType boxed = Boxing.primitiveOfWrapper(from);
                IrValue unboxed = emitUnbox(value, ((ClassType) from).name(), boxed);
                yield castIfNeeded(unboxed, IrType.from(to));
            }
            case NONE -> castIfNeeded(value, IrType.from(to));
        };
    }

    private IrValue emitBox(IrValue primitive, PrimitiveType prim, String wrapperName) {
        ClassSymbol wrapper = typed.classes().get(wrapperName);
        if (wrapper == null) {
            throw new IllegalStateException("missing wrapper class " + wrapperName);
        }
        MethodSymbol valueOf = null;
        for (MethodSymbol ms : wrapper.findMethods("valueOf")) {
            if (ms.isStatic() && ms.parameterTypes().size() == 1
                    && ms.parameterTypes().getFirst() == prim) {
                valueOf = ms;
                break;
            }
        }
        if (valueOf == null) {
            throw new IllegalStateException(wrapperName + ".valueOf(" + prim + ") not found");
        }
        IrValue arg = castIfNeeded(primitive, IrType.from(prim));
        return builder.call(valueOf.mangledName(), IrType.PTR, List.of(arg), false);
    }

    private IrValue emitUnbox(IrValue boxed, String wrapperName, PrimitiveType prim) {
        ClassSymbol wrapper = typed.classes().get(wrapperName);
        if (wrapper == null) {
            throw new IllegalStateException("missing wrapper class " + wrapperName);
        }
        String method = Boxing.unboxMethod(wrapperName);
        MethodSymbol ms = wrapper.findMethod(method).orElse(null);
        if (ms == null) {
            throw new IllegalStateException(wrapperName + "." + method + " not found");
        }
        builder.nullCheck(boxed);
        return builder.call(ms.mangledName(), IrType.from(prim), List.of(boxed), false);
    }

    private static JType jtypeFromIr(IrType t) {
        if (t == null) {
            return null;
        }
        return switch (t) {
            case I1 -> PrimitiveType.BOOLEAN;
            case I8 -> PrimitiveType.BYTE;
            case I16 -> PrimitiveType.SHORT;
            case I32 -> PrimitiveType.INT;
            case I64 -> PrimitiveType.LONG;
            case FLOAT -> PrimitiveType.FLOAT;
            case DOUBLE -> PrimitiveType.DOUBLE;
            default -> null;
        };
    }

    private static boolean isNullConst(IrValue v) {
        return v instanceof IrValue.Const c
                && (c.type() == IrType.PTR || c.type() == IrType.NATIVE_PTR)
                && "null".equals(String.valueOf(c.value()));
    }

    private IrValue defaultValue(IrType type) {
        return switch (type) {
            case I1 -> new IrValue.Const(IrType.I1, false);
            case I8 -> new IrValue.Const(IrType.I8, 0);
            case I16 -> new IrValue.Const(IrType.I16, 0);
            case FLOAT -> new IrValue.Const(IrType.FLOAT, 0.0f);
            case I32 -> new IrValue.Const(IrType.I32, 0);
            case I64 -> new IrValue.Const(IrType.I64, 0L);
            case DOUBLE -> new IrValue.Const(IrType.DOUBLE, 0.0);
            case PTR -> new IrValue.Const(IrType.PTR, "null");
            case NATIVE_PTR -> new IrValue.Const(IrType.NATIVE_PTR, "null");
            case VOID -> null;
        };
    }
}
