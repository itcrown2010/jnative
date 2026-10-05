package io.jnative.ir;

import io.jnative.ast.*;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.ObjectLayoutBuilder;
import io.jnative.semantic.ArrayLengthSymbol;
import io.jnative.semantic.ArrayType;
import io.jnative.semantic.ClassMetadata;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.PrimitiveType;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.semantic.VoidType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lowers typed AST to JNative IR (0.4 runtime foundation).
 */
public final class IrGenerator {

    private TypedCompilationUnit typed;
    private IrBuilder builder;
    private ClassSymbol currentClass;
    private MethodSymbol currentMethod;
    private ConstructorSymbol currentConstructor;
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

        Map<MethodSymbol, MethodDeclaration> methodAst = new HashMap<>();
        Map<ConstructorSymbol, MethodDeclaration> ctorAst = new HashMap<>();

        for (ClassDeclaration cls : unit.classes()) {
            ClassSymbol classSymbol = typed.classes().get(cls.name());
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
                    List<IrValue.Param> params = new ArrayList<>();
                    if (!ms.isStatic()) {
                        params.add(new IrValue.Param("this", IrType.PTR));
                    }
                    for (var p : ms.parameters()) {
                        params.add(new IrValue.Param(p.name(), IrType.from(p.type())));
                    }
                    IrFunction function = new IrFunction(
                            ms.mangledName(), IrType.from(ms.returnType()), params, ms.isMain());
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

        currentClass = null;
        currentMethod = null;
        currentConstructor = null;
        return module;
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
            case INT -> PrimitiveType.INT;
            case LONG -> PrimitiveType.LONG;
            case DOUBLE -> PrimitiveType.DOUBLE;
            case BOOLEAN -> PrimitiveType.BOOLEAN;
            case CHAR -> PrimitiveType.CHAR;
            case CLASS -> {
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
            for (int i = 1; i < stmts.size(); i++) {
                lowerStatement(stmts.get(i));
            }
        } else {
            emitDelegation(ctor, thisLocal);
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

    private void lowerMethod(IrFunction function, MethodDeclaration method, MethodSymbol ms) {
        builder.beginFunction(function);

        if (!ms.isStatic()) {
            IrValue.Local thisLocal = builder.alloca("this", IrType.PTR);
            builder.store(new IrValue.Param("this", IrType.PTR), thisLocal);
        }
        for (var p : ms.parameters()) {
            if (ms.isMain() && p.type() instanceof ArrayType) {
                continue;
            }
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
            builder.branch(condBlock);
        }
        builder.setBlock(endBlock);
    }

    private IrValue lowerExpression(Expression expr) {
        return switch (expr) {
            case LiteralExpression l -> lowerLiteral(l);
            case NullLiteralExpression ignored -> new IrValue.Const(IrType.PTR, "null");
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
        };
    }

    private IrValue lowerVariable(VariableExpression v) {
        Object resolved = typed.resolvedSymbols().get(v);
        if (resolved instanceof FieldSymbol field) {
            IrValue thisPtr = builder.load(builder.local("this"));
            ClassLayout layout = field.owner().layout();
            return builder.loadField(
                    thisPtr,
                    field.owner().name(),
                    layout.llvmTypeName(),
                    field.gepIndex(),
                    IrType.from(field.type()));
        }
        IrValue.Local local = builder.local(v.name());
        if (local == null) {
            throw new IllegalStateException("missing local for " + v.name());
        }
        return builder.load(local);
    }

    private IrValue lowerAssignment(AssignmentExpression a) {
        IrValue value = lowerExpression(a.value());
        Expression target = a.target();
        if (target instanceof VariableExpression v) {
            Object resolved = typed.resolvedSymbols().get(v);
            if (resolved instanceof FieldSymbol field) {
                IrValue thisPtr = builder.load(builder.local("this"));
                ClassLayout layout = field.owner().layout();
                IrValue casted = castIfNeeded(value, IrType.from(field.type()));
                builder.storeField(thisPtr, field.owner().name(), layout.llvmTypeName(),
                        field.gepIndex(), casted);
                return casted;
            }
            IrValue.Local local = builder.local(v.name());
            IrValue casted = castIfNeeded(value, local.type());
            builder.store(casted, local);
            return casted;
        }
        if (target instanceof FieldAccessExpression f) {
            FieldSymbol field = (FieldSymbol) typed.resolvedSymbols().get(f);
            IrValue receiver = lowerExpression(f.receiver());
            ClassLayout layout = field.owner().layout();
            IrValue casted = castIfNeeded(value, IrType.from(field.type()));
            builder.storeField(receiver, field.owner().name(), layout.llvmTypeName(),
                    field.gepIndex(), casted);
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
        FieldSymbol field = (FieldSymbol) resolved;
        IrValue receiver = lowerExpression(f.receiver());
        ClassLayout layout = field.owner().layout();
        return builder.loadField(
                receiver,
                field.owner().name(),
                layout.llvmTypeName(),
                field.gepIndex(),
                IrType.from(field.type()));
    }

    private IrValue lowerNew(NewObjectExpression n) {
        ConstructorSymbol ctor = (ConstructorSymbol) typed.resolvedSymbols().get(n);
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
        ClassMetadata meta = (ClassMetadata) typed.resolvedSymbols().get(c);
        IrValue obj = lowerExpression(c.expression());
        return builder.checkedCast(obj, meta.globalName());
    }

    private IrValue lowerCall(MethodCallExpression m) {
        MethodSymbol ms = (MethodSymbol) typed.resolvedSymbols().get(m);
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
        List<JType> paramTypes = ms.parameterTypes();
        for (int i = 0; i < m.arguments().size(); i++) {
            IrValue arg = lowerExpression(m.arguments().get(i));
            if (i < paramTypes.size()) {
                arg = castIfNeeded(arg, IrType.from(paramTypes.get(i)));
            }
            args.add(arg);
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
                default -> builder.call(ms.mangledName(), IrType.from(ms.returnType()), args);
            };
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
                && left.type() == IrType.PTR && right.type() == IrType.PTR) {
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
            case I16 -> new IrValue.Const(IrType.I16, 0);
            case I32 -> new IrValue.Const(IrType.I32, 0);
            case I64 -> new IrValue.Const(IrType.I64, 0L);
            case DOUBLE -> new IrValue.Const(IrType.DOUBLE, 0.0);
            case PTR -> new IrValue.Const(IrType.PTR, "null");
            case VOID -> null;
        };
    }
}
