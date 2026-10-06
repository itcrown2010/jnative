package io.jnative.ir;

import io.jnative.ast.*;
import io.jnative.ffi.AbiType;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.semantic.ArrayLengthSymbol;
import io.jnative.semantic.ArrayType;
import io.jnative.semantic.CallableBinding;
import io.jnative.semantic.ClassMetadata;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ClosureCapture;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.FunctionType;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.NativeCallbackSite;
import io.jnative.semantic.NativeIntrinsic;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructFieldSymbol;
import io.jnative.semantic.NativeStructSymbol;
import io.jnative.semantic.NativeStructType;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.PrimitiveType;
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
import java.util.Set;

/**
 * Lowers typed AST to JNative IR (0.4 runtime foundation).
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
    /** Number of finally blocks currently being lowered (return-in-finally must not re-enter). */
    private int finallyDepth;

    private record TryFrame(
            IrBasicBlock dispatchBlock,
            IrBasicBlock afterBlock,
            IrBasicBlock finallyBlock,
            List<CatchClause> catches
    ) {
    }

    public IrModule generate(TypedCompilationUnit typed) {
        this.typed = typed;
        CompilationUnit unit = typed.unit();
        IrModule module = new IrModule(unit.fileName());
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
            ClassSymbol classSymbol = typed.classes().get(cls.name());
            if (classSymbol == null) {
                continue;
            }
            for (MethodDeclaration method : cls.methods()) {
                if (method.isConstructor()) {
                    ConstructorSymbol ctor = findCtor(classSymbol, method);
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
                    MethodSymbol ms = classSymbol.findMethod(method.name()).orElse(null);
                    if (ms == null) {
                        continue;
                    }
                    if (ms.isNative()) {
                        if (ms.nativeFunction() != null) {
                            module.addNativeDeclaration(ms.nativeFunction());
                        }
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
                    configureEffectFlags(function, ms);
                    module.addFunction(function);
                    functionsByMangled.put(ms.mangledName(), function);
                    methodAst.put(ms, method);
                }
            }
        }

        // Implicit constructors without AST
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
                lowerImplicitConstructor(function, ctor);
            }
        }

        for (var entry : methodAst.entrySet()) {
            MethodSymbol ms = entry.getKey();
            currentClass = ms.owner();
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
            for (FieldSymbol field : cls.declaredFields()) {
                if (!field.isStatic() || field.initializer() == null) {
                    continue;
                }
                currentClass = cls;
                currentMethod = null;
                IrValue value = lowerExpression(field.initializer());
                IrValue casted = castIfNeeded(value, IrType.from(field.type()));
                builder.storeStatic(field.staticGlobal(), casted, field.isVolatile());
            }
        }
        builder.retVoid();
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

    private void lowerBuiltinThrowableMethods(IrModule module, TypedCompilationUnit typed) {
        ClassSymbol throwable = typed.classes().get("Throwable");
        if (throwable == null) {
            return;
        }
        for (ConstructorSymbol ctor : throwable.constructors()) {
            if (functionsByMangled.containsKey(ctor.mangledName())) {
                continue;
            }
            if (ctor.isImplicit()) {
                IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID,
                        List.of(new IrValue.Param("this", IrType.PTR)), false);
                module.addFunction(function);
                builder.beginFunction(function);
                builder.retVoid();
                continue;
            }
            if (ctor.parameters().size() != 1) {
                continue;
            }
            List<IrValue.Param> params = List.of(
                    new IrValue.Param("this", IrType.PTR),
                    new IrValue.Param("message", IrType.PTR));
            IrFunction function = new IrFunction(ctor.mangledName(), IrType.VOID, params, false);
            module.addFunction(function);
            builder.beginFunction(function);
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            IrValue.Local msgLocal = builder.alloca("message", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
            builder.store(new IrValue.Param("message", IrType.PTR), msgLocal);
            ClassLayout layout = throwable.layout();
            builder.storeField(
                    builder.load(thisLocal),
                    throwable.name(),
                    layout.llvmTypeName(),
                    1,
                    builder.load(msgLocal));
            builder.retVoid();
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
            ClassLayout layout = throwable.layout();
            IrValue msg = builder.loadField(
                    builder.load(thisLocal),
                    throwable.name(),
                    layout.llvmTypeName(),
                    1,
                    IrType.PTR);
            builder.ret(msg);
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

    private ConstructorSymbol findCtor(ClassSymbol owner, MethodDeclaration method) {
        List<JType> pts = new ArrayList<>();
        for (Parameter p : method.parameters()) {
            pts.add(resolveTypeName(p.type(), p.isArray()));
        }
        for (ConstructorSymbol c : owner.constructors()) {
            if (c.parameterTypes().equals(pts)) {
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
            case CLASS -> {
                if ("NativePtr".equals(typeName.name())) {
                    yield NativePtrType.VOID;
                }
                NativeStructSymbol ns = typed.nativeStructs().get(typeName.name());
                if (ns != null) {
                    yield ns.asType();
                }
                ClassSymbol cs = typed.classes().get(typeName.name());
                yield cs != null ? cs.asType() : PrimitiveType.INT;
            }
        };
        return isArray ? new ArrayType(base) : base;
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

        if (!ms.isStatic()) {
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        }
        for (var p : ms.parameters()) {
            IrType irType = IrType.from(p.type());
            IrValue.Local local = builder.alloca(p.name(), irType);
            builder.store(new IrValue.Param(p.name(), irType), local);
        }

        lowerStatement(method.body());

        if (!builder.block().isTerminated()) {
            if (function.returnType() == IrType.VOID) {
                builder.retVoid();
            } else {
                builder.ret(defaultValue(function.returnType()));
            }
        }
        currentFunction = null;
    }

    private void lowerStatement(Statement stmt) {
        if (builder.block().isTerminated()) {
            return;
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
                IrValue.Local local = builder.alloca(v.name(), type);
                if (v.initializer() != null) {
                    IrValue value = lowerExpression(v.initializer());
                    builder.store(castIfNeeded(value, type), local);
                } else {
                    builder.store(defaultValue(type), local);
                }
            }
            case ExpressionStatement e -> lowerExpression(e.expression());
            case IfStatement i -> lowerIf(i);
            case WhileStatement w -> lowerWhile(w);
            case ForStatement f -> lowerFor(f);
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
        if (r.value() == null) {
            builder.retVoid();
        } else {
            IrValue value = lowerExpression(r.value());
            builder.ret(castIfNeeded(value, builder.function().returnType()));
        }
    }

    private TryFrame innermostFinallyFrame() {
        // Skip finally frames we are already inside so `return` in finally does not loop.
        int skip = finallyDepth;
        TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
        for (int i = frames.length - 1; i >= 0; i--) {
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
        IrBasicBlock afterBlock = builder.newBlock("try.after");
        IrBasicBlock dispatchBlock = builder.newBlock("try.dispatch");
        IrBasicBlock finallyBlock = t.finallyBlock() != null ? builder.newBlock("try.finally") : null;
        TryFrame frame = new TryFrame(dispatchBlock, afterBlock, finallyBlock, t.catches());
        tryFrames.push(frame);
        builder.pushExceptionHandler(dispatchBlock);

        lowerStatement(t.tryBlock());
        if (!builder.block().isTerminated()) {
            branchToFinallyOrAfter(finallyBlock, afterBlock);
        }

        builder.popExceptionHandler();

        // During catch bodies: route new throws to finally (if any), else outer handler.
        if (finallyBlock != null) {
            builder.pushExceptionHandler(finallyBlock);
        } else if (!tryFrames.isEmpty()) {
            // Outer try is beneath current frame (still on stack until we pop below).
            TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
            if (frames.length >= 2) {
                builder.pushExceptionHandler(frames[frames.length - 2].dispatchBlock());
            }
        }

        builder.setBlock(dispatchBlock);
        emitCatchHandlers(frame, finallyBlock, afterBlock);

        if (finallyBlock != null) {
            builder.popExceptionHandler();
            builder.setBlock(finallyBlock);
            // finally itself: exceptions go to outer handler
            if (!tryFrames.isEmpty()) {
                TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
                if (frames.length >= 2) {
                    builder.pushExceptionHandler(frames[frames.length - 2].dispatchBlock());
                }
            }
            finallyDepth++;
            lowerStatement(t.finallyBlock());
            finallyDepth--;
            if (!tryFrames.isEmpty()) {
                TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
                if (frames.length >= 2) {
                    builder.popExceptionHandler();
                }
            }
            if (!builder.block().isTerminated()) {
                emitFinallyEpilogue(frame);
            }
        } else if (!tryFrames.isEmpty()) {
            TryFrame[] frames = tryFrames.toArray(new TryFrame[0]);
            if (frames.length >= 2) {
                builder.popExceptionHandler();
            }
        }

        tryFrames.pop();
        builder.setBlock(afterBlock);
        // Uncaught by this try: propagate to outer handler or unwind the function.
        builder.maybeCheckPendingPublic();
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
        IrValue pending = builder.call("jnative_exception_pending", IrType.I1, List.of(), false);
        builder.condBranch(pending, propagate, checkReturn);

        builder.setBlock(propagate);
        builder.branch(frame.afterBlock());

        builder.setBlock(checkReturn);
        IrValue hasReturn = builder.hasReturnCompletion();
        IrBasicBlock resume = builder.newBlock("finally.resume");
        builder.condBranch(hasReturn, resume, frame.afterBlock());

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
        return resolveTypeName(v.type(), v.isArray());
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
        builder.setBlock(endBlock);
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
            case GcCollectExpression ignored -> {
                builder.gcCollect();
                yield new IrValue.Const(IrType.I32, 0);
            }
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
            env = lowerExpression(binding.receiverExpression());
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
        builder.beginFunction(function);
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
            IrValue receiver = new IrValue.Param("env", IrType.PTR);
            List<IrValue> callArgs = new ArrayList<>();
            callArgs.add(receiver);
            callArgs.addAll(samArgs);
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

    private IrValue lowerVariable(VariableExpression v) {
        Object resolved = typed.resolvedSymbols().get(v);
        if (resolved instanceof FieldSymbol field) {
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
        Expression target = a.target();
        if (target instanceof VariableExpression v) {
            Object resolved = typed.resolvedSymbols().get(v);
            if (resolved instanceof FieldSymbol field) {
                IrValue casted = castIfNeeded(value, IrType.from(field.type()));
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
            IrValue casted = castIfNeeded(value, local.type());
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
            FieldSymbol field = (FieldSymbol) resolved;
            IrValue casted = castIfNeeded(value, IrType.from(field.type()));
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
            JType elemType = typed.expressionTypes().get(aexpr);
            IrValue casted = castIfNeeded(value, IrType.from(elemType));
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
        FieldSymbol field = (FieldSymbol) resolved;
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
                arg = castIfNeeded(arg, IrType.from(ctor.parameters().get(i).type()));
            }
            args.add(arg);
        }
        builder.constructorCall(ctor.mangledName(), args);
        return obj;
    }

    private IrValue lowerNewArray(NewArrayExpression n) {
        ClassMetadata meta = (ClassMetadata) typed.resolvedSymbols().get(n);
        JType elem = typed.expressionTypes().get(n);
        ArrayType at = (ArrayType) elem;
        IrValue length = lowerExpression(n.length());
        length = castIfNeeded(length, IrType.I32);
        long elemSize = ObjectLayoutBuilder.sizeOf(at.elementType());
        return builder.newArray(
                IrType.from(at.elementType()),
                length,
                elemSize,
                meta.globalName(),
                at.elementType().isReference());
    }

    private IrValue lowerArrayLoad(ArrayAccessExpression a) {
        IrValue array = lowerExpression(a.array());
        IrValue index = lowerExpression(a.index());
        JType elemType = typed.expressionTypes().get(a);
        return builder.loadElement(array, index, IrType.from(elemType));
    }

    private IrValue lowerInstanceOf(InstanceOfExpression i) {
        ClassMetadata meta = (ClassMetadata) typed.resolvedSymbols().get(i);
        IrValue obj = lowerExpression(i.expression());
        return builder.instanceOf(obj, meta.globalName());
    }

    private IrValue lowerCast(CastExpression c) {
        Object resolved = typed.resolvedSymbols().get(c);
        IrValue obj = lowerExpression(c.expression());
        if (resolved instanceof NativePtrType || typed.expressionTypes().get(c) instanceof NativePtrType) {
            // NativePtr casts are bitcasts at IR level (same machine pointer width).
            return castIfNeeded(obj, IrType.NATIVE_PTR);
        }
        ClassMetadata meta = (ClassMetadata) resolved;
        return builder.checkedCast(obj, meta.globalName());
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
        MethodSymbol ms = (MethodSymbol) resolved;
        List<IrValue> args = new ArrayList<>();
        IrValue receiver = null;
        if (!ms.isStatic()) {
            if (m.receiver() != null) {
                receiver = lowerExpression(m.receiver());
            } else {
                receiver = builder.load(builder.local("this"));
            }
            builder.nullCheck(receiver);
            args.add(receiver);
        }
        // Static call may still have a type-name receiver in AST (LibC.puts); ignore it.
        List<JType> paramTypes = ms.parameterTypes();
        for (int i = 0; i < m.arguments().size(); i++) {
            IrValue arg = lowerExpression(m.arguments().get(i));
            if (i < paramTypes.size()) {
                arg = castIfNeeded(arg, IrType.from(paramTypes.get(i)));
            }
            args.add(arg);
        }

        if (ms.samInvokeRuntime() != null && receiver != null) {
            List<IrValue> invokeArgs = args.size() > 1 ? args.subList(1, args.size()) : List.of();
            return builder.invokeCallable(receiver, IrType.from(ms.returnType()), invokeArgs);
        }

        if (ms.owner().name().equals("Throwable") && ms.name().equals("getMessage") && receiver != null) {
            ClassLayout layout = ms.owner().layout();
            return builder.loadField(
                    receiver,
                    ms.owner().name(),
                    layout.llvmTypeName(),
                    1,
                    IrType.PTR);
        }

        // String intrinsics
        if (ms.owner().isBuiltin() && ms.owner().name().equals("String") && receiver != null) {
            return switch (ms.name()) {
                case "length" -> builder.call("jnative_string_length", IrType.I32, List.of(receiver));
                case "charAt" -> builder.call("jnative_string_char_at", IrType.I16, args);
                case "equals" -> builder.call("jnative_string_equals", IrType.I1, args);
                case "indexOf" -> builder.call("jnative_string_index_of", IrType.I32, args);
                case "indexOfFrom" -> builder.call("jnative_string_index_of_from", IrType.I32, args);
                case "substring" -> builder.call("jnative_string_substring", IrType.PTR, args);
                case "substringFrom" -> builder.call("jnative_string_substring_from", IrType.PTR, args);
                case "startsWith" -> builder.call("jnative_string_starts_with", IrType.I1, args);
                case "endsWith" -> builder.call("jnative_string_ends_with", IrType.I1, args);
                case "equalsIgnoreCase" -> builder.call("jnative_string_equals_ignore_case", IrType.I1, args);
                case "trim" -> builder.call("jnative_string_trim", IrType.PTR, List.of(receiver));
                case "getBytes" -> builder.call("jnative_string_get_bytes", IrType.PTR, List.of(receiver));
                case "toLowerCase" -> builder.call("jnative_string_to_lower", IrType.PTR, List.of(receiver));
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
        if (ms.owner().name().startsWith("Atomic")) {
            return lowerAtomicIntrinsic(ms, args);
        }
        if ("String".equals(ms.owner().name())) {
            if ("fromBytes".equals(ms.name())) {
                return builder.call("jnative_string_from_bytes", IrType.PTR, List.of(args.get(0)), false);
            }
            if ("valueOf".equals(ms.name())) {
                return builder.call("jnative_string_value_of_i32", IrType.PTR, List.of(args.get(0)), false);
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

        // String concat
        if (b.operator() == BinaryOp.ADD) {
            JType lt = typed.expressionTypes().get(b.left());
            JType rt = typed.expressionTypes().get(b.right());
            if (lt instanceof ClassType lc && lc.name().equals("String")
                    && rt instanceof ClassType rc && rc.name().equals("String")) {
                IrValue left = lowerExpression(b.left());
                IrValue right = lowerExpression(b.right());
                return builder.call("jnative_string_concat", IrType.PTR, List.of(left, right));
            }
        }

        IrValue left = lowerExpression(b.left());
        IrValue right = lowerExpression(b.right());

        if ((b.operator() == BinaryOp.EQ || b.operator() == BinaryOp.NE)
                && (left.type() == IrType.PTR || left.type() == IrType.NATIVE_PTR)
                && (right.type() == IrType.PTR || right.type() == IrType.NATIVE_PTR)) {
            return builder.compare(
                    b.operator() == BinaryOp.EQ ? IrInstruction.CmpOp.EQ : IrInstruction.CmpOp.NE,
                    left, right);
        }

        IrType resultType = promote(left.type(), right.type());
        left = castIfNeeded(left, resultType);
        right = castIfNeeded(right, resultType);
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
            case AND, OR -> throw new IllegalStateException();
        };
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
            case NEGATE -> builder.binary(IrInstruction.BinOp.SUB, defaultValue(operand.type()), operand);
            case PLUS -> operand;
        };
    }

    private IrType promote(IrType a, IrType b) {
        if (a == IrType.DOUBLE || b == IrType.DOUBLE) {
            return IrType.DOUBLE;
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

    private IrValue castIfNeeded(IrValue value, IrType target) {
        return builder.cast(value, target);
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
